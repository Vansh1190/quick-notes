package com.vansh.quicknotes.domain.ai

import com.vansh.quicknotes.domain.lists.ProposedItem
import com.vansh.quicknotes.domain.lists.ProposedList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Feature layer (spec §8). Every feature = prompt -> strict JSON parse -> preview object the
 * UI shows before anything is written (principle 3: AI assists, never surprises). Parsing is
 * defensive: a malformed reply degrades to the v1 heuristic stubs, never a crash and never a
 * half-applied edit.
 */
class AiService(private val client: AiClient) {

    // ---- 1. Dump & Sort -------------------------------------------------------------

    suspend fun dumpAndSort(dump: String, existingTitles: List<String>): List<ProposedList> {
        val prompt = """
            You organize a brain-dump into todo lists. Reply with ONLY a JSON array, no prose:
            [{"title":"Groceries","items":[{"text":"milk"},{"text":"eggs"}]}]
            Rules:
            - Split the dump into 1-5 sensible lists; reuse an existing list title (exact string)
              when the items belong there. Existing lists: ${existingTitles.joinToString("; ").ifBlank { "(none)" }}
            - Keep item text short and imperative ("buy milk", "send invoice to Rahul").
            - Language: keep items in the user's language (English/Hindi/Hinglish as spoken).
            Dump:
            $dump
        """.trimIndent()
        return try {
            val result = client.chat(listOf(ChatMessage("user", prompt)), jsonMode = true)
            val array = JsonExtractor.firstJson(result.content) as? JsonArray ?: return emptyList()
            array.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val title = JsonExtractor.str(o, "title") ?: return@mapNotNull null
                val items = (o["items"] as? JsonArray)?.mapNotNull { i ->
                    val io = i as? JsonObject
                    val text = io?.let { JsonExtractor.str(it, "text") } ?: (i as? JsonPrimitive)?.contentOrNull
                    text?.takeIf { it.isNotBlank() }?.let { ProposedItem(text.trim()) }
                }.orEmpty()
                if (items.isEmpty()) null else ProposedList(title.trim(), items)
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    // ---- 5. Auto title + tags -------------------------------------------------------

    data class TitleTags(val title: String, val tags: List<String>)

    suspend fun titleAndTags(bodyText: String): TitleTags? {
        val prompt = """
            For the note text below reply ONLY JSON: {"title":"...", "tags":["a","b"]}
            - title: max 8 words, no trailing punctuation, capture the actual topic
            - tags: 1-3 short lowercase topic tags
            Note text:
            ${bodyText.take(4_000)}
        """.trimIndent()
        return try {
            val r = client.chat(listOf(ChatMessage("user", prompt)), jsonMode = true, maxTokens = 200)
            val o = JsonExtractor.obj(r.content) ?: return null
            val title = JsonExtractor.str(o, "title")?.trim()?.trimEnd('.', ',', ':', '-') ?: return null
            val tags = (o["tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                ?.map { it.lowercase().trim() }?.filter { it.isNotBlank() }?.take(3).orEmpty()
            TitleTags(title, tags)
        } catch (t: Throwable) {
            null
        }
    }

    // ---- 4. Clean Up (transcript/rough text -> tidy prose) ----------------------------

    suspend fun cleanUp(text: String, mode: String = "tidy"): String? {
        val instruction = when (mode) {
            "summarize" -> "Write a 2-4 sentence summary."
            "shorten" -> "Keep it, but cut the length by half."
            "grammar" -> "Only fix grammar/spelling; change nothing else."
            else -> "Rewrite as clean, readable note prose. Keep every fact, name, number and task. Do not invent anything."
        }
        val prompt = "$instruction Reply with ONLY the rewritten text, no commentary.\n\n" + text.take(8_000)
        return try {
            client.chat(listOf(ChatMessage("user", prompt)), maxTokens = 1_200).content.ifBlank { null }
        } catch (t: Throwable) {
            null
        }
    }

    // ---- 6. Link Essence --------------------------------------------------------------

    data class LinkEssence(val summary: String, val points: List<String>)

    suspend fun linkEssence(url: String, text: String): LinkEssence? {
        val prompt = """
            Summarize this page text for a personal notes app. Reply ONLY JSON:
            {"summary":"max 3 lines","points":["key point 1","key point 2"]}
            URL: $url
            Text:
            ${text.take(6_000)}
        """.trimIndent()
        return try {
            val r = client.chat(listOf(ChatMessage("user", prompt)), jsonMode = true, maxTokens = 500)
            val o = JsonExtractor.obj(r.content) ?: return null
            val summary = JsonExtractor.str(o, "summary") ?: return null
            val points = (o["points"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
            LinkEssence(summary.trim(), points.map { it.trim() }.filter { it.isNotBlank() }.take(5))
        } catch (t: Throwable) {
            null
        }
    }

    // ---- 3. Ask My Notes --------------------------------------------------------------

    data class AnswerWithCites(val answer: String, val noteIds: List<String>)

    suspend fun askMyNotes(question: String, context: List<Pair<String, String>>): AnswerWithCites? {
        val block = context.joinToString("\n\n") { (idTitle, snippet) -> "[$idTitle]\n$snippet" }
        val prompt = """
            Answer using ONLY the note excerpts. Reply ONLY JSON:
            {"answer":"...", "sources":["noteId1"]}  (sources are the [ids] you used, max 3)
            Question: $question
            Excerpts:
            ${block.take(7_000)}
        """.trimIndent()
        return try {
            val r = client.chat(listOf(ChatMessage("user", prompt)), jsonMode = true, maxTokens = 700)
            val o = JsonExtractor.obj(r.content) ?: return null
            val answer = JsonExtractor.str(o, "answer") ?: return null
            val sources = (o["sources"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
            AnswerWithCites(answer.trim(), sources)
        } catch (t: Throwable) {
            null
        }
    }

    // ---- 7. Loose Ends ----------------------------------------------------------------

    data class LooseEnd(val text: String, val noteId: String)

    suspend fun looseEnds(notes: List<Triple<String, String, String>>): List<LooseEnd> {
        val block = notes.joinToString("\n\n") { (id, title, body) -> "[$id] $title\n${body.take(600)}" }
        val prompt = """
            Find unfinished obligations in these notes: phrases like "need to", "should", "must",
            "remember to", "buy", "call", "send", "fix". Reply ONLY JSON:
            [{"text":"short task","noteId":"the [id] it came from"}]  Max 8, skip vague ones.
            $block
        """.trimIndent()
        return try {
            val r = client.chat(listOf(ChatMessage("user", prompt)), jsonMode = true, maxTokens = 600)
            val array = JsonExtractor.firstJson(r.content) as? JsonArray ?: return emptyList()
            array.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val text = JsonExtractor.str(o, "text") ?: return@mapNotNull null
                val noteId = JsonExtractor.str(o, "noteId") ?: return@mapNotNull null
                LooseEnd(text.trim(), noteId)
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    // ---- 2. Snap to Structure ----------------------------------------------------------

    data class Snippet(val kind: String, val fields: Map<String, String>, val display: String)

    suspend fun snapToStructure(ocrText: String): Snippet? {
        val prompt = """
            Classify this OCR'd text (receipt / contact / event / other) and extract fields.
            Reply ONLY JSON: {"kind":"contact|receipt|event|other","fields":{"name":"...","phone":"..."}}
            contact: name, phone, email, address if present. receipt: merchant, date, items (one string, comma separated), total. event: title, date, time, location.
            Text:
            ${ocrText.take(4_000)}
        """.trimIndent()
        return try {
            val r = client.chat(listOf(ChatMessage("user", prompt)), jsonMode = true, maxTokens = 500)
            val o = JsonExtractor.obj(r.content) ?: return null
            val kind = JsonExtractor.str(o, "kind")?.lowercase() ?: return null
            val fields = (o["fields"] as? JsonObject)?.mapNotNull { (k, v) ->
                (v as? JsonPrimitive)?.contentOrNull?.let { k to it }
            }?.toMap().orEmpty()
            if (fields.isEmpty() || kind == "other") return null
            Snippet(kind, fields, fields.entries.joinToString("\n") { "${it.key}: ${it.value}" })
        } catch (t: Throwable) {
            null
        }
    }

    // ---- 8. Daily Recap ------------------------------------------------------------------

    suspend fun dailyRecap(todayCaptures: List<Pair<String, String>>, tomorrowDue: List<String>): String? {
        val prompt = buildString {
            appendLine("Write a calm daily recap for the user. 3-6 short sentences, plain text, no markdown.")
            appendLine("First what they captured today, then what is due tomorrow. No unsolicited advice.")
            appendLine("Captured today (title - snippet):")
            appendLine(todayCaptures.joinToString("\n") { (t, s) -> "- $t - ${s.take(120)}" }.ifBlank { "- (nothing - say so lightly)" })
            appendLine("Due tomorrow:")
            appendLine(tomorrowDue.joinToString("\n") { "- $it" }.ifBlank { "- (nothing)" })
        }
        return try {
            client.chat(listOf(ChatMessage("user", prompt)), maxTokens = 400).content.ifBlank { null }
        } catch (t: Throwable) {
            null
        }
    }

    suspend fun testConnection(): String =
        client.chat(listOf(ChatMessage("user", "Reply with exactly: OK")), maxTokens = 30).content
}
