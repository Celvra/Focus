package fan.x0.focus.data.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCatalogPolicyTest {

    @Test
    fun `destructive tools require approval`() {
        val gated = ToolCatalog.all.filter { it.kind == ToolKind.Gated }.map { it.name }.toSet()
        assertEquals(
            setOf(
                "create_calendar_event",
                "delete_calendar_event",
                "delete_article",
                "remove_feed",
                "delete_goal",
                "delete_update",
                "delete_idea",
                "delete_file",
            ),
            gated,
        )
    }

    @Test
    fun `plan tool is the only plan kind`() {
        val plans = ToolCatalog.all.filter { it.kind == ToolKind.Plan }.map { it.name }
        assertEquals(listOf("create_plan"), plans)
    }

    @Test
    fun `onboarding tools stay minimal`() {
        val names = ToolCatalog.onboarding().map { it.name }.toSet()
        assertEquals(
            setOf(
                "save_profile",
                "save_memory",
                "set_assistant_name",
                "list_providers",
                "save_provider",
                "test_provider",
                "list_models",
                "complete_onboarding",
            ),
            names,
        )
    }

    @Test
    fun `every tool has a description and object schema`() {
        assertTrue(ToolCatalog.all.isNotEmpty())
        ToolCatalog.all.forEach { spec ->
            assertTrue(spec.name.isNotBlank())
            assertTrue(spec.description.isNotBlank())
            assertEquals("object", spec.parameters["type"].toString().trim('"'))
        }
    }
}
