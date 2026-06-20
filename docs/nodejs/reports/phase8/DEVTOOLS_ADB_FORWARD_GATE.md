# Phase 8 DevTools ADB Forward Gate

Status: deferred by disabled inspector gate

S8-38 depends on a real debug-only inspector endpoint. The current v1.1
runtime deliberately exposes no endpoint, so there is no safe `adb forward`
target and no Chrome DevTools URL to publish.

## Current Behavior

The current behavior is inherited from S8-37:

- no inspector listener is started;
- no device port is allocated;
- no host port is allocated;
- no `adb forward tcp:<host> tcp:<device>` command is emitted;
- no `chrome://inspect` or `devtools://` attach URL is generated;
- no breakpoint, pause/resume, console, async-stack, source-map, ESM, or
  TypeScript source-name debugging session can attach.

This avoids creating a misleading developer command that appears to enable
debugging while the runtime still lacks user consent, visible active-session
state, endpoint lifecycle ownership, and packaged release denial coverage.

## Future Workflow

If a later task implements debug-only inspector support, the developer workflow
must be owned by AutoJs6 instead of by script code:

1. Start an inspector session from a trusted debug UI or host/debug command.
2. Require explicit user action for the selected script execution.
3. Bind the device listener only to loopback.
4. Allocate a random ephemeral device port.
5. Display the endpoint, execution id, and expiry in a visible revocable UI or
   notification.
6. Return a structured session descriptor to the host/debug tool:

```json
{
  "schema": "autojs6-node-inspector-session-v1",
  "executionId": "<execution-id>",
  "host": "127.0.0.1",
  "devicePort": 0,
  "webSocketPath": "/<opaque-token>",
  "expiresAt": "<iso-8601>"
}
```

7. The host tool may then create the forward:

```powershell
adb -s QV710AF65F forward tcp:<host-port> tcp:<device-port>
```

8. Open Chrome DevTools through `chrome://inspect` or a generated
   `devtools://` URL that points at `127.0.0.1:<host-port>`.
9. Stop the inspector through the trusted UI/host command or automatically when
   the script stops, times out, crashes, or the service is destroyed.

## Acceptance Gate

Future S8-38 promotion must include:

- positive debug-build attach smoke through ADB forwarding;
- breakpoint, pause/resume, console, and async-stack smoke;
- source-name coverage for CJS, ESM, lightweight TypeScript, and source maps;
- `0.0.0.0` and non-loopback rejection;
- one active session per execution unless a later design proves otherwise;
- deterministic `adb forward --remove` instructions or host cleanup;
- no packaged release attach path;
- rollback to the S8-37 disabled gate.

Until those gates exist, S8-38 is intentionally documentation-only and
deferred.
