package com.vansh.quicknotes.domain.ai

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiServiceTest {

    private class FakeClient(private vararg val replies: String) : AiClient {
        var calls = 0
        var configured = true
        override fun isConfigured() = configured
        override suspend fun chat(
            messages: List<ChatMessage>,
            temperature: Double,
            maxTokens: Int,
            jsonMode: Boolean,
            toolName: String?,
            toolDescription: String,
            toolSchema: kotlinx.serialization.json.JsonObject?,
        ): ChatResult {
            val reply = replies[minOf(calls, replies.lastIndex)]
            calls += 1
            return ChatResult(reply, "stop", 10)
        }
    }

    @Test
    fun `dump and sort parses clean json reply`() = runTest {
        val service = AiService(
            FakeClient("""[{"title":"Groceries","items":[{"text":"milk"},{"text":"eggs"}]},{"title":"Work","items":[{"text":"send invoice"}]}]"""),
        )
        val lists = service.dumpAndSort("milk, eggs, send invoice", listOf("Work"))
        assertEquals(2, lists.size)
        assertEquals("Groceries", lists[0].title)
        assertEquals(listOf("milk", "eggs"), lists[0].items.map { it.text })
    }

    @Test
    fun `dump and sort tolerates prose wrapped json`() = runTest {
        val service = AiService(
            FakeClient("Sure! Here you go:\n[{\"title\":\"Groceries\",\"items\":[{\"text\":\"milk\"}]}]\nEnjoy!"),
        )
        val lists = service.dumpAndSort("milk", emptyList())
        assertEquals(1, lists.size)
    }

    @Test
    fun `dump and sort degrades to empty on garbage`() = runTest {
        val service = AiService(FakeClient("I cannot help with that."))
        assertTrue(service.dumpAndSort("milk", emptyList()).isEmpty())
    }

    @Test
    fun `title and tags parses and lowercases tags`() = runTest {
        val service = AiService(FakeClient("""{"title":"Dentist Friday","tags":["Health ","dentist"]}"""))
        val tt = service.titleAndTags("call the dentist on friday")!!
        assertEquals("Dentist Friday", tt.title)
        assertEquals(listOf("health", "dentist"), tt.tags)
    }

    @Test
    fun `title and tags null on bad json`() = runTest {
        val service = AiService(FakeClient("not json at all"))
        assertNull(service.titleAndTags("text"))
    }

    @Test
    fun `link essence parses summary and points`() = runTest {
        val service = AiService(FakeClient("""{"summary":"Three lines here","points":["a","b","c","d","e","f"]}"""))
        val e = service.linkEssence("https://x.com", "text")!!
        assertEquals("Three lines here", e.summary)
        assertEquals(5, e.points.size)
    }

    @Test
    fun `ask my notes extracts cited sources`() = runTest {
        val service = AiService(FakeClient("""{"answer":"You booked the 14th","sources":["n1","n2"]}"""))
        val r = service.askMyNotes("when is the trip", listOf("n1|Trip" to "we booked the 14th"))!!
        assertEquals(listOf("n1", "n2"), r.noteIds)
    }

    @Test
    fun `snap to structure classifies contact`() = runTest {
        val service = AiService(FakeClient("""{"kind":"contact","fields":{"name":"Rahul","phone":"+91 98... "}}"""))
        val s = service.snapToStructure("Rahul +91 9876543210")!!
        assertEquals("contact", s.kind)
        assertEquals("Rahul", s.fields["name"])
    }

    @Test
    fun `snap to structure rejects other`() = runTest {
        val service = AiService(FakeClient("""{"kind":"other","fields":{}}"""))
        assertNull(service.snapToStructure("random scribbles"))
    }

    @Test
    fun `loose ends parses tasks`() = runTest {
        val service = AiService(FakeClient("""[{"text":"buy milk","noteId":"n1"},{"text":"call bank","noteId":"n2"}]"""))
        val ends = service.looseEnds(listOf(Triple("n1", "Today", "need to buy milk"), Triple("n2", "Errands", "must call bank")))
        assertEquals(2, ends.size)
        assertEquals("n2", ends[1].noteId)
    }

    @Test
    fun `normalize reasoning strips think blocks and fences`() {
        assertEquals("answer", HttpAiClient.normalizeReasoning("reasoning\n</think>\nanswer"))
        assertEquals("{\"a\":1}", HttpAiClient.normalizeReasoning("```json\n{\"a\":1}\n```"))
        assertEquals("plain", HttpAiClient.normalizeReasoning("plain"))
    }

    @Test
    fun `json extractor handles braces inside strings`() {
        val el = JsonExtractor.obj("""here: {"text":"has } brace","n":2} trailing""")!!
        assertEquals("has } brace", (el["text"] as kotlinx.serialization.json.JsonPrimitive).content)
    }
}
