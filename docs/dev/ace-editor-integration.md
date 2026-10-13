# ACE Editor Integration

This document tracks the current AutoJs6 ACE editor deployment state. The default editor selection policy is automatic: an enabled, eligible `AutoJs6-Plugin-Ace-Editor` APK activates ACE, while a missing, disabled, or ineligible plugin leaves Native (`JsDroidEditor`) active. Native remains explicitly selectable and is also the fallback when the plugin runtime fails.

## Stable Path

- The host stores editor selection intent separately from the effective engine. The default `AUTO_ACE` intent resolves to ACE only while the plugin is available and otherwise resolves to Native.
- Native remains available from settings and from the editor engine menu. An explicit Native selection made while ACE is available enters `NATIVE_EXPLICIT` mode and suppresses later automatic ACE selection.
- Explicitly selecting an available ACE engine restores `AUTO_ACE` mode. Selecting ACE while the plugin is unavailable does not change either the selection mode or effective engine.
- ACE opens when the host discovers an enabled plugin with an accepted signer or trust state and compatible Editor API, host-version, and binary ABI metadata. Any discovery or load failure falls back to Native.
- The host APK contains the typed Editor API, secure loader, engine adapter, preferences, and diagnostics integration. The Ace WebView runtime, JavaScript bridge, language services, IME implementation, and static assets live in the plugin APK.
- Editor API 7 adds whole-document folding through `Edit -> Folding -> Unfold all / Fold all` (Ace Editor plugin 1.32.0+, host versionCode 5352+). Older plugin contracts remain loadable but do not expose this submenu; Native does not expose it either. Folding remains available in read-only mode, does not change document text or undo history, and is replayable from Action favorites. Unfold all removes nested folds in one operation. Loading documents temporarily disable the submenu.
- Ordinary large files stay on ACE. The old progressive large-file path remains Native-only.
- Runtime ACE failures fall back to Native through the editor engine switch path.
- Fallback snapshots preserve text, dirty state, cursor, selection, breakpoints, debug line, read-only state, theme, and font size as far as each engine supports them.
- ACE text is mirrored on the Kotlin side through change deltas, so fallback can keep recent edits even when the WebView cannot be queried.
- The plugin bundles ordinary Iosevka as its default WOFF2 font, with system `monospace` as the final fallback. Iosevka Slab and the other optional open-source fonts are installed on demand from the signed AutoJs6 font catalog; they are not bundled assets.
- ACE font selection is available from settings and from the editor `More -> ACE editor font` menu.
- Font downloads are application-scoped: closing or recreating the selection dialog detaches its observer without stopping the transfer; only the row-level cancel action cancels the download.
- When a refreshed signed catalog revokes or replaces the selected download, every attached ACE editor re-resolves that selection and immediately falls back to bundled Iosevka when it is no longer authorized.
- The font dialog uses the same download icon as Plugin Center for missing fonts. Its title overflow menu can download every missing or outdated authorized font in one confirmed batch; batch downloads keep the current selection and use the downloader's two-worker limit.
- The signed catalog is published from the `ace-fonts` component of `SuperMonster003/AutoJs6-Shared-Assets`. Font Release tags and signing keys are scoped to that component so unrelated shared assets can evolve independently.
- The plugin bundles selected `@sm003/autojs6-dts` declaration files and generated AutoJs6 static-completion indices. The legacy `autojs_indices.js` remains only as a compatibility fallback inside the plugin runtime.
- ACE LSP smart completion is a stable, default-on feature backed by the
  bundled TypeScript language service. The `native-local` manager exposes
  deterministic in-process options for completion, hover, diagnostics, and
  signature help. It does not load Pro's `lsp.js`, create a WebSocket, or start
  an external server. Users can explicitly disable it, and startup, failure,
  large-document, and opt-out paths keep the static completion index as a
  fallback.
- ACE hover tooltips are optional at the bridge boundary. The tooltip layer
  creates DOM lazily, uses the local safe markdown subset renderer, escapes raw
  HTML, and does not fetch external markdown resources.

## Plugin Hot-Plug Lifecycle

### Selection Intent And Effective Engine

- `AUTO_ACE` is the default and migration mode for a missing legacy preference
  or a legacy explicit ACE value. It selects ACE whenever the plugin is
  available and Native whenever it is not.
- A legacy explicit Native value migrates to `NATIVE_EXPLICIT`. A user selection
  of Native changes the mode to `NATIVE_EXPLICIT` only when ACE is available,
  so an already unavailable plugin cannot accidentally turn an automatic
  fallback into a permanent opt-out.
- A user selection of ACE while it is available changes the mode back to
  `AUTO_ACE`. Later disable, uninstall, reinstall, or re-enable transitions then
  continue to reconcile the effective engine automatically.
- A user selection of ACE while it is unavailable is rejected before the
  preference is written. The host shows an install-or-enable dialog whose
  positive button opens the official Plugin Center scope filtered by the exact
  ACE plugin package name.
- Automatic fallback, a large-file Native route, or a plugin runtime failure
  must never rewrite the stored selection intent. Effective-engine changes and
  explicit user selections use separate APIs.

### Availability Observation

- The application-scoped plugin state monitor observes both Plugin Center
  enable/authorization changes and Android package add, remove, replace,
  change, and data-clear broadcasts for the ACE package.
- Discovery distinguishes available, not installed, disabled, and ineligible
  states. Disabled application and service components remain discoverable for
  accurate user guidance.
- Package update removal events carrying `Intent.EXTRA_REPLACING=true` are
  ignored to avoid a transient Native fallback. Once replacement completes, an
  active ACE editor reloads the plugin session if package identity changed.
- State changes are delivered on the main thread and de-duplicated by change
  identity so multiple visible host surfaces do not emit duplicate notices.
- Installing, enabling, disabling, replacing, or uninstalling the plugin must
  take effect while the host remains running; none of these transitions may
  require an application restart.

### Foreground Editor And UI Safety

- If an active ACE editor loses the plugin, `EditorView` immediately switches
  to Native using the latest host-side shadow snapshot. Text, dirty state,
  cursor, selection, breakpoints, debug line, read-only state, theme, font size,
  search state, and visible position are preserved as far as Native supports
  them.
- The fallback path must not depend on a successful call into the removed or
  disabled plugin. The ACE adapter keeps host-side text and state caches and
  treats session destruction as idempotent.
- A package-state fallback cancels or rolls back any in-progress editor
  transfer before changing engines, invalidates stale asynchronous switch
  callbacks, and leaves unsaved content saveable in Native.
- A reason-specific long Toast reports disabled, unavailable, or uninstalled
  state. It is separate from the one-time runtime-failure diagnostics notice
  and is shown at most once for one plugin-state change.
- An ACE-scoped settings activity, editor settings dialog, ACE font dialog, or
  ACE plugin settings page that was entered while ACE was available closes
  when the plugin becomes unavailable. General settings entered while Native
  is already effective remains usable so the install-or-enable prompt can still
  be reached.

## Diagnostics

`AceDiagnostics` exports a plain-text snapshot for issue reports. The editor menu entry is:

```text
More -> Copy ACE diagnostics
```

The snapshot includes:

- engine type
- ACE health state
- last failure type and message
- asset URL
- WebView package version
- Android version
- LSP enabled/state, transport, URI, fallback, provider, TypeScript loader,
  document-size limit, semantic-suppression, and server availability fields
- file size and large-file policy
- TextMirror revision
- ready/state/heartbeat timestamps
- IME state, height, transition timestamps, and ignored IME request reason
- viewport policy, host IME event, and ACE resize schedule/dispatch/complete fields
- bridge event window counters for spotting JS-to-Kotlin event storms
- runtime event history for the last 30 ACE keyboard, resize, heartbeat,
  JavaScript, console, renderer, and fallback events

When ACE falls back to Native, `EditorView` keeps the last ACE diagnostics snapshot so the menu can still copy the failed ACE state after the Native editor is already active.

## UX Rules

- First manual Native/ACE switch shows that undo/redo history is not preserved across engines.
- Runtime ACE fallback shows a one-time Toast and points users to diagnostics.
- Plugin disable or uninstall while ACE is visible shows a reason-specific long
  Toast after Native has taken over.
- Debug-forced Native routing shows a one-time Toast.
- Settings details show the effective engine while preserving the independent
  automatic or explicit-Native selection intent.

## Clean Host Layout Decision

ACE does not need a separate clean-host experiment flag at this stage. The ACE path is already clean-host by default:

- `EditorViewportPolicyFactory` routes ACE to `AceEditorViewportPolicy`, not `NativeEditorViewportPolicy`.
- `AceEditorViewportPolicy` clears any residual bottom padding and delegates IME geometry to the ACE engine.
- Native bottom padding, Android `TextView` cursor math, and `requestRectangleOnScreen` stay native-only.
- ACE resize behavior is observable through diagnostics and covered by the IME show/hide checklist.
- ACE bridge cursor, text, and state events are throttled so keyboard animation
  does not create unbounded JS-to-Kotlin callback bursts.
- Dedicated keyboard and viewport coverage lives in
  `docs/dev/ace-keyboard-viewport-test-plan.md`, including first paint, tap
  anchors, repeated IME stress, completion accept, long-press selection, CJK
  input, rotation, fallback, ACE large-file direct loading, and bridge event storm
  diagnostics.
- Cross-APK instrumentation attaches the plugin-provided view to a real host
  activity. Repeated IME stress remains part of the physical-device matrix.

Do not add `key_ace_clean_host_layout` unless there is a concrete second ACE host layout to compare. A flag without another implementation would add state, test matrix size, and ambiguous diagnostics without improving the stable path.

See `docs/dev/editor-viewport-policy.md` for the Stage 3 v2 decision to keep
`EditorViewportPolicy` narrow and avoid adding `EditorViewportHost` until there
is a real second host or lifecycle requirement.

## Release Gate

Before an ACE release candidate is published:

- In the AutoJs6 host repository, run `node tools/ace-ci/check-ace-release-gate.mjs`.
- Run the host policy, mapping, and LSP preference unit tests, then compile
  `AceEditorPluginHostDeviceTest`.
- In the `AutoJs6-Plugin-Ace-Editor` repository, run
  `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug` and the plugin's
  connected-device smoke tests.
- Install a host-compatible plugin signed by the host or marked
  `OFFICIAL`/`TRUSTED`, then run the host cross-APK
  `AceEditorPluginHostDeviceTest`; a skipped test is not a release pass.
- Verify missing, disabled, untrusted, contract-incompatible, host-version
  incompatible, and linkage-failing plugins all keep the Native editor usable.
- Run `EditorEngineSelectionPolicyTest` and
  `AceEditorPluginPackageEventPolicyTest`. Confirm a fresh automatic mode tracks
  plugin availability, explicit Native suppresses automatic selection,
  explicit ACE restores it, and replacement removal is ignored.
- Run `ACE-HOTPLUG-001` through `ACE-HOTPLUG-006` from
  `docs/dev/ace-editor-test-plan.md` without restarting the host. Cover Plugin
  Center disable/enable, Android package disable/enable, install, replacement,
  and uninstall.
- Confirm active-editor disable and uninstall preserve unsaved text and dirty
  state in Native, show one reason-specific long Toast, and do not invoke stale
  plugin callbacks after destruction.
- Confirm unavailable ACE selection leaves preferences unchanged and opens
  Plugin Center filtered to the ACE package. Confirm ACE-scoped activities and
  dialogs close when availability is lost.
- Run the manual cases and IME checklist in
  `docs/dev/ace-editor-test-plan.md`, plus the dedicated matrix in
  `docs/dev/ace-keyboard-viewport-test-plan.md`.
- Track the automated/manual split in `docs/dev/ace-release-gate-status.md`.
- Treat `docs/dev/ace-release-device-report-sony-xq-at72.md` as historical
  pre-extraction evidence; record new cross-APK results separately.
- Verify plugin-side asset-missing, ready-timeout, JS-init, and renderer-gone
  hooks, plus the host-side large-file Native-routing hook.
- Verify recent small-file input survives fallback through the plugin text
  snapshot, and copy diagnostics both before and after fallback.
- Confirm `docs/dev/ace-lsp-design.md` keeps local smart completion default-on,
  preserves explicit opt-out and static degradation, and does not present an
  external/server-backed LSP as shipped.
- In the plugin repository, confirm `autojs6/types/lib.autojs6.d.ts`, selected
  declarations under `autojs6/types/autojs6`, ordinary Iosevka, and their
  licenses remain bundled in the plugin editor asset tree.
- Confirm optional fonts remain absent from the plugin APK and are accepted
  only through the signed catalog after size, WOFF2, and SHA-256 validation.
- Confirm the plugin repository includes `THIRD_PARTY_NOTICES.md`, the Ace
  license, the TypeScript license and notices, and the declaration license.

## Deferred Work

- VisualEditorAce from the Pro layout remains deferred. The Stage 3 v2
  investigation is recorded in
  `docs/dev/visual-editor-ace-investigation.md`; do not add an exposed
  `VISUAL_ACE` enum or UI entry until the ordinary ACE editor has recorded
  keyboard and viewport matrix results on representative devices.
- External/server-backed LSP remains a separate deferred path. The released
  `native-local` feature uses the bundled TypeScript language service and local
  declarations only; server startup and WebSocket transport are not exposed.
- Server-backed LSP transport and public VisualEditorAce engine exposure are
  not primary targets until their own scope, security, lifecycle, and
  representative-device gates are defined.
