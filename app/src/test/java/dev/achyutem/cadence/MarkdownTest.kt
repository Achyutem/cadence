package dev.achyutem.cadence

import dev.achyutem.cadence.domain.notes.Markdown
import dev.achyutem.cadence.domain.notes.MarkdownBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Markdown subset.
 *
 * The important property is not that it parses correct documents; it is that it never mangles an
 * *incorrect* one. Notes are edited character by character, so the parser sees half-typed syntax
 * constantly and must leave it alone rather than reformatting the rest of the note.
 */
class MarkdownTest {

    @Test
    fun `headings parse to their level`() {
        val blocks = Markdown.parse("# One\n## Two\n### Three")
        assertEquals(1, (blocks[0] as MarkdownBlock.Heading).level)
        assertEquals(2, (blocks[1] as MarkdownBlock.Heading).level)
        assertEquals(3, (blocks[2] as MarkdownBlock.Heading).level)
    }

    @Test
    fun `four hashes is not a heading`() {
        assertTrue(Markdown.parse("#### Four")[0] is MarkdownBlock.Paragraph)
    }

    @Test
    fun `task items are recognised before bullets`() {
        // "- [ ] x" also matches a plain bullet; order of testing decides which wins.
        val block = Markdown.parse("- [ ] Buy milk")[0]
        assertTrue(block is MarkdownBlock.TaskItem)
        assertFalse((block as MarkdownBlock.TaskItem).checked)
    }

    @Test
    fun `checked tasks are recognised in both cases`() {
        assertTrue((Markdown.parse("- [x] Done")[0] as MarkdownBlock.TaskItem).checked)
        assertTrue((Markdown.parse("- [X] Done")[0] as MarkdownBlock.TaskItem).checked)
    }

    @Test
    fun `bullets accept both markers`() {
        assertTrue(Markdown.parse("- one")[0] is MarkdownBlock.BulletItem)
        assertTrue(Markdown.parse("* one")[0] is MarkdownBlock.BulletItem)
    }

    @Test
    fun `numbered items keep their number`() {
        val block = Markdown.parse("3. third")[0] as MarkdownBlock.NumberedItem
        assertEquals(3, block.number)
    }

    @Test
    fun `bold and italic are distinguished`() {
        val spans = Markdown.parseInline("**bold** and *italic*")
        assertTrue(spans.any { it.bold && it.text == "bold" })
        assertTrue(spans.any { it.italic && it.text == "italic" })
    }

    @Test
    fun `an unclosed marker stays literal`() {
        // Typing "**" mid-sentence must not make the rest of the note bold.
        val spans = Markdown.parseInline("this is **not closed")
        assertTrue(spans.none { it.bold })
        assertEquals("this is **not closed", spans.joinToString("") { it.text })
    }

    @Test
    fun `an empty marker pair stays literal`() {
        val spans = Markdown.parseInline("a ** b")
        assertTrue(spans.none { it.bold })
    }

    @Test
    fun `inline code is not further parsed`() {
        val spans = Markdown.parseInline("`a * b * c`")
        assertEquals(1, spans.size)
        assertTrue(spans[0].code)
        assertEquals("a * b * c", spans[0].text)
    }

    @Test
    fun `an unterminated code fence runs to the end rather than being dropped`() {
        val blocks = Markdown.parse("```\nstill typing")
        assertEquals(1, blocks.size)
        assertEquals("still typing", (blocks[0] as MarkdownBlock.CodeBlock).code)
    }

    @Test
    fun `horizontal rules are recognised`() {
        assertTrue(Markdown.parse("---")[0] is MarkdownBlock.Rule)
        assertTrue(Markdown.parse("***")[0] is MarkdownBlock.Rule)
    }

    @Test
    fun `plain text strips syntax for the preview`() {
        val preview = Markdown.toPlainText("# Title\n\n- **bold** item\n- second")
        assertEquals("Title bold item second", preview)
    }

    @Test
    fun `plain text truncates with an ellipsis`() {
        val preview = Markdown.toPlainText("x".repeat(500), limit = 20)
        assertTrue(preview.endsWith("…"))
        assertTrue(preview.length <= 21)
    }

    @Test
    fun `toggling a checkbox rewrites only that line`() {
        val source = "- [ ] one\n- [ ] two\n- [ ] three"
        val toggled = Markdown.toggleTaskAtLine(source, 1)
        assertEquals("- [ ] one\n- [x] two\n- [ ] three", toggled)
    }

    @Test
    fun `toggling a checked box unchecks it`() {
        assertEquals("- [ ] one", Markdown.toggleTaskAtLine("- [x] one", 0))
    }

    @Test
    fun `toggling a line that is not a task changes nothing`() {
        val source = "just a paragraph"
        assertEquals(source, Markdown.toggleTaskAtLine(source, 0))
    }

    @Test
    fun `toggling out of range changes nothing`() {
        val source = "- [ ] one"
        assertEquals(source, Markdown.toggleTaskAtLine(source, 9))
    }

    @Test
    fun `parsing never throws on arbitrary input`() {
        listOf("", "   ", "***", "```", "- [", "#", "~~~~", "**_*~`", "\n\n\n").forEach { input ->
            Markdown.parse(input)
            Markdown.toPlainText(input)
        }
    }
}
