# Controlled WebSocket Design Gate

Status: planned, disabled by default. This document defines the boundary for a future controlled WebSocket module without enabling raw `net` or `tls`.

## Goal

AutoJs6 may expose WebSocket support for automation scripts that need server push or realtime control channels. The Safe Node Profile must not implement this by opening Node raw socket modules.

The target shape is:

```text
JS websocket module
  -> AutoJs6 bridge request
  -> NativeNodeEmbeddedScriptService
  -> app-owned WebSocket provider
  -> Android/OkHttp WebSocket stack
```

Raw Node networking remains outside the profile:

- `net`
- `tls`
- raw `http` upgrade APIs
- raw `https` upgrade APIs
- `dgram`

## Module Shape

Preferred user module:

```js
const websocket = require("websocket");

const connection = await websocket.connect("wss://example.com/socket", {
  timeoutMs: 10000,
  maxMessageBytes: 65536,
});

connection.on("message", (message) => {
  console.log(message.text);
});

await connection.send("hello");
await connection.close(1000, "done");
```

The module should be a controlled AutoJs6 module, not a transparent desktop `ws` clone. A future npm-compat adapter may be layered on top, but it must use the same provider and policy.

## Proposed API

```ts
type WebSocketUrl = string; // ws: or wss: only

interface ConnectOptions {
  headers?: Record<string, string> | Array<[string, string]>;
  timeoutMs?: number;
  maxMessageBytes?: number;
  maxQueueSize?: number;
  protocols?: string[];
  signal?: AbortSignal;
}

interface WebSocketMessage {
  type: "text" | "binary";
  text?: string;
  dataBase64?: string;
}

interface WebSocketConnection {
  id: string;
  url: string;
  readyState: "connecting" | "open" | "closing" | "closed";
  send(data: string | Buffer | ArrayBuffer | Uint8Array): Promise<void>;
  close(code?: number, reason?: string): Promise<void>;
  on(event: "open" | "message" | "close" | "error", listener: Function): this;
  off(event: "open" | "message" | "close" | "error", listener: Function): this;
}

function connect(url: WebSocketUrl, options?: ConnectOptions): Promise<WebSocketConnection>;
```

## Policy

Initial policy should be intentionally small:

- max connections per script: `2`
- default connect timeout: `10000` ms
- hard connect timeout cap: `60000` ms
- default inbound message budget: `65536` bytes
- hard inbound message budget: `1048576` bytes
- max queued inbound messages before user code drains events: `32`
- accepted schemes: `ws:` and `wss:` only
- reject `file:`, `content:`, `about:`, `data:`, `http:`, and `https:` URLs
- no automatic reconnect in v1
- no background keepalive or foreground-service promotion in v1

Overflow behavior:

- too many connections: reject `connect()` with `ERR_AUTOJS6_NETWORK_POLICY_DENIED`
- connect timeout: reject with `ERR_AUTOJS6_NETWORK_TIMEOUT`
- abort signal: reject or close with `ERR_AUTOJS6_NETWORK_ABORTED`
- inbound message too large: close the connection and emit/reject with `ERR_AUTOJS6_NETWORK_RESPONSE_TOO_LARGE`
- unsupported scheme: reject with `ERR_AUTOJS6_NETWORK_INVALID_URL`

## Binary Support

Binary messages should cross the bridge as Base64 strings, not raw native pointers. The JavaScript wrapper may expose `Buffer` when the Safe Node `buffer` module is available.

No streaming binary frames in v1. Every message is budgeted before delivery.

## Ping And Pong

The provider may rely on OkHttp platform ping behavior if available. User scripts should not receive raw ping/pong frames in v1.

If ping configuration is later exposed, it must be bounded:

- minimum interval
- maximum interval
- close on missed pong
- service destroy cancels timers

## Reconnect

No automatic reconnect in v1. Reconnect loops are easy to turn into background network activity. Userland can reconnect explicitly, but every new connection must pass the same policy and lifecycle checks.

A future reconnect helper needs a separate gate covering:

- max attempts
- backoff bounds
- foreground/background behavior
- battery impact
- packaged APK disclosure

## Lifecycle

Each connection must be registered under the current Node execution id.

On script stop, timeout, service destroy, or process shutdown:

- close all active connections
- reject pending `connect()` and `send()` calls
- remove all event listeners
- release provider handles
- prevent callbacks from re-entering a destroyed Node environment

Callbacks into JavaScript must be marshalled onto the Node event loop thread.

## Bridge Protocol

Proposed bridge methods:

```text
websocket.connect
websocket.send
websocket.close
websocket.drainEvents
```

Request shape:

```json
{
  "id": "bridge-call-id",
  "module": "websocket",
  "method": "connect",
  "args": [{
    "url": "wss://example.com/socket",
    "headers": [["authorization", "Bearer redacted"]],
    "protocols": ["json"],
    "timeoutMs": 10000,
    "maxMessageBytes": 65536
  }],
  "timeoutMs": 10000,
  "permissions": ["network"]
}
```

Event delivery should not call into JavaScript directly from an OkHttp callback. Use a bounded event queue and let the JS side drain events through the bridge or live transport.

## Packaged APK

Packaged APKs that opt into controlled WebSocket need:

- explicit project metadata opt-in
- `android.permission.INTERNET`
- packaged verifier report entry
- release checklist item proving raw `net`/`tls` still fail

No INTERNET permission should be added solely because the runtime has a planned WebSocket design.

## Diagnostics

The provider should expose counters similar to controlled fetch:

- active connection count
- max active connection count
- connect timeout count
- policy rejection count
- aborted/closed-on-destroy count
- inbound message dropped count
- bytes received and sent, capped or redacted for logs

Native values should use the `embedded_script.websocket_*` prefix.

## Tests Before Runtime Enablement

Required tests for the MVP task:

- disabled or planned state visible in `require("autojs6:profile")`
- `require("websocket")` behavior is documented before reservation
- fake provider connect/open/close
- send text
- receive text
- send binary through Base64/Buffer
- max connection rejection
- max message size rejection
- unsupported scheme rejection
- connect timeout
- abort before connect completes
- service destroy closes active connection
- raw `net.connect()` remains denied
- raw `tls.connect()` remains denied
- packaged verifier checks `android.permission.INTERNET` only when project metadata opts in

## Non-Goals

Task 61 does not implement:

- runtime `require("websocket")`
- desktop `ws` compatibility
- raw socket upgrade APIs
- reconnect helper
- streaming frames
- custom TLS certificates
- proxy customization
- background keepalive
- worker-thread WebSocket access
