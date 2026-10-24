package fan.x0.focus.data.assistant

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ModelCatalogMatchTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun indexOf(body: String): JsonObject = json.parseToJsonElement(body).jsonObject

    private val index = indexOf(
        """
        {
          "moonshotai": {"id":"moonshotai","api":"https://api.moonshot.ai/v1","models":{}},
          "openrouter": {"id":"openrouter","api":"https://openrouter.ai/api/v1","models":{}},
          "custom": {"id":"custom","api":"https://gateway.example.com/v1","models":{}}
        }
        """.trimIndent(),
    )

    @Test
    fun `matches provider by id`() {
        assertNotNull(modelsDevProvider(index, "openrouter", ""))
    }

    @Test
    fun `matches provider by alias`() {
        assertEquals(
            "moonshotai",
            modelsDevProvider(index, "kimi", "")?.get("id")?.toString()?.trim('"'),
        )
    }

    @Test
    fun `matches provider by api host`() {
        assertEquals(
            "custom",
            modelsDevProvider(index, "unknown-id", "https://gateway.example.com/v1")?.get("id")
                ?.toString()?.trim('"'),
        )
        assertNull(modelsDevProvider(index, "unknown-id", "https://nope.example.org/v1"))
    }

    @Test
    fun `matches model key with date suffix and namespace`() {
        val keys = listOf("zai-org/GLM-5.3", "gpt-5", "claude-sonnet-4-20250514")
        assertEquals("gpt-5", bestModelKey(keys, "gpt-5"))
        assertEquals("claude-sonnet-4-20250514", bestModelKey(keys, "anthropic/claude-sonnet-4"))
        assertEquals("gpt-5", bestModelKey(keys, "gpt-5-latest"))
        assertNull(bestModelKey(keys, "unknown"))
    }
}