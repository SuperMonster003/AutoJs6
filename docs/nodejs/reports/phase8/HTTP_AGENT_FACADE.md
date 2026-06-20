# S8-24 Controlled HTTP Agent Facade

Status: complete

S8-24 documents and gates the limited `http.Agent` / `https.Agent`
compatibility shape used by the controlled `node:http` / `node:https` client
facade. It is a package-compatibility facade only; it does not manage raw
sockets or expose Node socket pooling.

## Covered Surface

`NodeHttpAgentFacadeInstrumentationTest` verifies:

- `http.Agent`, `https.Agent`, `http.globalAgent`, and `https.globalAgent`
  expose the expected limited constructor shape
- safe options such as `keepAlive`, `maxSockets`, and `maxFreeSockets` are
  preserved on the facade object
- `defaultPort`, `protocol`, and `getName()` match the controlled protocol
  shape expected by common packages
- `request(..., { agent })` records the provided facade agent on
  `ClientRequest.agent` while still dispatching through controlled fetch
- `destroy()` marks the facade agent destroyed without operating on sockets
- `requests`, `sockets`, and `freeSockets` remain empty compatibility objects
- request-level `createConnection` and `socketPath` reject with
  `ERR_AUTOJS6_NETWORK_POLICY_DENIED`
- custom agent objects exposing `createConnection()` or `addRequest()` reject
  with `ERR_AUTOJS6_NETWORK_POLICY_DENIED`
- facade `createConnection()` / `addRequest()` methods stay disabled and return
  the public network-disabled error
- bridge requests carry `network`, and pending calls drain to zero

## Gate

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeHttpAgentFacadeInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

This gate is part of `nodeJsV11AlphaInstrumentationClasses`, and therefore
flows into the v1.1 alpha/beta functional profile selection.

## Evidence

Verified on Sony XQ-AT72 `QV710AF65F` on 2026-06-20:

- `:app:compileAppDebugAndroidTestKotlin`
- focused `NodeHttpAgentFacadeInstrumentationTest` finished 1/1 successfully
- combined HTTP client, HTTPS client, stream semantics, and agent facade
  command finished 4/4 successfully
- `:app:verifyNodeCompatibilityProfile`
- `git diff --check`
- `git diff -- version.properties` produced no diff

## Security Boundary

S8-24 keeps Agent as a controlled facade:

- no socket pool, socket object, fd, native handle, DNS lookup, TLS session, or
  custom connector is exposed
- `createConnection`, `addRequest`, and `socketPath` cannot redirect the
  facade to raw Node networking
- HTTPS certificate validation and TLS customization remain governed by S8-22
- dispatch still requires an enforced `network` manifest and the controlled
  fetch bridge
