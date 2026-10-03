# P2.4 input and P2.5 lists, scaffolds and notifications

Date: 2026-10-03. Starting plugin 6e80267 (build 12), isolated host 6812bdbd9f
(6.8.0 / 5316). Host master 2321c94294 was merged as 5fe21f9c3a before implementation;
the localized history merge preserved both Compose preview and newer Picker/Inspector entries.
Implementation commits: plugin cebd1d3 (build 13), host ee82363172 (command callbacks) and
60bae7f8c5 (keyboard ownership and device fixtures). Final documentation commit uses plugin build 14.

## Delivered behavior

The V1 renderer implements all 29 node types and advertises command-only Snackbar as its 30th
catalog entry. The frozen AAR, wire version, dependency ownership and 51-entry shared lock are
unchanged. The public JavaScript compose entry remains P3 work, and all localized announcements
continue to describe a development preview.

TextField and OutlinedTextField retain TextFieldState by node identity outside composition, including
offscreen lazy items. Native text, UTF-16 selection and composing ranges advance editSeq without
waiting for script echo. One notification per field per display frame uses the latest accepted
callback/generation. Edit observes native state synchronously before sequence arbitration, rejects
older/future sequences atomically, and rejects changes through declarative text patches. The five
slots, keyboard/IME options, line limits, errors, readOnly and password transformation are mapped.
Password masking covers both visible and input semantics and disables copy/cut. Read-only permits
selection; disabled or replaced editor connections cannot mutate state. Replacement/removal cancels
old pending notifications and errors. Generic click/long-click observation preserves native gestures.

The API35 replacement test exposed a Material focus interaction race: the new field emitted Focus
before the decoration collector subscribed, leaving native Focused=true while hiding its placeholder.
Keying composition by both ID and type prevents old component state reuse; TextFieldInteractionSource
also retains one active Focus for late subscribers. Per-collector identity deduplication prevents a
replayed and queued live Focus from requiring two Unfocus events. Press history is never replayed.
Four coroutine JVM tests and the original same-ID field replacement device test cover this behavior.

LazyColumn/Row use stable item keys and content types, logical content padding, arrangement/spacing,
native scroll state and display-frame-coalesced measured scroll events. Accepted-tree validation
rejects bad indices and commands belonging to removed/retyped nodes. requestScrollToItem applies
commands in the next measure, including a dataset change accepted in the same main-thread turn.
Scaffold/TopAppBar slots, controlled AlertDialog and both progress modes use Material components.

Snackbar selects the first Scaffold in stable preorder. A custom snackbarHost replaces that host,
so the session command is rejected rather than secretly redirected to another Scaffold. Its bounded
1024-request FIFO captures the execution generation and emits one action/dismissal per request.
Removing/replacing the host dismisses pending requests; session close cancels silently. Worker
failure drains callbacks before reporting an error. Host command callbacks use one-shot tickets
within the shared 4096 callback budget, survive unrelated commits, and release on terminal delivery,
submission failure, event eviction and close. Ticket generation publication precedes event emission
even when a drain was already queued. Node-event generation filtering remains unchanged.

## Host keyboard ownership

ComposeActivityInsets is shared by ScriptExecuteActivity and the isolated device fixture. While a
Compose view is attached, the host selects adjustResize, applies system-bar padding with
bottom=max(systemBars.bottom, ime.bottom), and consumes the insets. Scaffold/TopAppBar have zero
system insets; the renderer adds no systemBarsPadding or imePadding. The final detach restores the
previous window policy and legacy system-bar-only handling. Other embedders, including the P3.4
floaty integration, must implement/validate their own host ownership.

The device fixture measures the bottom field against the actual IME accessibility-window edge,
checks the original viewport after system Back hides the keyboard, and checks window-policy
restoration after detach. API24 measurements: initial content bottom 1794 px, IME top/field bottom
1019 px with 901 px host bottom padding, restored bottom 1794 px with 126 px system-bar padding.
The test originally combined asynchronous Blur, direct IMM hiding and platform clearFocus;
the IME reopened on API24. A real system Back action isolates the intended insets assertion.

## Verification record

| Check | Result |
| --- | --- |
| Plugin JVM | 63/63, including revision/frame queue 7, focus-interaction races 4, advanced padding 1 and Snackbar queue 6 |
| Host JVM | 27/27 Compose tests, including callback/session lifecycle 17; frozen API tests 13/13 |
| Plugin devices | API24 x86 AVD, API33 Redmi 22120RN86C and API35 Xiaomi Pad 6 Pro: 31/31 each (contract 5, existing renderer 12, fields 9, advanced components 5) |
| Debug cross-APK host | API24: Loader7 + Controls4 = 11/11; API33: Controls4 = 4/4 |
| Actual Chinese IME | API33 existing Xiaomi Sogou: physical QWERTY touches for nihao, Chinese candidate selection, native text/event equals 你好; 1/1 |
| Minified plugin | API24 and API35: counter/close, standalone INFO, controlled inputs, native input/IME and Snackbar callbacks, 6/6 each; final build14 artifact on API33: the same 6 plus actual Sogou candidate input, 7/7 |
| Native input details | Real InputConnection composing/commit/finish, continuous deletion, clipboard paste, UTF-16/reversed selection, stale/current edit races; clipboard restored |
| Generated documentation | 10-language source updates and all 36 plugin generated files pass --check; host localized history regenerated offline |
| Integrity | Shared fingerprint unchanged; debug/release DEX ownership scan passes; signature, exact graphics-path inventory and ELF/ZIP 16 KB alignment pass |
| Lint | Debug: no issues; release: 0 errors and the same 3 pre-existing unused identity-resource warnings |

The Sogou case is explicitly selected with `composeSogouEnglishQwerty=true` after observing the
device's installed English QWERTY layout. It temporarily selects Chinese and restores English in
finally; before/after screenshots confirm restoration. The IME may hold pinyin inside its own
candidate buffer without sending intermediate text to the application, so the acceptance assertion
waits for Chinese candidate commit. The separate InputConnection test covers native composing ranges.
No input method package was installed and the default-input-method setting was not changed.

### 1000-row measurement (debug, 3001 nodes)

| Device | Initial apply ms | Incremental patch ms | Scroll sample frames | Frame p50 / p95 / max ms |
| --- | ---: | ---: | ---: | --- |
| API24 x86 temporary AVD | 31.040 | 18.610 | 23 | 16.670 / 16.677 / 66.665 |
| API33 Redmi 22120RN86C | 139.093 | 89.125 | 13 | 16.940 / 50.855 / 203.450 |
| API35 Xiaomi Pad 6 Pro | 37.517 | 34.624 | 43 | 6.956 / 6.972 / 55.659 |

`AdvancedRendererTest.thousandRowPatchAndRealScrollFramesRetainKeysAndControlledSwitchState` records
real Choreographer intervals during the injected gesture and settling window, with no production
performance threshold. These small debug samples include initial work/jank, are not sustained
frame-rate claims, and leave threshold-setting/optimization to P5.3 as planned. The fixture verifies
host-controlled Switch state follows its stable key after movement, and separate tests cover an
accepted growth/shrink followed by ScrollTo in the same main-thread turn.

### Artifact and cleanup

Final local release: `releases/autojs6-plugin-compose-ui-v1.0.0-a48bd1c6.apk`, version 1.0.0 / build14,
2356066 bytes, CRC32 a48bd1c6, SHA-256
15c2cf0e86a9fff0963fb2e2614ad4dfd3d8e9be8b51a6b4435e216a7d44c598.
The preceding build12 preflight used identical renderer code; the final build updates only version
metadata and is additionally exercised through the installed host on API33. Final DEX inventory:
debug 9397262 bytes / 13148 classes / 90913 method references; release 7142 classes / 44715 references.

The temporary `compose_p24_api24` AVD (emulator-5560, x86, 2 GB userdata) and its metadata were
removed after validating its name and workspace-contained path. All four test-owned packages
(plugin, plugin test, isolated host, host test) were removed and absence checked on the AVD and
both physical devices. Device input-method identity remained unchanged. Clipboard and IME language
were restored, and test-owned /data/local/tmp capture files were removed.

During this session the original `D:/idea-projects/AutoJs6` worktree acquired unrelated Mail and
code-generation work. Those changes were not touched or committed. Host implementation and its
localized documentation remain committed in `D:/idea-projects/AutoJs6-ComposeUi-Spike` on
`spike/compose-ui-p0`; integration back into the active original worktree is deferred. Both task
worktrees are clean after the final documentation commits. D7 remains in force: no push, official
index entry or public release was performed.
