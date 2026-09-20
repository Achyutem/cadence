# Notes

Notes are the fifth dock destination: a heading and a body, with Markdown.

## The body is raw Markdown text

Not a parsed tree, not rich-text spans. Everything follows from that:

- It is trivially exportable and diffable, a note survives leaving this app intact.
- It cannot become corrupt in a way that loses words. A parser bug shows wrong formatting; a
  broken span model loses text.
- The renderer can improve later without a migration, because the source of truth is what the
  user typed.
- Tapping a checkbox in the preview **rewrites the underlying line**, producing exactly the
  document the user would have typed. Nothing can drift between what is shown and what is stored.

## The Markdown subset

No Markdown library. Full CommonMark plus a Compose renderer is a large dependency for syntax a
personal notes app never uses, reference links, HTML blocks, setext headings, tables.

Supported: `#`/`##`/`###` headings, `-`/`*` bullets, `1.` numbered items, `- [ ]` / `- [x]` tasks,
`>` quotes, fenced code, `---` rules, and inline `**bold**`, `*italic*`, `` `code` ``, `~~strike~~`.

Block parsing is line-based; inline parsing is a single left-to-right scan.

**It never throws and never rejects input.** Anything unrecognised stays literal, which is the
right failure mode for a document someone is mid-way through typing. An unclosed `**` stays two
asterisks rather than making the rest of the note bold; an unterminated code fence runs to the end
rather than being discarded. `MarkdownTest` is built around exactly these half-typed cases.

Inline parsing is deliberately not nesting-aware beyond one level: `**bold *and italic* **` renders
bold throughout. True nesting needs a real inline grammar and the payoff here is near zero.

## Autosave, not a save button

A note is a document. Losing one because the user backed out is unacceptable, and a save button
makes that the user's problem.

Edits are debounced by 600ms, and the editor flushes on the way out, via the back gesture, the
toolbar, or the composable leaving the tree. The debounce is a performance optimisation, never a
window in which work can be lost. `NotesFlowTest` asserts exactly this: it types, leaves, and
expects the note in the list.

A note still completely blank is never written, so opening the editor and changing your mind
leaves nothing behind.

## The `preview` column

The one denormalised field in the schema. It is a truncated, formatting-stripped copy of the body,
maintained on write.

A note list renders many previews per frame; running a Markdown parser over full bodies to do that
would make scrolling the slowest thing in the app. It is a cache of *presentation*, never a source
of truth; it is regenerated from `content` on every save, and on import, rather than being trusted
from the backup file.

The list query also does not select `content` at all: bodies can be thousands of characters and the
list only ever shows `preview`.
