# Explorer Action protocol v8

## Scope

Protocol v8 adds a narrowly scoped way for an Activity action to replace the one file that the user selected. Its first consumer is Archive Manager, which rebuilds a ZIP in a host-owned pending file and replaces the original only after a full read-back verification succeeds.

The new access mode does not grant arbitrary filesystem paths, parent-directory enumeration, or permission to replace a sibling file.

## Binary compatibility

`prepareTargetReplacement(targetId)` is appended after every v7 method in `IExplorerActionHostSession.aidl`. Existing Binder transaction numbers remain unchanged. Plugins compiled against v4 through v7 can continue to use their existing methods without recompilation.

Existing plugin catalogs also remain valid. The host accepts their older protocol versions and does not require them to implement or advertise target replacement. Only a plugin that advertises protocol v8 and declares `ACCESS_REPLACE_TARGET` can receive this capability.

## Catalog contract

`ACCESS_REPLACE_TARGET` has value `3`. The host accepts it only for an action with all of these properties:

- Protocol version 8 or newer.
- File target kind.
- Single-target cardinality.
- Overflow placement.
- Activity presentation.
- No separate output declaration.

The strict shape keeps replacement behind an explicit per-file menu action and prevents a plugin from silently combining it with multi-selection or create-in-parent access.

## Replacement lifecycle

Unlike create-in-parent outputs, a replacement cannot use the legacy unverified commit path. Its required lifecycle is:

```text
prepareTargetReplacement
    -> openOutput
    -> close writer
    -> openPendingOutput
    -> read and close verifier
    -> commitOutput
```

Any failure or cancellation before commit must call `abortOutput`. The host removes the pending file and leaves the selected target unchanged.

The host creates the pending file beside the selected target. Before commit it synchronizes pending file data, checks that the original target identity still matches the identity captured for the request, and uses a same-directory atomic rename to replace it. A direct replacement commit that skipped `openPendingOutput` is rejected.

## Target identity and repeated edits

The host identity includes device, inode, size, and last-modified time. It is checked when replacement is reserved and immediately before commit. If another actor changed or replaced the target, commit fails and the external version is preserved.

After a successful commit, the session records the new target identity. The same Activity session can therefore perform another verified edit without requesting broader access or reopening the plugin action.

## Explorer refresh

A committed create-in-parent output still emits an item-created notification. A committed replacement emits an item-changed notification for the original Explorer row, so the file list can refresh its size, timestamp, and preview state without treating the archive as a new sibling file.

## Plugin responsibilities

Target replacement is authorization to replace content, not proof that the content is valid. The plugin must:

- Finish and close every writer before requesting the pending descriptor.
- Fully validate the pending file through the read-only descriptor.
- Abort on validation failure or cancellation.
- Commit only after validation succeeds.
- Close the request-scoped host session when its Activity is finished.

The host cannot observe whether a plugin retained a duplicated writer descriptor. As in v7, requesting the pending descriptor is the plugin's explicit assertion that all writers are closed.
