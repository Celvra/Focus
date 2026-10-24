package fan.x0.focus.core.network.openai

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenAiModelCatalogTest {

    @Test
    fun `reads openai data array`() {
        val body = """{"object":"list","data":[{"id":"gpt-4o"},{"id":"gpt-4o-mini"}]}"""
        assertEquals(listOf("gpt-4o", "gpt-4o-mini"), parseModelIds(body))
    }

    @Test
    fun `reads models array and display names`() {
        val body = """{"models":[{"name":"llama3:8b"},{"id":"mistral"}]}"""
        assertEquals(listOf("llama3:8b", "mistral"), parseModelIds(body))
    }

    @Test
    fun `reads bare arrays and string data`() {
        assertEquals(listOf("a", "b"), parseModelIds("""["b","a"]"""))
        assertEquals(listOf("a", "b"), parseModelIds("""{"data":["a","b"]}"""))
    }

    @Test
    fun `deduplicates sorts and drops blanks`() {
        val body = """{"data":[{"id":"b"},{"id":"a"},{"id":"b"},{"id":" "}]}"""
        assertEquals(listOf("a", "b"), parseModelIds(body))
    }

    @Test
    fun `models url replaces chat completions suffix`() {
        assertEquals(
            "https://api.openai.com/v1/models",
            ModelEndpoint("https://api.openai.com/v1", "gpt-4o").modelsUrl,
        )
        assertEquals(
            "https://api.openai.com/v1/models",
            ModelEndpoint("https://api.openai.com/v1/chat/completions/", "gpt-4o").modelsUrl,
        )
    }

    @Test
    fun `parses openrouter metadata`() {
        val body = """
            {"data":[{"id":"z/x","description":"hi","context_length":200000,
            "architecture":{"input_modalities":["text","image"]},
            "pricing":{"prompt":"0.000002","completion":"0.00001"},
            "supported_parameters":["tools","reasoning_effort"],
            "knowledge_cutoff":"2024-09"}]}
        """.trimIndent()
        val meta = parseModelEntries(body).single().meta
        assertEquals(200000, meta.contextWindow)
        assertEquals(2.0, meta.inputPrice!!, 1e-9)
        assertEquals(10.0, meta.outputPrice!!, 1e-9)
        assertEquals(true, meta.supportsImage)
        assertEquals(true, meta.supportsTools)
        assertEquals(true, meta.supportsReasoning)
        assertEquals("2024-09", meta.knowledgeCutoff)
        assertEquals("hi", meta.description)
    }

    @Test
    fun `reads per million cost and creation epoch`() {
        val body = """{"data":[{"id":"m","cost":{"input":1.25,"output":10},"limit":{"context":128000},"created":1690848000}]}"""
        val meta = parseModelEntries(body).single().meta
        assertEquals(1.25, meta.inputPrice!!, 1e-9)
        assertEquals(10.0, meta.outputPrice!!, 1e-9)
        assertEquals(128000, meta.contextWindow)
        assertEquals("2023-08", meta.releaseDate)
    }

    @Test
    fun `fills missing fields from fallback`() {
        val upstream = ModelMeta(contextWindow = 1000, supportsTools = true)
        val fallback = ModelMeta(contextWindow = 2000, inputPrice = 3.0, supportsTools = false)
        val merged = upstream.fillMissingFrom(fallback)
        assertEquals(1000, merged.contextWindow)
        assertEquals(3.0, merged.inputPrice!!, 1e-9)
        assertEquals(true, merged.supportsTools)
    }

    @Test
    fun `epoch year month handles epoch and leap boundaries`() {
        assertEquals("1970-01", epochYearMonth(0))
        assertEquals("2024-02", epochYearMonth(1708387200))
    }
}
