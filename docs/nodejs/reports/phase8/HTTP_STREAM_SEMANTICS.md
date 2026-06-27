# S8-23 ClientRequest / IncomingMessage Stream Semantics

Status: complete

S8-23 deepens the controlled `node:http` / `node:https` client facade added in
S8-21 and hardened in S8-22. It does not add raw socket networking; requests
still dispatch through `__autojs6_controlled_fetch()` only after an enforced
bridge manifest declares `network`.

## Covered Surface

`NodeHttpStreamSemanticsInstrumentationTest` verifies:

- writable `ClientRequest` body chunks are buffered before controlled dispatch
- readable `IncomingMessage` emits `data`, `end`, and `close`
- `IncomingMessage.pipe()` forwards body chunks to a destination and tolerates a
  backpressure-style `write()` return value of `false`
- request-level `AbortSignal` cancellation maps to
  `ERR_AUTOJS6_NETWORK_ABORTED`
- pre-aborted `AbortSignal` rejects without dispatching a bridge request
- `maxResponseBytes` is forwarded to controlled fetch and rejects oversized
  responses with `ERR_AUTOJS6_NETWORK_RESPONSE_TOO_LARGE`
- service destroy rejects pending facade requests with
  `ERR_AUTOJS6_BRIDGE_PROCESS_DEAD`
- bridge pending calls drain to zero after success, cancellation, response
  budget rejection, and service destroy

## Gate

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeHttpStreamSemanticsInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

This gate is part of `nodeJsV11AlphaInstrumentationClasses`, and therefore
flows into the v1.1 alpha/beta functional profile selection.

## Evidence

Verified on Sony XQ-AT72 `QV710AF65F` on 2026-06-20:

- `:app:compileAppDebugAndroidTestKotlin`
- focused `NodeHttpStreamSemanticsInstrumentationTest` finished 1/1
  successfully
- focused `NodeHttpClientFacadeInstrumentationTest` and
  `NodeHttpsClientFacadeInstrumentationTest` finished 2/2 successfully after
  the stream-semantics change
- `:app:verifyNodeCompatibilityProfile`
- `git diff --check`
- `git diff -- version.properties` produced no diff

## Security Boundary

S8-23 keeps the HTTP facade as a controlled client compatibility layer:

- no `createServer()`, `listen()`, socket, fd, DNS, TLS session, `CONNECT`, or
  upgrade handle is exposed
- `pipe()` is compatibility-level body forwarding, not a raw socket stream
- `AbortSignal` aborts the controlled fetch bridge request; it does not expose
  process or signal authority
- response-size enforcement remains the controlled fetch budget
- service destroy cancellation is owned by the AutoJs6 bridge pending-call
  registry
