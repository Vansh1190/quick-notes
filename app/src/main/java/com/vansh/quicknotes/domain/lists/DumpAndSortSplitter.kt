package com.vansh.quicknotes.domain.lists

/**
 * "Dump & Sort" (spec §7): turn one spoken/typed stream into several lists.
 *
 * The real sort is the user's LLM (M6, feature 1). This heuristic splitter is the honest
 * v1 stub and the preview-card fallback when offline: sentence split → list buckets by
 * simple topical keywords → item extraction on comma/and/"also". Date phrases resolve via
 * [DatePhraseParser]. English/Hindi/Hinglish coverage is limited; the LLM path is the
 * answer there, so the splitter stays conservative rather than guessing wildly.
 */
data class ProposedList(
    val title: String,
    val items: List<ProposedItem>,
)

data class ProposedItem(
    val text: String,
    val dueAt: Long? = null,
    /** matched existing list title, if the item belongs to one */
    val existingListTitle: String? = null,
)

object DumpAndSortSplitter {

    private val sentenceSplit = Regex("[.!?\n]+")
    private val itemSplit = Regex(",|\\band\\b|\\bplus\\b|\\bऔर\\b|\\baur\\b", RegexOption.IGNORE_CASE)

    private val categories = listOf(
        "Groceries" to listOf("buy", "milk", "eggs", "bread", "grocery", "groceries", "vegetables", "sabzi", "doodh", "atta"),
        "Work" to listOf("work", "office", "invoice", "email", "meeting", "deploy", "bug", "client", "boss", "send the"),
        "Home" to listOf("home", "house", "clean", "fix", "repair", "ghar", "saman"),
        "Personal" to listOf("call", "mom", "dad", "friend", "birthday", "doctor", "gym", "sham", "evening"),
    )

    fun split(
        text: String,
        existingListTitles: List<String> = emptyList(),
        now: Long = System.currentTimeMillis(),
        timeZone: java.util.TimeZone = java.util.TimeZone.getDefault(),
    ): List<ProposedList> {
        val sentences = text.split(sentenceSplit).map { it.trim() }.filter { it.isNotEmpty() }
        if (sentences.isEmpty()) return emptyList()

        data class Bucket(val title: String, var matchedExisting: String? = null) {
            val items = mutableListOf<ProposedItem>()
        }
        val buckets = LinkedHashMap<String, Bucket>()

        for (sentence in sentences) {
            val (title, matchedExisting) = categorize(sentence, existingListTitles)
            val bucket = buckets.getOrPut(title) { Bucket(title, matchedExisting) }
            val stripped = stripLeadNoise(sentence)
            val pieces = stripped.split(itemSplit).map { it.trim().trim('.', ' ') }.filter { it.length > 1 }
            val due = DatePhraseParser.parse(stripped, now, timeZone)
            val chosen = if (pieces.size >= 2) pieces else listOf(stripped)
            chosen.forEach { piece ->
                bucket.items += ProposedItem(
                    text = cleanItem(piece),
                    dueAt = due ?: DatePhraseParser.parse(piece, now, timeZone),
                    existingListTitle = bucket.matchedExisting,
                )
            }
        }
        return buckets.map { (title, b) -> ProposedList(title, b.items) }
    }

    /** "For work, …" / "For groceries: …" explicit category prefixes. */
    private val explicitCategory = Regex("^\\s*for\\s+([a-z]+)\\b", RegexOption.IGNORE_CASE)

    private fun categorize(sentence: String, existing: List<String>): Pair<String, String?> {
        val lower = sentence.lowercase()
        explicitCategory.find(lower)?.let { m ->
            val word = m.groupValues[1]
            categories.firstOrNull { (title, _) -> title.lowercase() == word }?.let { return it.first to null }
            existing.firstOrNull { it.lowercase() == word }?.let { return it to it }
        }
        val norm = (" " + sentence.lowercase().replace(Regex("[^a-z ]"), " ") + " ").replace(Regex("\\s+"), " ")
        existing
            .map { t -> t to wordsOf(t).count { norm.contains(" $it ") } }
            .filter { (_, hits) -> hits > 0 }
            .maxByOrNull { (_, hits) -> hits }
            ?.let { (t, _) -> return t to t }
        val (title, words) = categories.maxByOrNull { (_, ws) -> ws.count { lower.contains(it) } } ?: return "Personal" to null
        return if (words.any { lower.contains(it) }) title to null else "Personal" to null
    }

    private fun stripLeadNoise(sentence: String): String {
        var s = sentence.trim()
        val noise = listOf("need to", "i need to", "i have to", "have to", "also", "for work", "for groceries", "reminder to", "remember to")
        var changed = true
        while (changed) {
            changed = false
            for (n in noise) {
                if (s.startsWith(n, ignoreCase = true)) {
                    s = s.drop(n.length).trim(' ', ',', ':', '-')
                    changed = true
                }
            }
        }
        return s
    }

    private fun cleanItem(piece: String): String {
        val s = piece.trim().trim('.', ',', ' ')
        return s.replaceFirstChar { it.uppercase() }
    }
}

    private fun wordsOf(title: String): List<String> =
        title.lowercase().replace(Regex("[^a-z ]"), " ").split(Regex("\\s+")).filter { it.length > 2 }
