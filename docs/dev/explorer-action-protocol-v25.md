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

The catalog key, the placement, the entry point, the rename dialog "More" menu, the transactions and
their access rule are in place. The rename executor and the undo journal behind `listRenameParent`, `renameEntries`,
`queryRenameBatch`, `listRenameBatches` and `undoRenameBatch` arrive with Batch Rename roadmap P3.1;
until then those calls fail with `UnsupportedOperationException` for an action that declared
`targetRename`, and with the usual access error for any other action. Do not ship a host between the
two steps.

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

## Renaming

`renameEntries(request)` takes a Bundle with:

- `RENAME_ENTRIES`: 1 to `MAX_RENAME_ENTRIES_PER_REQUEST` (2000) entry Bundles, each with
  `RENAME_TARGET_ID` (a request target ID), `RELATIVE_PATH` (`""` for the target itself, otherwise a
  descendant path below a directory target, validated like `listChildren`), `RENAME_NEW_NAME` (one
  path segment of at most `MAX_RENAME_NAME_BYTES` (255) UTF-8 bytes, no `/` or NUL, not `.` or `..`)
  and optional `RENAME_OVERWRITE`;
- optional `RENAME_ALL_OR_NOTHING`: revalidate every entry first and rename nothing when one fails.

The host revalidates each entry (it still exists with the kind and modification time the plugin
listed, the new name is valid and free or `RENAME_OVERWRITE` was requested), orders the renames so
that children go before their parents, uses a temporary name for case-only changes, swaps and
cycles, moves a replaced entry into its Trash first, and records the call as one batch. The result
carries `RENAME_BATCH_ID`, `RENAME_BATCH_STATE` (`COMMITTED`, `PARTIAL`, `REJECTED`), the counts
`RENAME_RENAMED_COUNT` / `RENAME_NOT_RENAMED_COUNT` and one item per entry in request order with
`RENAME_TARGET_ID`, `RELATIVE_PATH`, `RENAME_NEW_RELATIVE_PATH` and `RENAME_ITEM_STATE` (`RENAMED`,
`NOT_RENAMED`, `STALE`). Entries that were not renamed keep their names; renamed entries stay renamed
when a later entry fails (use undo to revert them).

When a request target itself is renamed, the session adopts the new path for that target ID.

## History and undo

`queryRenameBatch`, `listRenameBatches` and `undoRenameBatch` are scoped to the calling plugin UID
and to batches below the session targets. The host keeps at most `MAX_RENAME_BATCH_RESULTS` (20)
rename batches per plugin, shared with the `files.batchRename` script API, and evicts the oldest
finished ones first. Undo restores the old names newest entry first for every entry that still has
the name, kind and modification time the batch gave it, returns replaced entries from the host
Trash, never overwrites an occupied old name, and reports `RESTORED`, `STALE` or
`RECOVERY_REQUIRED` per entry.

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
order of the plugin and host session interfaces, including the five appended calls. Session
instrumentation for listing, renaming and undo arrives with roadmap P3.1.
