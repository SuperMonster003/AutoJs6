# Explorer Action directory browsing

## Purpose

Since protocol v4 an action may target a single directory (`TARGET_DIRECTORY` with
`CARDINALITY_SINGLE`). For such a target the host always attaches an `IExplorerActionHostSession`,
and that session is enough to browse the directory tree read-only: `listChildren` pages any folder
below the target through a nested relative path, and `openFile` opens any file below it. No
protocol change is needed for a plugin to show a directory as an album; this note records the
rules the existing session already enforces so plugin authors can rely on them.

The first consumer is 3-Maple Image 3.0.0, whose overflow action `browse-directory` ("View as
album") lists the chosen directory, shows its subfolders as folder cells and opens images in its
own viewer. The host code did not change for it.

## Catalog shape

- `TARGET_DIRECTORY`, `CARDINALITY_SINGLE`, `ACCESS_READ_ONLY`, `PLACEMENT_OVERFLOW`;
- Activity presentation and no output mode;
- no MIME type, extension or compound-suffix matcher: a directory action without matchers matches
  every directory (`ExplorerActionCatalogPolicy` accepts the empty matcher set only for directory
  targets);
- `readSiblings`, `playbackProgress`, related-file suffixes and `filesystemPath` are not needed.
  `filesystemPath` (v23) in particular replaces the session with a path and must stay unset here.

`ExplorerActionCatalogPolicyTest.protocolV12AcceptsAReadOnlyDirectoryOverflowActionWithoutMatchers`
pins this shape.

## Session rules for a directory target

`ExplorerActionHostInputAccess` applies the same containment to every call:

- `listChildren(targetId, relativePath, offset, limit)`: `relativePath` is `""` for the target
  itself or a `/`-joined path below it. It must be relative, contain no empty, `.` or `..` segment
  and no NUL character, and be at most `MAX_SESSION_RELATIVE_PATH_LENGTH` (16384) characters.
  Symbolic-link traversal is rejected: the unresolved and canonical paths must match and the result
  must stay inside the target. `limit` is 1 to `MAX_SESSION_PAGE_SIZE` (128); `offset` may not
  exceed the directory size. Children are sorted by lower-cased name, then by name.
- Each item carries `RELATIVE_PATH` (the joined path below the target), `DISPLAY_NAME`, `KIND`
  (`TARGET_FILE` or `TARGET_DIRECTORY`), `MIME_TYPE` (`inode/directory` for folders),
  `SIZE` (`-1` for folders), `LAST_MODIFIED`, `READABLE` and `SYMBOLIC_LINK`. `READABLE` is false
  for links and for anything that resolves outside the target.
- `openFile(targetId, relativePath)` resolves the same way and opens a readable regular file
  read-only.
- The host imposes no depth limit; a plugin must bound its own recursion. 3-Maple Image stops at
  8 levels and reads at most 4096 entries per folder.
- Only the target's display name and the parent display path reach the plugin through the launch
  intent; no absolute path is disclosed, and the session never grants content URIs for files below
  the target. Plugins expose such files to their own UI through a private provider if they need
  URIs (3-Maple Image does so for thumbnails).
- The session closes when the plugin calls `close()`, when the plugin process dies (`attachClient`)
  or when the host tears the session down; everything above fails after that.

## Plugin responsibilities

- Treat the listing as untrusted: validate that each item's `RELATIVE_PATH` equals the parent path
  joined with its `DISPLAY_NAME`, skip unreadable or linked entries, and keep pages bounded.
- Keep the session alive for as long as any screen uses it, and close it exactly once. When a
  second Activity (a viewer) borrows the session, make clear which side owns it.
- Do not request storage permissions for this mode and do not combine it with management actions
  that need other access modes; the session is read-only by declaration.
