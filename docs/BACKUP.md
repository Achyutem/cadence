# Backup

Whole-database export and import as one JSON document, through the Storage Access Framework, the
user picks the file, so Cadence needs **no storage permission** and never sees anything they did
not choose.

## A hand-written DTO layer, not the Room entities

A backup file is a contract with the user's future self. Room entities change shape every time the
schema does; if the file format *were* the entity, renaming a column would silently invalidate
every backup ever taken.

`domain/backup/BackupModels.kt` changes only when the format is deliberately revised, and
`format_version` says which revision a file is. Unknown keys are ignored on read, so a file written
by a newer version still restores whatever this version understands instead of failing outright.

Every date and time is ISO text rather than a number. A backup should be readable and repairable in
a text editor years from now, when the reader may not be this app; an epoch millisecond is not
something a person can check.

## Import replaces; it does not merge

Merging two histories requires identity that survives export, stable UUIDs on every row, or a
conflict policy the user has to understand. Both are real designs; neither is what "restore my
backup" means.

So import is a **full replace**, stated plainly in the confirmation dialog, and it is the only
destructive action in the app.

To make that safe it runs inside **one Room transaction**: the old data is deleted and the new data
inserted atomically, so a malformed file or a crash mid-import leaves the existing database exactly
as it was rather than half-erased.

Original row ids are preserved, so foreign keys, subtask parents, habit entries, recurrence rules,
survive the round trip without remapping. Rows are inserted in dependency order: rules and
reminders before the tasks and habits that point at them, parents before children.

## What is not in the file

`notes.preview` is a write-time cache of `content`, so it is regenerated on import rather than
trusted from the file. Nothing else derived is stored at all, so there is nothing else to omit.
