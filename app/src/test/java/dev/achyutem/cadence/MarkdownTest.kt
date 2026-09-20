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

    // --- List continuation ---

    @Test
    fun `pressing enter in a bullet continues the list`() {
        val (text, caret) = Markdown.continueListOnNewline("- one", caret = 5)!!
        assertEquals("- one\n- ", text)
        assertEquals(8, caret)
    }

    @Test
    fun `a task item continues unchecked, never carrying the tick forward`() {
        // The next item is a new thing to do, not an already-done one.
        val (text, _) = Markdown.continueListOnNewline("- [x] done", caret = 10)!!
        assertEquals("- [x] done\n- [ ] ", text)
    }

    @Test
    fun `a numbered item increments`() {
        val (text, _) = Markdown.continueListOnNewline("3. third", caret = 8)!!
        assertEquals("3. third\n4. ", text)
    }

    @Test
    fun `indentation is carried forward`() {
        val (text, _) = Markdown.continueListOnNewline("  - nested", caret = 10)!!
        assertEquals("  - nested\n  - ", text)
    }

    @Test
    fun `pressing enter on an empty item ends the list`() {
        // Two Enters gets you out, which is what every editor that does this converges on.
        val (text, caret) = Markdown.continueListOnNewline("- one\n- ", caret = 8)!!
        assertEquals("- one\n", text)
        assertEquals(6, caret)
    }

    @Test
    fun `a plain paragraph is left alone`() {
        assertEquals(null, Markdown.continueListOnNewline("just text", caret = 9))
    }

    @Test
    fun `continuation works mid-document`() {
        val source = "# Title\n- one\nafter"
        val (text, _) = Markdown.continueListOnNewline(source, caret = 13)!!
        assertEquals("# Title\n- one\n- \nafter", text)
    }

    @Test
    fun `an out of range caret is ignored`() {
        assertEquals(null, Markdown.continueListOnNewline("- one", caret = 99))
    }

    @Test
    fun `parsing never throws on arbitrary input`() {
        listOf("", "   ", "***", "```", "- [", "#", "~~~~", "**_*~`", "\n\n\n").forEach { input ->
            Markdown.parse(input)
            Markdown.toPlainText(input)
        }
    }
}
