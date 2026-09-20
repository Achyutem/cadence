package dev.achyutem.cadence.domain.notes

/**
 * A deliberately small Markdown subset.
 *
 * Cadence does not depend on a Markdown library. The full CommonMark spec is large, and a
 * compliant parser plus a Compose renderer for it is a megabyte-scale dependency to support
 * syntax a personal note-taking app will never use, reference links, HTML blocks, setext
 * headings, nested block quotes, tables.
 *
 * What is supported is what people actually type in notes:
 *
 * ```
 * # Heading            (levels 1–3)
 * - bullet             (also *)
 * 1. numbered
 * - [ ] task           (and - [x])
 * > quote
 * ```code block```
 * ---                  horizontal rule
 * **bold**  *italic*  `code`  ~~strike~~
 * ```
 *
 * The parser is line-based for blocks and a single left-to-right scan for inline spans. It never
 * throws and never rejects input: anything it does not recognise stays as literal text, which is
 * the right failure mode for a document the user is mid-way through typing.
 */

sealed interface MarkdownBlock {
    data class Heading(val level: Int, val spans: List<MarkdownSpan>) : MarkdownBlock
    data class Paragraph(val spans: List<MarkdownSpan>) : MarkdownBlock
    data class BulletItem(val spans: List<MarkdownSpan>) : MarkdownBlock
    data class NumberedItem(val number: Int, val spans: List<MarkdownSpan>) : MarkdownBlock
    data class TaskItem(val checked: Boolean, val spans: List<MarkdownSpan>, val sourceLine: Int) : MarkdownBlock
    data class Quote(val spans: List<MarkdownSpan>) : MarkdownBlock
    data class CodeBlock(val code: String) : MarkdownBlock
    data object Rule : MarkdownBlock
    data object Blank : MarkdownBlock
}

/** An inline run of text plus the styles active over it. */
data class MarkdownSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strikethrough: Boolean = false,
)

object Markdown {

    private val bulletRegex = Regex("""^\s*[-*]\s+(.*)$""")
    private val numberedRegex = Regex("""^\s*(\d+)[.)]\s+(.*)$""")
    private val taskRegex = Regex("""^\s*[-*]\s+\[([ xX])]\s*(.*)$""")
    private val headingRegex = Regex("""^(#{1,3})\s+(.*)$""")
    private val quoteRegex = Regex("""^\s*>\s?(.*)$""")
    private val ruleRegex = Regex("""^\s*(---+|\*\*\*+|___+)\s*$""")

    fun parse(source: String): List<MarkdownBlock> {
        val lines = source.lines()
        val blocks = mutableListOf<MarkdownBlock>()
        var index = 0

        while (index < lines.size) {
            val line = lines[index]

            // Fenced code. An unterminated fence runs to the end of the document rather than
            // being discarded; the user is probably still typing it.
            if (line.trimStart().startsWith("```")) {
                val code = StringBuilder()
                index++
                while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                    code.appendLine(lines[index])
                    index++
                }
                if (index < lines.size) index++ // consume the closing fence
                blocks += MarkdownBlock.CodeBlock(code.toString().trimEnd('\n'))
                continue
            }

            blocks += when {
                line.isBlank() -> MarkdownBlock.Blank
                ruleRegex.matches(line) -> MarkdownBlock.Rule

                // Task items must be tested before bullets: "- [ ] x" also matches a bullet.
                taskRegex.matches(line) -> {
                    val m = taskRegex.find(line)!!
                    MarkdownBlock.TaskItem(
                        checked = !m.groupValues[1].equals(" "),
                        spans = parseInline(m.groupValues[2]),
                        sourceLine = index,
                    )
                }

                headingRegex.matches(line) -> {
                    val m = headingRegex.find(line)!!
                    MarkdownBlock.Heading(m.groupValues[1].length, parseInline(m.groupValues[2]))
                }

                bulletRegex.matches(line) ->
                    MarkdownBlock.BulletItem(parseInline(bulletRegex.find(line)!!.groupValues[1]))

                numberedRegex.matches(line) -> {
                    val m = numberedRegex.find(line)!!
                    MarkdownBlock.NumberedItem(
                        m.groupValues[1].toIntOrNull() ?: 1,
                        parseInline(m.groupValues[2]),
                    )
                }

                quoteRegex.matches(line) ->
                    MarkdownBlock.Quote(parseInline(quoteRegex.find(line)!!.groupValues[1]))

                else -> MarkdownBlock.Paragraph(parseInline(line))
            }
            index++
        }
        return blocks
    }

    /**
     * Single left-to-right scan for inline markers.
     *
     * Deliberately not recursive and not nesting-aware beyond one level of each marker: `**bold
     * *and italic* **` renders as bold throughout rather than as nested emphasis. Supporting true
     * nesting means a real inline grammar, and the payoff in a notes app is close to zero.
     *
     * An unclosed marker is left as literal text, so typing `**` mid-sentence does not make the
     * rest of the note bold while you are still writing it.
     */
    fun parseInline(text: String): List<MarkdownSpan> {
        if (text.isEmpty()) return emptyList()
        val spans = mutableListOf<MarkdownSpan>()
        val literal = StringBuilder()
        var i = 0

        fun flushLiteral() {
            if (literal.isNotEmpty()) {
                spans += MarkdownSpan(literal.toString())
                literal.clear()
            }
        }

        while (i < text.length) {
            val rest = text.substring(i)
            val marker = MARKERS.firstOrNull { rest.startsWith(it.open) }

            if (marker == null) {
                literal.append(text[i]); i++; continue
            }

            val contentStart = i + marker.open.length
            val closeAt = text.indexOf(marker.close, startIndex = contentStart)
            if (closeAt < 0 || closeAt == contentStart) {
                // No closing marker, or an empty pair like `**`, treat as ordinary characters.
                literal.append(text[i]); i++; continue
            }

            flushLiteral()
            val content = text.substring(contentStart, closeAt)
            spans += MarkdownSpan(
                text = if (marker.code) content else stripInnerMarkers(content),
                bold = marker.bold,
                italic = marker.italic,
                code = marker.code,
                strikethrough = marker.strikethrough,
            )
            i = closeAt + marker.close.length
        }
        flushLiteral()
        return spans
    }

    /** Inside an already-styled run, leftover markers are noise; drop them. */
    private fun stripInnerMarkers(text: String): String =
        text.replace("**", "").replace("~~", "")

    private data class Marker(
        val open: String,
        val close: String = open,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val code: Boolean = false,
        val strikethrough: Boolean = false,
    )

    // Order matters: `**` must be tested before `*`, or bold would parse as two italics.
    private val MARKERS = listOf(
        Marker("**", bold = true),
        Marker("__", bold = true),
        Marker("~~", strikethrough = true),
        Marker("`", code = true),
        Marker("*", italic = true),
        Marker("_", italic = true),
    )

    /**
     * Plain text for the note list preview and for search.
     *
     * Runs on write, not on read; see [dev.achyutem.cadence.core.database.entity.NoteEntity].
     */
    fun toPlainText(source: String, limit: Int = 200): String {
        val text = buildString {
            for (block in parse(source)) {
                val piece = when (block) {
                    is MarkdownBlock.Heading -> block.spans.joinText()
                    is MarkdownBlock.Paragraph -> block.spans.joinText()
                    is MarkdownBlock.BulletItem -> block.spans.joinText()
                    is MarkdownBlock.NumberedItem -> block.spans.joinText()
                    is MarkdownBlock.TaskItem -> block.spans.joinText()
                    is MarkdownBlock.Quote -> block.spans.joinText()
                    is MarkdownBlock.CodeBlock -> block.code.replace('\n', ' ')
                    MarkdownBlock.Rule, MarkdownBlock.Blank -> ""
                }
                if (piece.isNotBlank()) {
                    if (isNotEmpty()) append(' ')
                    append(piece.trim())
                }
                if (length > limit) break
            }
        }
        return if (text.length > limit) text.take(limit).trimEnd() + "…" else text
    }

    private fun List<MarkdownSpan>.joinText(): String = joinToString("") { it.text }

    /**
     * Flip a `- [ ]` / `- [x]` checkbox on a specific source line.
     *
     * Editing the *text* rather than a parsed model is what keeps the Markdown authoritative:
     * tapping a checkbox in the rendered view produces exactly the document the user would have
     * produced by typing, so nothing can drift between what is shown and what is stored.
     */
    fun toggleTaskAtLine(source: String, lineIndex: Int): String {
        val lines = source.lines().toMutableList()
        if (lineIndex !in lines.indices) return source
        val line = lines[lineIndex]
        val match = taskRegex.find(line) ?: return source
        val wasChecked = !match.groupValues[1].equals(" ")
        lines[lineIndex] = if (wasChecked) {
            line.replaceFirst(Regex("""\[[xX]]"""), "[ ]")
        } else {
            line.replaceFirst("[ ]", "[x]")
        }
        return lines.joinToString("\n")
    }
}
