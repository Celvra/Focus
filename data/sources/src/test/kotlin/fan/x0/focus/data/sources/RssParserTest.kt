package fan.x0.focus.data.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class RssParserTest {
    private val parser = RssParser()

    @Test
    fun parsesRssItems() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/">
              <channel>
                <title>Example Feed</title>
                <link>https://example.com</link>
                <item>
                  <title><![CDATA[ First <b>post</b> ]]></title>
                  <link>https://example.com/one</link>
                  <guid isPermaLink="false">abc-1</guid>
                  <description><![CDATA[<p>Summary text</p>]]></description>
                  <dc:creator>Ada</dc:creator>
                  <pubDate>Mon, 10 Jun 2024 09:00:00 GMT</pubDate>
                </item>
                <item>
                  <title>Second post</title>
                  <link>https://example.com/two</link>
                  <pubDate>Tue, 11 Jun 2024 12:30:00 +0800</pubDate>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val feed = parser.parse(ByteArrayInputStream(xml.toByteArray()))

        assertEquals("Example Feed", feed.title)
        assertEquals(2, feed.articles.size)
        val first = feed.articles[0]
        assertEquals("First <b>post</b>", first.title)
        assertEquals("https://example.com/one", first.url)
        assertEquals("Ada", first.author)
        assertEquals("Summary text", first.summary)
        assertNotNull(first.publishedAt)
    }

    @Test
    fun parsesAtomEntries() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Atom Feed</title>
              <entry>
                <title>Entry one</title>
                <link rel="alternate" href="https://example.org/entry-1"/>
                <author><name>Grace</name></author>
                <summary>Short summary</summary>
                <content type="html">&lt;p&gt;Body text&lt;/p&gt;</content>
                <published>2024-06-10T09:00:00Z</published>
              </entry>
            </feed>
        """.trimIndent()

        val feed = parser.parse(ByteArrayInputStream(xml.toByteArray()))

        assertEquals("Atom Feed", feed.title)
        assertEquals(1, feed.articles.size)
        val entry = feed.articles[0]
        assertEquals("Entry one", entry.title)
        assertEquals("https://example.org/entry-1", entry.url)
        assertEquals("Grace", entry.author)
        assertEquals("Body text", entry.content)
        assertNotNull(entry.publishedAt)
    }

    @Test
    fun skipsEntriesWithoutHttpLink() {
        val xml = """
            <rss version="2.0"><channel><title>t</title>
              <item><title>no link</title><guid>urn:uuid:1</guid></item>
            </channel></rss>
        """.trimIndent()

        val feed = parser.parse(ByteArrayInputStream(xml.toByteArray()))

        assertTrue(feed.articles.isEmpty())
    }

    @Test
    fun parsesRfc822Date() {
        val millis = parser.parseDate("Mon, 10 Jun 2024 09:00:00 GMT")
        assertEquals(1718010000000L, millis)
    }
}
