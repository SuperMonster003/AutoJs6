# Explorer Action protocol v23

## Purpose

Protocol v23 adds one narrow, read-only capability for a single selected directory: the host
discloses that directory's canonical filesystem path. Its first consumer is the 3-Shell Terminal
plugin, whose "Open in terminal" action must `cd` into a real path. Every earlier capability
deliberately avoids disclosing paths; v23 keeps that rule for everything except the one directory
the user explicitly chose for an action that declares this need.

Disclosing the path grants nothing. The plugin reaches the directory only with its own storage
permissions (for example its own all-files access); the host adds no URI grant, file session or
descriptor for it.

## Binary compatibility

No AIDL changes. Every v1-v22 Binder transaction number remains unchanged. Hosts continue to
accept catalogs for older supported protocol versions, and older hosts reject a protocol-23
catalog through the existing advertised-version check, so the action simply does not appear.

## Catalog declaration

An action may set `ExplorerActionCatalogKeys.FILESYSTEM_PATH` (`filesystemPath`) to `true`. The
declaration is valid only for:

- protocol 23;
- `TARGET_DIRECTORY` with `CARDINALITY_SINGLE`;
- `ACCESS_READ_ONLY`, `PLACEMENT_OVERFLOW` and Activity presentation;
- no output mode, MIME type, extension or compound-suffix matcher, unmatched-file probe,
  sibling read, playback progress or related-file suffix.

Any other combination rejects the catalog action rather than broadening it. An absent or `false`
value keeps the pre-v23 meaning of the action.

## Launch request

For an accepted action the host resolves the selected item to its canonical file and requires it
to be a readable directory whose path is at most `ExplorerActionProtocol.MAX_FILESYSTEM_PATH_LENGTH`
(4096) characters. The single target Bundle in `ExplorerActionIntentExtras.TARGETS` then carries
`ExplorerActionTargetKeys.FILESYSTEM_PATH` with that canonical path; the other v4 target keys and
request extras are unchanged.

The host does not create an `IExplorerActionHostSession` for such an action, even though the target
is a directory: the plugin needs no directory paging, output or sibling access, so no session is
offered. The path is not added to any other action, and no other path is added to this one.

## Client responsibilities

A protocol-23 client must:

- declare `filesystemPath` only for an action that needs a real directory path, and validate that
  the negotiated protocol is 23;
- read the path only from the single target Bundle, require an absolute path within the length
  limit and treat anything else as an invalid request;
- access the directory only through its own permissions and report a missing permission to the
  user instead of asking the host for broader access;
- keep the path out of logs and never forward it to other apps.

## First consumer

3-Shell Terminal 1.1.0 declares the overflow action `three-shell-terminal.open-directory`
("Open in terminal"). Its exported Activity is protected by `org.autojs.permission.PLUGIN`,
validates the request and opens the plugin terminal in that directory, falling back to the
terminal home directory with a notice when the plugin lacks the storage permission. With this
action in place, the host no longer hard-codes "Open in terminal" in the directory menu; the
drawer item and the project toolbar keep their `TerminalLauncher` route.

## Verification

Catalog policy tests cover the accepted shape, the protocol gate and the rejection of every broader
combination (file or mixed targets, multiple targets, write access, primary placement, host
presentation and matchers). Device checks launch the plugin action from the directory menu of the
main file page and confirm that the terminal opens in the selected directory.
