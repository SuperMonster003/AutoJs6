# S8-26 HTTP npm Compatibility Corpus

Status: complete

S8-26 adds an offline, npm-shaped HTTP compatibility corpus for packages that
use the controlled `node:http` / `node:https` client facade. The corpus is not
a live network test and does not run `npm install`; every fixture is a fixed
asset with local `node_modules` snapshots authored for the gate.

## Covered Fixtures

`NodeHttpNpmCorpusInstrumentationTest` copies
`app/src/androidTest/assets/http_compat_corpus/` into a scoped working
directory, injects an enforced `network` manifest, installs a fake controlled
fetch transport, and verifies nine fixture patterns:

- package using `http.get()`
- package using `https.request()`
- package consuming `IncomingMessage` through `data` / `end`
- package posting `application/x-www-form-urlencoded` data
- package expecting request-level `AbortSignal`
- package expecting safe `http.Agent` / `https.Agent` options
- package following redirects with a follow-redirects-style wrapper
- package handling TLS provider failures from `https.request()`
- package expecting per-request proxy configuration denial without dispatch

The metadata subtest verifies:

- fixture count and required pattern coverage
- `package.json`, `test.cjs`, `expected.txt`, and `LICENSE_NOTE.md` presence
- CommonJS runner compatibility
- no vendored native `.node` addons
- no install-time lifecycle scripts

## Gate

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeHttpNpmCorpusInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

This gate is part of `nodeJsV11AlphaInstrumentationClasses`, and therefore
flows into the v1.1 alpha/beta functional profile selection.

## Evidence

Verified on Sony XQ-AT72 `QV710AF65F` on 2026-06-20:

- `:app:compileAppDebugAndroidTestKotlin`
- focused `NodeHttpNpmCorpusInstrumentationTest` finished 2/2 successfully
- combined HTTP client, HTTPS client, stream semantics, Agent facade, Undici
  facade, and HTTP npm corpus command finished 7/7 successfully
- `:app:verifyNodeCompatibilityProfile`
- `git diff --check`
- `git diff -- version.properties` produced no diff

## Security Boundary

S8-26 keeps the corpus inside the existing controlled network boundary:

- all request dispatch goes through the fake controlled fetch bridge transport
- every bridge request must carry the enforced `network` capability
- raw `net`, `tls`, `dns`, `dgram`, server/listen, `CONNECT`, upgrades, custom
  connectors, native addons, and install scripts remain unavailable
- pre-aborted `AbortSignal` paths are checked for no-dispatch behavior
- in-flight aborts must drain pending bridge calls to zero
