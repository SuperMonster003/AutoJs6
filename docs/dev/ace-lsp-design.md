# ACE LSP Smart Completion Design

ACE LSP smart completion is a released, default-on editor feature. Despite the
setting's historical LSP name, the shipped implementation is a bundled,
in-process TypeScript Language Service. It does not load Ace Pro's `lsp.js`,
open a WebSocket, start a process, or connect to an external language server.
Users can explicitly disable the feature; static AutoJs6 and ECMAScript
completion remains available when it is disabled or degraded.

The bundled TypeScript language service runs entirely in process and is the
only semantic engine covered by this release.

## Shipped Scope

- Setting resource/value: `key_ace_lsp_enabled` /
  `key_$_ace_lsp_enabled`
- Default: `true`
- Bridge method: `autojs.getLspOptions()`
- Native manager: `AceLspServerManager`
- Enabled manager identity: `manager=native-local`
- State and transport: `state=local-language-service` and
  `transport=in-process`
- External-server fields: `serverAvailable=false`, `startSupported=false`, and
  no `serverUri`
- Fallback: `fallback=static-completion`
- Bundled declaration file: `autojs6/types/lib.autojs6.d.ts`
- Declaration source: selected `@sm003/autojs6-dts` `aj6-int-*.d.ts` files
  under `autojs6/types/autojs6`

The local service provides semantic completion, hover, diagnostics, and
signature help for JavaScript and TypeScript documents. JSON documents use the
JSON-aware diagnostic path. The generated AutoJs6 index and local syntax
diagnostics stay active during startup and after any semantic-service failure.
Semantic results are authoritative when available, so a local variable that
shadows an AutoJs6 global is described by TypeScript rather than merged with an
unrelated static-global result.

Supported default suffixes are `.js`, `.mjs`, `.cjs`, `.jsx`, `.ts`, `.tsx`,
`.d.ts`, `.json`, `.auto.js`, and `.node.js`. An untitled buffer uses the synthetic
`file:///autojs6/editor/current.js` document URI.

The bundled TypeScript 4.2.4 runtime provides a TypeScript 4.2 syntax baseline
and local, single-file semantics. Project-wide module resolution is not part of
this release; unreliable module-not-found diagnostics are suppressed rather
than presenting missing in-memory project files as editor errors. Modern
NodeNext/`.mts`/`.cts` module semantics are not claimed by this scope.

Hover tooltip rendering is handled by `AutoJsAceTooltip`. It creates its DOM
lazily, uses a local safe-markdown subset, escapes raw HTML, and does not fetch
markdown resources.

## Loading And Lifecycle

The ACE bootstrap path does not synchronously load the roughly 9 MB TypeScript
runtime. After first paint, an idle callback (with a bounded timeout fallback)
starts the local service warm-up. Turning the setting on while the same editor
page is open schedules the same warm-up, so a WebView reload is not required.

Runtime and service-script loading has a five-second timeout and is retried once
after a short bounded delay. Service initialization itself also gets one
bounded reconstruction attempt. Standard TypeScript declaration libraries are
loaded lazily from the packaged assets, and a semantic request reuses one
document snapshot instead of copying the Ace session twice.
A stale callback cannot re-enable a disabled or destroyed client because load
results are guarded by the current configuration revision. Disabling LSP,
destroying the editor page, or opening the slow-operation circuit disposes the
TypeScript service and cancels pending retry/warm-up work.

`getLspState()` is observational: reading diagnostics does not create or warm a
TypeScript service by itself.

Runtime state distinguishes `configured`, `ready`, and `degraded`. Provider,
loader, suppression, document-limit, TypeScript-version, and last-operation
timing fields are sent through a sanitized bridge event and cached in Native
ACE diagnostics; source text is never included in that event.

## Resource Limits And Degradation

- Documents longer than 512 KiB (measured as JavaScript string length) do not
  enter the semantic TypeScript path. The editor remains usable with static
  completion; diagnostics are cleared to avoid another whole-document pass,
  and state reports the document length, limit, and suppression reason.
- A completion response is capped at 300 items before it reaches Ace.
- The first semantic operation taking at least 1,000 ms opens the local
  slow-operation circuit for that editor instance and disposes the service.
- Load, completion, hover, diagnostic, or signature failures are non-fatal.
  They keep editing available and fall back to the generated local index or
  local syntax checks.
- Fatal Native fallback remains reserved for editor-runtime failures such as a
  page-load error, ready timeout, JavaScript fatal error, or renderer loss.

## Explicit Opt-Out

The static default and the runtime preference fallback are both enabled. An
explicit stored `false` remains respected, and the settings UI continues to
expose the switch. When disabled, `autojs.getLspOptions()` returns `{}`, no
TypeScript runtime is loaded, no network transport is created, and the client
reports static providers.

File-type selection remains independently configurable. Restoring it returns
to the stable suffix set listed above.

## External Server Boundary

External/server-backed LSP is not part of this released feature. Any future
implementation must separately define server ownership, transport, restart
policy, project-root permissions, document synchronization, and Android API
compatibility. Definition, references, rename, document symbols, formatting,
code actions, and project-wide indexing remain deferred rather than silently
promised by the local smart-completion setting.

## Verification

- Preference tests verify the default-on behavior, explicit opt-out, reset
  behavior, and stable file-type gating.
- Manager tests verify `native-local`, in-process state, the synthetic untitled
  URI, local startup providers, and the 512 KiB semantic limit.
- `node tools/ace-lsp/test-lsp-client.mjs` loads the real bundled TypeScript
  runtime and service, then verifies semantic completion, hover/JSDoc,
  diagnostics, signature help, hot enable/disable, bounded retries, disposal,
  completion limits, large-document degradation, and static fallback behavior.
- `AceCodeEditorSmokeTest` covers default-on and hot-enable behavior in the
  WebView, verifies real TypeScript semantic providers without any network
  transport, and confirms that sanitized runtime readiness reaches Native
  diagnostics.
- `node tools/ace-tooltip/test-tooltip.mjs` verifies lazy tooltip creation and
  local escaped-markdown rendering.
