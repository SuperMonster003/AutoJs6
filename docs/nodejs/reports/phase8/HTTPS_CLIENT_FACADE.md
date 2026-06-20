# S8-22 Controlled HTTPS Client Facade

Status: complete

S8-22 completes the HTTPS-specific boundary for the controlled `node:https`
client facade. The request path shares the S8-21 controlled fetch-backed client
adapter, while HTTPS-only TLS customization stays fail-closed.

## Covered Surface

`NodeHttpsClientFacadeInstrumentationTest` verifies:

- `require("https") === require("node:https")`
- `https.request()` dispatches `https:` URLs through the controlled transport
  when an enforced bridge manifest declares `network`
- request headers and buffered request bodies are serialized to the controlled
  fetch bridge
- response status, status text, headers, and body are surfaced through the
  `IncomingMessage` subset
- safe `https.Agent` compatibility shape accepts `keepAlive`, `maxSockets`, and
  `maxFreeSockets`, and `destroy()` marks the agent destroyed
- bridge requests carry the `network` permission tag
- pending bridge calls drain to zero

## HTTPS Policy Denials

The focused gate rejects TLS customization with
`ERR_AUTOJS6_NETWORK_POLICY_DENIED`, including:

- `rejectUnauthorized: false`
- custom `secureContext`
- client certificate fields such as `cert`, `key`, and `ca`
- request-level `createConnection`
- `https.Agent({ rejectUnauthorized: false })`
- `https.Agent({ ca: ... })`

The implementation also denies the same class of TLS/OpenSSL customization
fields in request or agent options: `pfx`, `passphrase`, `secureOptions`,
`secureProtocol`, `ciphers`, `minVersion`, `maxVersion`,
`checkServerIdentity`, `session`, `requestCert`, `clientCertEngine`, `crl`,
`ecdhCurve`, `honorCipherOrder`, and `sigalgs`.

Certificate pinning and client certificates remain separate design-gated
features. S8-22 does not add a pinning API or expose certificate objects.

## Gate

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeHttpsClientFacadeInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

This gate is part of `nodeJsV11AlphaInstrumentationClasses`, and therefore
flows into the v1.1 alpha/beta functional profile selection.

## Evidence

Verified on Sony XQ-AT72 `QV710AF65F` on 2026-06-20:

- `:app:compileAppDebugAndroidTestKotlin`
- focused `NodeHttpsClientFacadeInstrumentationTest` finished 1/1 successfully
- focused `NodeHttpClientFacadeInstrumentationTest` finished 1/1 successfully
- key default-denial regressions for `NodeSecurityRegressionInstrumentationTest`
  and `EmbeddedScriptMvpInstrumentationTest` finished successfully
- `:app:verifyNodeCompatibilityProfile`
- `git diff --check`
- `git diff -- version.properties` produced no diff

## Security Boundary

S8-22 keeps HTTPS as a controlled client compatibility facade only:

- normal certificate validation remains provider-owned; scripts cannot disable
  it with `rejectUnauthorized: false`
- no arbitrary TLS socket, OpenSSL context, DNS lookup, `CONNECT` tunnel,
  socket upgrade, or certificate object is exposed
- client certificates and certificate pinning require future design gates
- without an enforced manifest declaring `network`, `https.request()` and
  `https.get()` keep the existing synchronous disabled error path
