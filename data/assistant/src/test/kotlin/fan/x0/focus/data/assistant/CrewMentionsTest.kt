package fan.x0.focus.data.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CrewMentionsTest {

    private val members = listOf(
        member("a1", "Ada"),
        member("b1", "Builder"),
        member("c1", "Builder Pro"),
    )

    @Test
    fun matchesNamesCaseInsensitive() {
        assertEquals(listOf("a1"), CrewMentions.ids("please ask @ada about it", members))
    }

    @Test
    fun ignoresPartialWords() {
        assertEquals(emptyList<String>(), CrewMentions.ids("email me at x@adax.com", members))
    }

    @Test
    fun picksLongestNameFirst() {
        assertEquals(listOf("c1"), CrewMentions.ids("@Builder Pro check this", members))
    }

    @Test
    fun mentionsEveryoneOnce() {
        assertEquals(setOf("a1", "b1", "c1"), CrewMentions.ids("@all please look", members).toSet())
        assertEquals(3, CrewMentions.ids("@all please look", members).size)
    }

    @Test
    fun collectsMultipleMentions() {
        assertEquals(
            listOf("a1", "b1"),
            CrewMentions.ids("@Ada and @Builder, both of you", members),
        )
    }

    @Test
    fun queryNeedsTrailingAt() {
        assertEquals("", CrewMentions.query("@"))
        assertEquals("ad", CrewMentions.query("hello @ad"))
        assertNull(CrewMentions.query("mail x@y"))
        assertNull(CrewMentions.query("@ada\nnext line"))
    }

    @Test
    fun parsesAvatarCodes() {
        assertEquals(CrewAvatarChoice(2, 3), CrewAvatars.parse("2:3", 0))
        assertEquals(CrewAvatarChoice(2, 3), CrewAvatars.parse("2,3", 0))
        assertEquals(CrewAvatars.defaultFor(5), CrewAvatars.parse(null, 5))
        val fallback = CrewAvatars.defaultFor(5)
        assertEquals(fallback, CrewAvatars.parse("bad", 5))
        assertEquals(CrewAvatarChoice(1, 0), CrewAvatars.parse("9:6", 0))
    }

    private fun member(id: String, name: String) = CrewMember(
        id = id,
        groupId = "g",
        name = name,
        duty = "",
        avatarPreset = 0,
        avatarPalette = 0,
        avatarImage = "",
        position = 0,
        active = true,
    )
}
