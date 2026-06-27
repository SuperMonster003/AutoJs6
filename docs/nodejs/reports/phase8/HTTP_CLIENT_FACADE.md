# S8-21 Controlled HTTP Client Facade

Status: complete

S8-21 adds a controlled `node:http` / `node:https` client facade on top of the
existing AutoJs6 controlled fetch bridge. It improves npm compatibility for
packages that probe or use `http.request()` / `http.get()` without granting raw
socket authority.

## Supported Surface

`NodeHttpClientFacadeInstrumentationTest` verifies:

- `require("http") === require("node:http")` and
  `require("https") === require("node:https")`
- `http.request()` and `http.get()` when an enforced bridge manifest declares
  the `network` capability
- `https.request()` over the same controlled transport
- request headers and response `headers`, `rawHeaders`, `statusCode`, and
  `statusMessage`
- `ClientRequest.write()`, `end()`, `setTimeout()`, `abort()`, and `destroy()`
- `IncomingMessage` `data` / `end` events, `setEncoding()`, `pause()`,
  `resume()`, and `pipe()`
- streaming request body compatibility through buffered chunks before dispatch
- response body delivery as `data` / `end` events
- timeout and abort mapping to AutoJs6 network error codes
- default redirect handling stays manual; redirects follow only when
  `redirect` or `maxRedirects` is explicitly configured
- pending bridge calls drain to zero

The facade serializes requests through `__autojs6_controlled_fetch()`, so it
inherits the controlled fetch URL, timeout, redirect, response-size, abort, and
provider policy instead of opening native Node sockets.

## Gate

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeHttpClientFacadeInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

This gate is part of `nodeJsV11AlphaInstrumentationClasses`, and therefore
flows into the v1.1 alpha/beta functional profile selection.

## Regression Coverage

The implementation was checked against the closest pre-existing denial paths:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeRawNetworkPolicyInstrumentationTest,org.autojs.autojs.engine.NodeFetchBridgeInstrumentationTest#jsFetchModuleUsesFakeTransportAndKeepsRawModulesDisabled,org.autojs.autojs.engine.NodeFetchBridgeInstrumentationTest#jsAxiosCompatibilityLayerUsesControlledFetchOnly" "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeSecurityRegressionInstrumentationTest#dangerousApisAndNetworkOperationsStayDenied" "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.EmbeddedScriptMvpInstrumentationTest#limitedHttpBuiltinsExposeMetadataAndBlockNetworkRequests" "-Pautojs.nodejs.test.deviceProfile=sony"
```

## Evidence

Verified on Sony XQ-AT72 `QV710AF65F` on 2026-06-20:

- `:app:compileAppDebugAndroidTestKotlin`
- focused `NodeHttpClientFacadeInstrumentationTest` finished 1/1 successfully
- raw network / fetch / axios regression command finished 3/3 successfully
- `NodeSecurityRegressionInstrumentationTest#dangerousApisAndNetworkOperationsStayDenied`
  finished 1/1 successfully
- `EmbeddedScriptMvpInstrumentationTest#limitedHttpBuiltinsExposeMetadataAndBlockNetworkRequests`
  finished 1/1 successfully
- `:app:verifyNodeCompatibilityProfile`
- `git diff --check`
- `git diff -- version.properties` produced no diff

## Security Boundary

S8-21 does not expose desktop Node networking. These boundaries remain
unchanged:

- without an enforced manifest declaring `network`, `http.request()`,
  `http.get()`, `https.request()`, and `https.get()` keep the existing
  synchronous disabled error path
- `createServer()`, `listen()`, raw sockets, raw `net` / `tls` / `dns` /
  `dgram`, `CONNECT` tunneling, and socket upgrades remain unsupported
- `Agent` compatibility shape does not expose sockets or connection pooling
- redirects are not followed by default; explicit `redirect` / `maxRedirects`
  options are required
- local `node_modules/http`, `node_modules/https`, `node_modules/net`,
  `node_modules/tls`, `node_modules/dns`, and `node_modules/dgram` packages
  cannot bypass the reserved builtin policy
- no native addon, process, Java/Android object, or unscoped filesystem
  capability is added

The facade is therefore a compatibility adapter for client requests only, not a
general Node networking promotion.
