# Explorer Action protocol v7

## Scope

Protocol v7 adds one optional verification step to the request-scoped v4 host session. It does not change action discovery, target grants, output naming or the v5/v6 read-only archive session.

## Binary compatibility

`openPendingOutput(transactionId)` is appended after every existing method in `IExplorerActionHostSession.aidl`. Existing Binder transaction numbers therefore remain unchanged. Plugins compiled against v4, v5 or v6 can continue to call `listChildren`, `openFile`, `prepareOutput`, `openOutput`, `commitOutput`, `abortOutput` and `close` without recompilation.

Only plugins that advertise protocol v7 may call the appended method. A v7 plugin must still declare the minimum AutoJs6 host version that first ships this interface.

## Pending output lifecycle

The compatible lifecycle remains:

```text
prepareOutput -> openOutput -> close writer -> commitOutput
```

The verified lifecycle is:

```text
prepareOutput
    -> openOutput
    -> close writer
    -> openPendingOutput
    -> read and close verifier
    -> commitOutput
```

The host returns a read-only descriptor for its unpublished staging file. The plugin cannot obtain the final filesystem path, modify the staged output through this descriptor, reopen it twice, or commit a transaction that was never opened for writing.

The host cannot observe whether a remote duplicated writer descriptor is still open. Calling `openPendingOutput` is therefore an explicit plugin assertion that all writers are closed. Once verification starts, the host will not issue another writer or verifier descriptor for that transaction.

Any verification failure must call `abortOutput`. The final name remains unpublished until `commitOutput` succeeds.
