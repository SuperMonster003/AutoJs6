# Controlled WebSocket

Status: stable and enabled by default in Node Profile v1.2.

The controlled WebSocket module provides provider-owned WebSocket sessions
without treating a raw socket as an AutoJs bridge handle. It shares the stable
network capability and has no separate build or release flag.

## API Shape

`require("websocket")` exposes a Promise-based `connect()` operation. A
connection supports:

- text and binary `send()`;
- `close()`;
- `on()`/`off()` and
  `addEventListener()`/`removeEventListener()`;
- `onopen`, `onmessage`, `onerror`, and `onclose` properties;
- explicit event draining through the host bridge.

Requests accept only `ws:` and `wss:` URLs. Headers, subprotocols, connect
timeout, queue size, and message limits are normalized before dispatch.

## Authorization And Limits

Controlled sessions require the runtime `network` permission when the bridge
permission manifest is enforced, plus Android `INTERNET` permission in the
packaged application. The provider enforces maximum active connections,
outbound queue length, frame/message size, connect timeout, cancellation, and
resource ownership by execution id.

OkHttp callbacks never invoke JavaScript directly. Events are serialized and
drained through the versioned bridge. Execution stop, provider destruction, or
runtime cancellation closes every owned session and prevents callbacks into a
destroyed Node environment.

Native `net` and `tls` are separate stable Node builtins. Their presence does
not bypass the controlled module's authorization or limits, and the controlled
module does not attempt to wrap their API.

## Contract And Diagnostics

- `NODEJS_NETWORK_ENABLED=true`
- `featureStates.webSocket=true`
- stable capability status
- provider source `runtime:stable-network`
- Doctor/profile output includes connection, queue, message, timeout, cleanup,
  and permission state

Project/package feature metadata cannot disable or enable WebSocket. Release and
AAR tasks contain no WebSocket opt-in path.

## Verification

```powershell
.\gradlew.bat --console=plain :app:verifyNodeLiveWebSocketGate "-Pautojs.nodejs.test.deviceProfile=sony"
```

The gate covers fake and live provider connect/send/receive/close flows,
protocol negotiation, binary frames, handler APIs, timeout, cancellation,
scheme denial, connection and message budgets, shadow protection, diagnostics,
manifest permission, and destroy cleanup.
