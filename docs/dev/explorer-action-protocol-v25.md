# Explorer Action protocol v25

## Purpose

Protocol v25 lets a read-only action rename the items it was opened with, and the items below a
selected directory, in their own directories. Its consumer is the Batch Rename plugin
(`AutoJs6-Plugin-Batch-Rename`, roadmap D22 / D23): the user selects files and folders, opens the
plugin's PowerRename-style screen, previews the new names and applies them; the host performs every
rename, records the call as an undoable batch and refreshes the file manager.

v25 also adds the rename dialog placement: the host's single-item rename dialog gets a "More" menu
listing rename actions for that item.

The plugin computes names only. It never receives write access to a file, cannot move an entry to
another directory and cannot touch an entry it did not list through the session.

## Status

Complete. The catalog key, the placement, the entry point, the rename dialog "More" menu and the five
transactions are in place. The transactions delegate to the host's Batch Rename executor and journal
(`core/plugin/batchrename`, Batch Rename roadmap P3.1), which the `files.batchRename` script API shares. Any action
that did not declare `targetRename` gets the usual access error (`IllegalStateException`) from all five.

## Binary compatibility

Five transactions are appended to `IExplorerActionHostSession` after `openRelatedFile`; every v1-v24
Binder transaction number is unchanged. Hosts continue to accept catalogs for older supported
protocol versions, and older hosts reject a protocol-25 catalog through the existing
advertised-version check, so the actions do not appear there.

Plugin test doubles that implement `IExplorerActionHostSession.Stub` (3-Maple Image, 3-Stack
Archive, 3-Amber Player) must implement the five methods once they stage a protocol-25
`explorer-action-api.aar`; staying on their current AAR keeps them unaffected.

## Catalog declaration

An action may set `ExplorerActionCatalogKeys.TARGET_RENAME` (`targetRename`) to `true`. The
declaration is valid only for:

- protocol 25 or later;
- `TARGET_FILE`, `TARGET_DIRECTORY` or `TARGET_MIXED`, with the cardinality and placement rules of
  those kinds;
- `ACCESS_READ_ONLY` and Activity presentation;
- no output mode, unmatched-file probe, `filesystemPath` or `targetTrash`;
- no `readSiblings`, `playbackProgress` or related-file suffixes.

Any other combination rejects the catalog action rather than broadening it. An absent or `false`
value keeps the pre-v25 meaning of the action. The host always attaches an
`IExplorerActionHostSession` to such an action.

`ExplorerActionValues.PLACEMENT_RENAME_DIALOG` (`4`) is valid only for protocol 25 or later, for
`CARDINALITY_SINGLE` and together with `targetRename`. The host lists such actions in the "More"
menu of its rename dialog (`ExplorerActionEntryPoint.RENAME_DIALOG`) and nowhere else. The menu exists
only in the main file manager; when no installed action matches the item, it holds a single
"Batch rename..." entry that names the reason (not installed, disabled, not authorized or too old) and
opens the Plugin Center at the Batch Rename plugin.

The Batch Rename catalog uses three shapes (roadmap D23):

| Action | Target | Cardinality | Placement |
| --- | --- | --- | --- |
| `batch-rename-selection` | `TARGET_MIXED`, `*/*` | multiple | selection toolbar |
| `batch-rename-directory` | `TARGET_DIRECTORY` | single | overflow |
| `batch-rename-single` | `TARGET_MIXED`, `*/*` | single | rename dialog |

## Listing names

- `listChildren` keeps its rules: below a directory target the plugin pages descendants, which is
  how it reaches the items below a selected folder. A file target still lists nothing without
  `readSiblings`, and `targetRename` cannot be combined with `readSiblings`.
- `listRenameParent(offset, limit)` pages the entries of the common parent directory of the request
  targets, names and kinds only, so the plugin can detect name conflicts with entries it does not
  rename. The result uses the `listChildren` page keys (`ITEMS`, `NEXT_OFFSET`, `COMPLETE`) and adds
  `RENAME_CASE_INSENSITIVE` (names differing only in case collide) and `RENAME_RESTRICTED_CHARACTERS`
  (the file system rejects `\ : * ? " < > |`). Neither the parent's other entries nor their contents
  become reachable through `openFile`.
- A session that may rename remembers what `listChildren` returned below its directory targets (kind,
  modification time and size); those records are what `renameEntries` checks a descendant against. They follow
  renames and undos made through the session.

## Renaming

`renameEntries(request)` takes a Bundle with:

- `RENAME_ENTRIES`: 1 to `MAX_RENAME_ENTRIES_PER_REQUEST` (2000) entry Bundles, each with
  `RENAME_TARGET_ID` (a request target ID), `RELATIVE_PATH` (`""` for the target itself, otherwise a
  descendant path below a directory target, validated like `listChildren`), `RENAME_NEW_NAME` (one
  path segment of at most `MAX_RENAME_NAME_BYTES` (255) UTF-8 bytes, no `/` or NUL, not `.` or `..`)
  and optional `RENAME_OVERWRITE`;
- optional `RENAME_ALL_OR_NOTHING`: revalidate every entry first and rename nothing when one fails.

A malformed request is rejected as a whole with `IllegalArgumentException` and renames nothing: no or
too many entries, an entry without target ID or new name, an unknown target ID, an entry named twice, a
relative path on a file target or one that fails the `listChildren` path rules (absolute, `.` / `..`
segments, symbolic links), or a descendant the session has not listed. Symbolic links listed by
`listChildren` (`SYMBOLIC_LINK`) cannot be renamed; leave them out. Only one rename or undo runs per
session at a time; a second call meanwhile fails with `IllegalStateException`.

The host revalidates each entry (it still exists with the kind the plugin listed, and a file still has
the modification time and size the plugin listed; a request target is checked against what the host
captured when it launched the action), checks the new name (one segment of at most 255 UTF-8 bytes, not
equal to the old name) and that it is free or `RENAME_OVERWRITE` was requested. It renames deeper entries
first, so a directory is renamed after everything below it; within one directory it moves case-only
changes and names another entry of the batch still holds (chains, swaps, cycles) to a temporary name
first, so the request order does not matter. An overwritten entry goes to the host Trash first; an entry
of the same batch is never overwritten. The call is recorded as one batch and the file manager is
refreshed.

The result carries `RENAME_BATCH_ID`, `RENAME_BATCH_STATE` (`COMMITTED`, `PARTIAL`, `REJECTED`),
`RENAME_BATCH_CREATED_AT` / `RENAME_BATCH_UPDATED_AT`, the counts `RENAME_RENAMED_COUNT` /
`RENAME_NOT_RENAMED_COUNT` / `RENAME_RESTORED_COUNT` and `RENAME_ENTRIES`: one item per request entry, in
request order, with `RENAME_ITEM_STATE` (`RENAMED`, `NOT_RENAMED`, `STALE`) and
`RENAME_NEW_RELATIVE_PATH`, the entry's relative path now below where its request target is now (`""` for
the target itself). The items do not repeat the target ID and relative path; the plugin pairs them with its
request by position, which keeps a 2000-entry answer small. Entries that were not renamed keep their names;
renamed entries stay renamed when another entry fails (use undo to revert them).

The session follows its renames: a renamed request target keeps its target ID at the new path, and listed
descendants keep their records under their new relative paths.

A request travels in one Binder transaction. With 64-character target IDs an entry costs roughly 350 bytes
of Parcel data, so a client that sends the maximum of 2000 entries comes close to the 1 MB transaction
buffer; a client should measure its request (`Parcel.dataSize()`) and split a large selection into several
requests, each of which becomes its own batch.

## History and undo

`queryRenameBatch`, `listRenameBatches` and `undoRenameBatch` see the batches the calling plugin made through
sessions whose common parent is this session's parent. Any other batch ID, including one of another plugin, of a
script or of another directory, answers `RENAME_BATCH_STATE_UNKNOWN` and is left alone. Batches survive a restart
of the host. The journal (`noBackupFilesDir/batch-rename/journals`, shared with the `files.batchRename` script
API) keeps the newest `MAX_RENAME_BATCH_RESULTS` (20) batches of each plugin and of scripts, and evicts the oldest
first.

`listRenameBatches` returns the batches newest first as summaries (ID, state, timestamps and counts, no
`RENAME_ENTRIES`); `queryRenameBatch` and `undoRenameBatch` return the full batch like `renameEntries`.

Undo gives the old names back shallowest entry first (a directory before the entries below it) with the same
temporary-name rules, for every entry that still has the kind the batch gave it and, for a file, the
modification time and size; others become `STALE`. It never overwrites an occupied old name
(`RECOVERY_REQUIRED`), returns an overwritten entry from the host Trash, and refreshes the file manager. A batch
whose undo hit an obstacle stays `RECOVERY_REQUIRED` and can be undone again once the obstacle is gone; a
`RESTORED` batch stays restored.

## Client responsibilities

A protocol-25 client must:

- declare `targetRename` only on an action that shows the user the old and new names before
  renaming, and call `renameEntries` only after an explicit user action;
- name only request targets and descendants it listed in this session, and never assume a name it
  did not list is free;
- treat `NOT_RENAMED` and `STALE` as normal outcomes and describe them plainly;
- offer Undo through `undoRenameBatch`.

## Verification

Catalog policy tests cover the three Batch Rename shapes, the protocol gate, the rename dialog
placement (single target and `targetRename` required, matching files and folders, listed only for
the rename dialog entry point on the main file manager) and the rejection of writable,
host-explorer, output, `filesystemPath`, `targetTrash` and media-session combinations.
`ExplorerActionAidlOrderTest` (module `plugin-api/explorer-action-api`) freezes the transaction
order of the plugin and host session interfaces, including the five appended calls.
`BatchRenameExecutorTest` (JVM) covers the executor and its journal: swaps, cycles, chains, case-only changes,
directories with renamed children (also swapped directories), partial failures, stale entries, all-or-nothing,
overwrite through the Trash, repeated undo, progress and eviction per owner.
`ExplorerActionRenameV25InstrumentationTest` covers the session: parent paging and traits, listed descendants,
targets that follow their renames, outcomes per entry, malformed requests, overwrite and undo through the real
Trash, history scoped to plugin and parent across a new journal instance, sessions without `targetRename` and
the default wiring to the host journal.
