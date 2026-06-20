# S8-25 Controlled Undici Facade

Status: complete

S8-25 adds a reserved `require("undici")` compatibility facade backed by the
same controlled fetch bridge used by global `fetch()` and the controlled
`node:http` / `node:https` client facade. It is not a desktop Undici runtime:
raw connector, socket, proxy, upgrade, and pipeline paths remain unavailable.

## Covered Surface

`NodeUndiciFacadeInstrumentationTest` verifies:

- `require("undici")` is reserved and cannot be shadowed by
  `node_modules/undici`
- the module is frozen and exposes `default` as the same facade object
- `fetch(input, init)` dispatches through controlled fetch when an enforced
  manifest declares `network`
- `request(input, init)` returns a limited Undici-like result with
  `statusCode`, `status`, `statusText`, `headers`, `body`, `trailers`,
  `opaque`, and `context`
- `Headers`, `Request`, and `Response` are the same Web Platform constructors
  exposed by the runtime
- `Dispatcher`, `Agent`, and `MockAgent` expose safe package-compatibility
  shapes without opening sockets
- `setGlobalDispatcher()` and `getGlobalDispatcher()` accept only AutoJs6
  limited dispatcher instances
- `MockAgent.disableNetConnect()`, `get()`, `intercept()`, `reply()`,
  `times()`, `persist()`, and `delay()` preserve common test-helper shapes
  without intercepting real network traffic
- `connect`, `stream`, dispatcher injection, raw custom connectors,
  `socketPath`, and unsafe dispatcher objects reject with
  `ERR_AUTOJS6_NETWORK_POLICY_DENIED`
- bridge requests carry `network`, and pending calls drain to zero

## Gate

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeUndiciFacadeInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

This gate is part of `nodeJsV11AlphaInstrumentationClasses`, and therefore
flows into the v1.1 alpha/beta functional profile selection.

## Evidence

Verified on Sony XQ-AT72 `QV710AF65F` on 2026-06-20:

- `:app:compileAppDebugAndroidTestKotlin`
- focused `NodeUndiciFacadeInstrumentationTest` finished 1/1 successfully
- combined fetch facade, Undici facade, and raw network policy command
  finished 3/3 successfully
- `:app:verifyNodeCompatibilityProfile`
- `git diff --check`
- `git diff -- version.properties` produced no diff

## Security Boundary

S8-25 keeps Undici as a controlled facade:

- `undici.fetch()` and `undici.request()` route through controlled fetch and
  require the same enforced `network` bridge capability
- `Dispatcher`, `Agent`, and `MockAgent` are shape-only compatibility objects;
  they do not own sockets, native handles, DNS lookup, TLS sessions, pools, or
  Android provider resources
- raw `connect`, `upgrade`, `stream`, and `pipeline` APIs remain denied
- raw dispatcher injection, custom `connect` / `factory`, `socketPath`, proxy,
  and tunnel options remain denied
- local `node_modules/undici` packages cannot shadow the AutoJs6 facade
