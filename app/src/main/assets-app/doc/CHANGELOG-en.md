******

### Release History

******

# v6.8.0

###### 2026/10/11

* `Hint` The screen color picker plugin is now 3-Color Picker 2.0 with a new package ID; install and enable it again. The old app can remain installed, but settings are not migrated automatically. The home drawer label is unchanged
* `Hint` Compose UI and its API reference, Ace Editor type completion and Offline Docs content are delivered as a local integration requiring a matching local host build; the plugin is not listed in the official index or formally released
* `Hint` Independent dialogs require AutoJs6 6.8.0 / 5321 and Compose UI with dialog-v1 support; TSX also requires TypeScript Engine 0.6.6. Non-UI scripts need the host overlay permission
* `Hint` This build uses Compose UI contract V2 and requires the matching AutoJs6 6.8.0 / 5322 host; companion TSX support requires TypeScript Engine 0.6.7. New hosts still support existing components in older V1 renderers; extended components require a V2 renderer
* `Hint` Packaged apps need OpenCV selected at packaging time for images and other OpenCV features; a same-named runtime brought in by an OCR plugin is no longer used
* `Feature` Packaged Node.js projects and scripts use the external Node.js Runtime plugin, support independently signed applications, and show installation guidance when the runtime is unavailable
* `Feature` A "Show launcher icon" switch in plugin settings, enabled by default, hides and restores the original icon variant for official and third-party plugins (requires Root or authorized Shizuku running as Root)
* `Feature` A launcher-icon option in the installation wizard applies only to plugins newly installed by that batch, defaults to showing icons and leaves already installed plugins unchanged
* `Feature` Screen actions bind confirmation to inspected host nodes, support text append and bounded scrolling, and report action results and window changes
* `Feature` Screen OCR is offered only when the host reports an available authorized OCR plugin; text is merged into bounded lines with coordinates
* `Feature` ai.agent script API for task creation, queries, events, input, confirmation, cancellation and detached background tasks; result/context expose @agent registered-script results and context, with three task examples
* `Feature` The options of ai.agent.run and ai.agent.create accept a boolean plan: with true, Three Stove Agent enters plan mode, where the model first proposes 1 to 8 steps that are reviewed through an input event of kind plan before the first tool runs; when omitted, the preset's plan mode switch applies
* `Feature` Three Stove Agent can run confirmed generated JavaScript, stop its own script on cancellation or timeout, and return structured results and console output; the script uses host permissions, without a file-access sandbox
* `Feature` ai.agent: MCP tools from selected local or external servers, with per-server risk settings and the mcp group disabled by default
* `Feature` The Three Stove Agent model broker accepts image inputs and image tool results for compatible vision models (requires plugin support)
* `Feature` Android 17 local network permission controls in the drawer, the settings of packaged apps and user-initiated VS Code connections, with permission declarations based on the actual targetSdk; switching it off offers to exit AutoJs6 so that Android revokes the permission, or to open system settings
* `Feature` Process exit history in developer options on Android 11 and later, with recent system reasons across app processes, memory limiter identification, and copyable reports (entry: long-press the app icon on the "About app and developer" page)
* `Feature` MCP Server drawer switch with installation, activation, enablement, authorization, and compatibility guidance; notification Stop synchronization; saved plugin settings retained on reconnect; permission-checked settings entry from the drawer and Plugin Center. Restores a previously enabled server when AutoJs6 opens, unless the user stopped it while the host was absent; no boot startup.
* `Feature` MCP workspace capabilities add Android application queries, editor line and column positioning, automatic accessibility activation using configured strategies, bounded Shell output, and read-only descriptors for large JSON results; root execution uses a separate shell.root grant and phone setting
* `Feature` Independent QuickJS Runtime plugin engine for local single-file JavaScript and ES modules, routed by the "quickjs"/"qjs" directive or `.quickjs.js/.quickjs.mjs` markers without falling back to Rhino, Node.js, or Bun
* `Feature` `device.pageSize` read-only property returns the running system memory page size in bytes, identifying 4 KB / 16 KB environments
* `Feature` Independent Bun Runtime plugin engine for local single-file JavaScript and TypeScript, routed by the "bun" directive or `.bun.js/.bun.ts/.bun.tsx` markers without falling back to Rhino or Node.js
* `Feature` The side drawer Tools section now adds the optional Screen Color Picker plugin as a third, default-off tool after Floating Button and Pointer Location; installation, enablement, runtime state, and removal refresh automatically without blocking the UI
* `Feature` MCP Server plugin contract module (plugin-api/mcp-server-api) with a frozen AIDL surface, a strict host client that binds the official plugin on a dedicated lease and rebinds up to three times after the plugin process dies, a grant-restricted host capability broker that routes plugin requests through the Node bridge provider, and Plugin Center registration as a default-off plugin
* `Feature` Manager for the "Accessibility service" drawer entry (a dialog, opened with the "Manager" button of the description dialog or by long-pressing the entry): the status section shows service status, system settings, in-process instance, uptime, event count and last event, windows and active window root, latest package and activity, service configuration, root and secure settings availability and listener counts; the control section offers start, stop, restart, clear node cache, self-check (counts window roots and nodes, then waits for an event) and an event monitor (live list of the latest accessibility events with their intervals; pause, clear, window state changes only and copy); the settings section holds the root and secure settings auto-enable switches; the full diagnostics text can be copied at once; the status and control headers carry buttons to refresh and to step the verbosity down or up (status: 4 levels, basic fields by default, the title shows the service status such as "State [Operational]" when all rows are hidden; control: 3 levels, the start / stop / restart rows by default, those three buttons move into the header when all rows are hidden) and the chosen levels are remembered
* `Feature` Accessibility manager permission rows open cached root management app candidates with manual additions, ADB secure settings authorization and Shizuku authorization or settings; stable mode is available in settings and as a status row with enable / disable guidance
* `Feature` Added explicit versioned `snapshot`/`capabilities` APIs and v1/v2 schema constants to `mediainfo`; Rhino keeps legacy `read` results, Node.js keeps the host v1 default, and plugin v2 plus short engine metadata are exposed only after explicit plugin capability advertisement
* `Feature` Clicking an archive file extracts it into private temporary storage for execution or opening while preserving runtime filename suffixes; APK/APKS/XAPK/APKM/APKZ/AAB support archive viewing and extraction from file menus and the information dialog menu without replacing existing row buttons
* `Feature` Explorer Action host file information capability v1 lets trusted read-only plugins append a bounded, SHA-256-bound localized summary to the existing APK information dialog; missing, disabled, incompatible, or failing plugins leave its original fields, installation and manifest actions, and failure handling unchanged
* `Feature` `pangu` global object with bundled pangu.js 10.1.0, providing `spaceText` for mixed CJK text spacing and `hasProperSpacing` checks, with TypeScript declarations and documentation
* `Feature` `pinyin` adds per-call `customDictionary` reading overrides, completes `compare` and `compact`, and exposes the same external-plugin provider to Node.js through `autojs6:bridge.callAutoJs` with an explicit `pinyin` capability
* `Feature` `pinyin4j` adds a capability-negotiated `readings` matrix for all readings in Hanyu, Tongyong, Wade-Giles, MPS2, Yale, and Gwoyeu Romatzyh; old plugins safely fall back only for Hanyu, while the same external-plugin provider is exposed to Node.js through `autojs6:bridge.callAutoJs` with an explicit `pinyin4j` capability
* `Feature` OpenCC plugin contract version 2 adds dynamic conversion-type discovery, batches of up to 1024 texts, and chains of up to 32 stages; the 16 composed methods drop from as many as 3 Binder round trips to 1 with a new plugin, while older plugins transparently fall back to the original per-item or per-stage calls
* `Feature` Image Quantization options add `maxPixels` and `maxMemoryBytes` with default 16000000-pixel and 256 MiB additional-working-memory budgets through `OPTIONS_API_VERSION>=4`; over-budget work fails with typed resource details, results report accounted peak memory, and stopping a script cancels its active native quantization
* `Feature` `images.quantizeToFile` streams an indexed PNG directly to disk with size and quality metrics; Image Quantization options now support `preserveAlpha`, common Bitmap configurations, hardware input, and wide-gamut input normalized to sRGB through `OPTIONS_API_VERSION>=3`
* `Feature` `images.quantize` now returns PNG bytes, size, achieved quality, and quantization error; omitting the minimum quality uses best effort (`minQuality=0`), while an explicit unmet lower bound throws `PngQuantBridge.QualityTooLowException` with code `PNG_QUANTIZATION_QUALITY_TOO_LOW`; the result API requires `OPTIONS_API_VERSION>=2`
* `Feature` `images.save`, `images.saveImage`, `images.compress`, and `images.compressToBytes` now accept named PNG quantization options for palette size, speed, independent quality bounds, dithering, and posterize; numeric `quality` remains backward compatible, while options require an Image Quantization plugin declaring `OPTIONS_API_VERSION>=1`
* `Feature` In-memory TypeScript execution through `engines.execScript`: `.ts/.tsx/.mts/.cts` source names use the independent compiler with file-equivalent diagnostics and Source Map stack mapping, Rhino returns the final expression value, and the full Log page plus editor Log panel evaluate one-line snippets interactively
* `Feature` Compiler-backed Node TypeScript projects can now compile runtime-created `.ts/.mts/.cts` files on demand: Node transfers bounded source through a byte- and SHA-256-bound PFD, the host reuses the resident project session, type diagnostics and Source Map stack mapping are preserved, and path escape, symlinks, and ambiguous targets remain denied
* `Feature` Ace now incrementally refreshes its resident TypeScript project language service after a save and cancels stale work with a 450 ms idle debounce plus a 50 ms post-save diagnostic scheduling delay; on the acceptance device, TS2322 refreshed 201 ms after saving a 20-file project without blocking input
* `Feature` Editor contract 5 adds project-wide TypeScript symbol rename: Ace submits bounded per-file edits with exact source identities, while the Host independently revalidates a clean project snapshot, shows a file/edit preview, freezes the active buffer, and atomically stages, publishes, or rolls back every file before refreshing diagnostics
* `Feature` Editor contract 4 adds host-authorized TypeScript auto-import and spelling corrections for the active buffer: requests can contain only bounded, ordered UTF-16 edits and exact base/result SHA-256 values, while the host independently replays them and rejects read-only, stale, malformed, overlapping, or tampered actions before one-shot approval
* `Feature` Editor contract 3 adds project-snapshot-authorized TypeScript definition navigation: Ace can request locations in project sources and frozen dependency declarations, while the host independently revalidates the project root, canonical relative path, source or dependency inventory fingerprint, content SHA-256, and range before opening and positioning the target; symlinks, stale snapshots, and mismatched unsaved content are rejected
* `Feature` Editor contract 2 now supplies Ace with the same bounded TypeScript project-source snapshot used by pre-run compilation, enabling live cross-file diagnostics for invalid import paths while retaining compatibility with contract-1 editor plugins
* `Feature` TypeScript dependency snapshots now reject native addon signals (`.node`, `binding.gyp`, and `gypfile`) and install lifecycle hooks (`preinstall`, `install`, and `postinstall`) with stable error codes and pure JavaScript/WASM guidance; ordinary package scripts remain inert metadata
* `Feature` Aligned the host TypeScript dependency contract with resolver policy revision 3 and added real release-manifest coverage for dayjs, lodash, zod, uuid, and nanoid, locking Rhino CommonJS entry selection and stable ESM-only errors
* `Feature` TypeScript project compilation and Ace now share one frozen `node_modules` type layer: package `types`/`typings`, TypeScript 6 `typesVersions`, nested declarations, and installed `@types` produce matching dayjs completion, hover, and strict TS2322 diagnostics in Rhino and Node profiles
* `Feature` Lua Runtime Host capabilities now provide file-script-isolated persistent key/value storage and Toast: `autojs.storage.get/put/remove/clear` uses an app-private, synchronously committed, bounded namespace, while `autojs.ui.toast` dispatches once through the Host main thread
* `Feature` Ace LSP defaults now include .mts/.cts and their declaration-file variants, allowing the external Ace editor to select the same Rhino/Node TypeScript execution profile; legacy default selections migrate while explicit custom suffix sets remain unchanged
* `Feature` APK Builder now compiles Rhino TypeScript projects at build time; packaged APKs contain only encrypted JavaScript and optional Source Maps without embedded source, never TypeScript sources or the compiler; encrypted projects use one-time AES-256-GCM staging, with cleartext held only in memory and zeroed after re-encryption
* `Feature` Node.js 24.5.0 script runtime, supporting scripts launched with the "nodejs" directive or extensions such as .node.js/.cjs/.mjs
* `Feature` Node.js Java interop uses a declarative host allowlist for Math, UUID, Rect, Point, Color, Locale, Build, Build.VERSION and TimeUnit, exposing `java.policy` and static field access to the plugin
* `Feature` Node.js host support for media playback sessions and media_store library access, including pause, resume, seek, status and cleanup, plus MediaStore query, import, rename, export, scan and delete; media.playback, media.library and media.library.mutate remain separate capabilities
* `Feature` Node.js ESM, dynamic import(), network and WebSocket, native network modules, worker_threads, child_process, and Java interop are stable features enabled by default, while permission declarations, allowlists, sandboxing, and resource budgets remain enforced
* `Feature` Node.js runtime supports CommonJS/partial ESM/local-only dynamic import()/lightweight TypeScript (.ts/.mts/.cts) source code
* `Feature` Node.js runtime supports a per-execution on-demand encrypted-module source provider that probes and decrypts only candidates actually selected by the module resolver
* `Feature` Node.js runtime supports controlled AutoJs6 bridge modules and adopts the Safe Node Profile security policy
* `Feature` Node.js runtime supports a persistent service process and process-scoped Node/V8 platform reuse, while creating a fresh isolate and Environment for each script execution
* `Feature` Persistent Node.js runtime supports idle or memory-pressure retirement, explicit shutdown, and isolation and rebuilding of unhealthy processes
* `Feature` External Node.js runtime plugin backend with required `plugin` and fallback-capable `pluginOptional` modes, persistent Binder sessions and process runtimes, trust/contract/ABI validation, and single-active zero-queue backpressure
* `Feature` External Node.js runtime plugin backend supports request-scoped workspace archive transport v2 with explicit input mapping, output writeback, and deletion tombstone manifests without scanning the host sandbox
* `Feature` Node.js Runtime debug builds support explicitly enabling a localhost-only Inspector/CDP session per execution, while release builds, remote listening, and blocking waits remain forbidden
* `Feature` App packaging supports Node.js script projects and automatically includes the required runtime libraries
* `Feature` Independent TypeScript compiler plugin client with frozen AIDL/R2 archive contract validation, UTF-8 single-file Rhino compilation, bounded diagnostics, and mandatory Source Map output
* `Feature` Local single-file .ts and Rhino-targeted .tsx scripts now pass through the independent compiler plugin before Rhino execution, preserve their UTF-8 source identity and Source Map output, reject type errors before execution, and never fall back to the Node.js plugin or raw TypeScript; unsupported declaration files and an unavailable compiler return explicit localized errors
* `Feature` Local single-file .ts and .tsx scripts using the "ui" directive support, after compilation, Rhino Activity execution with string XML and native TSX layouts, controlled intrinsic tags, Fragment, scalar attributes, view lookup, click callbacks, text updates, and explicit UI closure
* `Feature` TypeScript compiler diagnostics are exposed as host ScriptCompilationException records with the source file, one-based line and column, severity, and TS error code; Console and the editor bottom log panel render clean clickable locations that jump to the corresponding source position
* `Feature` Rhino TypeScript runtime exceptions consume the compiler Source Map v3, remap generated JavaScript stack frames to the original .ts file and line without inventing unavailable columns, and keep mapped positions clickable in Console and the editor log panel
* `Feature` Local single-file .mts/.cts scripts are compiled to .mjs/.cjs by the independent TypeScript compiler plugin's Node profile before execution by the Node.js Runtime plugin, preserving UTF-8 file names and bypassing legacy TypeScript regex stripping; single-file Node .ts is explicitly rejected until project package-metadata support is available
* `Feature` Node TypeScript runtime failures consume compiler Source Map v3 output to map V8 stack frames back to exact line and column locations in the original .mts/.cts file, with clickable mapped locations in Console and the editor log panel
* `Feature` TypeScript projects now compile a bounded project snapshot as one program for Rhino or Node, preserving relative imports, circular CommonJS dependencies, JSON runtime files, package `type` context, imported-module diagnostics, and per-module Source Map stack mapping; package metadata remains strict and lifecycle scripts are rejected
* `Feature` TypeScript compilation results are cached across executions by a semantic key over the source graph, compiler and declaration fingerprints, profile and policy revisions, and package metadata; unchanged scripts skip compiler prewarming and Worker compilation, while authenticated output archives are revalidated before reuse and corrupt or over-quota entries fall back to normal compilation
* `Feature` TypeScript projects carry one exact read-only `node_modules` snapshot from compilation into execution; Rhino loads CommonJS/JSON and dual-package `require` branches with stable ESM-only errors, while Node receives an immutable private workspace overlay and executes ESM/CJS/dual packages without writing dependency changes back
* `Feature` Timed tasks, broadcast tasks, shortcuts, and Tasker/local Run Intents now share the same TypeScript preparation pipeline as foreground launches; Tasker pre-execution JavaScript remains ordered before compiled Rhino/Node output, with Source Map line offsets accounting for the prefix instead of bypassing compilation and executing raw TypeScript
* `Feature` Project folders can create timed and broadcast tasks directly; when triggered, tasks re-resolve the Rhino/Node.js/Python entry from project.json and restore the project working directory and useFeatures configuration
* `Feature` Independent Python Runtime plugin backend for running local .py files and script projects declared with `type: python` in a separate CPython plugin process, with fixed-component/signer/protocol/ABI validation, hot plugging, and no fallback to JavaScript
* `Feature` Python launch integration covers the file manager/editor/local Run Intent/single-file Import Intent/new shortcuts/timed tasks/broadcast tasks and preserves strict single-file or project context; projects support internal imports, relative file access, and read-only `autojs6.app/device/execution/project` APIs
* `Feature` Python Runtime execution supports optional bounded pre-supplied stdin snapshots via the ExecutionConfig key `autojs6.python.stdinSnapshot`, with strict ByteArray/UTF-8 text admission, provider capability/size negotiation, private read-only PFD transport, and cleanup
* `Feature` Python Runtime protocol 1.4 negotiates explicit strict JSON results and optional output artifacts per execution, with count, normalized-path, per-file/aggregate-size, exact-PFD-reference, and SHA-256 limits; the Host consumes only typed results and never infers one from stdout
* `Feature` Python Runtime protocol 1.5 adds an execution-scoped synchronous Host capability broker with pinned-plugin UID verification, strict bounded JSON, call quotas, replay/lifetime guards, and no post-terminal use; the complete first low-risk slice exposes toast, clipboard get/set, app launch/open-URL operations, live battery/screen/brightness/volume information, level-aware Host console output, and permission-aware notifications to Python scripts
* `Feature` Python Runtime protocol 1.5 Host broker exposes bounded UTF-8 text read/write, existence/type checks, and sorted direct listings within the current project root or standalone script directory; normalized relative paths and stable limits prevent execution-root escape
* `Feature` Python Runtime protocol 1.5 Host broker exposes bounded alert/confirm/prompt/select dialogs only to user-authorized foreground launches; background launches fail with INTERACTIVE_NOT_ALLOWED, and cancellation or terminal cleanup dismisses active dialog UI
* `Feature` Python Runtime protocol 1.5 Host broker exposes path-safe current-engine metadata, deterministic self-stop, and up to 16 scoped asynchronous child launches for non-Python Host scripts; nested Python sessions fail with NESTED_PYTHON_NOT_ALLOWED while the provider remains single-session
* `Feature` Python Runtime protocol 1.5 Host broker exposes bounded accessibility click, long-click, timed press, swipe, Back, and Home actions; coordinates and durations are strictly validated, and an unavailable accessibility service fails deterministically with ACCESSIBILITY_UNAVAILABLE
* `Feature` Python Runtime protocol 1.5 Host broker exposes bounded execution-local accessibility tree snapshots and AND-composed selector queries with opaque node references for explicit click and text-setting actions; snapshots, scans, strings, retained nodes, and responses have fixed limits, while unavailable accessibility, incomplete scans, and stale nodes fail with stable typed errors
* `Feature` Python Runtime protocol 1.5 Host broker exposes Android 11+ accessibility screenshots as execution-local PNG or JPEG bytes through one retained image, ordered 32 KiB chunks, a 4 MiB encoded limit, SHA-256 metadata, explicit release, and terminal byte clearing; it opens no permission UI, while unavailable accessibility, failed capture, stale image handles, and limit violations fail with stable typed errors
* `Feature` Python Runtime protocol 1.5 Host broker searches a fresh Android 11+ accessibility screenshot for a bounded RGB color with an optional region and per-channel threshold, returning the first row-major coordinate or a deterministic miss without transferring or retaining image bytes
* `Feature` Python Runtime protocol 1.5 Host broker accepts one execution-local PNG/JPEG template through ordered 24 KiB chunks with a 1 MiB encoded limit and SHA-256 verification, decodes at most 2048 pixels per side and 1 million pixels, and searches a fresh Android 11+ accessibility screenshot in deterministic row-major order using opaque template pixels and an RGB threshold; lifecycle, region, and comparison budgets fail closed without an OpenCV runtime dependency, while Android's 333 ms screenshot request throttle is retried only for the exact interval-too-short error after 350 ms within the existing deadline
* `Feature` Python Runtime explicit strict JSON results are written to the Host console with a `[result]` prefix after script termination, while verified output artifacts are atomically published to an execution-specific directory below `.python-artifacts` in the project or script directory and their absolute paths are printed; this reserved directory is excluded from later project snapshots
* `Feature` Independent Lua Runtime plugin backend for running local .lua files in a separate PUC Lua 5.4.8 plugin process, with fixed-component/signer/protocol/ABI validation, bounded execution, and no fallback to JavaScript
* `Feature` Independent Java and Kotlin JVM Source Runtime plugin backends compile and run single-file .java/.kt scripts in isolated processes, with package/import support, live console output, cancellable sleep, host toast, and explicit capability negotiation
* `Feature` JVM Source Runtime support scope is single-file Java 8 (Java provider min API 24) and Kotlin 2.3 (Kotlin provider min API 26); this release was device-smoked only on API 31/arm64-v8a and makes no claim for untested API levels
* `Feature` JVM Source Runtime explicit execution supports bounded JSON arguments, independently authorized clipboard access, multi-file Java source packages and named entry classes, safe runtime exception locations and execution observations through Protocol 1.6 / Entry API 4; existing Protocol 1.1 / Entry API 2 plugins remain compatible for single-file execution
* `Feature` `ocr` defaults to live automatic engine selection in ML Kit/Paddle/Rapid order; `ocr.mode` reports the selected engine or an empty string, explicit modes stay fixed, and `auto`, an empty string or nullish values restore automatic selection through `mode` or `tap`
* `Feature` Plugin system supports extension interfaces for Node.js/ML Kit OCR/ML Kit Barcode/Rapid OCR/OpenCV/Image Quantization/text conversion/media information and more
* `Feature` Plugin system provides versioned TaggedWire transport and developer protocol packs for DEX compilation and AI Provider text generation, with dedicated conformance test apps and hostile fixtures
* `Feature` Official AutoJs6 plugins can read a versioned, read-only host appearance snapshot for theme colors, dark-mode policy, and language through the signature-protected settings contract
* `Feature` Plugin system supports an extension interface for offline project documentation
* `Feature` File manager supports single-file read-only Explorer Action plugin extensions and dynamically loads their actions in the overflow menu of the main file page
* `Feature` File manager supports Explorer Action protocol v2 primary actions to provide image viewing, audio playback, and video playback through three independent plugins
* `Feature` File manager supports Explorer Action protocol v3 output transactions with bounded write-only content URIs, output validation, crash recovery, and atomic publication of sibling files
* `Feature` File manager Explorer Action protocol v4 supports ordered file/directory/mixed targets and same-parent multi-selection actions, exposes plugin compression in directory menus and a five-cell vertical selection bar, and provides request-scoped host file sessions for controlled reads and atomic sibling output
* `Feature` File manager Explorer Action protocol v5 lets trusted single-file read-only plugins present paginated archive directories in the native Explorer list, path bar, theme, and Back stack through UID-pinned Binder sessions
* `Feature` File manager Explorer Action protocol v6 previews readable document, image, audio, and video entries through the existing host viewers
* `Feature` File manager Explorer Action protocols v7 through v10 add mandatory read-back verification of pending file output, atomic target replacement, host-owned directory-output transactions with client-death and restart recovery, and native current-folder or selected-entry extraction with bounded progress and cancellation, enabling archive plugins to edit a selected ZIP and safely publish verified output without storage permission
* `Feature` File manager Explorer Action protocol v11 adds bounded host-native password recovery for initial archive opening and extraction; retries preserve the archive path and selected entries while transient password buffers are cleared after each request
* `Feature` File manager Explorer Action protocol v12 adds a bounded sibling-volume source tied to each archive session; archive plugins can open host-approved ZIP and RAR volumes through opaque IDs and read-only descriptors without receiving a directory or filesystem path
* `Feature` File manager Explorer Action protocol v13 lets supported archive plugins rebuild filename decoding inside the same read-only session; the native path bar can select a ZIP encoding while preserving the deepest available internal path and existing selected entries
* `Feature` File manager Explorer Action protocol v14 adds bounded compound filename-suffix matching; archive plugins can register exact `.zip.001` and `.7z.001` first volumes and reuse the session-bound v12 sibling-volume source without matching arbitrary `.001` files or receiving a directory path
* `Feature` File manager Explorer Action protocol v15 adds recoverable multi-file output batches; plugins can publish only after every pending file is verified, while the host durably records parent and file identity and rolls back only matching members after failure or restart
* `Feature` File manager Explorer Action protocol v16 adds a host-owned source-to-Trash handoff gated by the exact original selection and every committed output; the host persists a recovery copy before source removal without granting plugins arbitrary-path or delete authority
* `Feature` File manager Explorer Action protocol v17 adds an explicit read-only Open as archive... fallback only when the normal primary action does not match; one existing archive session performs structural detection and can label the actual format in the native path bar without background menu scans or new file authority
* `Feature` File manager Explorer Action protocol v18 keeps the previous target version in host-private durable storage before a managed replacement, exposes only opaque bounded history metadata, and permits one restore only while the current file still matches the exact committed replacement; external changes are never overwritten and interrupted recovery evidence is retained
* `Feature` File manager Explorer Action protocol v19 lets writable archive sessions expose native Rename and Delete for supported entries; rewrites use host-owned verified atomic replacement, keep the previous archive version recoverable, and rebuild the same session while preserving surviving paths and selections
* `Feature` File manager Explorer Action protocol v20 lets writable native archive pages create empty folders and add explicit mixed selections of files and complete folder trees through a frozen, path-free input grant; empty folders and same-named directory roots are preserved with safe numbering, while source changes or any failed item roll back the whole archive replacement
* `Feature` File manager Explorer Action protocol v21 provides host-owned durable source-to-Trash batches with opaque history, per-item outcomes, and cross-session best-effort restore; existing names are never overwritten, source restore never deletes created archives, and interrupted recovery evidence remains host-private
* `Feature` File manager Explorer Action protocol v22 lets a single-file read-only action declare bounded related-file suffixes; the host freezes only exact `<selected name><suffix>` candidates and opens them through read-only descriptors without exposing parent listings, paths, or arbitrary siblings
* `Feature` File manager Explorer Action protocol v23 lets a single-directory read-only overflow action declare filesystemPath; the host then passes only the canonical path of the selected directory, creates no host file session for it and grants no additional access
* `Feature` Plugin Center provides a global settings page and a user guide page
* `Feature` Plugin metadata `instruction` supports localized `@raw/...` instruction resources, and the shared plugin resource resolver can now read local or package-qualified `@string/...` and `@raw/...` references
* `Feature` Plugin Center supports automatic SDK plugin mounting
* `Feature` Plugin Center supports multi-variant priority sorting
* `Feature` Plugin Center supports online plugin installation and updates
* `Feature` Plugin Center supports opening plugin launch pages and hiding plugins
* `Feature` Plugin Center supports available ABI intersection detection and prompts
* `Feature` Plugin Center resolves official APK Builder Template releases through the host compatibility matrix, selects an exact device-ABI artifact with universal fallback, retains the existing release-index/tag fallback, and guides uninstall/reinstall when the matched version is lower than the installed plugin
* `Feature` APK Builder Runtime Kits can declare an explicitly authorized, verified closed host patch interval; exact built-for hosts proceed silently, non-exact in-range hosts receive a localized warning, and out-of-range or malformed declarations are blocked before template transfer
* `Feature` Developer options add a default-off R1 switch for experimental remote APK building; disabled state blocks provider discovery and session opening, failures stop the current build without automatic local fallback, existing output remains protected by atomic publication, and process-local diagnostics retain only bounded enum/number fields
* `Feature` Plugin Center supports APK Builder Template plugins, allowing on-demand installation and updates of templates required for app packaging
* `Feature` Long-pressing the floating button on the Home Plugins tab opens the Plugin Center
* `Feature` Setup wizards for running the permission wizard and the plugin install wizard one after another, offered by a welcome prompt on the Home page after the first launch (entry: Settings > Config > Setup wizards)
* `Feature` Permission wizard for ticking the permissions of the Home drawer and requesting them one after another, with the built-in "Standard" / "Minimal" / "All" profiles and custom profiles; granted permissions can be hidden from the menu (entries: Setup wizards / the button of the "Permissions" group title in the Home drawer)
* `Feature` Plugin install wizard for ticking official plugins by category and installing them in one batch, with the built-in "Standard" / "Minimal" / "All" profiles and custom profiles that can be saved, renamed and deleted; installed plugins can be hidden from the menu (entries: Setup wizards / the floating button menu of the Home Plugins tab / the "+" menu of the Plugin Center)
* `Feature` Plugin install wizard installs while downloading, installs silently through the 3-Setup Installer plugin when Shizuku / Root / Dhizuku authorization is usable, and checks the install unknown apps permission before downloading when the system installer is used
* `Feature` runtime.loadAar method, for dynamically loading AAR files
* `Feature` Explicit runtime.loadJarWithR8 entry with default-off exact-component selection, same-signature identity pinning, bounded five-artifact R8 transport, isolated cache adoption, and fail-closed behavior without D8/dx fallback
* `Feature` ui.useAndroidLayout supports native Android layout widgets, include/merge, script project res references, and Drawable XML such as shape/selector/layer-list
* `Feature` JavaScript (Rhino) modules support compatible power/settings/work_manager interfaces and an AppInfo wrapper, and complete selected Base64/Crypto/WebSocket/ByteString/automator/images/timers APIs from Auto.js Pro 9.3.11
* `Feature` Global JavaScript (Rhino) ai routes every local and online request through an AI Provider plugin target; Developer options opens the trusted 3-Stone AI settings entry or exact install/enable/update guidance, while the host no longer stores AI profiles or credentials and no longer includes provider HTTP transport
* `Feature` Global JavaScript (Rhino) `ai.catalog` exposes one local/online plugin target catalog; omitting `plugin` and `target` selects the official plugin's declared default, `target` selects one exact target, and `plugin` selects an exact third-party component; responses include reasoning, complete usage and duration, finish reason, profile and resolved-target metadata, and stable failures never change routes silently
* `Feature` Global JavaScript (Rhino) `ai.session` API creates a persistent AI provider plugin Conversation, pins the target/provider/model/generation controls for the session, and sends only each new user prompt on later `ask`/`chat`/`stream` turns; normal completion retains context, while cancellation, timeout, protocol failure or explicit close releases the complete session
* `Feature` AI-provider routes for `ai.ask`/`ai.chat`/`ai.stream` and persistent `ai.session` support `structuredJson` and JSON-object `responseSchema`; a schema enables native LiteRT-LM JSON Schema constrained decoding, persistent sessions fix the same schema for every turn, and completed JSON text is validated strictly
* `Feature` AI Provider V2 local targets expose explicit `cpu`, `gpu`, and `npu` backend profiles as optional target controls; availability is device-specific, unavailable profiles fail without CPU fallback, sessions pin one backend, GPU requires loadable system OpenCL, and the official plugin reports NPU unavailable because its EAP runtime is not packaged
* `Feature` Global JavaScript (Rhino) yolo module provides a default-off exact-component YOLO provider route with versioned Binder/PFD transport, manifest-driven NCNN model loading, object detection, explicit cancellation and resource cleanup, and no silent provider fallback
* `Feature` Global JavaScript (Rhino) tts module provides system text-to-speech, intelligent long-text chunking, logical queues, voice and language discovery with offline voice filtering, observable tasks and audio events, silence insertion, and file synthesis with atomic publication; it also includes timeouts, backpressure, runtime cleanup, and protection against fallback from a requested engine
* `Feature` device.getMarketName method, for obtaining the device's "market name" _[`issue #513`](http://issues.autojs6.com/513)_
* `Feature` Timed tasks support monthly/yearly/countdown tasks and repeat termination conditions _[`issue #294`](http://issues.autojs6.com/294)_ _[`issue #537`](http://issues.autojs6.com/537)_
* `Feature` File manager supports multi-selection and batch copy/move/delete operations
* `Feature` File manager supports secure Markdown and HTML previews with GFM rendering, multiple themes, custom CSS, and controlled same-directory resource loading
* `Feature` Markdown and HTML previews support fullscreen mode, with a "Start in fullscreen mode" setting that is disabled by default
* `Feature` File manager supports leftmost icon actions for Markdown/HTML preview and image viewing/audio/video playback; the app can act as a system media viewer, with background audio playback and notification controls
* `Feature` File manager recognizes `.epub` e-books as a previewable document type and opens them with the Readium EPUB Reader plugin through the Explorer Action contract; books opened directly from archives get a dedicated 256 MiB limit
* `Feature` Pinned file manager path bar with current-path display, current-directory highlighting, direct navigation to parent directories, automatic end scrolling for long paths, horizontal browsing, parent/current colors matching category labels/file names, and alpha-weighted ink-centroid alignment across scripts and separator chevrons
* `Feature` File manager supports image information and detailed EXIF viewing
* `Feature` Image editing and format conversion are provided by the independent Image Tools plugin; when the plugin is unavailable, only the corresponding actions are hidden, while image viewing, image information, and EXIF viewing remain available
* `Feature` Image viewer automatically discovers all images in the same directory and supports synchronized focal-point pinch zoom, swipe and toolbar previous/next navigation, a compact three-row information bar for position/resolution/size/format/modification time/EXIF, fullscreen mode, a start-in-fullscreen setting, and a configurable background color with history, while the ActionBar, system bars, and icons adapt their colors and contrast to the background
* `Feature` File manager supports package details and manifest viewing for APK/APKS/XAPK/APKM/APKZ/AAB files
* `Feature` Package installer supports APK and compatible split APK sets from APKS/XAPK/APKM/APKZ containers, with device split selection and signing-certificate consistency checks
* `Feature` Code editor supports loading Ace from an external plugin, with signer and trust validation, Editor API contract/host-version/binary ABI compatibility checks, and automatic fallback to the native editor when unavailable
* `Feature` Code editor supports runtime hot plugging of the external Ace plugin and automatically switches engines as plugin installation and enabled states change
* `Feature` Ace editor now enables its bundled local TypeScript smart completion by default, with AutoJs6 API completion, signature help, hover information, and diagnostics; it can be disabled in settings
* `Feature` Ace editor settings support selectively loading the android/libraries/resources/main-app AutoJs6 TypeScript declaration groups; dependencies are loaded automatically, while all optional groups are disabled by default to reduce language-service startup time and memory use
* `Feature` Ace editor supports a shortcut panel/action history/action favorites/mobile text selection/pinch zoom and more
* `Feature` Code editor supports reloading or converting files with a selected encoding, optional BOM, encoding risk indicators, and undo/redo
* `Feature` Code editor information bar below the toolbar shows the file name, the cursor's line and column in real time, and the file encoding; the three items can be tapped to view file details, jump to a line, or change the encoding, respectively
* `Feature` Code editor system status bar, ActionBar, button icons and text, overflow and nested menus, in-editor dialogs, and native-editor cursor, handles, and selection controls dynamically follow the current editor theme; status-bar icons automatically maintain light/dark contrast for a visually unified surface from the top of the screen through the code area
* `Feature` Code editor supports an immersive fullscreen mode, with the preference persisted and available from both the menu and settings; swipe from an edge to reveal system bars temporarily, while Back exits fullscreen before leaving the editor
* `Feature` Code editor supports a bottom log panel (by [M17764017422](https://github.com/M17764017422)) _[`pr #502`](http://pr.autojs6.com/502)_
* `Feature` Code editor bottom log panel supports jumping from stack frames to source code (by [M17764017422](https://github.com/M17764017422)) _[`pr #502`](http://pr.autojs6.com/502)_
* `Feature` Code editor bottom log panel provides a "Run/Stop" button for quickly starting and stopping scripts
* `Feature` Code editor supports a dedicated settings page and an in-editor settings dialog, providing theme previews/separate light and dark mode configurations/fonts/gutter/word wrap and other display options
* `Feature` Settings page provides code editor options such as "Auto show bottom log panel after running" and "Code highlight threshold"
* `Feature` Packaged app configuration supports showing or hiding the "Log" and "Settings" launcher shortcuts _[`issue #550`](http://issues.autojs6.com/550)_
* `Feature` App packaging supports remote builds using APK Builder Template plugins
* `Feature` Developer options support a GitHub access token setting to increase request limits for update checks and plugin indexes
* `Feature` Global JavaScript (Rhino) R8 integration adds three verified artifact-export `runtime.loadJarWithR8` overloads and protocol 1.1 `runtime.retraceR8Stack`; mapping/seeds/usage/retrace metadata publish atomically, retrace binds mapping provenance, and missing or invalid providers fail without D8/dx or local fallback
* `Feature` Code editor settings now show an Ace language support matrix and per-language semantic switches for TypeScript/JavaScript, Python, Lua, and Java; Kotlin remains visible as P2+ without a provider, and file-type customization clearly acts as a pre-routing allowlist rather than language reclassification
* `Feature` Plugin update dialogs can display the corresponding plugin release history in the app
* `Feature` `auto.registerEvent(name, listener, options?)` gains optional de-duplication and debouncing: `{ distinct: 'window' | 'source' | true | (event) => key, within?: 500 }` drops repeated events with the same key within the window (such as the several `WINDOW_STATE_CHANGED` events of one window transition), `{ debounce: ms }` delivers only the last event of a burst once it has been quiet for that long; both off by default
* `Feature` Shizuku joins root and the secure settings permission as an unattended way of enabling the accessibility service (used when neither of the other two is available, writing the system setting through the Shizuku user service): a new "Enable accessibility service with Shizuku automatically" switch (on by default) in the settings page and in the settings section of the accessibility service manager, plus a "Shizuku access" row in the manager's status section
* `Feature` New `auto.setFlags()` flag `appWindowsFallback`: while the system does not provide the active window root yet (right after the accessibility service has been bound; on Android 8.0 possibly until the next window change), selectors and `auto.root` / `auto.rootInActiveWindow` / `auto.windowRoots` fall back to the roots of the application windows of the window list (focused window first); off by default
* `Feature` New `UiObject` methods `contentInvalid()` / `contextClickable()` / `multiLine()` / `dismissable()` (in the style of `clickable()` and the other boolean property methods, mirroring the existing compat accessors `isContentInvalid` etc.)
* `Feature` New methods on widget collections (the arrays returned by `find()` / `untilFind()` / `children()` / `siblings()` and the like): `at(i)` (negative counts from the end, null out of range), `first()` / `last()` (null when empty), `nonNull()` (drops the null entries), `slice(start?, end?)` (index rules of `Array.prototype.slice`, the result still carries the collection methods), and the per-node variants `performActionEach(action, ...args)` / `clickEach()` / `longClickEach()` / `scrollForwardEach()` / `scrollBackwardEach()` / `setTextEach(text)` (a boolean array, every node attempted; the boolean AND semantics of the existing `performAction` / `click()` etc. are unchanged); `filter` / `map` / `forEach` / `some` / `every` / `indexOf` / `for...of` are the array's own operations and keep working as before
* `Feature` Accessibility coordinate-click helpers: smartClickBounds, clickBoundsIfExists, clickBoundsAny, Flow wait and delayed clickBounds steps, and their aliases
* `Feature` Flow instance steps clickIfExists / clickAny / findAny, optional whenPresent branches and bounded asynchronous repeatUntil loops
* `Feature` New asynchronous, chainable wait API: `waitAsync(cond, timeout?, interval?)` (aliases `wait.async` / `auto.wait` / `flow.wait`) returns a `Flow` object instead of blocking the script; the condition may be a selector (string / regex / `UiSelector` / object / array), a function, a `UiObject`, a `Flow` or a `Promise`; a chain may continue with node actions (`click` / `longClick` / `setText` / `scrollForward` etc.), global actions (`back` / `home` / `swipe` etc.), `sleep` / `timeout` / `retry` / `scope` and the callbacks `then` / `catch` (`else`) / `finally` / `map` / `filter` / `peek` / `run`, yields its result through `sync()` / `await()` / `toPromise()` and is cancelled with `cancel()`; global entry points `waitThenClick` (`clickWait`), `waitForStable`, `waitForStableThenClick` (`clickWhenStable`), `clickWhenStableAfter`, `waitForVisible`, `waitForHidden` (`waitForGone`), plus in the `flow` (`$flow`) namespace `waitUntil` / `waitWhile` / `waitForActivity` / `waitForPackage` / `waitThenLongClick` / `sleep` (`delay`) / `run` / `of` / `all` / `race` / `any` / `cancelAll` / `Flow` / `pending`; blocking steps run on a per-script worker pool (`flow-<id>-<n>` threads, one serial lane per chain, so independent chains run concurrently) and callbacks are posted back to the script thread that created the chain (usable in UI mode, though `sync()` is forbidden on the UI thread), and every pending chain is cancelled and the pool stopped when the script exits; failures reject with a `FlowError` (an `Error` carrying `code` / `step` / `selector` / `elapsed` / `attempts` / `reason`) whose code is `TIMEOUT` / `ACTION_FAILED` / `CANCELLED` / `A11Y_UNAVAILABLE` / `INVALID_TARGET`; `flow.defaults({ timeout, interval, stableFor, strictActions, trace })` sets the per-script defaults (a read without arguments) and `flow.trace(true)` logs every step's start / end / elapsed time / outcome to the console; the convenience steps `clickAfter(min, max?)` / `longClickAfter` / `clickWhenStableAfter` and `sleep(min, max?)` accept a `"±delta"` string like the global `sleep`; the synchronous `wait()` is unchanged
* `Feature` High-level automation tools on both the `automator` module and the global scope: `smartClick(target, options?)` (climbs to a clickable ancestor when the target is not clickable, falls back to a tap gesture when nothing is, optionally verifies a condition after the click), `clickIfExists` / `clickAny` / `findAny`, `scrollUntil(target, options?)` (scrolls a container until the target shows, by accessibility actions or gestures, treating repeated unchanged content as the end), `typeInto(target, text, options?)` (a selector / an index / the focused field; replace or append; re-read verification and submit), `dismissPopups(targets, options?)` (closes one / every popup, or with `watch: true` returns a background guard handle driven by window change events), `collectList(container, item, options?)` (scrolls and collects items, deduplicated by content), `launchAndWait` / `backUntil` / `backToApp`, `toggle(target, checked)` (clicks only when the state differs, then verifies) and `retry(fn, options?)`; Flow gains the matching steps `.smartClick()` / `.scrollUntil()` / `.typeInto()` / `.dismissPopups()` / `.collectList()` / `.launchAndWait()` / `.backUntil()` / `.backToApp()` / `.toggle()` and the `flow.*` roots; every failure is a `FlowError` (`code` / `step` / `selector` / `reason`); `flow.defaults({ humanize: { offset, delay } })` gives the Flow action steps and the tools a human-like random tap offset and a pause before every action
* `Feature` Accessibility diagnostics: `auto.explain(selector, root?)` tallies a selector filter by filter (matches of each filter alone and what is left once the filters before it apply, the filter that leaves nothing, the near-miss nodes that pass every step before it), the result's `toString()` being the text report; `auto.dump(options?)` renders the window roots (or `root`) as a readable snapshot (indented text / JSON / uiautomator-style XML, with `maxDepth`, `properties`, `visibleOnly` and `maxNodes`); `UiObject.toJSON()` (so `JSON.stringify(node)` works) and `UiObject.dumpSubtree(options?)`; `auto.stats` holds the script's search / polled query / slowest selector / Flow step timings (`reset()` clears them, `toString()` is the text report); the layout inspector's code generator gains a "Code style" option that emits Flow-style snippets (such as `waitThenClick(selector).sync()`)
* `Feature` Event-driven waits: `waitForIdle(quietFor? | { quietFor?, timeout?, eventTypes? })` returns `{ quietFor, elapsed, events }` once no window state / content / windows-changed event has arrived for `quietFor` ms, replacing "sleep a while, then find"; `waitForEvent(type?, filter?, timeout?)` waits for the next accessibility event (the type a name, an array of names or `"*"`; the filter a function or `{ packageName, className, text }` with strings / regexes); `waitForToast(text?, timeout?)` and `waitForNotification(filter?, timeout?)` wait for another app's toast or a notification (substring / regex / function / object filters); all four exist as Flow roots (`flow.waitForIdle(...)` and so on) and chain steps (a chained `waitForIdle` passes the value through), and a timeout throws / rejects with a `FlowError` (`code` `TIMEOUT`); `auto.setFlags(["eventAssistedPolling"])` makes polling selector queries (`findOne(timeout)` / `untilFind()` and the like) and Flow waits retry as soon as an accessibility event arrives, the fallback interval of selector queries rising to 200 ms while nothing changes
* `Feature` Selector and node capabilities: the string selector syntax `select("text=Login clickable")` (whitespace-separated `key=value` / `key~=regex` or `key=/regex/flags` / `key` / `!key`, quotes around a value with spaces, an unquoted value with commas split into several arguments when needed as in `bounds=0,0,100,200`, the keys those of the selector registry; `UiSelector.select(String)` appends the filters and returns itself, so it chains with the builders; a malformed string throws with the character position, the offending term and candidate keys; `select()` without an argument stays the node action); `UiObject.isStale()` / `waitUntilGone(timeout?, interval?)` / `waitForStable(stableFor?, timeout?, interval?)` for node-level state checks and waits (default timeout 10 s, interval 50 ms; woken early by accessibility events under the `eventAssistedPolling` flag; refused on the UI thread); `findIterator()` for lazy finds (searched in `find()` order as the iteration advances, the rest of the tree left unwalked after a `break` in `for...of` or `close()`, `collect()` for the remaining nodes, `visited` / `produced` counters); window filter objects `{ type, packageName, title, displayId, active, focused, id, layer }` for `auto.setWindowFilter(...)` and the new `auto.findWindows(options?)` / `auto.findWindowRoots(options?)` (type names such as `application` / `inputMethod` / `system` / `accessibilityOverlay`, package name and title as a string or a regex, windows before API 30 counted on the default display)
* `Feature` Robustness (roadmap P5.5): a pending `waitForEvent` / `waitForIdle` / `waitForToast` / `waitForNotification` (synchronous or Flow form) ends at once with `A11Y_UNAVAILABLE` instead of timing out when the accessibility service is unbound meanwhile; Flow waits gain the `recoverService` option (also a default through `flow.defaults({ recoverService: true })`): a probe hitting a service outage then waits quietly for the service, one second at a time within the remaining timeout (an enabled but unbound service is re-enabled through the start strategies, the settings page is never opened and the user is asked at most once), the wait carries on with its target when the service returns in time and is otherwise rejected with an `A11Y_UNAVAILABLE` carrying `elapsed` / `attempts`; when the platform skips `onServiceConnected`, the registry adopts the instance from its first event and records the `adoptedByEvent` flag (`auto.state.adoptedByEvent`, a note on the in-process instance line of the service manager diagnostics), and the service info preferences (stable mode, gesture observing) are applied from that first event instead
* `Feature` Accessibility samples (roadmap P6.4): six scripts added to the in-app sample directory "无障碍服务" (Flow asynchronous waits and chained actions, toolkit form filling and popup dismissal, toolkit list scrolling and collection, string selectors and lazy iteration, selector diagnostics and node tree dumps, event-driven waits), each targeting the script's own floating window so that no other app is needed
* `Feature` The three title bar buttons of the floating console (minimize, layout state, close) are merged into a single "More" button whose menu offers Minimize, Resize, Input bar, Clear, Copy, Settings and Close; "Resize" enters a resize mode (the content dims and shows the current size, drag the right or bottom edge or the bottom-right corner to resize, tap elsewhere to leave); the "Settings" dialog toggles the time prefix, time format (presets or a custom pattern), log coloring, text size, status bar avoidance, and the title bar and content opacity
* `Feature` Console gains `setTimeVisible` / `setTimeFormat` / `setColorful` / `setAvoidStatusBar` / `setInputVisible` plus the matching `console.build` options (`timeVisible` / `timeFormat` / `colorful` / `avoidStatusBar` / `inputVisible`), mirroring the options of the floating window settings dialog; the more menus of the log page and the editor log panel add "Settings" (time prefix, time format, log coloring, text size) and an "Input bar" toggle, each remembered separately; new samples "Console/Time and coloring settings" and "Console/Order-independent settings"
* `Feature` Restores `console.rawInput(prompt?, ...args)` and `console.input(prompt?, ...args)` (abandoned since 6.3.1): they block the script thread until the user submits a line in the console input bar, and `input` evaluates the line as a JavaScript expression; the input bar is hidden by default and only appears while a script waits for input (the floating window shows itself and takes focus when nothing displays the console, and moves up to avoid the soft keyboard), or stays visible via `console.setInputVisible(true)` or the "Input bar" item of each console more menu; submitted text is echoed with a `> ` prefix and pending input is cancelled when the script ends; new sample "Console/Interactive input"
* `Feature` UI mode adds a `<globalconsole>` element showing the global console, while `<console>` now shows the console of the script itself (as in Auto.js 4); neither shows a time prefix by default; `<console global="true">` and the bare-attribute form `<console global>` are equivalent to `<globalconsole>` (layout XML now accepts bare attributes, treated as `"true"`); the element attributes cover the `console.setXxx` methods (`title` / `titleTextSize` / `titleTextColor` / `titleBackgroundColor` / `titleBackgroundTint` / `titleBackgroundAlpha` / `titleIconsTint` / `contentTextSize` / `contentTextColor` / `contentBackgroundColor` / `contentBackgroundTint` / `contentBackgroundAlpha` / `textSize` / `textColor` / `backgroundColor` / `backgroundTint` / `backgroundAlpha` / `timeVisible` / `timeFormat` / `colorful` / `inputVisible` and more), and setting `title` shows a title bar with a "More" menu; new sample "Layout/Console element"
* `Feature` Node.js bridge subscriptions push sensor, WebSocket, UI, overlay and input events through existing callbacks, with on/once/off listeners, bounded queues and compatible drainEvents
* `Feature` Optional idleExitMs releases an idle Node.js runtime process and reconnects for the next script, with idleForMs diagnostics and resident behavior preserved by default
* `Feature` Native stdin and readline accept console input, with execution-scoped JSON messages through autojs6:host and a backward-compatible v3 postMessage transaction
* `Feature` Node.js screen capture sessions use Android consent and the existing foreground service, with image handles, PNG/JPEG/WebP saving and cleanup when stopped or the script exits
* `Feature` Node.js image handles support clipping, resizing, grayscale, thresholding, template matching and color searches through the host image backend, with independent output handles and execution cleanup
* `Feature` Node.js image.toBytes transfers PNG or RGBA pixels through file descriptors into native Buffers, supports JNI and file bridge modes, and closes attachments after use, timeout or execution exit
* `Feature` Node device APIs expose live build, screen, battery and memory information, brightness control and timed screen wake locks; media supports setting stream volume, with Android permissions enforced
* `Feature` autojs6:events observes Android notifications, external Toast messages, accessibility keys and screen or battery broadcasts through push callbacks, with explicit permissions and automatic cleanup; native Node events remains unchanged
* `Feature` Node app supports Android service starts, broadcasts and installed package queries; dialogs supports text input, choices and progress windows with script cleanup, and keys provides accessibility system actions
* `Feature` Node recorder supports AAC recording with microphone permission, foreground disclosure, duration limits and script cleanup; ui.overlay supports declarative property updates, dragging and pushed events
* `Feature` Node scripts can run concurrently in two independent runtime processes, with a shared FIFO queue and execution-specific cancellation and input routing
* `Feature` MediaInfo queries support zero-based streamNumber, countGet stream counts, and infoKind for units, descriptions and readable names; Rhino and Node preserve default first-stream TEXT queries and negotiate extended plugin capabilities
* `Feature` Checks native ELF alignment before loading OpenCV and Image Quantization plugins, with a clear error for incompatible device page sizes
* `Feature` Plugin Center reads native page alignment from the official index and installed manifests, and warns before installing or enabling incompatible versions on 16 KB devices
* `Feature` MCP screenshot fallback: image.captureScreen(options) accepts the accessibility screenshot crop, scale, format and quality options and returns an encoded one-shot payload for descriptor transport; captureScreen() keeps returning an image handle, and missing or revoked MediaProjection consent returns a permission error.
* `Feature` `images.countPointsByColor(image, color, options?)` counts the pixels matching a color through an OpenCV mask, and `images.getMeanColor(image, region?)` returns the average color of an image or region
* `Feature` `images.readPixels` accepts an ImageWrapper in addition to a file path and leaves the passed image usable
* `Feature` `images.matchTemplate` and `images.findPointByImage` accept a `scales` option (one factor or a list) to match templates captured at other resolutions; every match now exposes `width`, `height`, `scale`, `center` and `rect`, and `MatchingResult` gains `size`, `isEmpty()`, `isNotEmpty()` and `filter()`
* `Feature` Floating run entry in the file explorer script menu, showing a translucent floating toolbar to start / stop the script, open it in the editor and view the log, following night mode and theme color
* `Feature` Layout inspector: long press an attribute in the node info dialog to select several and copy them as one chained selector (id('x').text('y')) or one object selector accepted by pickup() / detect(); "Save layout to file" writes the captured node tree to <working directory>/layouts/<time>.layout.json, and tapping such a file in the file explorer inspects it offline
* `Feature` 3-Shell Terminal plugin integration: the drawer item and the project toolbar open the plugin's terminal, the directory menu "Open in terminal" is provided by the plugin (1.1.0 or later), the drawer switch follows the plugin's sessions, and a missing, disabled or incompatible plugin leads to the Plugin Center, the plugin settings or an update prompt
* `Feature` Script global object terminal: session management, visible execution and output events, with output waits and exit codes (requires the 3-Shell Terminal plugin) (see Sample Code > 终端)
* `Feature` `images.matchFeatures` accepts an image or path for either argument and detects features on the fly; new options `ransacThreshold`, `minInliers` and `maxFeatures`, `ObjectFrame` exposes `width`, `height`, `angle`, `bounds`, `points`, `matches` and `inliers`, and `ImageFeatures` exposes `count` and `method`
* `Feature` Accessibility companion contract for official plugins: companions that publish a `follow-host` service policy are enabled and disabled together with the AutoJs6 accessibility service using the configured root, secure settings or Shizuku strategies, and receive the host service state broadcast
* `Feature` Bun Runtime plugin project execution: a script below a directory carrying `project.json` or `package.json` is packed into a bounded workspace archive (at most 16384 entries and 64 MiB, regular files and directories only) for a plugin that declares `SUPPORTS_WORKSPACE_ARCHIVE`, so relative imports inside the project resolve; the entry file carries the current editor source, symbolic links and special files abort the run, and bare scripts or older plugins keep the single-source path
* `Feature` Bun Runtime plugin host info snapshot: when the plugin declares `SUPPORTS_HOST_INFO` and the `host_info` grant is on (default), `runScript` requests carry `hostInfoVersion = 1` and a `hostInfo` Bundle with the host package name, `BuildConfig.VERSION_DATE` and the preferred language tag, so Bun scripts can read the host/plugin/execution snapshot the plugin verifies and writes through the `AUTOJS6_HOST_INFO_FILE` environment variable; the host never sends its own version (the plugin resolves it through PackageManager), the grant is revocable per run (`BunHostCapabilityGrants`), and plugins without the capability receive exactly the previous request
* `Feature` Bun Runtime plugin capability bridge for dynamic calls with the first two capabilities `ui.toast` and `device.info`: when the plugin declares `SUPPORTS_HOST_CAPABILITY_BRIDGE` and at least one of the `ui_toast` / `device_info` grants is on (default), `runScript` requests carry `hostCapabilityBridgeVersion = 1`, a per-run `IBunHostCapabilityBroker` binder pinned to the plugin UID and execution ID, and the granted capability IDs; Bun scripts call `GET /v1/info` and `POST /v1/<capability>` over the plugin's unix socket, the host relays JSON uninterpreted (64 KiB requests, 256 KiB results, 1024 calls per run, 4 in flight, 10 s each) and answers through `IBunHostCapabilityCallback`. `ui.toast` shows at most four toasts per run from the main thread, `device.info` returns only `android.os.Build` facts; both switches live in the plugin's page of the Plugin Center, and plugins without the capability receive exactly the previous request
* `Feature` "Trash and version history" settings category: a retention policies sub-page edits version history (retention days, versions per file, size per file, total size, max tracked file size), trash (retention days, size limit) and emergency drafts (retention days, size limit), where 0 means unlimited and a change triggers a cleanup right away; plus an "Also clear version history when deleting permanently" switch
* `Feature` "Clean up now" and "Settings" entries in the trash and version history page menus, and a long press on either drawer item opens the retention policy settings
* `Feature` Client mode address dialog can scan the QR code shown by the VSCode extension (1.1.0+) to connect without typing the address
* `Feature` VSCode extension protocol v2 with request/response messages and remote file management methods (browse, read, write, rename and delete device files, run device scripts) for VSCode extension 1.1.0+
* `Feature` Version history list page: the editor and explorer entries now open a dedicated list showing time, size, operation and encoding per revision and marking the one identical to the current file; tap to preview, restore or delete, long-press for multi-selection delete, sort by time or size
* `Feature` Version history diff view: compare a revision with the current file, or select two revisions and compare them; added and removed lines are colored line by line, unchanged runs are folded and expandable, lines can be searched by text; oversized content shows line and size statistics only
* `Feature` Manual snapshots and pinning in version history: the editor menu gains "Create snapshot" (optional label) and snapshots are pinned by default; any revision can be pinned, unpinned or relabelled from the revision list, pinned revisions are exempt from the day / count / per-file size cleanup, and the global size cleanup removes unpinned revisions first
* `Feature` Version history export: the revision details dialog gains "Export", which writes the revision byte for byte (original encoding and BOM kept) to a chosen directory under a confirmed file name (the original name with the revision time stamp by default); overwriting a tracked file keeps its current content first
* `Feature` When version history is opened from the editor, the revision details dialog offers "Restore and save": after confirming the overwrite, the revision is loaded into the editor and saved to disk right away (the default "Restore" still only changes the editor content)
* `Feature` Trash multi-selection: long-press an item to start selecting, with select all / deselect all, batch restore (to each item's original path or one chosen directory; when names clash, choose skip / overwrite / auto-rename for the whole batch, and items sharing a name inside the batch are always auto-renamed; the progress dialog can be aborted and a summary reports restored / skipped / failed counts) and batch removal from the trash
* `Feature` Trash content preview: the item dialog gains "Preview", which opens text files in the read-only editor, lists the files inside a trashed directory (relative path, size, file and folder counts) and reports binary content instead of opening it
* `Feature` Trash item details: the item dialog gains "Details", showing name, type, original path, time moved to trash, size, SHA-256 and the expected expiry (with days and hours left) as selectable text that can be copied to the clipboard in one tap
* `Feature` After the explorer moves a file or directory to the trash, the snackbar offers "Undo", which restores each item to its original path only while that path is still free (never overwriting); items already gone from the trash or whose path is occupied are reported as skipped in the summary
* `Feature` The trash detects broken items whose data file is missing: the list marks them as "Data missing" in a highlight color, tapping offers only details and removal, and a new "Remove broken items" menu action clears them in one go; the periodic cleanup only logs their count and never deletes them automatically
* `Feature` VSCode extension connection: both client mode and server mode gain a "Manager" dialog (opened by the "Manager" button of the drawer entry or by long-pressing it) showing live status (connection state, server or listening address, peer host, extension and protocol versions, connection duration, last activity, reconnect session, last error; each row copyable), actions (connect, disconnect, reconnect now, stop reconnecting, start, stop, restart, disconnect all clients, copy address) and switches (auto reconnect, retry on network change, restore connection or start server on launch, notify on client changes); the server mode manager gains a "Clients" section listing every connected VSCode, with per-client details, disconnect and reconnect
* `Feature` VSCode extension connection: client mode reconnects automatically after an unexpected disconnect (extension crash, VSCode restart, network hiccup) with exponential backoff, immediate retry once a network is available and restore on app launch; both sides announce intentional closes with a bye message, so an explicit disconnect on either side is never followed by an automatic reconnect
* `Feature` VSCode extension connection: pairing confirmation - an unknown device connecting in client mode must be allowed in VSCode first (optionally remembered), while devices that scanned the QR code are trusted automatically; requires extension 1.1.0+
* `Feature` Server mode: LAN discovery (mDNS) - when enabled, the VSCode extension (1.1.0+) lists this device in its LAN connect picker, no address typing needed
* `Feature` VSCode remote files: chunked transfer - large files are read and written in 1 MiB pieces with progress shown in VSCode; requires extension 1.1.0+; fs.list can return file md5 on request so the extension can compare files when pulling project changes from the device
* `Feature` Client mode manager: a "History" entry in the control section shows the server addresses connected before; tap one to connect, long-press to delete, or clear them all
* `Feature` Mail plugin contract module (plugin-api/mail-api) with a frozen AIDL surface, a host client that opens account sessions on a dedicated binding lease, reopens them after the plugin process dies, retries once after an XOAUTH2 token refresh and streams attachments to disk through pipes, and Plugin Center registration as a default-on plugin
* `Feature` Script API `mail` of the mail plugin (mail roadmap P3.1 and P3.2): `mail.connect(options or alias)` with provider presets and a `tokenProvider` for XOAUTH2, `MailClient` with `test`, `send` (attachments from script paths through read-only descriptors), `folders` / `folder(path)` / `createFolder` / `deleteFolder` / `renameFolder`, `fetch`, `search` with a client-side fallback marker, `get` / `fetchBody`, `download` and `raw` streaming into the working directory with `onProgress`, `setFlags` with `markRead` / `markUnread` / `flag` / `unflag`, `move`, `copy`, `delete`, `expunge`, `append` and `close`, each with an `Async` Promise form settled on the script thread; message results carry `Date` fields, printable addresses and bound `load` / `download` methods, failures are `MailError` objects with `code` and `retryable`, and `mail.providers`, `mail.accounts`, `mail.setDefault` plus forwarders of every client method live on the global
* `Feature` Script API `mail.watch` of the mail plugin (mail roadmap P5): `client.watch(folder?, {mode?, pollIntervalMs?, fetchBody?, reconnect?})` and `mail.watch` return a `MailWatch` emitter whose `message(message, watch)`, `mode`, `resync`, `error` and single `close(reason)` events arrive on the script thread and keep the script alive until `stop()`; the plugin watches with IMAP IDLE (polling fallback, POP3 by UIDL), the host drops stale generations and out-of-order deliveries, a plugin death is reported as `PLUGIN_UNAVAILABLE` and by default followed by a new watch on the reopened session with a `resync`, and closing the client or leaving the script stops its watches
* `Feature` Mail plugin contract version 2 with background watches (mail roadmap P8): the appended `IMailPlugin.openTrigger` / `listTriggers` and the `IMailTrigger` / `IMailTriggerCallback` interfaces subscribe to the watches the plugin keeps running in its own foreground service, and the new "On mail arrived" intent task (`org.autojs.autojs6.action.MAIL_TRIGGER`, received behind the host's signature permission) launches a script for each new message of a chosen watch, optionally filtered by sender and subject, with the event document in `engines.myEngine().execArgv.mail`; repeated deliveries are dropped and bursts are throttled to one launch per task every 3 s; a version 1 plugin never sees the new calls
* `Feature` EPUB plugin contract module (plugin-api/epub-api) with a frozen AIDL surface for book extraction (metadata, table of contents, reading order, chapter text, resources, search) and token-based reader sessions, the host discovery wrapper of the `epub` engine, and Plugin Center registration of the Readium EPUB Reader package, whose Explorer Action and EPUB services share one entry
* `Feature` EPUB plugin host client (core/plugin/epub, plugin roadmap P5.4): `EpubPluginHost.openBook` validates the path, opens it read-only and hands out a book client (metadata, table of contents, reading order, paged chapter text as plain text or Markdown, resource export through a spooled pipe, search, positions) on a dedicated binding lease with a death recipient; `openReader` opens a reader session, reads its one-time token and starts the plugin's reader Activity itself (two-step launch), bridges the session events (open, progress, bookmark, error, close) with generation and sequence checks, and forwards goTo, navigate, setPreferences, getBookmarks and getState; every failure maps to the contract's error codes
* `Feature` Script API `epub` of the Readium EPUB Reader plugin (plugin roadmap P6.1): `epub.open(path or options)` returns an `EpubBook` with `metadata`, `toc` (a `{ title, href, children }` tree), `readingOrder`, `positions`, `text(target?, options?)` (an href, a reading-order index, a table-of-contents entry or the whole book, paged with `offset` / `maxChars` and `format` text or markdown), `textAll`, `cover` (an `Image` or a file), `resource`, `search` and `close`; the convenience layer `epub.metadata` / `toc` / `readingOrder` / `text` / `cover` / `search` opens and closes the book per call, `epub.isAvailable()` probes the plugin, each method has an `Async` Promise form settled on the script thread, paths follow the script's working directory and refuse URIs, failures are `EpubError` objects with `code`, the plugin-unavailable hint is localized, and the script's exit closes the books it left open
* `Feature` Reader control of the `epub` script API (plugin roadmap P6.2): `epub.read(path, options?)` opens the reader of the Readium EPUB Reader plugin and returns an `EpubReaderSession` event emitter whose `open`, `progress`, `bookmark`, `error` and `close` events arrive on the script thread, with `goTo(href | progression | locator)`, `next` / `prev` / `nextChapter` / `prevChapter`, `setPreferences(partial)`, `bookmarks()`, `close({ finish? })` and the properties `locator`, `progress`, `title`, `chapterTitle`, `href`, `positions`, `state`, `reason`, `isOpen` / `isClosed`; the start position comes from `href`, `progression` or `locator` with optional `preferences`, a script that stops draining its events gets `error(LIMIT_EXCEEDED)` and `close(overflow)`, and the script's exit ends the session while the reader stays open for the user
* `Feature` EPUB samples (plugin roadmap P6.3): three scripts added to the new in-app sample directory "电子书" (EPUB 元数据与目录: metadata, table of contents, reading order and positions of a book found under Download or Books; 导出章节文本: chapter and whole-book text export as plain text or Markdown, cover export and full-text search; 打开阅读器并监听进度: a reader session with `open` / `progress` / `bookmark` / `error` / `close` listeners, start preferences, a chapter turn and `close()` that leaves the reader to the user); every script checks `epub.isAvailable()` first and explains how to install the Readium EPUB Reader plugin
* `Feature` EPUB contract version 2 with highlights and notes (plugin roadmap P9.4): the appended `IEpubBook.getAnnotations` (paged, in reading order) and the `highlight` reader-session event carry the reader's highlights and notes as an `annotations` document (id, style, color, note, quote, chapter title, locator, createdAt, updatedAt); the host negotiates the version from the plugin's `epubMaxContractVersion` and stamps it on every request, so version-1 plugins never see the new call; the `epub` script API gains `book.annotations()` and `epub.annotations(path)` with their `Async` forms, and `EpubReaderSession` emits `highlight({ action, id, style, color, note, quote, title, locator, createdAt, updatedAt })` with `action` added, updated or removed
* `Feature` Three Stove Agent plugin contract and host brokers with restricted device access, model-call quotas and resource cleanup when a link is lost; the model broker supports native tool-call continuations with per-round model quota checks
* `Feature` Agent script registration and scoped execution with project.json / @agent headers, argument validation, timeout cancellation and structured results
* `Feature` Three Stove Agent management in Plugin Center with installation, activation, authorization and compatibility guidance; optional Tools entry in the installation wizard when a published APK is present in the official index
* `Feature` Three Stove Agent plugin settings can propose extra script folders, which the host validates, saves and restores on reconnect; invalid folders preserve the accepted configuration
* `Feature` installer module for installing, updating and uninstalling apps, with silent Shizuku / Root operations and capability checks for Dhizuku authorization, notification installation and the mode: 'persistent' option of setDefault; Dhizuku remains subject to device/profile owner policies (requires the 3-Setup Installer plugin) (see Project documentation > [Installer](https://docs.autojs6.com/#/installer))
* `Feature` Search bar at the top of the layout hierarchy and layout bounds inspector windows, matching nodes by keyword or regular expression over text / desc / id / className: the hierarchy window keeps only matching nodes and their ancestors and highlights the hit, the bounds window outlines only matching nodes, matches can be stepped through back and forth, the bar collapses into a draggable pill, and the query is kept when switching between the two windows
* `Feature` Tapping the match counter of the layout inspector search bar opens a results list dialog (node summary, path, depth and bounds; tap to jump to and select a node, long-press for its node info; an explanation when nothing matches), and the bar keeps a query history (recorded on enter or navigation, up to 20 entries, shown as a drop-down while the input is empty, long-press to delete, clearable, kept across sessions)
* `Feature` Filter profiles in the layout inspector search bar: filter nodes by tri-state boolean attributes (14 of them, such as clickable / scrollable / enabled), has id / text / desc, class presets (buttons / text / inputs / images / checkboxes and switches / lists and scrollers / containers), depth / childCount / indexInParent ranges, minimum width and height, on-screen only, package names and actions; a built-in read-only default profile, edits applied at once and savable as user profiles (save as / rename / delete), with the selected profile remembered across sessions
* `Feature` Region selection in the layout bounds window: choose "Select region" in the filter profiles and drag a rectangle to keep only the nodes lying entirely inside it; the region stays in effect when switching to the hierarchy window
* `Feature` Field mode in the layout inspector search bar: tap the mode toggle (Aa / k=v) to enter several conditions in the script selector string syntax (such as text=OK clickable, id~=btn_.*, !enabled, minDepth=3); a syntax cheat sheet shows while the input has focus, syntax errors show the position, the term and candidate names in a hint strip, and the query mode is remembered across sessions
* `Feature` "Copy as selector" in the layout inspector: tap "Copy as selector" in the search results dialog or long-press the counter to put the keyword (as the Contains / Match call of the text / desc / id / className property the current match hit), the field conditions, the filter profile and the selected region on the clipboard as a chained script selector; conditions that cannot be expressed are appended as comments
* `Feature` Selector methods minDepth / maxDepth / minIndexInParent / maxIndexInParent (inclusive, like minChildCount / maxChildCount), also usable in the selector string syntax (such as minDepth=3)
* `Feature` Selector mode in the layout inspector search bar: once the mode toggle reaches "$()", type a chained selector expression as in a script (such as text("OK").clickable().depth(3) or textMatch(/ok|cancel/i); the selector() / $() prefix is optional) and its string / number / boolean / regex literals land on the selector registry with script semantics; a half-typed expression shows "Expression not finished" instead of an error, syntax errors show the position, the call and candidate method names, and "Copy as selector" outputs the normalized expression
* `Feature` Layout Inspector "Generate code" supports single-widget/collection scopes, waiting until found/waiting with a timeout/immediate checks, action receivers and text parameters, Classic/Flow styles, alternative plans and location explanations, with matching widgets previewable in the layout
* `Feature` The code generation models page supports local import, on-demand downloads, managing and comparing multiple versions, and updates for official models (Settings > Accessibility service > Code generation models)
* `Feature` User color libraries for the theme color: create or clone a library, add colors from the color palette, another library or history, and rename, change values, reorder by dragging or delete in batches; the libraries themselves can also be reordered by dragging and deleted in batches
* `Feature` Intelligent color library: each group picks its color by the current state of a state source, which can be night mode, time periods, sunrise and sunset (location from the time zone or entered coordinates), the day of the week or workdays and the weekend, seasons, battery ranges, charging, battery saver, the accessibility service, running scripts or script states (switched with colors.theme.state); groups rotate in sequence or at random on state changes, app start, script start, at an interval (first change now, at the next whole minute or at the next whole hour, at least 5 seconds) or at daily times; date overrides show a chosen group on date ranges or holidays (including Chinese lunar festivals such as the Spring Festival and Mid-Autumn Festival, optionally some days before or after); rotation pauses while theme color pages are open
* `Feature` Intelligent color library presets "Material day and night" (19 Material families, with day and night shades picked by contrast against the window backgrounds), "Background color" and "Follow wallpaper" (the primary, secondary and tertiary colors of the wallpaper, Android 8.1 or later, refreshed when the wallpaper changes); clone a preset, create a new library or generate one from a seed color (the theme color, the color palette or a wallpaper color, with a harmony or tones, night colors filled in for night mode), then adjust the state source, group rotation, order and group colors in the editor, where the other states of a group can be filled in from its first one (undoable)
* `Feature` Color library import and export in the AutoJs6 JSON format: export one library from its long press menu or several with "Export color libraries..." (presets as snapshots); import previews the libraries of a file with their color or group counts, always creates new libraries, numbers duplicate names, and explains why a file cannot be imported
* `Feature` "Color tools" in the color details: complementary, analogous, triadic, split complementary and square harmonies, a light to dark tonal scale, contrast against the day and night window backgrounds (with the nearest color reaching 3:1 when it falls short) and protanopia, deuteranopia and tritanopia simulation; each color can be applied as the theme color, added to a color library or copied
* `Feature` "Derived colors" setting for the theme color (Off by default, identical to earlier versions): Auto derives the accent color, used by floating action buttons, and the dark color, used by the status bar, from the primary color, and plugins receive all three; each state of an intelligent color library group can set its own accent and dark colors, which take precedence under both settings
* `Feature` Color favorites: check "Favorite" or "Pinned" in the color details, or long press a library color or a color tools swatch; the virtual library "Favorites" sorts manually (by dragging), by time added, hue or lightness with pinned colors first, adds colors from the palette and creates an intelligent color library with one group per favorite; favorites are also seeds of "Generate from a seed color"
* `Feature` Virtual library "Frequent and recommended": pinned favorites, other favorites and history colors scored by use count and recency (14 day half-life), with similar colors merged, at most 30 and hidden while empty; entries apply the way they were chosen and can become an intelligent color library; color history now counts uses, and its list can merge similar colors
* `Feature` `colors.distance(colorA, colorB, algorithm?)` returns the color distance on the threshold scale of each detection algorithm, `colors.invert(color)` inverts RGB while keeping alpha, `colors.blend(colorA, colorB, ratio?)` mixes two colors and `colors.contrast(foreground, background)` computes the WCAG contrast ratio; all four are also available on Color instances
* `Feature` Perceptual color functions in the colors module: toOklab / oklab and toOklch / oklch, ensureContrast (WCAG ratio, 4.5 by default), harmony, tones, derive (primary, accent and dark), nightVariant, simulate (protanopia, deuteranopia, tritanopia) and extract (dominant colors of an image with count, region and step options); colors.distance, colors.isSimilar and images.detectColor accept the oklab algorithm (see Sample Code > 图像与颜色)
* `Feature` colors.theme controls the theme color from scripts: get, set (accent and dark options), reset, libraries, activate and deactivate (intelligent color libraries), next (rotates at once), state (switches the "Script states" source) and current; script changes within 500 ms are merged and recorded in the history as "Script: name", and the theme color page offers "Undo script change" to restore the previous selection, including an active intelligent color library (see Sample Code > 图像与颜色)
* `Feature` Long-pressing TAB on the Ace editor shortcut panel triggers smart hints, showing code completion, parameter hints or symbol documentation based on the cursor context and cycling overloads while parameter hints are visible; hardware keyboard Ctrl+Space / Ctrl+Shift+Space / Ctrl+Q map to smart hints / parameter hints / symbol documentation
* `Feature` Code editor settings gain a "Smart hints" group (Ace engine only): automatic completion trigger (while typing identifiers / only after typing "." / off), minimum characters for completion, completion delay, parameter hints trigger (when the cursor enters a call / only after typing "(" or "," / off) and switching overloads with arrow keys; changes apply immediately and can be recorded in action favorites (requires Ace Editor plugin 1.26.0)
* `Feature` compose / $compose script API with 29 node factories, retained handles, reactive state/render/ref, batched updates, queued posts, themes and Snackbar session commands (requires the Compose UI plugin)
* `Feature` compose.mount hosts UI pages; compose.floaty supports UI and non-UI scripts with raw or resizable windows, pixel position/size, touch/focus controls and worker updates through compose.post, with owned session cleanup on close or script termination
* `Feature` The Compose UI category of built-in samples provides counter, form validation, 1000-item list, floating HUD, and theme scripts, each with plugin and permission prerequisites
* `Feature` Complete Compose UI API reference with companion Ace Editor type completion and local Offline Docs content for components, nodes, state, modifiers, themes, sessions and floating windows
* `Feature` Plugin Center recognizes and manages installed Compose UI development preview plugins, requiring host 6.8.0 / 5316 or later
* `Feature` TSX supports `<compose.Column>`, `<compose:Text>`, references to node factories, fragments, slots and reactive callbacks; a single tree cannot mix Compose and legacy XML nodes
* `Feature` XML `<compose>` containers and compose.attach embed independent Compose sessions in UI pages or legacy floating windows; compose.AndroidView embeds an existing Android View or one returned by a synchronous factory
* `Feature` ui.inflate accepts compiled XML TSX layouts and binds their onClick callbacks
* `Feature` compose.dialog returns an updatable, closable session for dialogs and modal bottom sheets in UI or ordinary scripts
* `Feature` Material 3 extended components: navigation bars and drawers, tabs, bottom sheets and menus, date and time pickers, paging and grids, chips, badges, segmented buttons, floating action buttons, search bars, tooltips and pull to refresh
* `Feature` compose.memo reuses render fragments whose dependency values and read states are unchanged, so a partial update of a large list rebuilds only the changed rows
* `Feature` Developer options include "Allow Inspector for Node.js scripts", disabled by default, for local breakpoints and CPU/heap profiling in Debug and Release with a compatible Node.js Runtime plugin; desktop DevTools connects through ADB port forwarding
* `Feature` Layout Inspector "Generate code" supports relative locating plans that identify the target through a nearby widget's text / desc / id and a fixed compass relation (for example an icon in a list row located by the row title), generating filter and compass calls
* `Fix` Plugin search filters remaining active after closing search or switching home tabs
* `Fix` A host crash when intelligent theme colors refresh on returning to the foreground or recreating a screen and widget callbacks register additional theme targets
* `Fix` Plugin Center showing Unknown ABIs for official plugins verified to contain no native code, and losing indexed native compatibility metadata after a restart
* `Fix` The plugin installation wizard waiting for a timeout after a failed or cancelled installation when installing consecutive plugins through the system installer
* `Fix` Plugin Center selecting an x86_64 APK on an x86 device despite an available x86 APK
* `Fix` Package information becoming inaccessible from the file manager when APK Inspector hides the information button
* `Fix` Three Stove Agent model-call failures lost network, authentication and response error categories (requires Provider plugin support)
* `Fix` Assistant tool-option merging no longer enables default-off tool groups when the base tools object is empty
* `Fix` Compact accessibility trees shortened resource IDs, preventing literal selectors from matching the displayed IDs
* `Fix` Compatibility of plugin file snapshots, Node.js package installation and TypeScript project file operations on Android 7/8.0
* `Fix` Shizuku permission request listeners remaining registered after destruction
* `Fix` Missing translations for Python long-running task notifications and missing English text in DEX compiler settings
* `Fix` Official plugins requesting host appearance during host startup could crash preference initialization before the application context was ready
* `Fix` Three Stove Agent screen observations and actions could continue while the screen was off or locked instead of returning SCREEN_LOCKED
* `Fix` Plugin capability brokers writing toast text into host logs when toast logging was requested
* `Fix` Agent falsely rejecting node references and reporting an outdated app name after an action confirmation window closes
* `Fix` Node actions handle deeply nested screens and distinguish container bounds and action capabilities when identifying a target
* `Fix` Enabling the YOLO plugin from Plugin Center failed because its probe handshake required the plugin to be enabled already; the probe now requires only authorization and the exact-component handshake, while runtime entry points still require the saved enable record
* `Fix` MCP Server installed via adb could appear enabled in Plugin Center while runtime checks treated it as disabled when no enable preference was saved; both now default to disabled and preserve explicit user choices
* `Fix` Unnecessary blank lines below short accessibility manager status values such as Unbound and None
* `Fix` Node.js controlled Fetch inline responses exceeding the Binder transaction size limit were silently lost, leaving scripts waiting for a bridge timeout
* `Fix` Destroying the Node.js WebSocket provider cancelled connections before peers could receive graceful close frames
* `Fix` Runnable Node.js projects without APK packaging metadata, such as host-events, not recognized as projects in the file list and project toolbar
* `Fix` Node.js project start and finish logs showed main.js without its path instead of the full path and actual extension of the entry file, such as main.cjs
* `Fix` The screen color picker settings button opens the plugin main page, and the MCP Server drawer subtitle is hidden when stopped
* `Fix` Node.js workspace transfer and module loading leaked file descriptors, slowing subsequent scripts and application restart after projects with many files
* `Fix` Node.js project writeback no longer replaces unchanged files, avoiding slow script completion and sluggish application response with large dependency trees
* `Fix` The Flow hospital registration example returns false instead of null when a doctor row cannot be parsed, avoiding boolean coercion errors
* `Fix` Plugin authorization results return correctly from the main screen so a successful grant can complete the pending enable action
* `Fix` The HTTP isInsecure / insecure option could leave certificate and hostname verification disabled for later requests; it now applies only to the current request
* `Fix` HTTP requests in host and packaged release apps could be blocked by the network security configuration; cleartext policy now matches debug builds while keeping GitHub resource domains restricted to HTTPS
* `Fix` Background APK installations could not continue when the system blocked the confirmation activity; session status is now saved, with confirmation available through a notification or on returning to the app, including after notification denial or process recreation
* `Fix` Script widgets could lose their script paths and click mappings during batch updates when widget IDs contained multiple digits
* `Fix` Binary reads of built-in samples through the host bridge use the existing Okio base64 encoder on Android 7.0 / 7.1, avoiding an API 26-only Java method and supporting MCP sample resources
* `Fix` MCP file operations and Shell support Android 24 / 25 without newer Java filesystem or process methods; recursive deletion unlinks symbolic links without deleting their targets
* `Fix` The CI editor plugin gate required legacy contract version 1 and rejected hosts supporting newer compatible contract versions
* `Fix` A crash when Paddle OCR and Rapid OCR are used sequentially in the same packaged app
* `Fix` MediaInfo and image file paths now allow absolute paths, parent directories and valid filenames; recording outputs use the same Android file access rules with the updated host
* `Fix` File Manager direct execution did not honor the corresponding UTF-8, UTF-16, or UTF-32 encoding for scripts with a Unicode BOM, causing files that ran normally in the editor to fail with `Invalid character`
* `Fix` Android Studio download information was no longer written automatically to shared README data after the scraper migration
* `Fix` Aligned experimental remote single-file build numbering with the legacy builder, and added fail-closed workspace storage preflight using cross-checked expanded input sizes, a build-verified template expansion bound, and a 256 MiB reserve
* `Fix` Ace WebView could briefly expose a pure-white page before the host theme and initial document took effect, producing a conspicuous flash with dark editor themes; first presentation now waits for explicit theme-applied, document-committed, and stable-paint signals instead of a fixed delay
* `Fix` WebView file choosers in UI scripts could fail to open when the root layout context lost its Activity association
* `Fix` UI scripts could unexpectedly exit when entering split screen or resizing a window because Activity recreation discarded the running script
* `Fix` Native and Ace editors could lose cursor positions and selections after Activity recreation, and large documents opened without a file path could lose unsaved text
* `Fix` The editor text area could collapse in landscape with the keyboard open, and the Log page input could be covered by the keyboard
* `Fix` The Main page search query and expanded search field could be lost when split-screen changes recreated the Activity
* `Fix` Ace could fall back to the legacy engine on the first Lua-file open when synchronous LuaLS native-runtime probing delayed initial presentation
* `Fix` Concurrent access to the shared font store by the AutoJs6 host and Ace plugin could throw a JVM OverlappingFileLockException and crash the app during font preview
* `Fix` APK Builder now preserves a TypeScript/TSX entry's Rhino execution-mode directive (including `ui`) in the generated launcher, so compiled UI projects start in the correct Activity mode
* `Fix` Embedded Node.js APK packaging requests now fail before staging or opening a remote session with external Runtime-plugin migration guidance; execution-mode parsing no longer logs source tokens or exception details
* `Fix` Remote APK builds cancelled before or during session startup could be reported as failed; cancellation now converges to exactly one `STATUS_CANCELLED` terminal result while preserving the previous output and completing cleanup
* `Fix` Android 9–11 could expose no modern archive signer for a valid V2/V3 remote APK and reject it before publication; the host now uses the validated legacy signer view only as a platform fallback while retaining mandatory `apksig` cryptographic verification
* `Fix` A cryptographically valid remote APK with the requested package and version but a different signing certificate could pass host output validation; the host now binds the verified single signer to the default or custom signing identity selected before opening the Binder session and rejects mismatches before replacing the previous output
* `Fix` After a plugin process died, multiple terminal Binder callbacks could let a delayed unbind cancel the immediately recreated service binding, blocking plugin rediscovery until the 60-second bind timeout
* `Fix` When the R1 developer switch was enabled but the installed APK Builder plugin explicitly advertised `supportsRemoteBuild=false`, provider discovery could stop without actionable guidance; the host now rejects it before preparation and explains how to install a capable build or turn off the switch
* `Fix` Enabling the TypeScript command row in the full Log page and editor bottom Log panel let a wrap-content navigation-bar spacer consume the entire remaining height, collapsing the log list to zero; the row now stays above a visible log viewport, and the Log page FAB uses its original bottom offset
* `Fix` Archive entry Rename and Delete actions did not honor the current session's declared mutation capabilities, allowing read-only or unsupported sessions to expose operations the provider could not complete
* `Fix` File manager Explorer actions could retain stale cache entries or row references after a plugin was enabled, re-authorized, or updated, causing recognized files to fall back to the system application chooser until AutoJs6 restarted
* `Fix` File manager video buttons could open the system application chooser for formats already classified as video; all recognized video types now use the official Video Player plugin first, and if it is missing, disabled, unauthorized, incompatible, unavailable, or fails to launch, the host shows recovery guidance with an explicit "Open with other apps" option
* `Fix` M3U file rows could show plugin, play and edit actions together; playback now prefers 3-Amber, then 3-Terra, with Plugin Center links to install, enable or authorize a player when none is available
* `Fix` Redundant plugin action icons for .m3u8/.pls/.wax/.wvx/.asx playlists in the file manager; keep Play and Edit while retaining the matched player
* `Fix` File manager APK inspection could fail on ordinary APKs with many resource entries because of the archive entry limit; standalone APKs now read a size-bounded manifest directly while multi-APK containers retain traversal validation
* `Fix` Packaged Rhino CommonJS require could not decrypt dependent JavaScript modules, causing multi-file build-time TypeScript output to fail with "Cannot read file"
* `Fix` Node.js UI handles could remain active after their Android Activity was destroyed, allowing late updates and retaining provider-owned resources until execution teardown
* `Fix` Node.js plugin execution could reject large plaintext dependency graphs and real-world node_modules workspaces because module-probe and workspace-archive budgets were too small
* `Fix` Empty and simple Node.js scripts started slowly in working directories containing many files because unnecessary module scanning read candidate files in full
* `Fix` Persistent Node.js process was incorrectly retired when a short-lived script launcher UI became hidden
* `Fix` An expected Binder disconnection callback from the persistent Node.js process after cleanly stopping an interactive long-running task in a packaged app could overwrite the stopped state with failed
* `Fix` Node.js console.assert incorrectly threw an AssertionError when an assertion failed instead of only writing the failure message to stderr
* `Fix` Node.js script results were returned late because runtime process-settle waiting blocked result delivery
* `Fix` Active Node.js cancellation could remove the execution registration before unhealthy-process quarantine, leaving completion waiters blocked and delaying recovery
* `Fix` Automatic embedded fallback after a lost external runtime Binder response could execute the same script twice
* `Fix` Node.js TypeScript workspace-archive runs could synchronize runtime-only compiler output back beside source files, causing subsequent executions to collide with the original project
* `Fix` Android private-directory aliases /data/user/0 and /data/data were not normalized and written back into plugin requests, causing valid Node.js workingDirectory paths to be incorrectly rejected as escaping sandboxRoot
* `Fix` nodePlugin builds without a host Node runtime incorrectly excluded JNI/AAR libraries for every ABI, causing Rhino Shell initialization to crash because libjackpal-termexec2.so was missing
* `Fix` Node.js R6 evidence collection and verification rejected APK signer identities reported by Android Build Tools 37 because apksigner changed its certificate digest output format
* `Fix` Running the project directly from IntelliJ IDEA failed native page alignment verification because the APK lookup did not use the current build's artifact directory
* `Fix` IntelliJ IDEA builds of Android modules failed because compileJava task matching was ambiguous
* `Fix` Kotlin compilation running out of memory during multi-variant builds
* `Fix` SDK XML v4 parsing warnings with AGP 9.1 and APK native alignment checks incorrectly triggered by JVM unit-test assembly tasks, using shared build plugins 1.8.3
* `Fix` IntelliJ IDEA full-project builds failed after Clean because staged Python Runtime API AARs were resolved before generation and conformance fixtures lacked newly appended AIDL methods
* `Fix` RemoveWorkManagerInitializer Lint error during builds caused by a conflict between WorkManager's default initializer and on-demand initialization
* `Fix` APK Builder Template builds failed after `clean` because the default Runtime Kit directory omitted the `universal` variant suffix
* `Fix` Node.js R6 soak evidence could reject a healthy persistent runtime when Android reported zero currently allocated native-heap bytes
* `Fix` Node.js R6 packaged APK evidence was skipped or bound to arm64 because the collector did not stage a fresh Inrt template or pass the target ABI
* `Fix` Node.js project launches ignored node.timeoutMs in project.json, causing scripts configured for longer waits to time out after about 5 seconds
* `Fix` Cross-ABI app packaging could copy native libraries from the installed host process into another ABI directory, causing 32-bit packaged apps to load 64-bit shell native libraries and crash
* `Fix` TypeScript UI scripts with compilation failures before engine creation could crash the host while reporting diagnostics because the global execution listener dereferenced a missing engine
* `Fix` Stack-frame and compiler-diagnostic links treated one-based source columns as zero-based editor offsets, moving the cursor one character too far
* `Fix` Experimental DEX compiler host could accept DEX versions incompatible with the requested minApi, reject the valid 65,536 field/method ID boundary, and recreate a rolled-back partial artifact when a queued pipe drain started late
* `Fix` runtime.loadJar could not dynamically load Java class files compiled for newer Java versions correctly
* `Fix` app.isInstalled/kill and related methods could not use package name arguments correctly _[`issue #495`](http://issues.autojs6.com/495)_
* `Fix` Shell-related methods could become unresponsive on devices without Root access
* `Fix` Multiple-choice results returned by dialogs.multiChoice were incorrectly converted to strings _[`issue #518`](http://issues.autojs6.com/518)_
* `Fix` The app could crash when a script dialog was dismissed while its thread Looper was exiting because a timer callback could no longer be scheduled
* `Fix` Scripts could remain running after timers were cancelled or worker threads stopped because stale callback deadlines prevented the Looper from exiting
* `Fix` Selector module could choose the wrong method signature when invoking overloaded methods through reflection
* `Fix` Widget search with the BFS algorithm returned one result more than the requested maximum, `find(0)` returned one result instead of an empty collection, and nodes already fetched when the maximum was reached were not recycled
* `Fix` Multi-segment parent or clickable compass segments (such as `p4ppp12p` or `k2k3`) passed to `UiObject.compass` only counted the first segment
* `Fix` The `k` segment of `UiObject.compass` returned the node itself instead of `null` when no clickable node existed within range
* `Fix` Computing the parent index of the `source` node of an `auto.registerEvent` callback could loop forever or throw an out-of-bounds exception and crash the accessibility service when the node tree had changed
* `Fix` After the accessibility service restarted, `pickup` without a root node and selectors created by `new UiSelector()` could keep returning `null` or an empty collection until a new script runtime was created
* `Fix` `auto.stateListener` was a single global slot, so listeners set by multiple scripts overwrote each other and were not removed when a script ended
* `Fix` Waiting methods such as `auto.waitFor` could not be woken up and could wait until timeout when the accessibility service had started delivering events but the system never invoked `onServiceConnected`
* `Fix` Stopping the accessibility service with root silently did nothing yet reported success when AutoJs6 was the only enabled accessibility service, so the service could not be stopped or appeared to "restart automatically" right away
* `Fix` Creating an app screen automatically re-enabled an accessibility service that the user had explicitly stopped (through root or secure settings); now only a service that is enabled in system settings but not running is restored, everything else is started on demand by scripts, the layout inspector and other entry points
* `Fix` `auto.start` and `auto.enable` re-enabled the accessibility service through root or secure settings even when it was already running, needlessly restarting it
* `Fix` Restarting the accessibility service re-enabled it before the system had unbound the previous instance, which some systems (e.g. Android 8.0) coalesced into "still enabled" so that no restart actually happened
* `Fix` `auto.windowRoots` returned an array holding a null node instead of an empty array while the system had not yet determined the active window (e.g. right after the accessibility service was rebound), so calling methods on its element threw an exception
* `Fix` `automator.isServiceRunning()` only checked whether an accessibility service instance existed in the process, which disagreed with the "running" notion of `auto.isRunning()` and the drawer switch (enabled in system settings and bound in the process)
* `Fix` Entry points such as `auto()` threw "enabled but not running" when the accessibility service was not enabled, and "service is disabled" when it was enabled but not running, i.e. the two messages were swapped (the reason is now one of not enabled / enabled but not bound / bound but not operational yet)
* `Fix` `auto.services` always returned an empty array unless automatic enabling through root or secure settings was configured (reading the enabled services list needs neither)
* `Fix` The accessibility service switch in the drawer showed a service that was "enabled but not running" as on (it is now off with a "malfunctioning" subtitle, and switching it on re-enables the service)
* `Fix` "Inspect layout bounds" / "Inspect layout hierarchy" in the floating button had to be triggered twice when the accessibility service was not enabled but could be started automatically (root or secure settings): the first tap only started the service and the second one inspected (the inspection now continues right after the service has started, likewise for the quick settings tiles and the layout inspection broadcasts, and starting no longer blocks the main thread)
* `Fix` Event callbacks registered with `auto.registerEvent` and key listeners of `events.observeKey()` were all lost once the accessibility service restarted (including a rebind after the system had unbound it); subscriptions now live on a process-wide event bus and outlive the service instance
* `Fix` A scope API compatibility regression after the Rhino engine update caused some runtime helpers to malfunction
* `Fix` The Rhino module bridge handled null/undefined, Callable objects, constructors, prototypes, realms, and Kotlin return values incorrectly, potentially causing cross-realm objects, lost callbacks, or runtime exceptions
* `Fix` Proxy-wrapped functions could not be used as callbacks by legacy modules such as dialogs, events, timers, UI, selector, and automator, and TimerThread callback arguments could be passed as a nested array
* `Fix` Callable and BaseFunction compatibility overloads caused ambiguous parameter types when ordinary JavaScript functions called Java methods such as EventEmitter.on
* `Fix` EventEmitter.once listeners could be invoked more than once during recursive event dispatch or when a listener threw an exception
* `Fix` Global actions such as automator.home could throw a null pointer exception when servant Looper initialization was interrupted on first use, and gesture Y coordinates could incorrectly use the horizontal scaling ratio
* `Fix` Argument dispatch, return values, numeric boundaries, image coordinates, and temporary resource lifecycles in the early JavaScript module migration did not match the original implementation
* `Fix` The app could crash when requesting screen capture permission on Android 14+ due to abnormal media projection foreground service startup _[`issue #516`](http://issues.autojs6.com/516)_
* `Fix` Repeated OCR calls across multiple threads or files could repeatedly request screen capture permission _[`issue #551`](http://issues.autojs6.com/551)_
* `Fix` OCR plugin selection errors could name the wrong engine, omit the required engine ID/variant and detected plugins, or report failed discovery as a missing installation
* `Fix` Paddle OCR plugin service could disconnect after running for a long time and fail to recover automatically _[`issue #515`](http://issues.autojs6.com/515)_ _[`issue #525`](http://issues.autojs6.com/525)_
* `Fix` Paddle OCR plugin could not be woken automatically when not running, causing feature calls or project packaging to fail _[`issue #517`](http://issues.autojs6.com/517)_
* `Fix` Incorrect `images.resize(ImageWrapper, width, height)` calls could pass the height to OpenCV as an interpolation value, while incompatible native exception unwinding could crash the app instead of reporting a script exception
* `Fix` File manager media details could remain as "..." or crash the app when the MediaInfo plugin library was unavailable or a Binder call failed, without falling back to Android platform metadata
* `Fix` File manager video detail dialogs incorrectly displayed embedded audio format and metadata fields
* `Fix` The Markdown and HTML preview main document could be incorrectly rejected by the request interceptor on some WebView versions and physical devices
* `Fix` Image viewer pinch zoom did not track finger movement closely, was difficult to return to the original size when pinching inward, and could briefly or continuously retain the theme color on the ActionBar when first opened
* `Fix` ImageViewer could crash during destruction because Glide attempted to start an image load for an already destroyed Activity
* `Fix` Accessibility service could stop unexpectedly while performing consecutive operations such as back _[`issue #527`](http://issues.autojs6.com/527)_
* `Fix` Memory usage could continue to increase after repeatedly running scripts (attempted fix) _[`issue #548`](http://issues.autojs6.com/548)_
* `Fix` The original path might not be preserved when running scripts through external URIs or in the background, causing relative paths to resolve incorrectly _[`issue #545`](http://issues.autojs6.com/545)_
* `Fix` Tasker could not invoke and run scripts correctly _[`issue #501`](http://issues.autojs6.com/501)_
* `Fix` The Home > Docs tab could crash the app when ViewPager received an anomalous multi-pointer sequence containing an invalid pointer index
* `Fix` Multiple Ace editor interaction issues involving IME input/text selection/code folding/pinch zoom/stack navigation and related scenarios
* `Fix` Code editor settings primary titles, the fx function panel, the left-pinned documentation drawer, and the Ace text-selection toolbar could still use AutoJs6 app colors instead of the current editor theme, while Ace line actions could appear more than once
* `Fix` Fullscreen editing could let the soft keyboard cover the multifunction key bar, and the navigation-bar area below the fullscreen hint Snackbar could remain transparent
* `Fix` The code editor documentation sidebar could show only its themed background with no content, while editor settings lacked stable hierarchical role colors derived from the editor background, causing title, subtitle, category, and control colors to disappear after list scrolling and item recycling
* `Fix` The soft keyboard could cover the multifunction key bar outside fullscreen mode
* `Fix` The code editor information bar could omit file extensions or briefly display a temporary end-of-file cursor position while opening a document, and a light-colored gap could flash between the multifunction key bar and the soft keyboard when app and editor themes used different brightness
* `Fix` Ace editor could become unresponsive and fall back to the native editor after an incompatible-encoding reload of a large file produced an extremely long text line
* `Fix` Disabling or uninstalling the external Ace plugin while its editor or settings interface was active could cause unsaved editing state to be lost or leave the interface in an invalid state
* `Fix` Plugin Center action icons were spaced too far from their labels after the row layout was flattened
* `Fix` Plugin Center left September dates such as `Sept 13, 2026` unnormalized instead of displaying `yyyy-MM-dd`
* `Fix` Plugin Center could display a stale remote icon instead of the current installed icon when a plugin had no online icon URL
* `Fix` Switching Plugin Center between light and dark modes could briefly display an installed icon from the previous theme; both variants are now prepared in the background and the current theme is selected on the first bind
* `Fix` Plugin Center icon cache and swipe refresh state could become inconsistent in some scenarios
* `Fix` A single local plugin process failure could leave Plugin Center with a completely blank list
* `Fix` Returning to Plugin Center from another screen or the background could make a concurrent refresh transiently remove installed legacy SDK plugins or replace them with disabled index-only entries, reordering rows, rebinding switches, and shifting the scroll position
* `Fix` Installed plugin icons on the Home Plugins tab did not switch dynamically when night mode changed
* `Fix` Plugin Center could install an APK from the wrong distribution variant when a plugin release contained multiple variants
* `Fix` APK Builder Template plugins could appear twice or incorrectly report updates
* `Fix` Offline project documentation integrity verification could reuse stale cached content while the plugin was being updated
* `Fix` The Manifest button and complete multiline content were missing from APK details in the file manager
* `Fix` The file manager incorrectly ignored case in ZIP entry names when inspecting APKs, causing some valid packages with case-distinct entries to be reported as containing duplicates and preventing them from being viewed or installed
* `Fix` External URI cache imports trusted content-provider filenames, potentially allowing path traversal or same-name overwrites
* `Fix` File manager list scroll position was reset after deleting files from the Recycle Bin
* `Fix` Search fields could keep the default blue cursor instead of the theme-appropriate foreground color, while their hints could remain in the fallback language instead of following the current app language
* `Fix` Switch views in home drawer settings had an excessively small effective touch area
* `Fix` Home drawer switches could be clipped and bottom action buttons hidden on narrow screens such as 360x640 or after resizing the window
* `Fix` Closing or recreating the home screen could crash the app when drawer listener cleanup accessed a released context
* `Fix` Recent app package names and Activity information displayed in the floating button menu were incorrect _[`issue #529`](http://issues.autojs6.com/529)_
* `Fix` The floating button "Run script" dialog could fail to restore after being minimized (attempted fix) _[`issue #500`](http://issues.autojs6.com/500)_
* `Fix` Splash screen background did not extend into the short-edge display cutout area
* `Fix` File manager could fail to refresh a newly committed Explorer Action sibling output or could display a committed output directory as a file
* `Fix` Leaving a native archive page on Android 7 could crash while cleaning the session preview cache because the cleanup path called a newer File NIO API
* `Fix` Returning from an Explorer Action player could trigger up to two full file-list rebinds, causing severe flashing even though the folder and scroll position were retained
* `Fix` Clicking File from another Home tab skipped the ViewPager transition animation
* `Fix` Returning from a landscape plugin page such as the video player rebuilt the whole main UI on the configuration change, making the file manager flash severely and sometimes twice in a row
* `Fix` Opening the Home Plugins tab or standalone Plugin Center for the first time, or returning to any Home tab after visiting Plugins, could block immediate gestures for about half a second
* `Fix` Home-to-Plugins transitions and Plugin Center pull-to-refresh animations could freeze midway while plugin service metadata and dense cold list rows were prepared; the standalone Plugin Center could also perform root/settings accessibility restoration during Activity creation
* `Fix` Explorer Action puzzle icons for ZIP/JAR and other plugin-handled files filled the entire action cell and appeared oversized
* `Fix` The file manager could not reliably distinguish TypeScript `.ts/.mts` sources and declarations from MPEG-2 TS video with the same suffixes, and did not always treat `.d.ts/.d.mts/.d.cts` as declaration files
* `Fix` Plugin-backed `.py/.ts/.java/.kt` source files did not consistently use executable-source icons and expose the Run action
* `Fix` Launching `.ts/.tsx` files through Intent and other entry points could display an incorrect `.js` extension in the host console and lose the original file path
* `Fix` Minimal script projects without APK packaging metadata fell back to `main.js` instead of launching the `main` entry declared in `project.json`
* `Fix` Plugin activation entries could be resolved incorrectly for application ID suffix variants, and wake success could be reported before the service was available
* `Fix` Calling the compat accessors `getChild()` / `getParent()` etc. on a `UiObject` that carried a node allocator recursed endlessly (the allocator called back into the wrapper's own accessors); the allocator now works on the raw node and registers by object identity rather than node equality (two instances of the same view are no longer released as one). It is still not wired into script runtimes: below Android 13 nodes are pooled per process, so a batch release at exit could recycle an instance already reused by another script
* `Fix` The selector condition `contextClickable()` showed up as `checkable` in the selector's string form (`selector().toString()`, error messages)
* `Fix` The global function form `filter(fn)` failed with "Invalid arguments", and a regex inside a map selector array (`pickup({text: [/regex/]})`) was rejected
* `Fix` Decimals in the string form of selectors (`selector().toString()`, error messages) showed up as `boundsLeft(0,5)` under UI languages whose decimal separator is a comma (French, Russian, ...); a dot is used everywhere now
* `Fix` Java exceptions thrown by the global function form of selector methods (`algorithm('xyz')`, `textMatch('(')`, ...) could not be caught by the script's `try / catch` and terminated the script; they are thrown as catchable script errors now
* `Fix` With the `findOnUiThread` flag set, a search posted to the UI thread could hang forever when its wake-up was lost (the UI thread finished the search before the script thread started waiting), and `findOne(timeout)` never timed out while the UI thread was blocked; the wait is now bounded by the query's timeout and cannot miss the wake-up
* `Fix` `UiObject.clickBounds()` prepared a Looper on the script thread and called `quit()` once the gesture finished, which quit the script thread's own event loop: `setTimeout` and other callbacks never ran afterwards and the script ended early; nodes obtained from a query now tap through the script's gesture dispatcher (the servant Looper), and nodes from other sources (such as the `source` of an `auto.registerEvent` callback) wait for the result on a shared callback thread without touching the calling thread's Looper; on the main thread (for example in a `"ui"` script) the call failed because the main Looper may not quit
* `Fix` The `pickup` / `detect` result type `['compass', 'p']` always failed with "unknown result type" because `compass` was listed with a `String` parameter in the interceptor table while its signature takes `CharSequence`; it works now
* `Fix` When the layout inspector generated code for a child of a collection widget without an action selected, the generated `forEach` block contained the invalid statement `target.;`; the line is omitted now
* `Fix` Text and controls in layout inspector floating windows and dialogs had insufficient contrast with some theme colors or in night mode
* `Fix` Incomplete escaping of quotes, backslashes, and control characters in Layout Inspector "Copy as selector" and selector string output
* `Fix` Layout Inspector code generation ignored text-setting and some query actions, expanded a single list target into actions on the whole group, used deprecated query calls, silently fell back from Flow or failed on long presses, and located same-named IDs across packages incorrectly
* `Fix` Of several overloads with the same name on a widget collection array only the last one survived (such as `performAction(action)` next to `performAction(action, ...args)`), so calling it with the other number of arguments failed with a parameter mismatch; all overloads are bound per name now
* `Fix` Since 6.7.0 Alpha13 a plain string passed to the `contains` selector family (`textContains` / `descContains` / `contentContains` / `idContains` / `classNameContains` / `packageNameContains` etc.) was compiled as a regex pattern (`textContains(".")` matched any non-empty text, `textContains("1+1")` matched "11", `textContains("(")` threw); it is a literal substring again, consistent with `text(s)` / `textStartsWith` / `textEndsWith`; pass a regex (`textContains(/re/i)`) or use `textMatch` / `textMatches` for patterns, and a string in the form "/re/flags" is no longer treated as a regex by `contains`
* `Fix` `requestFocus()` / `disableFocus()` of floating windows (`floaty.rawWindow` / `floaty.window`) updated the layout of the attached window right on the script thread and occasionally threw `CalledFromWrongThreadException`; they now run on the UI thread (like `setSize` / `setPosition`)
* `Fix` The accessibility notification observer (behind `events.observeToast()` and the like) treated every system notification as another app's toast (its notification check named this app's own subclass, which never matches), so notifications never reached its notification listeners and other apps' notifications were misreported as toasts; it now checks the framework's `android.app.Notification`
* `Fix` The call order of `console.setPosition` / `setSize` / `setGravity` and `console.show()` affected the result (e.g. `console.show(); console.setPosition(500, 400)` needed a `sleep` in between, and the reverse order could drop the setting); settings are now recorded and applied once the window is attached, so order and timing no longer matter
* `Fix` Dragging the floating console by its title bar occasionally failed (the drag gesture was bound before the window was attached and raced with button clicks and window closing); the title bar drag is now set up after attachment, the movement is clamped to the screen and the position is recorded when the drag ends
* `Fix` The floating console had a 12dp margin on every side, so `console.setPosition(0, 0)` could not reach the screen edge; the margin is removed, the Y coordinate starts below the status bar by default (disable with `setAvoidStatusBar(false)`), and both the position and the size are clamped to the screen
* `Fix` The MCP Server session controller could publish the `starting` snapshot pulled right after `openServer` on top of a `running` status the plugin had already delivered through its callback, so `status` never reported the listener as running; the pulled snapshot is now published only when no callback status arrived while it was in flight
* `Fix` The side drawer kept showing outdated MCP Server guidance such as "not installed" after the plugin was installed, enabled or updated, until AutoJs6 was restarted; an installed plugin that Android has not activated yet now shows "Activation required" before the switch is used
* `Fix` MediaInfo details and snapshots show the original source path in Complete name instead of a private cache or descriptor path, while preserving the snapshot display filename
* `Fix` Node bridge `accessibility.swipe` / `accessibility.gesture` (the coordinate gestures of the MCP Server and Node plugins) answered `false` (cancelled) for every gesture after the first one on a reused broker dispatch thread: the gesture automator without a handler prepared a Looper on the calling thread and quit it once the first gesture completed, so the next call on that thread returned at once and its real result went to a dead handler; the bridge now hands the automator the main-looper handler, as the Python host broker does
* `Fix` OpenCV and ImageQuant plugin policies rejected native libraries built with NDK r28c (28.2.13676358) as a native NDK version mismatch, which blocked the rebuilt OpenCV plugin v1.1.2; both r26b and r28c builds are now accepted
* `Fix` The OpenCV and Image Quantization plugin loaders continued after read-only cache protection failed; native files are now verified as read-only before loading for Android 17 compatibility
* `Fix` runtime.loadDex on Android 7.0 / 7.1 could overwrite the staged DEX with ART's optimized output, causing class loading to fail
* `Fix` Dynamically loaded Kotlin compilers failing due to restricted Unsafe atomics or a missing reflection runtime
* `Fix` Legacy Rhino scope reflection APIs preventing external libraries from automatically registering Java/Kotlin classes
* `Fix` MediaProjection bridge captures retry transient missing frames up to six times, allowing consecutive screenshots after the capture pipeline refreshes without requesting consent again
* `Fix` OpenCV matrices behind `images.imread`, `images.matToImage` and Mat-backed images were either never released or shared a native header with a source that could be garbage collected, causing native memory leaks and dangling pointers
* `Fix` A finishing script recycled the images of every other running script; images are now registered and recycled per script runtime
* `Fix` `colors.isSimilar` and `images.detectColor` with the `hs` algorithm matched any color, and `colors.rgb` / `colors.rgba` treated fractional or percentage components as integers
* `Fix` `images.isEqual` returned true for images differing in a single channel, `images.mse` saturated at `255`, and `images.ncc` / `images.isGrayscale` mishandled grayscale or RGBA input
* `Fix` `images.invert` dropped the alpha channel, `ImageWrapper.saveTo` wrote Mat-backed images with swapped red and blue channels, and `images.clip` silently produced wrong results for regions outside the image
* `Fix` Images passed by path to `images.clip`, `images.findCircles` and `images.readPixels` could be recycled before use, and `images.findImage` / `images.matchTemplate` could throw or return wrong matches for templates near the image border or larger than the search region
* `Fix` `images.load` applies a 30-second timeout and closes its connection, and `images.saveBitmap`, feature matching and histogram comparison release their temporary streams and matrices
* `Fix` Template matching returned coordinates rounded to a coarse pyramid level (off by up to several pixels) once a match reached the threshold early, could report the same location twice for neighbouring candidates, and returned results unsorted and beyond `max`
* `Fix` `images.matchTemplate` threw for an image and template with different channel counts, never matched a solid-color template, and `useTransparentMask` masked only the alpha channel instead of the transparent pixels; `MatchingResult.sortBy(compareFn)` failed when the comparator returned a non-integer number
* `Fix` `images.matchFeatures` returned a wrongly sized frame when the object features were computed with a `scale` other than 1, re-sorted the corners so a rotated object lost the correspondence to its own corners, could return a distorted frame from an unreliable homography, drew the `drawMatches` image from the descriptor matrices instead of the images, and threw an OpenCV error for images without key points
* `Fix` `images.detectAndComputeFeatures` leaked its intermediate matrices, and ORB found no key points at all in images smaller than 63 px and only a few in small objects because of the default 31 px border
* `Fix` The MCP Server session controller rebound the plugin about one second after the user force-stopped it in the system settings, which started it again against that decision; a plugin that is no longer available after its process died (stopped, disabled or uninstalled) is not rebound any more, the drawer switch closes and its subtitle shows the activation guide the way a fresh install does
* `Fix` Version history now fingerprints the content after a save, so the next save of a renamed or moved file re-attaches to its existing history; re-attaching by fingerprint only happens when the old path is gone, so an identical copy no longer takes over the original file's history
* `Fix` Trash and version history resolve the internal storage root from the current user's actual path instead of a fixed /storage/emulated/0, so they work in multi-user and work profile environments
* `Fix` Restoring a version to disk, or restoring a file from the trash with overwrite, first records the target's current content as a RESTORE_PRE revision; trash overwrite restores write to a temporary file and replace the target afterwards, so an abort or crash no longer leaves a half-written file
* `Fix` Auto rename on a trash restore conflict decides directories by the item type, so a directory with a dot in its name (such as my.project) no longer has an extension split off
* `Fix` The version history dialog lists every retained revision (previously at most 20), offers Preview from the editor entry as well, and keeps its mode after a revision is deleted and the list reloads
* `Fix` Orphan blob cleanup for version history now protects blobs younger than 30 minutes, so a fresh snapshot whose database row is still pending is no longer deleted
* `Fix` Files deleted on the PC remained on the device after saving a project from the VSCode extension, and stale files in the cache directory kept reappearing _[`vscode-ext issue #29`](http://vscext-project.autojs6.com/issues/29)_
* `Fix` Receiving a project archive from the VSCode extension converted the whole byte array to a string for logging, which could crash with OOM _[`issue #523`](http://issues.autojs6.com/523)_
* `Fix` A single half-dead VSCode extension connection blocked the log writer thread so that new connections received no logs _[`issue #385`](http://issues.autojs6.com/385)_
* `Fix` Version history stayed at the old path after a file or directory was renamed or moved in the explorer; it now follows the new path (directories rewrite every tracked path beneath them), and when a move overwrites a tracked file both histories merge into the destination path
* `Fix` Restoring a single trash item reported "done" even when the restore had failed because the progress dialog swallowed the exception; the actual error is now shown
* `Fix` Explorer operation messages shown from the main thread (such as "Moved to trash") appeared twice
* `Fix` When restoring a directory from the trash, archive entry names are validated against the destination's canonical path, so entries containing ".." or absolute paths can no longer be written outside the destination (such entries are skipped and logged)
* `Fix` Scripts sent by the VSCode extension (TypeScript, Lua, Node.js, Bun, QuickJS, Python) were all executed as Rhino JavaScript; they are now routed to the engine matching the file name
* `Fix` Handshake rejection or connection failure dialogs of the VSCode extension connection could misbehave once the Activity was destroyed (language or dark mode change); they fall back to a toast
* `Fix` Client mode server address history was only written on a failed handshake and never for a successful connection, so the "History" of the address dialog stayed empty
* `Fix` When picking the APK for an update, a release without an asset for the first preferred ABI could pick a later lower-priority asset in the list (such as the universal one) instead of the asset of the next preferred ABI
* `Fix` The built-in package installer could reject V2/V3-signed APKs on Android 9-11 with "Base APK has no verifiable signer" because the platform archive parser left the signing info empty; the deprecated archive signature view is now consulted as a fallback
* `Fix` The five Three plugins use transparent icons in Plugin Center that follow the host light or dark theme without launcher backgrounds
* `Fix` Package inspection recognizes APK split sets in ordinary ZIP files, including equivalent containers without a known package extension
* `Fix` 3-Setup Installer confirmation screens not opening on Android 14 and later when installation is started while AutoJs6 is in the foreground
* `Fix` Package manifest roots and shared UIDs could be misread from comments or spoofed namespaces; duplicate attributes, conflicting resource identities and unverifiable shared UIDs now stop processing
* `Fix` Plugin Center could restore removed or renamed plugins from cache after a successful index refresh
* `Fix` Missing circular icon backgrounds in Plugin Center details dialogs and stale themed borders when cached artwork is reused
* `Fix` Material colors chosen in the legacy layout could not be found with "Locate current theme color" after switching layouts, and the Material colors page did not show the current color summary
* `Fix` Opening plugin details right after a cold start of the Plugin Center could make the dialog flash because each list refresh rebuilt it
* `Fix` Plugin Center kept a plugin disabled after a refresh found it temporarily unreachable (for example during an update or while the system blocked binding); the plugin is now switched back on once it can be reached again, while plugins switched off by hand stay off
* `Fix` Packaged UI scripts occasionally displaying a blank page and exiting during startup
* `Fix` Compose UI rejects mounting another page or floating window inside a render callback and keeps the current page; pages can be mounted again after plugin updates
* `Fix` Component factories treated the first positional node as a property object when several node arguments were supplied
* `Fix` Compose node property getters preserve null values, including default progress and cleared DatePicker selections, instead of returning undefined
* `Fix` With Paddle OCR v3 and a v4/v5/v6 plugin both enabled, packaging selected the latter and Paddle OCR did not work in the packaged app; packaged apps now accept only the PP-OCRv3 plugin and no longer copy the v4-v6 models and native libraries they cannot run
* `Fix` APK packaging silently kept one of two same-named native libraries with different content; it now stops and names both sources
* `Improvement` The Standard plugin installation profile includes 3-Setup Installer, and system APK installation no longer displays the installer-plugin recommendation tip
* `Improvement` Three Stove Agent model payload reading on Android 7-10 uses public APIs while preserving file offsets and cancellation of stalled pipes
* `Improvement` Host lint checks exclude AutoJs4 compatibility classes and resources by path while continuing to check the rest of the host
* `Improvement` Plugin Center writes an explicit enable record and runs the enable probe after observing a fresh installation of a default-off plugin (YOLO, MCP Server) signed with the host's official certificate; trusted third-party or user-granted signers, adb sideloads and updates still require manual enabling, and runtime consumers keep reading only the saved record
* `Improvement` Accessibility, client mode and server mode managers share section expand/collapse buttons and dividers; fully collapsed settings open in a separate dialog through a gear button, and collapsed client controls include address history; collapsed server controls also provide Disconnect all clients
* `Improvement` Time visibility and format are remembered independently for the editor log panel, log activity, globalconsole element, console element and floating console; time is shown by default on the two log pages and hidden on the other three surfaces
* `Improvement` Task lists and script start/finish logs consistently abbreviate known roots as $cwd, $sdcard, $sample, $files, $cache and $data while retaining actual entry names and extensions; directory matching respects component boundaries and trailing separators
* `Improvement` The MCP Server drawer entry uses the MCP protocol icon with slightly thicker strokes, with size and tint matching the other entries
* `Improvement` Flow errors retain asynchronous task methods, script files and line numbers in flowStack and stack, and console.error displays the diagnostic stack automatically
* `Improvement` Flow repeatUntil maxAttempts defaults to 0 for unlimited attempts within the total timeout
* `Improvement` Stability waits compare ordered node collections and immutable snapshot projections, with subflow deadline and cancellation handling
* `Improvement` Android 17 local network authorization moves to plugin-center enablement and plugin settings, with no launcher permission page; missing permission keeps the plugin disabled and automatic startup silent
* `Improvement` Explain Advanced Protection in the host and companion, showing the global mode separately from actual service availability and secure settings control
* `Improvement` Packaged apps request storage access only when the project requires shared paths; permission denial, settings return, hidden launcher recovery and boot startup use the actual access state
* `Improvement` media.playMusic uses a foreground music service with audio focus, notification controls, script ownership, and cleanup when the script exits
* `Improvement` Foreground service help explains Android 17 background audio restrictions and how to restart the service from the visible app before retrying playback
* `Improvement` Make the Screen Color Picker plugin's Activity launch delegation explicit, using sender options tied to host visibility on Android 16 and later
* `Improvement` LAN connection recovery hints for HTTP, WebSocket and the Node controlled network bridge preserve original errors and explain retry after authorization; generic permission-name conversion is independent of the system language
* `Improvement` Rename the home drawer group from "Connect to PC" to "Remote connections" to cover client mode, server mode and MCP Server
* `Improvement` Raise project compileSdk to 37 (Android 17) and improve debug builds and lint verification for SDK migration
* `Improvement` Raise the targetSdk of the app and packaged apps (inrt) to 37 (Android 17); packaged apps move from targetSdk 29 to 37, local network permission is requested on demand on Android 17 and packaged apps guide shared storage access when needed
* `Improvement` Unify package inspection at 262144 archive entries and raise package, nested scan, manifest, and metadata budgets for large APK/AAB/APKS files
* `Improvement` Information dialogs share a reusable item component with key:value insertion at any position, automatic key column alignment, and cancellation of asynchronous reads on dismissal
* `Improvement` APK/APKS/XAPK/APKM/APKZ/AAB component information moves to the "More information" dialog in the top-right menu, sharing the main dialog item style and updating as metadata is loaded
* `Improvement` APK and other package information dialogs move the APK Inspector detailed summary into the top-right menu, above "More information"
* `Improvement` Node bridge accessibility module adds dump (text, JSON, or XML window snapshots bounded by 400 nodes, 32 levels, and 256 KiB), explain (per-filter hit counts of a selector descriptor), and screenshot (accessibility screenshot on Android 11+ with scale, region, format, and quality options, delivered as a one-shot cache file or a workspace file), and the app module adds currentWindow (current package, activity, and window list)
* `Improvement` Node bridge adds a files module (list, stat, read, write, mkdir, rename, delete) rooted at the working directory: paths must be normalized relative paths, symbolic links cannot lead outside, reads and writes are capped at 1 MiB with UTF-8 or base64 content, listings at 2000 entries, write and delete require the dedicated files.write and files.delete capability tokens, and the workspace file manager refreshes after changes
* `Improvement` Node bridge adds a console module (console.tail reads the newest entries of the global console with lines up to 500, sinceId, level and a 256 KiB budget), engines.execScript and execScriptFile accept captureConsole and waitMs options (wait for the script to finish and collect the console output of its id window, captureMode global-window), the console keeps at most 4096 log entries (oldest dropped, the console view refreshes by entry id), and ConsoleImpl exposes addLogListener and removeLogListener
* `Improvement` Node bridge adds host-process semantics for engines.list, engines.stop(id) and engines.stopAll with scope host (list and stop executions of the host ScriptEngineService while the existing engines.all / stopAll semantics stay unchanged), device.info (model and system version, screen size / density / orientation, battery, host version, accessibility service state), and the read-only app.listSamples / app.readSample (enumerate and read the built-in sample scripts, paths confined to the sample root, 1 MiB per read)
* `Improvement` The `TypeScript console` now stays collapsed by default and appears on demand from the full Log page or editor Log panel overflow menu only when the compiler plugin is available, with visibility remembered independently; its green Run action becomes a red Stop action while a snippet is active, the full Log page shows the actual working directory, and the editor Log panel overflow menu also consolidates Copy, Send, and Export
* `Improvement` TypeScript TSX runtime validation messages now follow the app language across all 10 supported locales instead of exposing hardcoded English
* `Improvement` Ace font preview now shows a download-required hint above the multifunction keyboard while the selected font is unavailable, and hides it after download or when switching to an available font
* `Improvement` The code editor Jump to line dialog now immediately replaces input above the document's maximum line with that maximum
* `Improvement` Node.js controlled Fetch request and response bodies and outgoing WebSocket messages support file descriptor transport, reducing large binary payload overhead across Binder
* `Improvement` Removed the obsolete Node.js Runtime legacy TypeScript stripping request flag; raw TypeScript remains compiler-only and is never regex-erased by the runtime
* `Improvement` Removed host-side Node.js Gradle switches and duplicate examples, type declarations, project wizard, and verification scripts; runtime defaults and development assets are now owned by the Node.js Runtime plugin
* `Improvement` DEX compiler diagnostics now preserve bounded redacted INFO/WARNING/ERROR details with optional logical origin, archive entry, and position metadata; Developer options show the process-local last JAR route and localized BUSY, timeout, and fallback guidance
* `Improvement` TypeScript compilation now emits privacy-safe per-request observations for preparation, cache lookup, compiler binding/prewarm, remote compilation, output handling, total latency, archive size, source count, and host heap change, and breaks compiler time down into before-ingress, ingress, inter-phase, parse, semantic, emit, and after-emit intervals; malformed or out-of-order progress callbacks are ignored and these observations do not impose performance thresholds
* `Improvement` Improved argument compatibility for KeyCode methods _[`issue #492`](http://issues.autojs6.com/492)_
* `Improvement` KeyCode methods support sending key code commands with Shizuku permission
* `Improvement` Deferred initialization of non-critical components to improve app startup performance
* `Improvement` The app no longer shows permission request dialogs on startup; when all files access is missing, the Home page shows a reminder that can grant it directly
* `Improvement` Cached compilation results for bundled Rhino resources and metadata for runtime enhancement functions to improve script initialization performance
* `Improvement` Synced the Rhino engine with upstream, adding new.target support and improving scope, TypedArray, and debugger compatibility
* `Improvement` Improved formatting performance for large arrays in the Rhino console
* `Improvement` Refactored the Kotlin module augmentation architecture with declarative mount specifications supporting multiple aliases and independent `$` prefix control per name, explicit Rhino realm binding, separated tasks and work_manager semantics, and expanded module compatibility and semantic regression tests
* `Improvement` Reused the persistent Node.js process and process-scoped runtime to improve warm script startup performance
* `Improvement` Added phase-by-phase Node.js startup timing and trustworthy cold/runtime-warm performance reporting
* `Improvement` Cached built-in Node.js native library resolution to avoid repeated plugin discovery
* `Improvement` Cached authorized external Node.js runtime candidates and persistent Binder sessions with state invalidation and atomic reconnection to reduce discovery and bind overhead
* `Improvement` Removed project-wide encrypted-module scans from the default Node.js execution path to improve startup performance for non-trivial scripts and large projects
* `Improvement` Added real multi-round Rhino/Node engine-switch regression coverage for script execution, persistent external-plugin session reuse and recovery, and clean teardown
* `Improvement` Added persistent external-plugin runtime soak testing and Node.js production-evidence gates covering device/ABI matrices, runtime lifecycle, resource metrics, and 8-hour/24-hour stability
* `Improvement` Updated Node.js native verification to resolve and validate the external runtime plugin source and build roots
* `Improvement` Made Node.js final integration and release gates fail closed when required reports, current-source evidence, or production acceptance artifacts are missing or stale
* `Improvement` Disabled legacy regex-based TypeScript stripping by default for raw and dynamic Node.js inputs, requiring compiler output unless a temporary request-scoped compatibility flag is explicitly enabled
* `Improvement` Mapped computed Node.js TypeScript import() specifiers from .ts/.mts/.cts to their precompiled .js/.mjs/.cjs modules within the closed compilation snapshot, with stable errors for missing, ambiguous, or escaping targets and no late TypeScript source fallback
* `Improvement` Standalone Python scripts now append a safe console hint for an exact ModuleNotFoundError, directing sibling/local module imports to an explicit Python project with `project.json` and `type: python` without exposing private paths
* `Improvement` Python Runtime output chunks are written to the Host console as they arrive, with independent incremental UTF-8 decoding for stdout/stderr preserving code points split across chunks and a 16 MiB end-to-end output limit
* `Improvement` Python project.json supports a positive-integer timeout in milliseconds, defaulting to 5 minutes, capped at 30 minutes, and still bounded by the Runtime plugin limit
* `Improvement` Python project.json supports explicit module entry through "entryMode": "module" and a normalized dotted main name, enabling package-relative imports with standard Python module semantics
* `Improvement` Raise Python project workspace limits to 64 MiB compressed, 8192 file entries, and 128 MiB extracted, and match the snapshot's actual three-dimensional requirement against Runtime Provider capabilities before dispatch to support project-local pure-Python dependencies
* `Improvement` Python project.json supports finite stdin snapshots as inline text or an explicit project-relative file, allowing sys.stdin.read() to consume pre-supplied input without interactive prompts
* `Improvement` Modularized functionality related to ML Kit OCR/ML Kit Barcode/Rapid OCR/OpenCV/Image Quantization/OpenCC/Pinyin/Pinyin4j/MediaInfo as plugins
* `Improvement` Consolidated file manager media detail queries into a single MediaInfo snapshot to reduce Binder calls and wait time
* `Improvement` Limited external file viewing and media playback fallbacks to read-only URI access
* `Improvement` Unified file manager plugin actions for default clicks, primary buttons, media detail playback, and the overflow menu under an explicit main-file-page policy with one deterministic fallback
* `Improvement` Archive rows use view and information icons, archive browsing removes right-side path-bar actions, selected-entry extraction offers a destination picker, and plugin context menus omit plugin-name prefixes
* `Improvement` Explorer Action v7 lets archive plugins reopen hidden pending output read-only and verify it before final publication while preserving direct-commit compatibility for v4 clients
* `Improvement` Explorer Action v12 verifies caller UID, regular-file identity, size, modification time, symlink status, and session lifetime before each sibling-volume open, with fixed limits on names, catalog size, and open requests
* `Improvement` Native archive selection now shows only Exit and Extract, integrates bounded progress and cancellation, and keeps the five-action filesystem bar legible on narrow screens by centering labels below icons
* `Improvement` Preserved the file manager's established compact path bar, list, and five-action selection-bar geometry while retaining accessibility labels, button roles, selection state, and TalkBack announcements; plugin launch also validates Explorer Action request sizes fail-closed
* `Improvement` Moved the AI and DEX compiler conformance providers from the production `plugins` directory into the dedicated `test-apps` build group, preserving hostile Binder regression coverage while keeping independent production plugins as a separate release boundary
* `Improvement` DEX compiler experimental host path remains default-off and requires an explicit component while enforcing exact-component UID and complete signer-set pinning, single-dispatch fallback, semantic cache provenance, bounded callback ingress, isolated Binder deadlines, structural DEX ZIP validation, reliable-pipe bounded draining with peer errors checked at EOF, rollback-safe single publication guarded by source and destination digests, and atomic arbitration between asynchronous termination signals and the publication commit point that forces rollback when cancellation, timeout, or Binder death wins before commit
* `Improvement` DEX compiler R1.1 keeps the experiment default-off and lets raw runtime.loadJar JARs prefer one explicitly selected same-signed exact-component provider, with one built-in D8/dx fallback on failure, no fallback on cancellation, and AAR loading kept on the built-in path
* `Improvement` DEX compiler R1 is complete while remaining default-off and exact-component opt-in: R1.1 production loading is 7/7, R1.2 automation is 4/4, R1.3 real-provider matrix is 7/7, and exit conditions are 3/3. API 34 production routing passed 8/8, host DEX 16 suites/149 tests, wire 4/24, fake-provider 5/25, and plugin 48 tests with lint 0 errors. Canonical campaign f3c2b1af-be93-41e7-b541-f167f90e5cc1 passed API 24/25 via D8 CLI, API 26/28/34/36 via D8Command on x86_64, and API 31 on the QV arm64 multi-user device; earlier failed or intentionally terminated campaigns remain preserved. Last-waiter cooperative cancellation and broader recovery remain unchecked R2 work
* `Improvement` Experimental DEX compiler conformance now exercises real Android Binder transport and artifact storage, exact signed components, hostile callback/output/timeout scenarios, target-aware DEX validation, and DexClassLoader execution
* `Improvement` Default-off pure AI provider host policy provides deterministic trust selection, exact identity pinning and revalidation, host/provider/target capability intersection, ambiguous-candidate rejection, plugin-managed credential isolation, diagnostics without sensitive content, deterministic terminal ownership, verified tool and descriptor JSON, aggregate streaming length/SHA-256/quota enforcement, duplicate decoded JSON-key rejection, and domain-separated binding keys
* `Improvement` The connected AI Provider V2 host path combines exact-component PackageManager discovery and Binder metadata negotiation with paged `listTargets` coordination, per-dispatch and callback identity reinspection, single-use catalog tokens, immutable credential-free target planning, bounded session/PFD transport, streaming credits, process fuses, one terminal owner, and idempotent cleanup for explicit `ai.*` plugin routes
* `Improvement` Modularized the Ace WebView runtime/JavaScript bridge/built-in language services/static assets as a separately installable plugin, reducing host APK coupling and allowing independent updates
* `Improvement` Modularized offline project documentation as a plugin, reducing the host APK size and supporting independent installation and updates
* `Improvement` Moved offline project documentation inventory parsing and full SHA-256 verification on first load to a background thread to avoid blocking the UI
* `Improvement` Moved the complete AXML/ARSC/parsing/signing/keystore/cryptography toolchain required for APK builds into the APK Builder Template plugin, with lightweight APK inspection in the host
* `Improvement` APK Builder plugin discovery now validates the installed certificate SHA-256 before any Binder connection, blocks untrusted signers with the observed package and fingerprint, and keeps the official signer path unchanged
* `Improvement` Packaged apps are always built by the APK Builder plugin on the device; the experimental remote build developer switch and its plugin build flag are removed
* `Improvement` Move the approximately 1.3 MiB full AutoJs6 completion index out of Ace's synchronous first-presentation path, loading it during idle time after the first visible code frame and seamlessly replacing the bootstrap index; all five Android 9-15 environments restored 400 globals and 80 modules
* `Improvement` Plugin Center checks OCR service classes before startup and reports enable failures promptly, with a 5-second handshake deadline and cancellation of abandoned bindings
* `Improvement` Code editor engine selection respects explicit user choices and provides access to Plugin Center when Ace is unavailable
* `Improvement` Code editor "Run" button changes to "Stop" after being tapped, allowing scripts to be stopped quickly
* `Improvement` Reorganized the code editor action bar and menu hierarchy, making Log a persistent action while improving the placement of File encoding and Read-only mode
* `Improvement` Improved code editor interactions for themes/fonts/toolbars/read-only mode/folding/selection menus and more
* `Improvement` Improved code editor file-encoding detection and save safety; encoding conversions are written to disk only when the user saves the file
* `Improvement` Further reduced the code editor information bar height and horizontal spacing to free more space for the code area
* `Improvement` Bottom log panel supports jumping to source code by clicking stack frames on the standalone log page
* `Improvement` Switching the project documentation source takes effect immediately
* `Improvement` File manager decodes and formats the complete binary AndroidManifest.xml in the background only after the Manifest button is tapped, with optimized large-text scrolling and pinch zoom performance
* `Improvement` File manager APK details show the maximum SDK only when explicitly declared in the manifest, and rename Install to Exception while showing it only when an exception exists
* `Improvement` Improved file manager Markdown previews with securely sanitized raw HTML commonly used on GitHub, HTTPS remote images, narrow-screen table scrolling, and compatibility with legacy hidden comments
* `Improvement` Improved the style and functionality of the Plugin Center "Sort" and "Filter" dialogs
* `Improvement` Plugin Center lists common distribution variants by default and provides a separate page for other variants
* `Improvement` Improved multilingual changelogs/language caching/official verification/index loading and other Plugin Center features
* `Improvement` Plugin Center renders a lightweight local snapshot and cached index first, then refreshes plugin metadata and the online index in parallel; abnormal plugins remain disabled and show their actual failure with recovery guidance when enabling is attempted
* `Improvement` Standardized multilingual translation coverage/file organization/name ordering/ASCII punctuation for project string resources
* `Improvement` Limited APK language resources to the 10 locale configurations supported by the project to reduce package size
* `Improvement` Build system supports dynamic Kotlin/KSP/AGP/R8 compatibility matching and reduces Gradle startup memory pressure
* `Improvement` Eliminated Kotlin compiler warnings from Java nullable return values, redundant expressions, and Android deprecated APIs while retaining compatibility with older Kotlin versions
* `Improvement` Finalized the unpublished official AI plugin as 3-Stone AI while replacing its cross-process contract with neutral `ai-provider-api`, `org.autojs.plugin.ai.provider.api`, `org.autojs.plugin.AI_PROVIDER`, new Binder descriptors, the AP schema domain, and renamed conformance fixtures; no identity aliases are retained
* `Improvement` Implemented AI Provider V2 paged target discovery directly: every target carries stable target/provider/model identity, locality, configuration and availability, capabilities, limits, controls, and allowed HTTPS origins; REMOTE/HYBRID execution and plugin-managed credentials are supported without credential bytes crossing Binder, and no superseded wire aliases remain
* `Improvement` File manager directory refreshes dispatch minimal list diffs, keeping unchanged rows bound and preserving the live scroll position during in-place refreshes
* `Improvement` Refactored the file manager around explicit list layout, row presentation, selection toolbar, archive workflow, input capability, output reservation, and recovery components, reducing oversized view/session classes and making navigation, security boundaries, and action policies independently testable
* `Improvement` TypeScript compiler binding and bootstrap now run during a bounded startup prewarm instead of the first user compilation; revision-1 project sessions retain one live binding after editor or compile use, while idle startup expiry, memory pressure, failures, and app exit safely fall back to cold compilation
* `Improvement` The scraper suite now restores 9 independent version tasks and 5 host-only tasks, plus 1 cross-repository sync task; AutoJs6-Gradle-Platform-Versions centrally exports version-map-validated JSON metadata for the stable Android Studio release
* `Improvement` Upgraded Gradle 9 Wrapper version 9.6.1 -> 9.7.1
* `Improvement` Gradle platform compatibility data and its updater tasks moved to AutoJs6-Gradle-Platform-Versions; AutoJs6 now consumes version 1.5.0 from Maven Local directly, without local data overrides, duplicate scrapers, or the former Puppeteer dependency
* `Improvement` HTML and Markdown previews are delegated to dedicated Previewer plugins; the main app keeps the entry and guides installation or enabling when the plugin is missing, disabled, or unavailable
* `Improvement` Consolidated all Pinyin calls through the corresponding plugin and removed the unused built-in dictionary and conversion implementation
* `Improvement` Starting or stopping the accessibility service through root or secure settings no longer writes the system's `accessibility_enabled` value (maintained by the system itself from the bound services since Android 7.0), so stopping this service no longer leaves a stale 0 behind while other accessibility services stay enabled
* `Improvement` Floating windows such as the floating button with its dialogs and the layout inspector now follow AutoJs6's own language and night mode settings instead of the system's
* `Improvement` The floating button "More" menu opens without waiting for foreground app queries, loads information in the background and refreshes only the affected subtitles; package and Activity share one query, and dismissing the menu cancels pending loads and discards late results
* `Improvement` The `source` of the event object received by `auto.registerEvent` callbacks is now resolved on first access (every event used to walk the parent chain eagerly for its depth and index), which lowers the cost of callbacks that never read it; below API 33 a `source` that was not read during the callback is null afterwards
* `Improvement` Accessibility window root resolution now has a single implementation (`AccessibilityBridge` rewritten in Kotlin, its three duplicated code paths unified in `WindowRootPolicy` / `WindowRootProvider`, which the layout inspector uses for its windows as well): with a window filter set, `auto.root` moves on to the next window when the first passing window has no root (it used to return `null` right away); `auto.windowRoots` and node actions can no longer meet a `null` root; the fact that `auto.rootInActiveWindow` ignores the window filter is now stated explicitly in the sources
* `Improvement` Accessibility node recycling is now decided in one place, `NodeLifecycle` / `NodeRecyclePolicy` (nodes need no recycling from Android 13 on, so the no-op `recycle()` is no longer called for each of them; the inline recycling formerly scattered over the search algorithms, scroll actions, the service's cached root and the allocator all goes through it, and a failing recycle is logged instead of aborting a search)
* `Improvement` Accessibility screenshots (`automator.captureScreen()`) are taken by the new `A11yScreenshotter`: the hardware buffer behind a screenshot is now always closed (only the wrapping bitmap used to be recycled), and the retry on the system's rate-limit error is capped at 60 attempts (3 s), after which the promise resolves to `null` like for any other failure (it used to never resolve while the system kept rate limiting)
* `Improvement` Selector filters, the search algorithms (DFS / BFS) and the compass walk (`compass`) now work against the Android-free `UiNode` interface (implemented by `UiObject`), so all three are covered by JVM unit tests over an in-memory node tree; the script-facing API and behaviour are unchanged
* `Improvement` Selector properties are now described once in the declarative `SelectorRegistry`: the `UiSelector` builder methods are generated from it, map selectors (`pickup({...})` etc.) are parsed by it instead of by reflection, and the global selector functions are exported from it plus an explicit whitelist (`toStringReadable` and the mangled names of `internal` members are no longer exposed as globals); numeric arguments of global functions and map selectors are uniformly rounded to integers (map selectors used to truncate)
* `Improvement` Selector filters deduplicated: the string properties (text / desc / content / id / className / packageName and their variants) share `StringPropertyFilter`, the bounds metrics (left / boundsLeft / minLeft / maxLeft etc.) share `MetricsFilter`, the integer properties share `IntFilter`; the screen-relative filters (bounds / metrics / screenCenter / screenCoverage) evaluate against an injectable screen size and are covered by JVM unit tests; a selector evaluates a snapshot of its filter list, so one selector shared between threads is safe; the script-facing API and behaviour are unchanged
* `Improvement` Selector queries (`findOne(timeout)` / `untilFind` / `untilFindOne` / `exists` / `find(max)` and the deprecated `findOne()` / `waitFor()`) run as a polling plan (`PollPolicy`: timeout, interval, times, backoff) with unchanged defaults (50 ms interval, a timeout of 0 or less waits forever); the methods that wait without limit are marked "may block forever" in their KDoc; with the `findOnUiThread` flag set, a search posted to the UI thread waits at most the query's timeout and fails with a readable error once it passes (`findOne(timeout)`), while unbounded queries (`untilFind` etc.) wait interruptibly so that stopping the script ends them
* `Improvement` `UiObject` split up: the alias table of the `pickup` / `detect` result types, the picker and the interceptor that calls node methods by name moved to `core/automator/pick` (`PickResultType` / `Picker` / `Detector` / `Invoker`), `summary()` / `toString()` / `isSimilar()` to `UiObjectSummary`; new `UiObject.fingerprint()` (a hash of bounds, text, description, class name, child count and the key boolean properties, without visiting siblings or children); `indexInParent()` of a node reached through `parent()` is computed on first use; the layout inspector's code generator (`CodeGenerator` / `ReadOnlyUiObject`) converted to Kotlin, the latter now a snapshot view implementing `UiNode` directly; the script-facing API and behaviour are unchanged
* `Improvement` Cross-runtime reuse (roadmap P5.4): the Python host's selector capabilities (`selector.snapshot` / `selector.find` / `selector.click` / `selector.set_text`) and the Node Bridge's `accessibility` module (`findOne` / `findAll` / `click` / `longClick` / `setText` / `scrollForward` / `scrollBackward` / `findByText` / `clickText`) now consume the script runtime's accessibility core (the `UiNode` walk, the selector filters, the window root provider, the node lifecycle), both JSON wire protocols staying identical field by field; Node Bridge searches no longer leak the nodes visited on the way below Android 13; the Python host's accessibility screenshots take their single attempts through the shared `A11yScreenshotter` (keeping the host's bounded retry and 4-second deadline)
* `Improvement` Every script runtime now owns its console (its own log entries and floating window, with every line forwarded to the global console); the floating window only shows the output of the current script (as in Auto.js 4) and re-running a script with the same title replaces the floating window left by the previous run; log entries store the raw content and a timestamp and each view renders the time prefix on demand (hidden by default in the floating window, switchable in the log page settings); Node bridge `console.tail` entries gain a `time` field (epoch milliseconds) while `text` keeps its time prefix
* `Improvement` Screen OCR examples request Android capture consent and recognize real image handles; unavailable OCR or barcode plugins return readable unavailable errors, while recognition failures remain errors
* `Improvement` Node scripts support negotiated asynchronous startup, releasing Binder threads during long-running execution and returning workspace changes after terminal completion, with legacy synchronous host compatibility
* `Improvement` Node bridge `accessibility.dump` JSON nodes carry the state flags `scrollable`, `checkable`, `checked`, `editable`, `focused`, `selected` and `longClickable` next to the seven descriptor fields; the MCP Server session grant includes the `keys.notifications` / `quickSettings` / `powerDialog` / `lockScreen` global actions for the plugin's `ui_press_key` tool, and a `keys.*` call without the accessibility service now answers the `unavailable` category like the `accessibility` module; a zero-length `accessibility.swipe` (identical end points) is dispatched as a press of `durationMs`, which the coordinate forms of the MCP `ui_click` / `ui_long_click` tools rely on
* `Improvement` Host and runtime-kit native libraries support 16 KB alignment, with rebuilt jackpal JNI libraries, NDK r28.2 libc++, provenance records and shared build verification
* `Improvement` APK Builder reports incompatible native libraries in build progress and results, and records alignment in local build-input metadata while preserving compressed packaging
* `Improvement` Plugin Center version summaries also normalize ISO 8601 date-time strings such as `2026-09-11T14:30:00+08:00` to `yyyy-MM-dd`, keeping the calendar date of the string's own offset
* `Improvement` Multi-color matching (`images.findPointByColors`, `images.findPointsByColors`) pre-filters candidates with vectorized OpenCV masks, running tens of times faster on dense images with identical results
* `Improvement` Color detectors expose a `distance` method shared by `colors.isSimilar`, `images.detectColor` and multi-color matching; the `hs` algorithm uses circular hue distance and unknown algorithm names raise a descriptive error
* `Improvement` Screen capturer state is read without locking, and `images.releaseScreenCapturer` no longer blocks concurrent image calls while releasing
* `Improvement` Pyramid levels of template matching use area interpolation, masked matching keeps the normalized correlation coefficient, and small `max` values still refine several candidates so the best match is not lost at coarse levels
* `Improvement` Layout hierarchy window: rows summarize id / text / desc / clickable and other flags, the selected node and all of its ancestors are highlighted in the tree and outlined on screen, the tree scrolls sideways, a "Select parent node" menu item walks the selection upwards, and a one-time tip explains exiting with the volume down key
* `Improvement` Feature matching keeps only RANSAC inliers and validates the projected quadrilateral, corrects a matcher incompatible with the descriptor type instead of throwing, accepts `scale` above 1 to enlarge small objects, and ORB detects up to 20000 key points by default so textured scenes no longer starve the object region
* `Improvement` Node bridge adds the read-only app.listDocs / app.readDoc (enumerate and read the verified offline documentation assets of the installed AutoJs6-Plugin-Offline-Docs, paths confined to the docs root, 1 MiB per read, available: false without an eligible plugin) so that the MCP Server plugin can expose autojs6://docs/ resources
* `Improvement` Retention limits for version history and trash (days, versions, per-file bytes, total bytes, tracked file size) treat zero or negative values uniformly as unlimited and share one policy object
* `Improvement` Version history skips a snapshot whose content is identical to the newest stored revision, avoiding duplicate storage
* `Improvement` Moving items to the trash checks the size limit first and, when the items exceed it, offers to move anyway, delete permanently or cancel; older entries are evicted right after the move, and the size limit always keeps the newest entry
* `Improvement` The periodic cleanup applies the complete retention policy (days, versions, per-file and total size for version history; days and size for the trash) and drops file entries that own no revisions; emergency draft limits treat 0 as unlimited as well
* `Improvement` VSCode extension connections gain an application-level heartbeat (ping/pong) and a frame length limit, releasing dead connections promptly and avoiding huge allocations on desync _[`issue #333`](http://issues.autojs6.com/333)_ _[`issue #523`](http://issues.autojs6.com/523)_
* `Improvement` Files saved from the VSCode extension can keep their relative directory structure inside the working directory _[`vscode-ext issue #37`](http://vscext-project.autojs6.com/issues/37)_
* `Improvement` The explorer records the current content of a file as an OVERWRITE_PRE revision before a copy or move replaces it; when a directory is overwritten, the tracked files inside get snapshots
* `Improvement` Each trash list item shows an expiry countdown (derived from the trash retention days, hidden when unlimited), and items expiring within 24 hours are highlighted
* `Improvement` When a trash item cannot be previewed or restored because its data file is missing or damaged, a localized message is shown instead of the raw exception text
* `Improvement` When a trash item is restored to a location other than its original path, the version history of the file (or of every file under the directory) follows it to the new path; moving a file to the trash records the version history entry it was linked to at that time
* `Improvement` A damaged directory archive in the trash no longer aborts the whole restore: readable entries are restored as usual, damaged ones are recorded and skipped (half-written files are removed), the restored / failed counts are reported at the end, and the item stays in the trash
* `Improvement` The version history file list shows the same file-type icons as the explorer; the version history and trash lists now refresh incrementally by diff, so stat / countdown / selection changes no longer redraw the whole list
* `Improvement` Scan QR code to connect: the camera uses continuous focus with metering and decoding uses TRY_HARDER, which makes aiming at an on-screen code much easier
* `Improvement` Client mode address dialog: "Scan QR code" moved from the icon at the end of the input to the "more" menu in the title bar; the scan page no longer has a flashlight button (the code is shown on a screen, which lights itself)
* `Improvement` Client mode and server mode managers: the status and control sections expand and collapse step by step like in the accessibility service manager (3 levels each, the chosen level is remembered; fully collapsed, the status title carries the connection state and the primary actions move into the control header); the "Copy" button of all three managers moved into the title bar "more" menu as "Copy information", and the "System" button of the accessibility service manager likewise as "System settings"
* `Improvement` Smaller APK: the unused HTML-to-Markdown path and unused dependencies are removed, JUnit no longer reaches the production DEX through CommonMark and Spongy Castle, and the Rhino runtime no longer bundles the JLine terminal library
* `Improvement` Update check now calls the GitHub REST API directly (OkHttp + Gson) for the latest release and its APK list, and the release-notes fallback is rendered locally instead of through GitHub's Markdown rendering endpoint
* `Improvement` Global log file writing (console.setGlobalLogConfig / resetGlobalLogConfig and the LogConfigurator / Level / LogManager globals) uses a built-in rolling file logger instead of Log4j; the documented options and the PatternLayout subset (%d / %p / %c / %C / %m / %n / %t / %r with width modifiers) are preserved, and the rest of the Log4j API is no longer available
* `Improvement` Rhino engine now skips Java methods whose parameter types do not exist on the running system (such as AppCompatTextView.setTextClassifier on Android 7.x) instead of failing reflection of the whole class; the UI mode text element uses the current AppCompatTextView on Android 7.x and the legacy appcompat 1.0.2 library is no longer needed
* `Improvement` Only the Simplified Chinese and English release histories are bundled in the app; other languages are fetched online and cached, and offline the cached copy of the current language is shown, then the bundled English one
* `Improvement` PNG bitmaps in the resource directory are converted to lossless WebP (launcher icons excluded) and a handful of unused resources are removed to shrink the APK
* `Improvement` Reduce Manifest, Java and Kotlin compiler warnings in plugin conformance apps and host tests while preserving native library packaging and Android version compatibility
* `Improvement` compact output and nodeRef references for accessibility.dump, with node relocation and stale-reference rejection
* `Improvement` accessibility.readScreenText returns bounded screen text and positions using screenshots and authorized OCR plugins
* `Improvement` Three Stove Agent capability grants allow opening notifications and quick settings while other ungranted key actions remain denied
* `Improvement` respond on ai.agent task objects accepts an array of 1 to 8 non-blank strings to answer a Three Stove Agent plan review request (input events with kind plan); text and boolean answers are unchanged
* `Improvement` When a Provider itself rejects or aborts a request (invalid request, protocol violation, descriptor or quota problems and so on), the Three Stove Agent model broker reports the stage as a closed PROVIDER_* reason instead of collapsing everything into a reasonless PROVIDER_FAILED; the reason is derived from AiErrorCode only and carries no Provider text
* `Improvement` The Three Stove Agent model broker re-arms the deadline with the request's timeoutMs after each accepted batch of native tool results, so every continuation round has the full timeout and the whole turn is bounded by the plugin's run budget and the round cap; waiting without submitting results still expires on the original deadline. Multi-round native tasks used to share the first request's deadline and ended with MODEL_TIMEOUT after 5 minutes
* `Improvement` Three Stove Agent is enabled and disabled only in Plugin Center; the home drawer switch is removed and scripts and the plugin UI connect on demand; official installations are enabled by default while explicit user disablement is preserved
* `Improvement` Rename the video player from 3-Ember Player to 3-Amber Player; update plugin installation guidance, M3U playback preference and transparent icon recognition for its new application ID
* `Improvement` The package installer moved to the 3-Setup Installer plugin: the install entries of the file manager, Plugin Center, a finished build and the self-update hand the package to the plugin's install dialog when the plugin is available and to the system installer otherwise, with a hint to install the plugin; the host no longer registers the "open APK with AutoJs6" system entry, and apks / xapk / apkm / apkz split containers need the plugin
* `Improvement` Installation history distinguishes installs started from AutoJs6 entries and installer scripts, with no change to script API usage (requires a 3-Setup Installer version with installation history)
* `Improvement` installer and host installation entries can use the plugin's source/package-prefix profiles, with explicit options taking precedence and null resetting inherited installer/installReason/packageSource values; source deletion requires successful installation and retains files shared with retained or unsuccessful batch items (requires a 3-Setup Installer version with source profiles)
* `Improvement` installer installation options accept grantAllRequestedPermissions, requestUpdateOwnership, dexopt, installReason and packageSource when supported by the selected plugin; omission preserves existing behavior, while explicit false/none still requires support
* `Improvement` The Three Stove Agent model broker fuses a Provider component when a model call times out while its synchronous Binder transaction has not returned; the fuse used to last until AutoJs6 restarted, failing later tasks at once with RATE_LIMITED: FUSED. It is now lifted automatically once that transaction has returned and the run has settled, so later tasks are admitted again without a restart
* `Improvement` Layout hierarchy rows drop the colored level beams on the left (the indent stays) and move the expand / collapse marker next to the node text: a right chevron for collapsed rows, a down chevron for expanded rows and a small hollow circle for rows without descendants, all three in one slightly darker tint
* `Improvement` Node info multi-selection uses themed row backgrounds with unchanged row heights; the buttons are Settings / Back / Copy, and the copy behavior is remembered across sessions: Always ask (default), Copy object selector, or Copy chained selector
* `Improvement` Larger query mode, filter, regex and navigation symbols in the layout inspector search bar; the regex switch uses a simple ".*" icon
* `Improvement` Mail account settings and Plugin Center now recognize 3-Stamp Mail under its new package name
* `Improvement` Plugin Center prefers dedicated transparent monochrome icons for standalone plugins, follows the host light or dark theme, and retains fallback support for older plugin versions
* `Improvement` Official catalog icons keep the same source and appearance before and after installation, including neutral grayscale artwork
* `Improvement` EPUB integration uses the new 3-Folio EPUB installation identity while preserving the existing epub API and file-manager actions
* `Improvement` Plugin Center setup tracks the new installation identities of 3-Maple Image, 3-Stack Archive and 3-Hanzi CC, combining image viewing and editing into one recommended item
* `Improvement` Plugin Center and setup support separate light and dark circular icon backgrounds, with fixed neutral colors for the Three series and consistent catalog artwork and backgrounds before and after installation
* `Improvement` The light and dark window background entries of the default color library are merged into one "Background color" entry offering Light, Dark, Auto and Auto inverted; selections made before the upgrade are kept
* `Improvement` The theme color summary names the library and color (the current group for intelligent color libraries), and "Locate current theme color" finds colors in user libraries and the active intelligent color library; the legacy layout shows the active intelligent color library by name, and choosing a color there turns it off
* `Improvement` Search on the color library list also covers the colors of user libraries and the groups of intelligent color libraries (by name or any of their colors); results name their library and apply or activate on tap, and the search help explains the scope
* `Improvement` Ace editor parameter hints support overloaded methods, showing the overload index with tappable switching and preferring the documentation of the current parameter
* `Improvement` Editor menu "Jump" gains "Jump to previous cursor" (formerly the TAB long-press action), which can be recorded in action favorites
* `Improvement` Ace editor parameter hints can switch overloads with the hardware Alt+Up / Alt+Down, the keyboard bar's Up and Down keys (while several overloads are shown; can be turned off in settings) and the up / down buttons at the top right of the hint window
* `Improvement` Ace editor parameter hints reappear after tapping the editor (not after scrolling), and ESC hides both parameter hints and symbol documentation
* `Improvement` Ace editor parameter hints support overloaded methods even when LSP is off or the semantic service is unavailable: the static index carries 146 declared overloads (such as app.startActivity), the built-in ECMAScript signatures gain overloads for splice / replace / JSON.stringify / Array.from and more, and Python / Lua parameter hints keep every signature
* `Improvement` Ace editor hover documentation can be scrolled and selected on touch screens, and smart hints wait for asynchronous parameter hints in Python / Lua files instead of falling back to completion
* `Improvement` The plugin info dialog shows the full "Supported ABIs" separately from the "Installed ABIs" in the current APKs, including splits; support declarations in the plugin manifest take priority, followed by the index and then plugin INFO, so plugins such as Ace Editor no longer report only the installed APK ABI
* `Improvement` Compose UI native text editing preserves selection and IME composition with keyboard avoidance, and reports each Snackbar action or dismissal once
* `Improvement` ComposeError provides localized messages and stable codes with node/property/cause and script stack details; each engine permits up to 8 live sessions, rejects operations on closed handles and clears callbacks, refs and pending work; updating, uninstalling or disabling the plugin closes its active sessions with the corresponding error
* `Improvement` cancelable=false disables back, outside-click and swipe dismissal; explicit close and script exit still release the dialog while preserving existing pages and other sessions
* `Improvement` Node.js runtime prewarming overlaps host engine, TypeScript and workspace preparation to reduce launch waiting; startup timings include the first output for interactive and scheduled scripts
* `Improvement` In packaged apps the OpenCV plugin runtime and the private OpenCV of OCR plugins are independent: images and ocr work together even with different OpenCV versions, and OCR alone no longer needs OpenCV (requires OCR plugin releases that link OpenCV privately)
* `Improvement` APK packaging uses the newest libc++_shared.so among the host and the plugins, and stops when a native library needs C++ runtime symbols that it lacks
* `Dependency` Modularized Android AXML
* `Dependency` Modularized Android ARSC Editor
* `Dependency` Modularized APK Signer
* `Dependency` Modularized APK Verifier
* `Dependency` Modularized ARSCLib version 1.3.8
* `Dependency` Modularized Image Quantization version 2.17.0
* `Dependency` Modularized OpenCV version 4.8.0
* `Dependency` Added juniversalchardet version 2.0.4
* `Dependency` Added AndroidX AsyncLayoutInflater version 1.1.0
* `Dependency` Removed Android Annotations version 4.8.0
* `Dependency` Removed local APK Parser module
* `Dependency` Removed Glide KSP version 5.0.5
* `Dependency` Removed ICU4J version 77.1
* `Dependency` Removed Jaredrummler Android Device Names version 2.1.1
* `Dependency` Removed JCIP Annotations version 1.0
* `Dependency` Removed Junit Jupiter version 5.13.4
* `Dependency` Removed Retrofit2 Converter Gson version 3.0.0
* `Dependency` Removed Retrofit2 Kotlin Coroutines Adapter version 0.9.2
* `Dependency` Removed Tiny Sign version 0.9
* `Dependency` Removed redundant app-module declaration of AndroidX Room KTX version 2.8.1
* `Dependency` Removed AndroidX MultiDex version 2.0.1
* `Dependency` Removed MLKit Barcode Scanning version 17.3.0
* `Dependency` Removed MLKit Text Recognition Chinese version 16.0.1
* `Dependency` Removed OpenCC version 1.2.2
* `Dependency` Removed Pinyin4j version 2.5.1
* `Dependency` Removed Jieba Analysis version 1.0.3-SNAPSHOT (modified)
* `Dependency` Removed local Rapid OCR library
* `Dependency` Removed Flexmark Java HTML to Markdown version 0.64.8
* `Dependency` Removed local JavaMail for Android library
* `Dependency` Removed GitHub API version 1.306
* `Dependency` Removed Jackson DataBind version 2.13.4.2
* `Dependency` Removed Commons IO version 2.8.0
* `Dependency` Removed Apache Commons Lang3 version 3.18.0
* `Dependency` Removed Spongy Castle Prov version 1.58.0.0
* `Dependency` Removed Android dx version 1.14
* `Dependency` Removed Log4j version 1.2.17
* `Dependency` Removed Android Logging Log4j version 1.0.3
* `Dependency` Removed local AndroidX AppCompat 1.0.2 library
* `Dependency` Appended ZXing Android Embedded version 4.3.0
* `Dependency` Appended AndroidX Test Ext JUnit version 1.3.0
* `Dependency` Added Android Hidden API Bypass version 6.1
* `Dependency` Modularize Package Archive Parser
* `Dependency` Modularize Installer API
* `Dependency` Upgrade AndroidX Lifecycle from 2.6.2 to 2.9.4
* `Dependency` Upgrade AndroidX SavedState from 1.2.1 to 1.3.2
* `Dependency` Upgrade AndroidX Emoji2 from 1.3.0 to 1.4.0
* `Dependency` Upgrade AndroidX Window from 1.0.0 to 1.5.0
* `Dependency` Modularize Compose UI API

# v6.7.0

###### 2026/03/14

* `Feature` Plugin Center feature, supporting plugin install/uninstall/update and related operations (entry: home drawer button/home tab)
* `Feature` Version History feature, supporting viewing/restoring history versions of editable files (entry: home drawer button/file manager menu/code editor menu)
* `Feature` Trash feature, supporting viewing/restoring deleted files/folders (entry: home drawer button)
* `Feature` Paddle OCR (PP-OCRv5) plugin, for optical character recognition
* `Feature` cvt module, for data unit conversion (see Project Docs > [Unit Conversion](https://docs.autojs6.com/#/cvt))
* `Feature` fmt module, for data formatting (see Project Docs > [Formatting](https://docs.autojs6.com/#/fmt))
* `Feature` zip module, for file compression and extraction operations (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (see Project Docs > [Zip](https://docs.autojs6.com/#/zip))
* `Feature` mediainfo module, for viewing detailed information of media files (see Project Docs > [Media Info](https://docs.autojs6.com/#/mediainfo))
* `Feature` cvt.bytes method, for byte data unit conversion (see Project Docs > [Unit Conversion](https://docs.autojs6.com/#/cvt))
* `Feature` fmt.bytes method, for byte data formatting (see Project Docs > [Formatting](https://docs.autojs6.com/#/fmt))
* `Feature` s13n.bytes method, for standardizing byte data (see Project Docs > [Standardization](https://docs.autojs6.com/#/s13n))
* `Feature` app.isDualInstalled method, for detecting whether a dual-installed app is installed (requires Shizuku or Root permission) _[`issue #450`](http://issues.autojs6.com/450)_
* `Feature` device.getSharedDeviceId method, for obtaining a unified shared device ID across apps _[`issue #455`](http://issues.autojs6.com/455)_
* `Feature` device.setPointerLocation and other Toggleable series methods, for setting or getting the pointer location system setting _[`issue #381`](http://issues.autojs6.com/381)_
* `Feature` dialogs.build method now supports textAllCaps/(positive/negative/neutral)TextAllCaps option parameters, for controlling whether button texts are all uppercase
* `Feature` images.loadAsync method, for asynchronously fetching network image resources _[`issue #327`](http://issues.autojs6.com/327)_
* `Feature` ui.getNavigationBarHeight method/navigationBarHeight property (getter), for getting navigation bar height _[`issue #456`](http://issues.autojs6.com/456)_
* `Feature` ui.getVisible(Status/Navigation)BarHeight method/visible(Status/Navigation)BarHeight property (getter), for quickly getting visible height of the status bar or navigation bar _[`issue #456`](http://issues.autojs6.com/456)_
* `Feature` http.put/del(ete)/head methods, for sending network requests with different HTTP methods
* `Feature` http.getAsync/postAsync/requestAsync and related methods, for asynchronously sending network requests _[`issue #423`](http://issues.autojs6.com/423)_
* `Feature` body objects returned by http module request-related methods now include stream/saveToFile/close and related methods _[`issue #452`](http://issues.autojs6.com/452)_
* `Feature` http module request-related methods now support cache control option parameters (cacheBody/bodyCacheThresholdBytes)
* `Feature` http module request-related methods now support insecure option parameters (isInsecure/insecure), for ignoring certificate-related exceptions _[`issue #417`](http://issues.autojs6.com/417)_
* `Feature` http module request-related methods now support the options.client option, for configuring OkHttpClient.Builder (such as followRedirects) _[`issue #454`](http://issues.autojs6.com/454)_
* `Feature` auto.state property (getter) and shizuku.state property (getter), for obtaining accessibility service status and Shizuku service status
* `Feature` runtime.(set/is)JavaPrimitiveWrap methods, for setting or getting Java primitive wrapping strategy _[`issue #435`](http://issues.autojs6.com/435)_
* `Feature` autojs.(restart/exit) methods, for restarting or exiting the AutoJs6 app, and supporting automatic script execution on app restart with the specified arguments _[`issue #460`](http://issues.autojs6.com/460)_
* `Feature` UiObject#isShifted method, for detecting control position changes _[`issue #469`](http://issues.autojs6.com/469)_
* `Feature` UiObject#snapshot method, for capturing the current state of a control _[`issue #469`](http://issues.autojs6.com/469)_
* `Feature` engines.myEngine().getStartTime() method, for getting the script engine startup timestamp _[`issue #449`](http://issues.autojs6.com/449)_
* `Feature` crash global method, for testing the crash report page
* `Feature` structuredClone global method, for deep-cloning JavaScript objects (see [MDN](https://developer.mozilla.org/zh-CN/docs/Web/API/Window/structuredClone))
* `Feature` Equivalent series selectors (UiSelector#id/text/...) and contains series selectors (UiSelector#xxxContains) now support regular expression parameters
* `Feature` Regular expression parameters of selectors now support flags (i, m, s, u)
* `Feature` Regular expressions now support lookbehind assertion syntax _[`issue #464`](http://issues.autojs6.com/464)_
* `Feature` File manager now includes "Move to" and "Copy to" menu items, supporting operation abort and progress status display
* `Feature` Code editor now includes the "Multi-function Keyboard" - "Symbol Settings" menu item, supporting symbol editing and symbol creation/import/export by profile level
* `Feature` Home drawer now includes the "Pointer Location" tool
* `Feature` Home drawer now includes the "All Files Access" switch
* `Feature` Home drawer now includes the "Show UI from Background" switch (for [Xiaomi/Vivo] devices)
* `Feature` Settings page now includes the "Java Primitive Wrap" option _[`issue #435`](http://issues.autojs6.com/435)_
* `Feature` Settings page now includes the "Scheduled Task Engine" option, supporting AlarmManager/WorkManager/JobScheduler _[`issue #457`](http://issues.autojs6.com/457)_ _[`issue #434`](http://issues.autojs6.com/434)_ _[`issue #449`](http://issues.autojs6.com/449)_ _[`issue #388`](http://issues.autojs6.com/388)_ _[`issue #378`](http://issues.autojs6.com/378)_ _[`issue #163`](http://issues.autojs6.com/163)_ _[`issue #53`](http://issues.autojs6.com/53)_ _[`issue #21`](http://issues.autojs6.com/21)_
* `Feature` Settings page now includes the "App Launcher Icon" option, supporting adaptive icons/transparent background icons _[`issue #405`](http://issues.autojs6.com/405)_
* `Feature` Settings page now includes the "Restart Strategy" option, for configuring whether the home drawer restart button uses the fast restart strategy
* `Feature` Settings page now includes the "Screen Capture Permission Request Delay" option, for preventing the permission request window from blocking the screen
* `Feature` Launcher shortcuts in the Settings page now include the "Plugins" option, for jumping to the Plugin Center page via a shortcut
* `Feature` Packaged apps now support auto-start on boot, with a switch added on the configuration page
* `Feature` Packaged apps now support app shortcuts (log page/settings page)
* `Feature` Packaged app configuration page now includes undo/redo/save buttons
* `Feature` Packaged app configuration page now includes a profile mechanism, supporting profile creation/modification/deletion/import/export _[`issue #483`](http://issues.autojs6.com/483)_
* `Feature` Scrapers tool (run-scrapers.mjs), for automatically updating structured data in Gradle build scripts/common README data/README template data and more
* `Feature` Gradle Wrapper switcher tool (gradle-wrapper-switcher), for switching local Gradle major versions (such as Gradle 8.x and Gradle 9.x)
* `Fix` Incorrect parameter count and type restrictions for the notice method _[`issue #462`](http://issues.autojs6.com/462)_
* `Fix` Issue where the ui.R property (getter) could not be used properly
* `Fix` Issue where exceptions raised in ui.post callback parameters could fail silently
* `Fix` Issue where global methods such as isJavaClass/isJavaPackage were invalid
* `Fix` Issue where the timeout parameter of timers.keepAlive did not work
* `Fix` Issue where floaty.window/rawWindow methods could not accept string parameters _[`issue #467`](http://issues.autojs6.com/467)_
* `Fix` Issue where floaty.rawWindow could display some controls incorrectly due to an incorrect context being passed
* `Fix` Issue where util.isString method was invalid
* `Fix` Issue where util.class[Name]/getClass[Name] could return incorrect results
* `Fix` Issue where util.java.array could not properly use type parameters such as Boolean
* `Fix` Issue where objects returned by threads.disposable() could be unexpectedly boxed when accessing stored data _[`issue #435`](http://issues.autojs6.com/435)_
* `Fix` Issue where style options of the floating log window could not reset properly when console.build was called multiple times
* `Fix` Issue where (title/content)BackgroundColor options of console.build could override transparency or tint options _[`issue #458`](http://issues.autojs6.com/458)_
* `Fix` Issue where console/toast and related methods could lose trailing zeros when displaying Java Double values
* `Fix` Issue where console/toast and related methods could not display BigInt values
* `Fix` Issue where the toast method could cause memory leaks due to holding a strong reference to the Toast instance
* `Fix` Issue where some global objects could lose JavaScript prototype properties and methods
* `Fix` Issue where the this object could point incorrectly when JavaScript expressions were used as attribute values in XML syntax
* `Fix` Issue where the linkify option parameter of dialogs.build did not work
* `Fix` Issue where dialogs.build could break original data in object parameters
* `Fix` Incorrect internal frame rate calculation in the setMaxFps method of the canvas element control
* `Fix` Incorrect width calculation when images.concat performed vertical concatenation
* `Fix` Issue where canceling authorization in images.requestScreenCapture could crash the app
* `Fix` Issue where screenshots obtained by images.captureScreen could have incorrect orientation or black border padding _[`issue #337`](http://issues.autojs6.com/337)_
* `Fix` Issue where 1 could be incorrectly parsed as a percentage when some images-related methods used region parameters
* `Fix` Issue where the oneShot flag could fail when exceptions occurred in some images-related methods _[`issue #372`](http://issues.autojs6.com/372)_
* `Fix` Issue where some images-related methods could cause memory leaks _[`issue #372`](http://issues.autojs6.com/372)_
* `Fix` Issue where images.save and ImageWrapper#saveTo could not properly write files when the input path did not exist
* `Fix` Issue where ImageWrapper#recycle failed to recycle image resources
* `Fix` Issue where some overloaded ocr methods might not work properly
* `Fix` Issue where results obtained by ocr.detect might not match ocr.mode _[`issue #468`](http://issues.autojs6.com/468)_
* `Fix` Issue where accessibility events registered by auto.registerEvent could be mistakenly cleared by other scripts _[`issue #466`](http://issues.autojs6.com/466)_ _[`issue #343`](http://issues.autojs6.com/343#issuecomment-3263953918)_
* `Fix` Issue where UiObject#child could throw ArrayIndexOutOfBoundsException on Android 10 _[`issue #416`](http://issues.autojs6.com/416)_
* `Fix` Issue where built-in module method calls could run into inconsistent Rhino context problems
* `Fix` Issue where creating a large number of floating windows in scripts could leave residual windows after script exit
* `Fix` Issue where project.json configuration parameters might not be parsed properly when running a project
* `Fix` Issue where the excludedDirs parameter in project.json could cause configuration parsing failure during project packaging _[`issue #428`](http://issues.autojs6.com/428)_
* `Fix` Issue where packaging might not work properly on Android 7.x
* `Fix` Issue where original key names could be modified when saving script project configuration files
* `Fix` Issue where very large build version codes or build times in script project configuration files could crash the app
* `Fix` Issue where frequent fetching or rebuilding of ImageReader could crash the app due to no available frame in the buffer
* `Fix` Issue where the input event observer InputEventObserver could cause noticeable app startup lag
* `Fix` Issue where Shizuku user service processes did not terminate properly and accumulated _[`issue #474`](http://issues.autojs6.com/474)_
* `Fix` Issue where packaged apps could not properly use Paddle OCR and Rapid OCR features
* `Fix` Issue where key values in packaged app configuration file project.json could be modified unexpectedly
* `Fix` Issue where checkbox icons could flicker when scrolling the "Required Permissions" list on the packaged app configuration page
* `Fix` Issue where statistics on the Release History page could be displayed incompletely on some systems due to font differences
* `Fix` Issue where MLKit Google OCR could not initialize properly on some devices (tentative fix) _[`issue #8`](http://issues.autojs6.com/8#issuecomment-3117061768)_
* `Fix` Issue where file manager function buttons could not properly receive click events on some devices (tentative fix) _[`issue #465`](http://issues.autojs6.com/465)_
* `Fix` Issue where blank lines in the code editor displayed box glyphs on some devices (tentative fix)
* `Fix` Issue where the soft keyboard could block text content in the code editor
* `Fix` Issue where code content could still be edited in read-only mode in the code editor
* `Fix` Issue where tapping the title area or some menu items in read-only mode in the code editor could crash the app
* `Fix` Issue where loading large files in the code editor could crash the app (tentative fix)
* `Fix` Abnormal initialization of function button states when the code editor automatically opened a new file
* `Fix` Issue where putting the app in background while editing a file in the code editor could crash the app
* `Fix` Issue where tapping the fx button in the code editor on Android 15+ could not show the quick panel for module functions (tentative fix)
* `Fix` Issue where ErrorDialogActivity could fail to start properly or disappear automatically in a short time _[`issue #479`](http://issues.autojs6.com/479)_ _[`issue #471`](http://issues.autojs6.com/471)_ _[`issue #414`](http://issues.autojs6.com/414)_ _[`issue #340`](http://issues.autojs6.com/340#issuecomment-2973485826)_
* `Fix` Incorrect accepted parameter types for the Canvas constructor _[`issue #402`](http://issues.autojs6.com/402)_
* `Fix` Issue where copying detailed information on the crash report page did not work
* `Fix` Issue where custom back navigation logic stopped working on Android 16+, causing abnormal back behavior
* `Fix` Issue where the app could crash when pressing the back button or switching tabs after displaying file manager search results
* `Fix` Issue where the UI did not refresh automatically after deleting project folders in the file manager
* `Fix` Issue where the app could crash when task panel list items changed frequently
* `Fix` Issue where navigation bar foreground color could behave abnormally with night mode when using "three-button" gesture navigation
* `Fix` Issue where the APK file type information dialog might fail to obtain app name and SDK information
* `Fix` Issue where in client mode, disconnecting and reconnecting shortly after connecting could lead to handshake timeout and make reconnection difficult
* `Fix` Issue where in client mode, actions such as screen rotation or language switching that recreated the Activity always caused AutoJs6 to reconnect
* `Fix` Issue where in server mode, actions such as screen rotation or language switching that recreated the Activity caused the VSCode console to stop outputting logs _[`issue #385`](http://issues.autojs6.com/385)_
* `Fix` Issue where connecting to the VSCode plugin in multiple ways at the same time could multiply the number of printed logs
* `Fix` Issue where code generation on the layout analysis page could fail for collection controls (tentative fix) _[`issue #328`](http://issues.autojs6.com/328)_
* `Fix` Issue where the "Use 'Volume Down' Key to Control Recording" switch on the Settings page did not work _[`issue #480`](http://issues.autojs6.com/480)_
* `Fix` Issue where coordinate values in code files generated by script recording might not match actual screen pixel values _[`issue #480`](http://issues.autojs6.com/480)_
* `Fix` Issue where the first recorded action (such as tap or swipe) in code files generated by script recording was always ignored
* `Fix` Issue where background file operations from the floating button "Run Script" dialog could crash the app
* `Fix` Issue where the floating button state could reset when re-entering the home page after the home Activity lifecycle ended
* `Fix` Issue where the Xiaomi device "Display over other apps" switch could jump to the wrong settings page
* `Fix` Issue where after aborting update download, network packets could still continue downloading in the background (tentative fix)
* `Fix` Issue where the Rebuild Project task could not complete properly when isCleanup[Paddle/Rapid]Ocr option was enabled in build tools
* `Improvement` Pluginized Paddle OCR (PP-OCRv3) related features _[`issue #334`](http://issues.autojs6.com/334)_ _[`issue #331`](http://issues.autojs6.com/331)_ _[`issue #325`](http://issues.autojs6.com/325)_
* `Improvement` Enhanced compatibility of runtime.loadJar method (by [LYS86](https://github.com/LYS86)) _[`pr #475`](http://pr.autojs6.com/475)_
* `Improvement` Improved compatibility of ui.getStatusBarHeight method/statusBarHeight property (getter) to a certain extent
* `Improvement` console.log and related methods now support displaying detailed information when printing global objects (images, app, ocr, etc.)
* `Improvement` dialogs.build method now supports native MaterialDialog.Builder option parameters
* `Improvement` dialogs.build method now supports loose string matching for animation/linkify option parameters
* `Improvement` Improved stability and consistency of screen orientation and width/height data obtained by device.rotation/rotation/width/height methods
* `Improvement` Improved execution efficiency and compatibility of app.launchDual/startDualActivity and related methods
* `Improvement` android.graphics.Paint#setColor now properly parses color parameters such as ColorInt/ColorHex/ColorName
* `Improvement` Exception messages for argument types of built-in module-related methods now include type summary information
* `Improvement` Built-in modules (dayjs/cheerio/dayjs/i18n) now use global lazy loading instead of script execution mode
* `Improvement` When printing huge data through console-related methods, some content is properly omitted according to upper limits to reduce print time
* `Improvement` Initial countdown value of the console floating window increased from 6 seconds to 9 seconds
* `Improvement` Internal implementation of the console floating window is now lock-free and queue-based, improving parameter setting efficiency and success rate
* `Improvement` Background color behavior-related APIs (alpha/tint/base color) of the console floating window now better align with Android design conventions _[`issue #458`](http://issues.autojs6.com/458)_
* `Improvement` Optimized menu collapse timing when tapping menu items after expanding the floating button in the file manager
* `Improvement` File manager/task panel now supports showing file/task count statistics
* `Improvement` When saving files fails in the code editor, they are automatically saved as drafts and support Save As to a new file
* `Improvement` Improved smoothness to a certain extent when loading large files in the code editor
* `Improvement` Code editor "Find/Replace" now supports state persistence and real-time display of search count information
* `Improvement` When prompting to save in the code editor, it now ensures saving succeeds before exiting the editor to reduce save failure rate
* `Improvement` State of the code editor save button now better matches user expectations
* `Improvement` Code editor now supports long-press functionality for keys such as "-", "=", "(", "[", "{", "<"
* `Improvement` Packaged app build process dialog now includes an "Abort" button
* `Improvement` Improved display behavior of the packaged app build process dialog and added elapsed time statistics
* `Improvement` When installing packaged apps with new configuration, related configuration of the old app will be overwritten
* `Improvement` Packaged app configuration page now checks required permissions by default (WAKE_LOCK/INTERNET/WRITE_EXTERNAL_STORAGE/...) _[`issue #397`](http://issues.autojs6.com/397)_
* `Improvement` Packaged app configuration page now supports reading/configuring more options (show logs on launch/show splash screen on launch/splash slug text/...)
* `Improvement` Packaged app settings page now includes a foreground service switch _[`issue #406`](http://issues.autojs6.com/406)_
* `Improvement` Key name conflict detection added when saving script project configuration files to prevent ambiguous keys
* `Improvement` Crash report page now supports pinch-to-zoom font resizing and adds common function buttons
* `Improvement` App launcher icons now support adaptive icon features _[`issue #405`](http://issues.autojs6.com/405)_
* `Improvement` Rhino engine now falls back to raw reflection types when generic signature parsing fails, improving reflective method availability on lower Android versions
* `Improvement` Divider lines added to switch-type items in the home drawer; tapping the title text area now shows detail dialogs and supports quick jump to system settings when needed
* `Improvement` When starting or restarting AutoJs6, tapping the title text area of "Client Mode" in the home drawer now supports aborting an ongoing connection attempt
* `Improvement` Client mode now supports connecting via IPv6 addresses and domain names
* `Improvement` Client mode now supports detection prompts for special-purpose IPv4 addresses (loopback/broadcast/multicast/reserved/...)
* `Improvement` Client mode now supports connection status display and management (correct address/abort connection)
* `Improvement` Server mode now supports showing the number of connected clients
* `Improvement` Reading notification permission now supports automatic reconnect mechanism ([requestRebind](https://developer.android.com/reference/android/service/notification/NotificationListenerService#requestRebind(android.content.ComponentName))) to improve stability of notification-listening related features
* `Improvement` Floating button now improves safety when launching Activities from background to avoid app crashes
* `Improvement` Floating button "More" dialog now uses asynchronous data loading for smoother display
* `Improvement` Floating button "Run Script" dialog now includes a "Home" menu item
* `Improvement` Floating button "Run Script" dialog now supports minimization and state restoration, and does its best to keep the window resident or restore it automatically
* `Improvement` Script recording and playback now support Shizuku permission (playback smoothness may be somewhat affected) _[`issue #320`](http://issues.autojs6.com/320)_
* `Improvement` Theme color settings page now uses a fast positioning method when locating theme colors to improve efficiency
* `Improvement` Replaced deprecated [LocalBroadcastManager](https://developer.android.com/jetpack/androidx/releases/localbroadcastmanager) with [LiveData](https://developer.android.com/topic/libraries/architecture/livedata) and [SharedFlow](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow)
* `Improvement` Gradle build scripts now improve extraction efficiency for 7z files
* `Improvement` Gradle build scripts now support obtaining detailed Android Studio IDE version (such as "2025.1.4.7")
* `Improvement` Gradle build scripts now support automatically generating data required by VersionCodesList class to reduce script startup delay
* `Improvement` Use [Version Catalogs](https://developer.android.com/build/migrate-to-catalogs) to centrally manage Gradle dependency and plugin versions
* `Improvement` Modularized Gradle build scripts, moved shared build logic into build-logic, and abstracted it as convention plugins
* `Improvement` Use Gradle convention plugins to simplify loading logic of local AAR libraries
* `Improvement` Removed the "android.enableJetifier=true" Gradle option to adapt to Gradle 9
* `Improvement` Use [Toolchain](https://docs.gradle.org/current/userguide/toolchains.html) instead of sourceCompatibility/targetCompatibility to reduce build environment differences
* `Dependency` Modularized Expandable Layout version 1.6.0
* `Dependency` Modularized Expandable RecyclerView version 3.0.0-RC1
* `Dependency` Modularized Recyclerview Flexible Divider version 1.4.0
* `Dependency` Localized Root Shell version 1.6
* `Dependency` Added AndroidX Activity (KTX) version 1.12.2
* `Dependency` Added AndroidX Core (KTX) version 1.15.0
* `Dependency` Added AndroidX Work Runtime version 2.11.0
* `Dependency` Added AndroidX Interpolator version 1.0.0
* `Dependency` Added Room RxJava2 version 2.8.1
* `Dependency` Upgraded Android SDK version 35 -> 36
* `Dependency` Upgraded Gradle 8 version 8.14 -> 8.14.4
* `Dependency` Upgraded Gradle 9 version 8.14 -> 9.4.0
* `Dependency` Upgraded Rhino version 1.8.1-SNAPSHOT -> 2.0.0-SNAPSHOT
* `Dependency` Upgraded Desugar JDK Libs version 2.0.4 -> 2.1.5
* `Dependency` Upgraded Android Material version 1.12.0 -> 1.13.0
* `Dependency` Upgraded AndroidX AppCompat version 1.7.0 -> 1.7.1
* `Dependency` Upgraded AndroidX Room version 2.7.1 -> 2.7.2
* `Dependency` Upgraded AndroidX WebKit version 1.13.0 -> 1.14.0
* `Dependency` Upgraded Material Progressbar version 1.4.2 -> 1.6.1
* `Dependency` Upgraded Apache Commons version 3.16.0 -> 3.18.0
* `Dependency` Upgraded Retrofit2 Retrofit version 2.11.0 -> 2.12.0
* `Dependency` Upgraded Retrofit2 Converter Gson version 2.11.0 -> 2.12.0
* `Dependency` Upgraded Retrofit2 RxJava2 version 2.11.0 -> 2.12.0
* `Dependency` Upgraded Joda Time version 2.12.7 -> 2.14.0
* `Dependency` Upgraded Gson version 2.11.0 -> 2.13.1
* `Dependency` Upgraded ARSCLib version 1.3.1 -> 1.3.5
* `Dependency` Upgraded Jsoup version 1.19.1 -> 1.20.1
* `Dependency` Upgraded Junit Jupiter version 5.10.3 -> 5.13.0
* `Dependency` Upgraded OpenCC version 1.2.0 -> 1.2.2
* `Dependency` Removed Android Support Annotation v7 version 28.0.0
* `Dependency` Removed Android Support AppCompat v7 version 28.0.0
* `Dependency` Removed Android Support RecyclerView v7 version 28.0.0

# v6.6.4

###### 2025/05/31

* `Hint` API change: ui.(status/navigation)BarAppearanceLight[By] -> ui.(status/navigation)BarIconLight[By]
* `Feature` util.dpToPx/spToPx/pxToDp/pxToSp methods for pixel unit conversion
* `Fix` Subtitles may be displayed incompletely when rotating the screen to landscape orientation
* `Fix` Some page content may be obscured by the side navigation bar when rotating to landscape
* `Fix` Incomplete status bar background tint area on some pages in Android 15 _[`issue #398`](http://issues.autojs6.com/398)_
* `Fix` Code editor might write files with low-confidence encoding causing decoding errors (attempted fix)
* `Improvement` Improved layout adaptability on the App and Developer pages and removed unnecessary layout categories
* `Improvement` README.md build section added multiple methods to help locate the target settings page _[`issue #404`](http://issues.autojs6.com/404)_
* `Dependency` Added AndroidX ConstraintLayout version 2.2.1

# v6.6.3

###### 2025/05/27

* `Feature` Release history feature: view multi-language release changelogs and statistics
* `Feature` timers.keepAlive method (now global) for keeping the script alive
* `Feature` Event listeners such as engines.on('start/stop/error', callback) for global engine events
* `Feature` images.detectMultiColors method for multi-point color verification _[`issue #374`](http://issues.autojs6.com/374)_
* `Feature` images.matchFeatures / detectAndComputeFeatures methods: full-resolution image matching (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) _[`issue #366`](http://issues.autojs6.com/366)_
* `Feature` images.compressToBytes method to compress an image and generate a byte array
* `Feature` images.downsample method for pixel down-sampling and creating a new ImageWrapper
* `Feature` ui.keepScreenOn method to keep the device screen on while a UI page is focused
* `Feature` ui.root property (getter) to obtain the "window content root container" node of the UI layout
* `Feature` webview element now supports JsBridge-based web page layouts (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) [see Sample Code > Layout > Interactive HTML / Vue2 + Vant (SFC)] _[`issue #281`](http://issues.autojs6.com/281)_
* `Feature` Layout Inspector supports hiding controls (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #371`](http://pr.autojs6.com/371)_ _[`issue #355`](http://issues.autojs6.com/355)_
* `Fix` Online docs in the Home > Docs tab and Docs activity may be covered by the system navigation bar
* `Fix` Clicking Toolbar buttons on some pages could accidentally trigger the title click event
* `Fix` Blank lines in the code editor showed box glyphs on some devices (tentative fix)
* `Fix` Color-picker dialog in the theme-color settings page could stack infinitely
* `Fix` Volume-up key failed to stop all scripts when the accessibility service was disabled
* `Fix` IME overlay issue when editing custom broadcast content in the Scheduled Tasks page
* `Fix` Controls inside webview elements could not activate the soft keyboard properly
* `Fix` APK file info dialog might fail to obtain app name and SDK info
* `Fix` File-manager sample code might fail to auto-load child directory contents when entering a project folder
* `Fix` Top content of UI mode on Android 15 was covered by the status bar
* `Fix` Status-bar background color on some Android 15 pages could not follow the theme color dynamically
* `Fix` dialogs module could not use the customView property _[`issue #364`](http://issues.autojs6.com/364)_
* `Fix` Expression parameter of dialogs.input might not return the execution result
* `Fix` Using JavaAdapter led to a ClassLoader stack overflow _[`issue #376`](http://issues.autojs6.com/376)_
* `Fix` console.setContentTextColor caused log text color to lose its default value _[`issue #346`](http://issues.autojs6.com/346)_
* `Fix` console.setContentBackgroundColor could not accept color-name arguments _[`issue #384`](http://issues.autojs6.com/384)_
* `Fix` images.compress implementation corrected: now changes encoding quality instead of pixel down-sampling
* `Fix` images.resize method did not work properly
* `Fix` engines.all could trigger ConcurrentModificationException _[`issue #394`](http://issues.autojs6.com/394)_
* `Fix` Incorrect date formats in some languages within README.md
* `Fix` Gradle build could fail due to invalid library archive length _[`issue #389`](http://issues.autojs6.com/389)_
* `Improvement` Added gradient separators to the Layout Inspector menu for lightweight grouping
* `Improvement` project.json now supports a permissions option for script projects (by [wirsnow](https://github.com/wirsnow)) _[`pr #391`](http://pr.autojs6.com/391)_ _[`issue #362`](http://issues.autojs6.com/362)_
* `Improvement` Automatically read and check declared permissions of installed apps when packaging a single file _[`issue #362`](http://issues.autojs6.com/362)_
* `Improvement` Expanded theme-color adaptation scope; supports more widget types
* `Improvement` Adaptive drawer width on the Home page for landscape / ultra-wide screens
* `Improvement` Added horizontal and small-screen layouts for About App & Developer pages
* `Improvement` Settings-page dialogs now offer a "Use default value" menu option
* `Improvement` Floating Action Button in File Manager auto-hides when tapping elsewhere
* `Improvement` Code formatter now supports operators such as `??`, `?.`, `??=`
* `Improvement` Code editor supports reading and writing files encoded in GB18030 / UTF-16 (LE/BE) / Shift_JIS, etc
* `Improvement` Code editor supports displaying detailed file information (path/encoding/line break format/total bytes and characters, etc.) _[`issue #395`](http://issues.autojs6.com/395)_
* `Improvement` Added operation-error prompts for intent actions (edit / view / install / send / play, etc.)
* `Improvement` webview element's url attribute supports relative paths
* `Improvement` ImageWrapper#saveTo path parameter supports relative paths
* `Improvement` images.save supports PNG file-size compression when using the quality parameter _[`issue #367`](http://issues.autojs6.com/367)_
* `Improvement` Clearing ignored update records and client-mode connection addresses is now supported
* `Improvement` Version-update information supports multi-language display (in sync with current UI language)
* `Improvement` Asynchronous loading significantly improves File Manager list scrolling smoothness
* `Improvement` Improved content and formatting of script exception messages in the console
* `Improvement` Sample code now supports resetting a folder to its initial contents
* `Improvement` Increased efficiency when checking APK signature information
* `Improvement` Optimized dialog display efficiency and information presentation for APK/media file type info
* `Improvement` Gradle build script now better adapts to newer versions _[`discussion #369`](http://discussions.autojs6.com/369)_
* `Dependency` Bundled Material Dialogs 0.9.6.0 (modularized)
* `Dependency` Bundled Material Date Time Picker 4.2.3 (modularized)
* `Dependency` Bundled libimagequant 2.17.0 (localized)
* `Dependency` Bundled libpng 1.6.49 (localized)
* `Dependency` Added ICU4J 77.1
* `Dependency` Added Jsoup 1.19.1
* `Dependency` Added Material Progressbar 1.4.2
* `Dependency` Added Flexmark Java HTML to Markdown 0.64.8
* `Dependency` Upgraded Gradle 8.14-rc-1 -> 8.14
* `Dependency` Upgraded AndroidX Room 2.7.0 -> 2.7.1

# v6.6.2

###### 2025/04/16

* `Feature` Methods such as ui.statusBarAppearanceLight, statusBarAppearanceLightBy, navigationBarColor, etc
* `Feature` ui.statusBarHeight attribute (getter) for obtaining the status bar height _[`issue #357`](http://issues.autojs6.com/357)_
* `Feature` images.flip method for image flipping _[`issue #349`](http://issues.autojs6.com/349)_
* `Feature` Added "file extension" option in the settings page
* `Feature` New layout support in the theme color settings page (grouping, positioning, search, history, enhanced color palette, etc.)
* `Fix` Issue where Android 15's status bar background color did not match the theme color
* `Fix` Issue where the plugins.load method failed to properly load plugins _[`issue #290`](http://issues.autojs6.com/290)_
* `Fix` Issue where the dx library could not be used properly on Android 7.x _[`issue #293`](http://issues.autojs6.com/293)_
* `Fix` Potential synchronization issues in ScriptRuntime when using require to import built-in modules (tentative fix) _[`issue #298`](http://issues.autojs6.com/298)_
* `Fix` Issue where the notice module was missing extension methods such as getBuilder _[`issue #301`](http://issues.autojs6.com/301)_
* `Fix` Issue where methods like shizuku/shell could not accept string parameters _[`issue #310`](http://issues.autojs6.com/310)_
* `Fix` Issue where the colors.pixel method could not accept single-channel image parameters _[`issue #350`](http://issues.autojs6.com/350)_
* `Fix` Issue where methods such as engines.execScript/execScriptFile had abnormal default working directories when executing scripts _[`issue #358`](http://issues.autojs6.com/358)_ _[`issue #340`](http://issues.autojs6.com/340)_ _[`issue #339`](http://issues.autojs6.com/339)_
* `Fix` Issue where floaty.window/floaty.rawWindow could not be executed on background threads
* `Fix` Issue where floaty.getClip might not correctly retrieve clipboard content _[`issue #341`](http://issues.autojs6.com/341)_
* `Fix` Issue where ui.inflate returned values were missing prototype methods such as attr/on/click
* `Fix` Issue where incorrect scope context binding when using XML syntax to use a JavaScript expression as an attribute value _[`issue #319`](http://issues.autojs6.com/319)_
* `Fix` Issue where exceptions in some method calls could not be caught by try..catch blocks _[`issue #345`](http://issues.autojs6.com/345)_
* `Fix` Issue where generating code on the layout analysis page could cause the app to crash _[`issue #288`](http://issues.autojs6.com/288)_
* `Fix` Issue where packaged applications could not properly use the shizuku module _[`issue #227`](http://issues.autojs6.com/227)_ _[`issue #231`](http://issues.autojs6.com/231)_ _[`issue #284`](http://issues.autojs6.com/284)_ _[`issue #287`](http://issues.autojs6.com/287)_ _[`issue #304`](http://issues.autojs6.com/304)_
* `Fix` Issue in the code editor where jumping to the end of a line might actually move the cursor to the beginning of the next line
* `Fix` Issue where rapid consecutive clicks on dialog-type items in the settings page could cause the app to crash
* `Improvement` Streamlined the APK file size for the packaged application template
* `Improvement` Enhanced app (and packaged apps) support for more permissions _[`issue #338`](http://issues.autojs6.com/338)_
* `Improvement` Packaging app configuration page now supports Pinyin library options
* `Improvement` Optimized the status bar background and text color on the main activity page of packaged apps
* `Improvement` Added special permission toggles (such as access all files and send notifications) to the packaged app settings page _[`issue #354`](http://issues.autojs6.com/354)_
* `Improvement` Automatically switched control text and icon colors based on the brightness of the theme color
* `Improvement` Improved visual experience when contrast between control theme color and background is too low
* `Improvement` Enhanced HEX input control in the color palette for better compatibility when pasting color values from the clipboard
* `Improvement` Set in-app navigation bar to be transparent or semi-transparent to enhance visual experience
* `Improvement` Default UI mode for status bar and navigation bar set to `md_grey_50` color in light mode
* `Improvement` Homepage drawer's accessibility service toggle now syncs with script code
* `Improvement` Homepage document page search now supports bidirectional find buttons
* `Improvement` Homepage "Files" tab now supports toggling floating button visibility via long press
* `Improvement` Code editor title text now supports auto-adjusting font size
* `Improvement` Log page floating button visibility now linked with list scrolling actions
* `Improvement` Script project configuration file project.json now supports more packaging options _[`issue #305`](http://issues.autojs6.com/305)_ _[`issue #306`](http://issues.autojs6.com/306)_
* `Improvement` Improved project.json to support lenient matching of option names and alias compatibility
* `Improvement` APK file type info dialog now includes file size and signature scheme information
* `Improvement` APK file type info dialog now supports click listeners for copying text and navigating to app details
* `Improvement` Attempted to restore com.stardust prefixed packages to improve code compatibility _[`issue #290`](http://issues.autojs6.com/290)_
* `Improvement` Enhanced floaty.window/floaty.rawWindow to support execution on both main and background threads
* `Improvement` Global getClip method now leverages floaty.getClip as needed to improve compatibility
* `Improvement` Improved compatibility when passing null paths to files.path and related methods
* `Improvement` Synchronized with the latest official upstream code of the Rhino engine and performed necessary code adaptations
* `Improvement` Enhanced README.md to better document project build and run instructions _[`issue #344`](http://issues.autojs6.com/344)_
* `Dependency` Added Eclipse Paho Client Mqtt V3 version 1.1.0 _[`issue #330`](http://issues.autojs6.com/330)_
* `Dependency` Upgraded Android SDK version from 34 to 35
* `Dependency` Upgraded Gradle from 8.12 to 8.14-rc-1
* `Dependency` Upgraded Rhino from 1.8.0-SNAPSHOT to 1.8.1-SNAPSHOT
* `Dependency` Upgraded AndroidX Recyclerview from 1.3.2 to 1.4.0
* `Dependency` Upgraded AndroidX Room from 2.6.1 to 2.7.0
* `Dependency` Upgraded AndroidX WebKit from 1.12.1 to 1.13.0
* `Dependency` Upgraded Pinyin4j from 2.5.0 to 2.5.1

# v6.6.1

###### 2025/01/01

* `Feature` Pinyin module for Chinese Pinyin conversion (Refer to project documentation > [Chinese Pinyin](https://docs.autojs6.com/#/pinyin))
* `Feature` Pinyin4j module for Chinese Pinyin conversion (Refer to project documentation > [Chinese Pinyin](https://docs.autojs6.com/#/pinyin4j))
* `Feature` Methods UiObject#isSimilar and UiObjectCollection#isSimilar for determining whether a control or a collection of controls is similar
* `Feature` Global method "currentComponent", used to obtain the name information of the currently active component
* `Fix` Issue where the project could not compile properly in certain environments due to a rollback to an earlier version
* `Fix` "Non-primitive value" exception that may occur when calling non-existent methods
* `Fix` Issue where script shortcuts could not be added properly on certain devices (tentative fix) _[`issue #221`](http://issues.autojs6.com/221)_
* `Fix` Incorrect parameter type restrictions for the automator.click/longClick methods _[`issue #275`](http://issues.autojs6.com/275)_
* `Fix` Issue where selectors did not support ConsString type parameters _[`issue #277`](http://issues.autojs6.com/277)_
* `Fix` Missing methods and properties on UiObjectCollection instances
* `Improvement` Packaging app configuration page now supports signing configuration, keystore management, and permission settings (by [luckyloogn](https://github.com/luckyloogn)) _[`pr #286`](http://pr.autojs6.com/286)_
* `Improvement` Improved accuracy in identifying the current package name and activity name of the floating window (Priority: Shizuku > Root > A11Y)
* `Improvement` Improved accuracy in recognizing `currentPackage` and `currentActivity` (Priority: Shizuku > Root > A11Y)
* `Improvement` Restore the ability to select text content of individual entries in the log activity window via double-click or long press _[`issue #280`](http://issues.autojs6.com/280)_
* `Improvement` Recover as much critical information as possible for script projects when project.json is corrupted
* `Improvement` Automatically convert Simplified Chinese to Pinyin (including multi-tone characters) for generated package name suffixes when packaging single files
* `Improvement` Support for negative arguments in the UiSelector#findOnce and UiSelector#find methods
* `Improvement` Enhanced adaptability of app.startActivity/startDualActivity methods
* `Improvement` UI elements and className-related selectors now support more package name prefix omission forms (e.g., RecyclerView, Snackbar, etc.)
* `Improvement` Synchronize with the latest upstream code of the Rhino engine and adapt it to the existing project
* `Dependency` Added Pinyin4j version 2.5.0
* `Dependency` Added Jieba Analysis version 1.0.3-SNAPSHOT (modified)
* `Dependency` Upgrade Gradle version from 8.11.1 to 8.12

# v6.6.0

###### 2024/12/02 - Built-in module rewrite, upgrade cautiously

* `Hint` The built-in modules are rewritten in Kotlin to enhance script execution efficiency, but iterative improvements are needed
* `Hint` The built-in init.js file is empty by default, allowing developers to extend built-in modules or mount external modules.
* `Feature` Axios module / Cheerio module (Ref to [AutoX](https://github.com/kkevsekk1/AutoX))
* `Feature` SQLite module for simple operations on SQLite databases (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (See project documentation > [SQLite](https://docs.autojs6.com/#/sqlite))
* `Feature` MIME module for processing and parsing MIME type strings (See project documentation > [MIME](https://docs.autojs6.com/#/mime))
* `Feature` Nanoid module for string ID generation (Ref to [ai/nanoid](https://github.com/ai/nanoid))
* `Feature` Sysprops module for obtaining runtime environment configuration data (See project documentation > [System Properties](https://docs.autojs6.com/#/sysprops))
* `Feature` OCR module supports [Rapid OCR](https://github.com/RapidAI/RapidOCR) engine
* `Feature` Layout analysis supports window switching (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Feature` auto.clearCache method supports clearing control caches (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Feature` threads.pool method supports simple application of thread pools (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Feature` images.matchTemplate method supports the useTransparentMask option parameter, enabling transparent template matching (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Feature` images.requestScreenCaptureAsync method for asynchronously requesting screenshot permissions in UI mode (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Feature` images.requestScreenCapture method supports the isAsync option parameter, enabling asynchronous screen capture (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Feature` images.on('screen_capture', callback) and other event listener methods support listening for screen capture availability events (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Feature` images.stopScreenCapture method supports actively releasing resources related to screenshot applications (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Feature` Images.psnr/mse/ssim/mssim/hist/ncc and images.getSimilarity methods for obtaining image similarity metrics
* `Feature` images.isGrayscale method for determining whether an image is grayscale
* `Feature` images.invert method for negative image conversion
* `Feature` s13n.point/time methods for standardizing point objects and duration objects (See project documentation > [Normalization](https://docs.autojs6.com/#/s13n))
* `Feature` console module's gravity, touchThrough, backgroundTint methods (See project documentation > [Console](https://docs.autojs6.com/#/console))
* `Feature` Mathx.randomInt/Mathx.randomFloat methods for returning random integers or random floating-point numbers within a specified range
* `Feature` app.launchDual/startDualActivity methods for handling dual app launch (Requires Shizuku or Root permissions) (Experimental)
* `Feature` app.kill method for forcefully stopping an app (Requires Shizuku or Root permissions)
* `Feature` floaty.getClip method for indirectly obtaining clipboard content using a floating window
* `Fix` Memory leak in View Binding of Fragment subclasses (e.g., [DrawerFragment](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/ui/main/drawer/DrawerFragment.kt#L369) / [ExplorerFragment](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/ui/main/scripts/ExplorerFragment.kt#L48))
* `Fix` Instance memory leak in classes such as [ScreenCapture](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/core/image/capture/ScreenCapturer.java#L70) / [ThemeColorPreference](https://github.com/SuperMonster003/AutoJs6/blob/10960ddbee71f75ef80907ad5b6ab42f3e1bf31e/app/src/main/java/org/autojs/autojs/ui/settings/ThemeColorPreference.kt#L21)
* `Fix` Issue causing app crash when requesting screenshot permissions on Android 14 (by [chenguangming](https://github.com/chenguangming)) _[`pr #242`](http://pr.autojs6.com/242)_
* `Fix` Issue causing app crash when starting foreground service on Android 14
* `Fix` Issue with run button in code editor not lighting up properly on Android 14
* `Fix` App may not run properly after packaging due to missing necessary library files _[`issue #202`](http://issues.autojs6.com/202)_ _[`issue #223`](http://issues.autojs6.com/223)_ _[`pr #264`](http://pr.autojs6.com/264)_
* `Fix` App crash when editing project due to missing specified icon resources _[`issue #203`](http://issues.autojs6.com/203)_
* `Fix` Unable to use parameters properly to obtain screenshot resources of specified screen orientation when requesting screenshot permissions
* `Fix` Issue with some devices unable to add script shortcuts properly (Trial fix) _[`issue #221`](http://issues.autojs6.com/221)_
* `Fix` Cumulative request sending delay issue with methods related to sending requests in http module _[`issue #192`](http://issues.autojs6.com/192)_
* `Fix` Shizuku service may not work properly before AutoJs6 enters the main activity page (Trial fix) _[`issue #255`](http://issues.autojs6.com/255)_
* `Fix` random(min, max) method may have out-of-bounds results
* `Fix` Issue where result type parameter of pickup methods cannot be properly passed empty arrays
* `Fix` Issue where control rectangle obtained by UiObject#bounds() may be inadvertently modified, breaking its immutability
* `Fix` Issue with text/button/input elements where text containing half-width double quotes cannot be parsed properly
* `Fix` Issue with text/textswitcher elements where autoLink attribute functionality fails
* `Fix` Issue with different scripts erroneously sharing the same ScriptRuntime object
* `Fix` Issue with global variables HEIGHT and WIDTH losing dynamically-generated Getter properties
* `Fix` Issue with potential high-latency startup caused by RootShell loading on script startup
* `Fix` Issue with floating console window background color setting leading to loss of rectangular rounding style
* `Fix` Access service auto-start may encounter abnormal service issues (Trial fix)
* `Fix` Issue with triggering ViewPager switch when swiping left or right on WebView control on homepage document page
* `Fix` Issue with file manager unable to recognize file extensions containing uppercase letters
* `Fix` File manager may not automatically recognize project when first entering project directory
* `Fix` Issue with file manager page unable to refresh automatically after deleting folder
* `Fix` Issue with file manager sorting files and folders where ASCII initial letter names are put back
* `Fix` FAILED ASSERTION exception in code editor debug function
* `Fix` Issue with unable to debug again properly after closing editor during code editor debug process
* `Fix` Issue with potentially omitting end characters when jumping to line end in code editor
* `Fix` Issue with flickering screen when starting log activity page on main activity page
* `Fix` Issue with packaged app unable to use opencc module properly
* `Improvement` Improved tap hint experience for the "Unavailable ABI" control on the packaging app configuration page
* `Improvement` Supports using Shizuku to control 'Pointer Location' display switch
* `Improvement` Supports using Shizuku to control 'Projection Media' and 'Modify Secure Settings' permission switches
* `Improvement` Automator.gestureAsync/gesturesAsync supports callback function parameters
* `Improvement` tasks module uses synchronous way for database operations to avoid potential data access inconsistencies
* `Improvement` Script execution mode supports pipeline symbol separation mode parameters (e.g., starting with `"ui|auto";`)
* `Improvement` Script execution mode supports single quotes and backticks and allows omitting semicolons (e.g., starting with `'ui';` or `'ui'`)
* `Improvement` Script execution mode supports quick import of built-in extension modules such as axios, cheerio, and dayjs (e.g., starting with `"axios";`)
* `Improvement` Script execution mode supports x or jsox mode parameters for quick enabling of JavaScript built-in object extension modules (e.g., starting with `"x";`)
* `Improvement` img element src and path attributes support local relative paths (e.g., `<img src="a.png"` />)
* `Improvement` Code editor supports intelligently determining insertion location when importing Java classes and package names
* `Improvement` images module supports using paths directly as image parameters
* `Improvement` importPackage supports string parameters
* `Improvement` In client mode, the IP address supports smart recognition when importing from the clipboard and also supports intelligent conversion of space-key input
* `Improvement` File manager supports default prefix selection when creating new files and automatically generates appropriate numeric suffix
* `Improvement` File manager specifically informs on exception message when running project _[`issue #268`](http://issues.autojs6.com/268)_
* `Improvement` File manager supports more types and displays corresponding icon symbols (supports over 800 file types)
* `Improvement` Editable file types (jpg/doc/pdf/...) in file manager have added edit buttons
* `Improvement` APK files in file manager support viewing basic information, Manifest information, and permissions list
* `Improvement` Audio/video media files in file manager support viewing basic information and MediaInfo information
* `Improvement` Package single file support auto-fill appropriate standardized package name and invalid character filter prompt
* `Improvement` Package single file support automatically sets icon and auto-increments version number and version name based on installed same package name application
* `Improvement` Package configuration file supports abis/libs option to specify default included ABI architecture and libraries
* `Improvement` Support relevant message prompts when abis/libs options of package configuration file are invalid or unavailable
* `Improvement` LeakCanary is excluded from official release version to avoid unnecessary growth
* `Improvement` All English comments in project source code are accompanied by Simplified Chinese translations to enhance readability
* `Improvement` README and CHANGELOG support multi-language (Automatically generated by script)
* `Improvement` Enhance Gradle build script's version adaptability
* `Dependency` Include MIME Util version 2.3.1
* `Dependency` Include Toaster version 12.6
* `Dependency` Include EasyWindow (for Toaster) version 10.3
* `Dependency` Upgrade Gradle version from 8.5 -> 8.11.1
* `Dependency` Upgrade Rhino version 1.7.15-SNAPSHOT -> 1.8.0-SNAPSHOT
* `Dependency` Upgrade Android Material version 1.10.0 -> 1.12.0
* `Dependency` Upgrade AndroidX Annotation version 1.7.0 -> 1.9.1
* `Dependency` Upgrade AndroidX AppCompat version 1.6.1 -> 1.7.0
* `Dependency` Upgrade AndroidX WebKit version 1.8.0 -> 1.12.1
* `Dependency` Upgrade Apache Commons version 3.13.0 -> 3.16.0
* `Dependency` Upgrade ARSCLib version 1.2.4 -> 1.3.1
* `Dependency` Upgrade Gson version 2.10.1 -> 2.11.0
* `Dependency` Upgrade Jackson DataBind version 2.13.3 -> 2.13.4.2
* `Dependency` Upgrade Joda Time version 2.12.5 -> 2.12.7
* `Dependency` Upgrade LeakCanary version 2.12 -> 2.14
* `Dependency` Upgrade MLKit Barcode Scanning version 17.2.0 -> 17.3.0
* `Dependency` Upgrade MLKit Text Recognition Chinese version 16.0.0 -> 16.0.1
* `Dependency` Upgrade Retrofit2 Converter Gson version 2.9.0 -> 2.11.0
* `Dependency` Upgrade Retrofit2 Retrofit version 2.9.0 -> 2.11.0
* `Dependency` Upgrade Desugar JDK Libs version 2.0.3 -> 2.0.4
* `Dependency` Upgrade Test Runner version 1.5.2 -> 1.6.2
* `Dependency` Upgrade Junit Jupiter version 5.10.0 -> 5.10.3
* `Dependency` Downgrade OkHttp3 version 5.0.0-alpha.11 -> 4.12.0

# v6.5.0

###### 2023/12/02

* `Feature` opencc module (Refer to project documentation > [Chinese Conversion](https://docs.autojs6.com/#/opencc)) (Ref to [LZX284](https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-8cff73265af19c059547b76aca8882cbaa3209291406f52df1dafbbc78e80c46R268))
* `Feature` UiSelector adds [plus](https://docs.autojs6.com/#/uiObjectType?id=m-plus) and [append](https://docs.autojs6.com/#/uiObjectType?id=m-append) methods _[`issue #115`](http://issues.autojs6.com/115)_
* `Feature` Packaging app configuration page now supports filtering for ABI and libraries (Ref to [AutoX](https://github.com/kkevsekk1/AutoX)) _[`issue #189`](http://issues.autojs6.com/189)_
* `Fix` Abnormally large file size issue in packaged applications (Ref to [AutoX](https://github.com/kkevsekk1/AutoX) / [LZX284](https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-d932ac49867d4610f8eeb21b59306e8e923d016cbca192b254caebd829198856R61)) _[`issue #176`](http://issues.autojs6.com/176)_
* `Fix` Issue where packaged applications cannot display and print some exception messages
* `Fix` Fixed an issue where a blank app icon could appear after selecting an app icon on the packaging app configuration page
* `Fix` Context not initialized exception when packaging application includes MLKit Google OCR library
* `Fix` ocr.recognizeText/detect methods not working
* `Fix` Mismatch between displayed language and application setting language for some texts (such as log page)
* `Fix` Text overflow issue in home page drawer switch items for some languages
* `Fix` Issue where accessibility service automatically closes immediately after being enabled on some devices without any prompt messages _[`issue #181`](http://issues.autojs6.com/181)_
* `Fix` Issue where enabling accessibility service on some devices might cause application crash due to physical keys (attempted fix) _[`issue #183`](http://issues.autojs6.com/183)_ _[`issue #186`](http://issues.autojs6.com/186#issuecomment-1817307790)_
* `Fix` Issue with pickup functionality after restarting accessibility service using auto(true) (attempted fix) _[`issue #184`](http://issues.autojs6.com/184)_
* `Fix` Issue where creating floating window in floaty module might cause application crash when dragging (attempted fix)
* `Fix` Issue where app.startActivity cannot use shorthand parameters _[`issue #182`](http://issues.autojs6.com/182)_ _[`issue #188`](http://issues.autojs6.com/188)_
* `Fix` Issue where code throws exception when imported class name conflicts with global variable using importClass _[`issue #185`](http://issues.autojs6.com/185)_
* `Fix` Issue where accessibility service cannot be used on Android 7.x
* `Fix` Issue where runtime.loadJar/loadDex methods might not work properly on Android 14 (attempted fix)
* `Fix` Issue where 'Layout Bounds Analysis' and 'Layout Hierarchy Analysis' are unavailable in Android system quick settings panel _[`issue #193`](http://issues.autojs6.com/193)_
* `Fix` Issue where auto-update check might cause application [ANR](https://developer.android.com/topic/performance/vitals/anr) (attempted fix) _[`issue #186`](http://issues.autojs6.com/186)_
* `Fix` Issue where file manager cannot return to workspace path page after clicking 'Up' button in example code folder _[`issue #129`](http://issues.autojs6.com/129)_
* `Fix` Issue where replace button is not shown when using replace function in code editor
* `Fix` Issue where long-press delete in code editor might cause application crash (attempted fix)
* `Fix` Issue where fx button in code editor cannot show module function quick panel
* `Fix` Issue where module function quick panel button function name might overflow in code editor
* `Improvement` The code editor module's quick function panel adapts to night mode
* `Improvement` The startup page of the packaged application is adapted to night mode, and the layout of application icons is adjusted
* `Improvement` Packaging app configuration page now supports using the soft keyboard ENTER key to move the cursor focus
* `Improvement` Packaging app configuration page now supports tapping ABI headings and library headings to toggle select-all state
* `Improvement` Packaging app configuration page now uses smarter default ABI selection and adds guidance hints for unavailable options
* `Improvement` The file manager adjusts the display of menu items based on the type and characteristics of files and folders
* `Improvement` The file manager's folder right-click menu adds a packaging application option
* `Improvement` When accessibility services are enabled but malfunction, an abnormal state is reflected in the drawer switch of the AutoJs6 homepage
* `Improvement` The console includes detailed stack information when printing error messages.
* `Dependency` Added ARSCLib version 1.2.4
* `Dependency` Added Flexbox version 3.0.0
* `Dependency` Added Android OpenCC version 1.2.0
* `Dependency` Upgrade Gradle version from 8.5-rc-1 -> 8.5

# v6.4.2

###### 2023/11/15

* `Feature` dialogs.build method supports the inputSingleLine option parameter
* `Feature` Added console.setTouchable method _[`issue #122`](http://issues.autojs6.com/122)_
* `Fix` Issue where some methods in ocr module could not recognize region parameters _[`issue #162`](http://issues.autojs6.com/162)_  _[`issue #175`](http://issues.autojs6.com/175)_
* `Fix` Issue where version details could not be retrieved when a new version is found on Android 7.x
* `Fix` Issue where requesting screenshot permission on Android 14 caused application crash
* `Fix` Issue where quickly switching 'Floating Button' switch on the home page drawer might cause application crash
* `Fix` Issue where floating button might still be displayed after rebooting the application when it was closed using menu
* `Fix` Issue where changing AutoJs6 language in settings page could not take effect on systems Android 13 and above
* `Fix` Issue where initial build could not automatically complete OpenCV resource deployment using build tools
* `Improvement` Native implementation of bridges module to improve script execution efficiency (Ref to [aiselp](https://github.com/aiselp/AutoX/commit/7c41af6d2b9b36d00440a9c8b7e971d025f98327))
* `Improvement` Refactored accessibility service related code to enhance stability of accessibility service functionality (experimental) _[`issue #167`](http://issues.autojs6.com/167)_
* `Improvement` Enhanced print output format for UiObject and UiObjectCollection
* `Improvement` Added upgrade prompt when Gradle JDK version does not meet the requirements in build tools
* `Dependency` Upgraded Gradle version from 8.4 -> 8.5-rc-1
* `Dependency` Downgraded Commons IO version from 2.14.0 -> 2.8.0
* `Dependency` Downgraded Jackson DataBind version from 2.14.3 -> 2.13.3

# v6.4.1

###### 2023/11/02

* `Fix` Issue where build tools could not adapt to unknown platforms (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #158`](http://pr.autojs6.com/158)_
* `Fix` Issue where script exit might cause application crash _[`issue #159`](http://issues.autojs6.com/159)_
* `Fix` Incorrect return value type for body.contentType in http module's response object _[`issue #142`](http://issues.autojs6.com/142)_
* `Fix` Incorrect return data for device.width and device.height _[`issue #160`](http://issues.autojs6.com/160)_
* `Fix` Issue where long-press delete in code editor might cause application crash (attempted fix) _[`issue #156`](http://issues.autojs6.com/156)_
* `Fix` Issue where reverse selecting text in code editor followed by normal operations might cause application crash
* `Fix` Issue where long-pressing AutoJs6 application icon on some devices could not display shortcut menu
* `Fix` Issue where clicking confirm button was unresponsive when packaging project on some devices
* `Fix` Issue where app.sendBroadcast and app.startActivity could not use shorthand parameters
* `Fix` Functional anomaly when calling methods like JsWindow#setPosition in floaty module for the first time
* `Improvement` Added Termux related permissions to support Intent calls to Termux for executing ADB commands _[`issue #136`](http://issues.autojs6.com/136)_
* `Improvement` http module's response object can reuse body.string() and body.bytes() methods
* `Improvement` Added GitHub Actions support for automatic packaging (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #158`](http://pr.autojs6.com/158)_
* `Improvement` Build tools adapt to Temurin platform
* `Dependency` Upgraded Gradle version from 8.4-rc-3 -> 8.4
* `Dependency` Upgraded Android dx version from 1.11 -> 1.14

# v6.4.0

###### 2023/10/30

* `Feature` ocr module supports Paddle Lite engine (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #120`](http://pr.autojs6.com/120)_
* `Feature` Packaging function supports internal and external plugin packaging methods (by [LZX284](https://github.com/LZX284)) _[`pr #151`](http://pr.autojs6.com/151)_
* `Feature` WebSocket module (Refer to project documentation > [WebSocket](https://docs.autojs6.com/#/webSocketType))
* `Feature` barcode / qrcode modules (Refer to project documentation > [Barcode](https://docs.autojs6.com/#/barcode) / [QRCode](https://docs.autojs6.com/#/qrcode))
* `Feature` shizuku module (Refer to project documentation > [Shizuku](https://docs.autojs6.com/#/shizuku)) and permission switch in home page drawer
* `Feature` device.rotation / device.orientation methods
* `Feature` Support for static property access of internal Java classes
* `Feature` Support for selecting and switching application language in Android system settings page (Android 13 and above)
* `Feature` Added app shortcuts activation by adding or long-pressing application icon in settings page, allowing access to documents and settings etc. [App Shortcuts](https://developer.android.com/guide/topics/ui/shortcuts?hl=zh-cn)
* `Fix` Re-merged some PRs (by [aiselp](https://github.com/aiselp)) to solve the issue where some scripts could not end normally _[`pr #75`](http://pr.autojs6.com/75)_ _[`pr #78`](http://pr.autojs6.com/78)_
* `Fix` Issue where packaged applications could not use new APIs in AutoJs6 (by [LZX284](https://github.com/LZX284)) _[`pr #151`](http://pr.autojs6.com/151)_ _[`issue #149`](http://issues.autojs6.com/149)_
* `Fix` Styling anomaly in packaged applications under system night mode
* `Fix` Issue where file extension information was lost when saving files locally with VSCode plugin
* `Fix` Issue where running project with coroutine feature caused uncaught exceptions leading to application crash
* `Fix` Issue where floating button could not record its position state information when restarting or exiting application
* `Fix` Issue where updated device configuration information could not be retrieved when device screen orientation changed _[`issue #153`](http://issues.autojs6.com/153)_
* `Fix` Issue where Toolbar title font size was too small when screen rotated to landscape orientation
* `Fix` Issue where home page tabs layout was too crowded when screen rotated to landscape orientation
* `Fix` Issue where floating button might overflow the screen when screen rotated to landscape orientation _[`issue #90`](http://issues.autojs6.com/90)_
* `Fix` Issue where floating button's coordinates and screen edge directions could not be restored when screen rotated multiple times
* `Fix` Issue where message floating frame might miss or repeatedly display in some devices
* `Fix` Issue where message floating frame might be obstructed when multiple scripts are running simultaneously _[`issue #67`](http://issues.autojs6.com/67)_
* `Fix` Issue where clicking layout while analyzing layout with broadcast could not pop up menu and caused application crash
* `Fix` Issue where WebSocket instances created from the second time onwards could not normally trigger listeners
* `Fix` Reverted importPackage's global redirection method to avoid package import anomalies in some scopes _[`issue #88`](http://issues.autojs6.com/88)_
* `Fix` Issue where using copy or export functions in log activity page might cause application crash
* `Improvement` Renamed export function to send function and re-implemented meaningful export function in log activity page
* `Improvement` Added automatic interception and prompt when entry count is too large for send function in log activity page
* `Improvement` ocr module is compatible with both Google MLKit and Paddle Lite engines (Refer to project documentation > [Optical Character Recognition](https://docs.autojs6.com/#/ocr?id=p-mode))
* `Improvement` Increased the success rate of auto-starting accessibility service
* `Improvement` Migrated Kotlin annotation processor from kapt to KSP
* `Improvement` Build tools support IntelliJ Idea EAP versions
* `Improvement` Build tools adapt to Java release versions as much as possible to avoid 'invalid release version' issues
* `Improvement` Build tools optimize IDE and related plugin downgrade logic and add version prediction capability
* `Improvement` Adapted to VSCode plugin 1.0.7
* `Dependency` Added Rikka Shizuku version 13.1.5
* `Dependency` Added MLKit Barcode Scanning version 17.2.0
* `Dependency` Upgraded Android SDK version from 33 -> 34
* `Dependency` Upgraded OpenCV version from 4.5.5 -> 4.8.0 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `Dependency` Upgraded Gradle version from 8.3-rc-1 -> 8.4-rc-3
* `Dependency` Upgraded Apache Commons Lang3 version from 3.12.0 -> 3.13.0
* `Dependency` Upgraded Glide version from 4.15.1 -> 4.16.0
* `Dependency` Upgraded Android Analytics version from 14.3.0 -> 14.4.0
* `Dependency` Upgraded AndroidX WebKit version from 1.7.0 -> 1.8.0
* `Dependency` Upgraded AndroidX Preference version from 1.2.0 -> 1.2.1
* `Dependency` Upgraded AndroidX Annotation version from 1.6.0 -> 1.7.0
* `Dependency` Upgraded AndroidX Recyclerview version from 1.3.0 -> 1.3.2
* `Dependency` Upgraded Android Material version from 1.9.0 -> 1.10.0
* `Dependency` Upgraded AndroidX AppCompat version from 1.4.2 -> 1.6.1
* `Dependency` Upgraded Commons IO version from 2.8.0 -> 2.14.0
* `Dependency` Upgraded Jackson DataBind version from 2.13.3 -> 2.14.3
* `Dependency` Removed Zeugma Solutions LocaleHelper version 1.5.1

# v6.3.3

###### 2023/07/21

* `Feature` Code commenting feature in code editor (by [little-alei](https://github.com/little-alei)) _[`pr #98`](http://pr.autojs6.com/98)_
* `Feature` auto.stateListener for accessibility service connection status listening (by [little-alei](https://github.com/little-alei)) _[`pr #98`](http://pr.autojs6.com/98)_
* `Feature` Added nextSibling / lastChild / offset methods to UiObject type (Refer to project documentation > [UiObject](https://docs.autojs6.com/#/uiObjectType))
* `Fix` Issue where VSCode plugin could not parse data when script character total length exceeded four-decimal digits _[`issue #91`](http://issues.autojs6.com/91)_ _[`issue #93`](http://issues.autojs6.com/93)_ _[`issue #100`](http://issues.autojs6.com/100)_ _[`issue #109`](http://issues.autojs6.com/109)_
* `Fix` Issue where VSCode plugin could not save file properly _[`issue #92`](http://issues.autojs6.com/91)_ _[`issue #94`](http://issues.autojs6.com/93)_
* `Fix` Issue where clicking 'Manage Accessibility Service' in floating button menu might not trigger page navigation
* `Fix` Issue where runtime.requestPermissions method was missing _[`issue #104`](http://issues.autojs6.com/104)_
* `Fix` Issue where events.emitter did not support MainThreadProxy parameter _[`issue #103`](http://issues.autojs6.com/103)_
* `Fix` Code formatting issue in code editor present in _[`pr #78`](http://pr.autojs6.com/78)_
* `Fix` StackOverflow error issue in ClassLoader call stack when using JavaAdapter _[`issue #99`](http://issues.autojs6.com/99)_ _[`issue #110`](http://issues.autojs6.com/110)_
* `Improvement` Adjusted module scope (by [aiselp](https://github.com/aiselp)) _[`pr #75`](http://pr.autojs6.com/75)_ _[`pr #78`](http://pr.autojs6.com/78)_
* `Improvement` Removed signature verification during launch for release version apps (by [LZX284](https://github.com/LZX284)) _[`pr #81`](http://pr.autojs6.com/81)_
* `Improvement` Enhanced editor code commenting behavior, style, and cursor position handling based on _[`pr #98`](http://pr.autojs6.com/98)_
* `Improvement` Added code commenting menu item based on _[`pr #98`](http://pr.autojs6.com/98)_
* `Improvement` Adapted to VSCode plugin 1.0.6
* `Improvement` Added level parameter support to UiObject#parent method (Refer to project documentation > [UiObject](https://docs.autojs6.com/#/uiObjectType))
* `Dependency` Upgraded Gradle version from 8.2 -> 8.3-rc-1

# v6.3.2

###### 2023/07/06

* `Feature` crypto module (Refer to project documentation > [Crypto](https://docs.autojs6.com/#/crypto)) _[`issue #70`](http://issues.autojs6.com/70)_
* `Feature` Added textswitcher / viewswitcher / viewflipper / numberpicker / video / search controls to UI mode
* `Feature` Added copy and export log functionalities to Log Activity Page _[`issue #76`](http://issues.autojs6.com/76)_
* `Feature` Added IP address history feature to Client Mode
* `Fix` Issue where IP address information might not be displayed after auto-connecting in Client Mode or auto-starting in Server Mode
* `Fix` Issue where connection is lost and cannot be reconnected after switching language or night mode in Client Mode and Server Mode
* `Fix` Issue where custom port could not be used when entering target address in Client Mode
* `Fix` Issue where some characters cause AutoJs6 to crash when entering target address in Client Mode
* `Fix` Issue where remote commands in VSCode plugin might fail to parse, causing commands to be unresponsive (tentative fix)
* `Fix` Issue where version details could not be fetched when a new version is discovered on Android 7.x
* `Fix` Issue where images.pixel could not get pixel color values from accessibility service screenshots _[`issue #73`](http://issues.autojs6.com/73)_
* `Fix` Issue where built-in properties could not be used with Android native controls (uppercase) in UI mode
* `Fix` Issue where runtime.loadDex/loadJar only the first file took effect when loading multiple files _[`issue #88`](http://issues.autojs6.com/88)_
* `Fix` Issue where launcher only displayed document icon when installing app on some devices (tentative fix) _[`issue #85`](http://issues.autojs6.com/85)_
* `Improvement` Adapted to VSCode plugin 1.0.5
* `Improvement` Supported cheerio module (Refer to [aiselp](https://github.com/aiselp/AutoX/commit/7176f5ad52d6904383024fb700bf19af75e22903)) _[`issue #65`](http://issues.autojs6.com/65)_
* `Improvement` JsWebSocket instance supports rebuild method to recreate instance and establish connection _[`issue #69`](http://issues.autojs6.com/69)_
* `Improvement` base64 module supports number array and Java byte array as main parameters for encoding and decoding
* `Improvement` Added support for JavaMail for Android _[`issue #71`](http://issues.autojs6.com/71)_
* `Improvement` Used Blob data type when fetching version update information to enhance adaptability in proxy-free network environments
* `Improvement` Display target IP address in home page drawer subtitle during Client Mode connection
* `Improvement` Added prompt for invalid input when entering target address in Client Mode
* `Improvement` Supported using enter key on soft keyboard to establish connection in Client Mode
* `Improvement` Kept Server Mode active after being enabled (unless manually closed or app process ended) _[`issue #64`](http://issues.autojs6.com/64#issuecomment-1596990158)_
* `Improvement` Implemented bidirectional version detection and prompt abnormal results between AutoJs6 and VSCode plugin _[`issue #89`](http://issues.autojs6.com/89)_
* `Improvement` Added reading SMS data permission (android.permission.READ_SMS) (default off)
* `Improvement` Internal implementation of findMultiColors method (by [LYS86](https://github.com/LYS86)) _[`pr #72`](http://pr.autojs6.com/72)_
* `Improvement` Supported loading by directory level or simultaneously loading multiple files with runtime.loadDex/loadJar/load
* `Dependency` Upgraded LeakCanary version from 2.11 -> 2.12
* `Dependency` Upgraded Android Analytics version from 14.2.0 -> 14.3.0
* `Dependency` Upgraded Gradle version from 8.2-milestone-1 -> 8.2

# v6.3.1

###### 2023/05/26

* `Feature` Release notification permission and home page drawer switch _[`issue #55`](http://issues.autojs6.com/55)_
* `Feature` UI mode supports simple Android layout parsing (Refer to Sample Code > Layout > Simple Android Layout)
* `Feature` Added console / imagebutton / ratingbar / switch / textclock / togglebutton controls to UI mode
* `Feature` UI mode supports [OmniColor](https://docs.autojs6.com/#/omniTypes?id=omnicolor) type for control color values (e.g., color="orange")
* `Feature` Fully supports attr method for control properties in UI mode (e.g., ui.text.attr('color', 'blue'))
* `Feature` Supports concise form for boolean type property values in UI mode (e.g., clickable="true" can be shortened to clickable or isClickable)
* `Feature` Button control supports isColored and isBorderless boolean properties
* `Feature` console.resetGlobalLogConfig method for resetting global log configuration
* `Feature` web.newWebSocket method for creating WebSocket instance (Refer to project documentation > [Web](https://docs.autojs6.com/#/web?id=m-newwebsocket))
* `Fix` Abnormal folder sorting in File Manager
* `Fix` Issue where floaty module built floating window could not adjust style and position _[`issue #60`](http://issues.autojs6.com/60)_
* `Fix` Issue where floaty module built floating window overlapped with system status bar
* `Fix` Functional anomaly in http.postMultipart method _[`issue #56`](http://issues.autojs6.com/56)_
* `Fix` Issue where scripts could not run on Android 7.x _[`issue #61`](http://issues.autojs6.com/61)_
* `Fix` Issue where project could not be built when sign.property file was missing
* `Fix` Issue where AutoJs6 might crash when placed in the background due to lack of foreground notification permission in higher versions (API >= 33)
* `Fix` Issue where floating button in log window could not clear logs after invoking console.show method
* `Fix` NullPointerException related to prototype during script debugging in script editor
* `Fix` Issue where script editor ran temporary scripts in cache folder instead of saving and running at original location, to prevent potential loss of script content
* `Fix` Adjusted level color bar width in layout analysis to avoid control names not displaying when there are too many levels _[`issue #46`](http://issues.autojs6.com/46)_
* `Improvement` Added exit button to close window in layout analysis floating window _[`issue #63`](http://issues.autojs6.com/63)_
* `Improvement` Used abbreviated form for absolute script paths to reduce text length and increase readability
* `Improvement` Replaced use of Error with Exception to avoid crashing AutoJs6 on exceptions
* `Improvement` Migrated view binding method from ButterKnife to View Binding _[`issue #48`](http://issues.autojs6.com/48)_
* `Improvement` Auto-started Server Mode on AutoJs6 launch when it was abnormally closed _[`issue #64`](http://issues.autojs6.com/64)_
* `Improvement` Auto-reconnected last used address in Client Mode on AutoJs6 launch when it was abnormally closed
* `Dependency` Upgraded LeakCanary version from 2.10 -> 2.11
* `Dependency` Upgraded Android Material version from 1.8.0 -> 1.9.0
* `Dependency` Upgraded AndroidX WebKit version from 1.6.1 -> 1.7.0
* `Dependency` Upgraded OkHttp3 version from 3.10.0 -> 5.0.0-alpha.9 -> 5.0.0-alpha.11
* `Dependency` Upgraded MLKit Text Recognition Chinese version from 16.0.0-beta6 -> 16.0.0

# v6.3.0

###### 2023/04/29

* `Feature` ocr module (refer to project documentation > [Optical Character Recognition](https://docs.autojs6.com/#/ocr)) _[`issue #8`](http://issues.autojs6.com/8)_
* `Feature` notice module (refer to project documentation > [Message Notification](https://docs.autojs6.com/#/notice))
* `Feature` s13n module (refer to project documentation > [Serialization](https://docs.autojs6.com/#/s13n))
* `Feature` Color module (refer to project documentation > [Color Type](https://docs.autojs6.com/#/colorType))
* `Feature` colors.setColorPaint method for setting android.graphics.Paint brush color (refer to project documentation > [Color Type](https://docs.autojs6.com/#/colorType?id=m-setpaintcolor))
* `Feature` Keep the screen on functionality and setting options while in the foreground
* `Feature` Additional documentation launcher for independent reading of application documents (support hiding or showing in settings)
* `Fix` Abnormal function of colors.toString method
* `Fix` Abnormal function of app.openUrl method automatically adding protocol prefix
* `Fix` Abnormal behavior of app.viewFile/editFile when the parameter corresponding file does not exist
* `Fix` Callback function of pickup method cannot be called
* `Fix` The bounds attribute value of control information displayed by layout analysis is replaced by a comma symbol
* `Fix` Selectors bounds/boundsInside/boundsContains cannot normally filter narrow empty rectangles (e.g., inverted boundary rectangles) _[`issue #49`](http://issues.autojs6.com/49)_
* `Fix` Issue causing app crash when clicking or long pressing the home document tab after changing theme or language
* `Fix` Text editor jitter issue when zooming font size with two fingers
* `Fix` Issue with some dependency sources unable to be downloaded in build scripts (now fully integrated) _[`issue #40`](http://issues.autojs6.com/40)_
* `Fix` Issue with Tasker unable to add AutoJs6 Action Plugin (tentative fix) _[`issue #41`](http://issues.autojs6.com/41)_
* `Fix` Issue with ButterKnife annotations unable to parse resource ID when compiling projects with higher version JDKs _[`issue #48`](http://issues.autojs6.com/48)_
* `Fix` Higher probability of accessibility services encountering service exceptions (tentative fix)
* `Fix` Issue with images.medianBlur size parameter usage not matching documentation
* `Fix` Issue with engines module losing the period symbol between file name and extension when displaying full script name
* `Fix` Potential computational error in the weighted RGB distance detection algorithm internal implementation (tentative fix)
* `Fix` Issue with console module floating window related methods not usable before the show method
* `Fix` Issue with console.setSize and other methods possibly not taking effect _[`issue #50`](http://issues.autojs6.com/50)_
* `Fix` Color constants assignment error in colors.material color space
* `Fix` Issue with UI mode date picker minDate and maxDate attributes unable to correctly parse date format
* `Fix` Issue with switching to the home "Task" tab page quickly after running the script displaying two identical running tasks
* `Fix` Issue with file management page state possibly being reset when returning from other pages _[`issue #52`](http://issues.autojs6.com/52)_
* `Fix` Issue with file management page sort state not matching icon display state
* `Improvement` Added file and folder modification time display on the file management page
* `Improvement` File management page sort type supports status memory
* `Improvement` README.md added sections for project build and script development assistance _[`issue #33`](http://issues.autojs6.com/33)_
* `Improvement` Region option parameter of images module related methods supports more input methods (refer to project documentation > [Omni Types](https://docs.autojs6.com/#/omniTypes?id=omniregion))
* `Improvement` Added pref/homepage/docs/about etc. support for shorthand parameters in app.startActivity
* `Improvement` Mounted global methods of web module to the module itself to enhance availability (refer to project documentation > [World Wide Web](https://docs.autojs6.com/#/web))
* `Improvement` Default implementation of some commonly used WebView setting options in web.newInjectableWebView method
* `Improvement` Added various conversion methods, utility methods, more static constants and color names that can be directly used as parameters in colors module
* `Improvement` Added various console floating window style configuration methods in console module and support for unified window style configuration by build builder
* `Improvement` Support dragging title area to move window position for console floating window
* `Improvement` Support automatic delayed closing after script ends for console floating window
* `Improvement` Support adjusting font size with pinch-to-zoom for both console floating window and its Activity window
* `Improvement` Support timeout option parameter for http module related methods
* `Improvement` Support active downgrade (fallback) of JDK version in Gradle build script
* `Improvement` Support for Gradle build script to automatically select appropriate build tool versions based on platform type and version (to some extent)
* `Dependency` Localized Auto.js APK Builder version 1.0.3
* `Dependency` Localized MultiLevelListView version 1.1
* `Dependency` Localized Settings Compat version 1.1.5
* `Dependency` Localized Enhanced Floaty version 0.31
* `Dependency` Added MLKit Text Recognition Chinese version 16.0.0-beta6
* `Dependency` Upgraded Gradle version 8.0-rc-1 -> 8.2-milestone-1
* `Dependency` Upgraded Android Material version 1.7.0 -> 1.8.0
* `Dependency` Upgraded Glide version 4.14.2 -> 4.15.1
* `Dependency` Upgraded Joda Time version 2.12.2 -> 2.12.5
* `Dependency` Upgraded Android Analytics version 14.0.0 -> 14.2.0
* `Dependency` Upgraded AndroidX WebKit version 1.5.0 -> 1.6.1
* `Dependency` Upgraded AndroidX Recyclerview version 1.2.1 -> 1.3.0
* `Dependency` Upgraded Zip4j version 2.11.2 -> 2.11.5
* `Dependency` Upgraded Junit Jupiter version 5.9.2 -> 5.9.3
* `Dependency` Upgraded AndroidX Annotation version 1.5.0 -> 1.6.0
* `Dependency` Upgraded Jackson DataBind version 2.14.1 -> 2.14.2
* `Dependency` Upgraded Desugar JDK Libs version 2.0.0 -> 2.0.3

# v6.2.0

###### 2023/01/21

* `Feature` Redesign and rewrite of project documentation (partially completed)
* `Feature` Multi-language adaptation for Western/France/Russian/Arabic/Japanese/Korean/English/Traditional Chinese, etc
* `Feature` Added path selection/history/default value intelligent suggestion functions to the work path setting options
* `Feature` File manager supports jumping to the parent directory of any directory (up to "Internal Storage")
* `Feature` File manager supports setting any directory as the work path shortcut
* `Feature` Version update ignore and manage ignored update functions
* `Feature` Text editor supports zooming font size with two fingers
* `Feature` idHex selector (UiSelector#idHex) (refer to project documentation > [Selector](https://docs.autojs6.com/#/uiSelectorType))
* `Feature` action selector (UiSelector#action) (refer to project documentation > [Selector](https://docs.autojs6.com/#/uiSelectorType))
* `Feature` Match series selectors (UiSelector#xxxMatch) (refer to project documentation > [Selector](https://docs.autojs6.com/#/uiSelectorType))
* `Feature` pickup selector (UiSelector#pickup) (refer to project documentation > [Selector](https://docs.autojs6.com/#/uiSelectorType)) _[`issue #22`](http://issues.autojs6.com/22)_
* `Feature` Control detection (UiObject#detect) (refer to project documentation > [Control Node](https://docs.autojs6.com/#/uiObjectType))
* `Feature` Control compass (UiObject#compass) (refer to project documentation > [Control Node](https://docs.autojs6.com/#/uiObjectType)) _[`issue #23`](http://issues.autojs6.com/23)_
* `Feature` Global wait method wait (refer to project documentation > [Global Object](https://docs.autojs6.com/#/global?id=m-wait))
* `Feature` Global scaling methods cX/cY/cYx (refer to project documentation > [Global Object](https://docs.autojs6.com/#/global?id=m-wait))
* `Feature` Global App type (refer to project documentation > [Application Enumeration](https://docs.autojs6.com/#/appType))
* `Feature` i18n module (JavaScript-based multi-language solution based on banana-i18n) (refer to project documentation > Internationalization)
* `Fix` Page text flickering and some page button function anomalies after software language switching
* `Fix` Issue with project toolbar not displaying after software startup when work path is a project
* `Fix` Issue with work path possibly changing automatically following software language switching _[`issue #19`](http://issues.autojs6.com/19)_
* `Fix` Significant delay in scheduled task startup (tentative fix) _[`issue #21`](http://issues.autojs6.com/21)_
* `Fix` Issue with internal modules with dependencies unable to be used normally when JavaScript module name is overwritten _[`issue #29`](http://issues.autojs6.com/29)_
* `Fix` Issue with Android higher versions possibly not automatically collapsing the quick settings panel after clicking relevant icons (tentative fix) _[`issue #7`](http://issues.autojs6.com/7)_
* `Fix` Issue with overlapping of some pages and notification bar area on higher version Andes systems
* `Fix` Issue with example code setting brush color not running normally on Android 10 and above
* `Fix` Corrected example code "Music Manager" to "File Manager" and restored normal function
* `Fix` Issue with possible localization drift when refreshing file manager
* `Fix` Issue with binding error in ui module scope causing some UI-based scripts to not access component properties
* `Fix` Issue with losing recorded content due to external area click in the dialog box when entering file name after recording script
* `Fix` Issue with automatic line-wrapping failure causing content loss when some chapter titles in the document exceed screen width
* `Fix` Issue with example code area in the document unable to slide left and right normally
* `Fix` Issue with abnormal performance and inability to undo refresh operation when refreshing document page
* `Fix` Issue with night mode linkage failure of home drawer after initial installation
* `Fix` Issue with night mode forced on after application startup when system night mode is on
* `Fix` Issue with theme color possibly not taking effect after night mode is turned on
* `Fix` Issue with some setting options text and background color being the same and unrecognizable in night mode
* `Fix` Issue with incomplete display of function button text on the about page due to excessive text length
* `Fix` Issue with text and button overlap on the home drawer settings item title due to excessive length
* `Fix` Issue with root permission switch status not synchronized after prompt message dialog disappears on home drawer permission
* `Fix` Issue with no ADB tool dialog popping up when root permission fails to modify home drawer permission switches
* `Fix` Issue with no root permission prompt during initial use of pointer position display
* `Fix` Abnormal layout of icon elements on the icon selection page
* `Fix` Issue with screen flickering caused by night mode setting when starting the text editor (tentative fix)
* `Fix` Issue with the maximum available value limited when setting font size in text editor
* `Fix` Issue with runtime duration not being logged in the log after script execution ends in some Android systems
* `Fix` Issue with floating button still displayed after application restart when floating button menu close button was used
* `Fix` Issue with menu overflow below long press list item possibly causing pop-up menu overflow screen during layout hierarchy analysis
* `Fix` Issue with navigation bar buttons hard to identify on Android 7.x system when night mode is off
* `Fix` Possible issue with request not closing in http.post and other methods
* `Fix` Issue with loss of alpha channel information in the result when colors.toString method alpha channel is 0
* `Improvement` Redirected public classes of Auto.js 4.x version to achieve downward compatibility as far as possible (to some extent)
* `Improvement` Merged all project modules to avoid possible circular references and other issues (temporarily removed inrt module)
* `Improvement` Migrated Gradle build configuration from Groovy to KTS
* `Improvement` Added multi-language support to Rhino exception messages
* `Improvement` Home drawer permission switch prompts message only when enabled
* `Improvement` Home drawer layout closely aligned under the status bar to avoid the top color strip's low compatibility
* `Improvement` Check for updates/download updates/update prompt functions compatible with Android 7.x system
* `Improvement` Redesigned settings page (migrated to AndroidX)
* `Improvement` Settings page supports long press on settings options to get detailed information
* `Improvement` Added "Follow System" setting option in night mode (Android 9 and above)
* `Improvement` Application startup screen adapted for night mode
* `Improvement` Added numeric identifier to application icon to enhance the user experience of coexistence with multiple open-source versions
* `Improvement` Added more Material Design Color options to theme color
* `Improvement` Appropriately lightweighted and theme color compatible icons for list items such as file manager/task panel
* `Improvement` Text color of prompt text in home search box adapted for night mode
* `Improvement` Adapted dialog box/text/Fab/AppBar/list items and other components for night mode
* `Improvement` Adapted document/settings/about/theme color/layout analysis and other pages and floating button menu for night mode
* `Improvement` Page layout as compatible as possible with RTL (Right-To-Left) layout
* `Improvement` Added icon animation effects on the about page
* `Improvement` Automatically updated year information in copyright statement text on the about page
* `Improvement` Automatically determined and set appropriate work directory after initial installation
* `Improvement` Disabled pinch-to-zoom function on document pages to avoid abnormal document content display
* `Improvement` Simplified task name and path display in relative path on Task Panel list items
* `Improvement` Appropriately abbreviated button text to avoid text overflow in text editor
* `Improvement` Supported resetting the font size setting to the default value in text editor
* `Improvement` Enhanced floating button click response speed
* `Improvement` Directly performed layout range analysis when clicking floating button layout analysis button
* `Improvement` Adaptive layout analysis theme (floating window follows application theme, quick settings panel follows system theme)
* `Improvement` Reordered layout control information list according to possible frequency of use
* `Improvement` Automatically optimized output format according to selector type when clicking to copy layout control information
* `Improvement` Supported returning to the parent directory instead of directly closing the window when selecting files with a floating window and pressing the return key
* `Improvement` Supported digital validity detection and automatic conversion of dot-separated symbols when entering address in client mode to connect to computer
* `Improvement` Displayed IP address of corresponding device in home drawer after client and server establish a connection
* `Improvement` Added rewrite protection to some global objects and built-in modules (refer to project documentation > Global Object > [Rewrite Protection](https://docs.autojs6.com/#/global?id=%e8%a6%86%e5%86%99%e4%bf%9d%e6%8a%a4))
* `Improvement` Supported string parameters and variable-length parameters in importClass and importPackage
* `Improvement` Supported printing stack trace information when exception occurs in ui.run
* `Improvement` Conveniently obtained resource ID of AutoJs6 through ui.R and auto.R
* `Improvement` Supported App type parameters and application alias parameters in methods related to operating applications in app module
* `Improvement` Supported omission of pre-filled parameters in methods related to asynchronous callbacks in dialogs module
* `Improvement` Supported url option parameters in app.startActivity, etc. (refer to example code > Application > Intent)
* `Improvement` Returned null instead of throwing exceptions when failing to get IMEI or hardware serial number in device module
* `Improvement` Enhanced text brightness of the log floating window displayed by console.show to improve content recognition
* `Improvement` Supported saving image files with relative paths in ImageWrapper#saveTo
* `Improvement` Redesigned global colors object and added support for HSV / HSL and other color modes (refer to project documentation > [Color](https://docs.autojs6.com/#/color))
* `Dependency` Localized Android Job version 1.4.3
* `Dependency` Localized Android Plugin Client SDK For Locale version 9.0.0
* `Dependency` Localized GitHub API version 1.306
* `Dependency` Upgraded Android SDK version 32 -> 33
* `Dependency` Added JCIP Annotations version 1.0
* `Dependency` Added AndroidX WebKit version 1.5.0
* `Dependency` Added Commons IO version 2.8.0
* `Dependency` Added Desugar JDK Libs version 2.0.0
* `Dependency` Added Jackson DataBind version 2.13.3
* `Dependency` Added Jaredrummler Android Device Names version 2.1.0
* `Dependency` Added Jaredrummler Animated SVG View version 1.0.6
* `Dependency` Replaced Jrummyapps ColorPicker version 2.1.7 with Jaredrummler ColorPicker version 1.1.0
* `Dependency` Upgraded Gradle version 7.5-rc-1 -> 8.0-rc-1
* `Dependency` Upgraded Gradle build tool version 7.4.0-alpha02 -> 8.0.0-alpha09
* `Dependency` Upgraded Kotlin Gradle plugin version 1.6.10 -> 1.8.0-RC2
* `Dependency` Upgraded Android Material version 1.6.0 -> 1.7.0
* `Dependency` Upgraded AndroidX Annotation version 1.3.0 -> 1.5.0
* `Dependency` Upgraded AndroidX AppCompat version 1.4.1 -> 1.4.2
* `Dependency` Upgraded Android Analytics version 13.3.0 -> 14.0.0
* `Dependency` Upgraded Gson version 2.9.0 -> 2.10
* `Dependency` Upgraded Joda Time version 2.10.14 -> 2.12.1
* `Dependency` Upgraded Kotlinx Coroutines version 1.6.1-native-mt -> 1.6.1
* `Dependency` Upgraded OkHttp3 version 3.10.0 -> 5.0.0-alpha.7 -> 5.0.0-alpha.9
* `Dependency` Upgraded Zip4j version 2.10.0 -> 2.11.2
* `Dependency` Upgraded Glide version 4.13.2 -> 4.14.2
* `Dependency` Upgraded Junit Jupiter version 5.9.0 -> 5.9.1

# v6.1.1

###### 2022/05/31

* `Feature` Check for updates/download updates/update prompt functions (refer to Settings page) (currently not supported on Android 7.x systems)
* `Fix` Issue with the application unable to read/write external storage on Android 10 systems _[`issue #17`](http://issues.autojs6.com/17)_
* `Fix` Issue where long presses on the editor page could cause the application to crash _[`issue #18`](http://issues.autojs6.com/18)_
* `Fix` Issue with the long-press menu 'Delete Row' and 'Copy Row' functions not working on the editor page
* `Fix` Issue with the 'Paste' function missing from the options menu on the editor page
* `Improvement` Resource localization of some exception message strings (en / zh)
* `Improvement` Adjust the button layout of the unsaved content dialog and add color differentiation
* `Dependency` Added github-api version 1.306
* `Dependency` Replaced retrofit2-rxjava2-adapter version 1.0.0 with adapter-rxjava2 version 2.9.0

# v6.1.0

###### 2022/05/26 - Package name changed, proceed with caution

* `Hint` Changed the application package name to org.autojs.autojs6 to avoid conflicts with the open-source Auto.js application package name
* `Feature` Added 'Projection Media Permission' switch to the home drawer (Root / ADB method) (experimental switch status detection)
* `Feature` File browser supports displaying hidden files and folders (refer to Settings page)
* `Feature` Forced Root check function (refer to Settings page and example code)
* `Feature` autojs module (refer to Example Code > AutoJs6)
* `Feature` tasks module (refer to Example Code > Tasks)
* `Feature` console.launch() method to launch the log activity page
* `Feature` util.morseCode tool (refer to Example Code > Tools > Morse Code)
* `Feature` util.versionCodes tool (refer to Example Code > Tools > Android Version Information Query)
* `Feature` util.getClass() and other methods (refer to Example Code > Tools > Get Class and Class Names)
* `Feature` timers.setIntervalExt() method (refer to Example Code > Timers > Conditional Periodic Execution)
* `Feature` colors.toInt() / rgba() and other methods (refer to Example Code > Image and Color > Basic Color Conversion)
* `Feature` automator.isServiceRunning() / ensureService() methods
* `Feature` automator.lockScreen() and other methods (refer to Example Code > Accessibility Service > Android 9 New Additions)
* `Feature` automator.headsethook() and other methods (refer to Example Code > Accessibility Service > Android 11 New Additions)
* `Feature` automator.captureScreen() method (refer to Example Code > Accessibility Service > Take Screenshots)
* `Feature` dialogs.build method supports option parameters such as animation, linkify, etc. (refer to Example Code > Dialogs > Customized Dialogs)
* `Fix` inputHint, itemsSelectedIndex, and other option parameters of dialogs.build behave abnormally
* `Fix` Issue with JsDialog#on('multi_choice') callback parameters
* `Fix` Issue with UiObject#parent().indexInParent() always returning -1 _[`issue #16`](http://issues.autojs6.com/16)_
* `Fix` Issue with Promise.resolve() returned Thenable not being called near the end of the script
* `Fix` Possible spelling errors in package or class names (boardcast -> broadcast / auojs -> autojs)
* `Fix` Issue with images.requestScreenCapture() potentially causing application crashes on higher Android versions (API >= 31)
* `Fix` Issue with images.requestScreenCapture() potentially causing application crashes when multiple script instances request it simultaneously
* `Fix` Possible deadlock issue when invoking new RootAutomator()
* `Improvement` RootAutomator cannot be instantiated without Root permissions
* `Improvement` Redesigned the 'About Application and Developer' page
* `Improvement` Refactored all built-in JavaScript modules
* `Improvement` Refactored all Gradle build scripts and added a common configuration script (config.gradle)
* `Improvement` Gradle build tools support automatic version number management and automatic naming of build files
* `Improvement` Gradle build tools add task support to append CRC32 digest to build files (appendDigestToReleasedFiles)
* `Improvement` shell() calls will write exceptions into the return result instead of directly throwing exceptions (no need for try/catch)
* `Improvement` Replaced original json2 module with Rhino built-in JSON
* `Improvement` auto.waitFor() supports timeout parameters
* `Improvement` threads.start() supports arrow function parameters
* `Improvement` console.trace() supports log level parameters (refer to Example Code > Console > Print Call Stack)
* `Improvement` device.vibrate() supports mode vibration and Morse code vibration (refer to Example Code > Device > Mode Vibration / Morse Code Vibration)
* `Improvement` Adapted read/write permissions for external storage to higher Android versions (API >= 30)
* `Improvement` Enhanced font readability in both normal and night themes for the console using Material Color
* `Improvement` Saved all instances of ImageWrapper as weak references and automatically recovered them when the script ends (experimental)
* `Dependency` Added CircleImageView version 3.1.0
* `Dependency` Upgraded Android Analytics version 13.1.0 -> 13.3.0
* `Dependency` Upgraded Gradle build tools version 7.3.0-alpha06 -> 7.4.0-alpha02
* `Dependency` Upgraded Android Job version 1.4.2 -> 1.4.3
* `Dependency` Upgraded Android Material version 1.5.0 -> 1.6.0
* `Dependency` Upgraded CrashReport version 2.6.6 -> 4.0.4
* `Dependency` Upgraded Glide version 4.13.1 -> 4.13.2
* `Dependency` Upgraded Joda Time version 2.10.13 -> 2.10.14
* `Dependency` Upgraded Kotlin Gradle plugin version 1.6.10 -> 1.6.21
* `Dependency` Upgraded Kotlinx Coroutines version 1.6.0 -> 1.6.1-native-mt
* `Dependency` Upgraded LeakCanary version 2.8.1 -> 2.9.1
* `Dependency` Upgraded OkHttp3 version 5.0.0-alpha.6 -> 5.0.0-alpha.7
* `Dependency` Upgraded Rhino engine version 1.7.14 -> 1.7.15-SNAPSHOT
* `Dependency` Upgraded Zip4j version 2.9.1 -> 2.10.0
* `Dependency` Removed Groovy JSON version 3.0.8
* `Dependency` Removed Kotlin Stdlib JDK7 version 1.6.21

# v6.0.3

###### 2022/03/19

* `Feature` Multi-language switch function (not yet perfect)
* `Feature` recorder module (refer to Example Code > Timers)
* `Feature` Use 'Modify Secure Settings Permissions' to automatically enable accessibility services and toggle settings
* `Fix` Issue with quick settings panel not automatically collapsing after clicking relevant icons (tentative fix) _[`issue #7`](http://issues.autojs6.com/7)_
* `Fix` Issue where AutoJs6 might crash when the toast is forced to display
* `Fix` Issue with AutoJs6 possibly crashing when the Socket transmission data header information is incomplete
* `Improvement` Automatically enable accessibility services according to option settings when starting or restarting AutoJs6
* `Improvement` Attempt to automatically enable accessibility services when the floating button switch is turned on
* `Improvement` Added corresponding English translations for all resource file elements
* `Improvement` Slightly adjusted the layout of the home drawer to reduce project item spacing
* `Improvement` Added synchronization of foreground service status switch in the home drawer
* `Improvement` Synchronize switch status on demand immediately when the home drawer is expanded
* `Improvement` Added status detection and result prompts for displaying pointer position
* `Improvement` Supported 64-bit operating systems (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `Improvement` Applied transparency settings simultaneously when initializing the floating button (no need to click to apply transparency)
* `Improvement` Added detection of whether the file is example code and result prompt when resetting file content
* `Improvement` Transferred plugin download address from GitHub to JsDelivr
* `Dependency` Added Zeugma Solutions LocaleHelper version 1.5.1
* `Dependency` Downgraded Android Material version 1.6.0-alpha02 -> 1.5.0
* `Dependency` Upgraded Android SDK version 31 -> 32
* `Dependency` Upgraded Kotlinx Coroutines version 1.6.0-native-mt -> 1.6.0
* `Dependency` Upgraded OpenCV version 3.4.3 -> 4.5.4 -> 4.5.5 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `Dependency` Upgraded OkHttp3 version 3.10.0 -> 5.0.0-alpha.4 -> 5.0.0-alpha.6
* `Dependency` Upgraded Gradle build tools version 7.2.0-beta01 -> 7.3.0-alpha06
* `Dependency` Upgraded Auto.js-ApkBuilder version 1.0.1 -> 1.0.3
* `Dependency` Upgraded Glide Compiler version 4.12.0 -> 4.13.1
* `Dependency` Upgraded Gradle release version 7.4-rc-2 -> 7.4.1
* `Dependency` Upgraded Gson version 2.8.9 -> 2.9.0

# v6.0.2

###### 2022/02/05

* `Feature` images.bilateralFilter() bilateral filtration image processing method
* `Fix` Issue where multiple calls to toast only took effect on the last call
* `Fix` Issue where toast.dismiss() might not work
* `Fix` Issues with client mode and server mode switches possibly not working properly
* `Fix` Issues with client mode and server mode switch statuses not refreshing properly
* `Fix` Issue with parsing UI mode text elements on Android 7.x (ref to [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`issue #4`](http://issues.autojs6.com/4)_ _[`issue #9`](http://issues.autojs6.com/9)_
* `Improvement` Ignore sleep()'s ScriptInterruptedException
* `Dependency` Added AndroidX AppCompat (Legacy) version 1.0.2
* `Dependency` Upgraded AndroidX AppCompat version 1.4.0 -> 1.4.1
* `Dependency` Upgraded AndroidX Preference version 1.1.1 -> 1.2.0
* `Dependency` Upgraded Rhino engine version 1.7.14-SNAPSHOT -> 1.7.14
* `Dependency` Upgraded OkHttp3 version 3.10.0 -> 5.0.0-alpha.3 -> 5.0.0-alpha.4
* `Dependency` Upgraded Android Material version 1.6.0-alpha01 -> 1.6.0-alpha02
* `Dependency` Upgraded Gradle build tools version 7.2.0-alpha06 -> 7.2.0-beta01
* `Dependency` Upgraded Gradle release version 7.3.3 -> 7.4-rc-2

# v6.0.1

###### 2022/01/01

* `Feature` Support for VSCode plugin connection in client (LAN) and server (LAN/ADB) modes (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Feature` base64 module (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `Feature` Added global methods isInteger/isNullish/isObject/isPrimitive/isReference
* `Feature` Added polyfill (Object.getOwnPropertyDescriptors)
* `Feature` Added polyfill (Array.prototype.flat)
* `Improvement` Extended global.sleep to support random ranges/negative numbers
* `Improvement` Extended global.toast to support duration control/forced overlay control/dismiss
* `Improvement` Globalized package name objects (okhttp3/androidx/de)
* `Dependency` Upgraded Android Material version 1.5.0-beta01 -> 1.6.0-alpha01
* `Dependency` Upgraded Gradle build tools version 7.2.0-alpha04 -> 7.2.0-alpha06
* `Dependency` Upgraded Kotlinx Coroutines version 1.5.2-native-mt -> 1.6.0-native-mt
* `Dependency` Upgraded Kotlin Gradle plugin version 1.6.0 -> 1.6.10
* `Dependency` Upgraded Gradle release version 7.3 -> 7.3.3

# v6.0.0

###### 2021/12/01

* `Feature` Added a restart app button at the bottom of the home drawer
* `Feature` Added switches for ignoring battery optimization/showing on top of other apps in the home drawer
* `Fix` Fixed issue where initial installation caused theme color rendering issues in some areas
* `Fix` Fixed issue where the project could not be built when the sign.property file does not exist
* `Fix` Fixed incorrect month storage/read errors for one-time tasks in the scheduled task panel
* `Fix` Fixed issue where switch colors in the app settings page did not change with the theme
* `Fix` Fixed issue where packing plugins could not be detected and invalid plugin download addresses
* `Fix` Fixed issue where the switch state of 'View Usage Stats Permission' in the home drawer might not sync
* `Fix` Fixed potential Mat memory leak issue in TemplateMatching.fastTemplateMatching
* `Improvement` Upgraded Rhino engine version 1.7.7.2 -> 1.7.13 -> 1.7.14-SNAPSHOT
* `Improvement` Upgraded OpenCV version 3.4.3 -> 4.5.4
* `Improvement` Enhanced compatibility of ViewUtil.getStatusBarHeight
* `Improvement` Removed user login related modules and layout placeholders from the home drawer
* `Improvement` Removed community and market tab pages from the home and optimized the layout
* `Improvement` Modified default switch states for some settings options
* `Improvement` Added SinceDate to the About page and optimized copyright display
* `Improvement` Upgraded JSON module to the 2017-06-12 version and integrated cycle.js
* `Improvement` Removed automatic update check functionality when Activity is brought to the foreground and removed related update check buttons
* `Improvement` Improved internal logic of AppOpsKt#isOpPermissionGranted
* `Improvement` Enhanced security of ResourceMonitor using ReentrantLock (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `Improvement` Replaced JCenter repositories with Maven Central and other repositories
* `Improvement` Extracted and removed duplicate local library files
* `Dependency` Localized CrashReport version 2.6.6
* `Dependency` Localized MutableTheme version 1.0.0
* `Dependency` Added AndroidX Preference version 1.1.1
* `Dependency` Added SwipeRefreshLayout version 1.1.0
* `Dependency` Upgraded Android SDK version 28 -> 30 -> 31
* `Dependency` Upgraded Android Analytics version 7.0.0 -> 13.1.0
* `Dependency` Upgraded Android Annotations version 4.5.2 -> 4.8.0
* `Dependency` Upgraded Gradle build tools version 3.2.1 -> 4.1.0 -> 7.0.3 -> 7.2.0-alpha04
* `Dependency` Upgraded Android Job version 1.2.6 -> 1.4.2
* `Dependency` Upgraded Android Material version 1.1.0-alpha01 -> 1.5.0-beta01
* `Dependency` Upgraded AndroidX MultiDex version 2.0.0 -> 2.0.1
* `Dependency` Upgraded Apache Commons Lang3 version 3.6 -> 3.12.0
* `Dependency` Upgraded Appcompat version 1.0.2 -> 1.4.0
* `Dependency` Upgraded ButterKnife Gradle plugin version 9.0.0-rc2 -> 10.2.1 -> 10.2.3
* `Dependency` Upgraded ColorPicker version 2.1.5 -> 2.1.7
* `Dependency` Upgraded Espresso Core version 3.1.1-alpha01 -> 3.5.0-alpha03
* `Dependency` Upgraded Eventbus version 3.0.0 -> 3.2.0
* `Dependency` Upgraded Glide Compiler version 4.8.0 -> 4.12.0
* `Dependency` Upgraded Gradle Build Tool version 29.0.2 -> 30.0.2
* `Dependency` Upgraded Gradle release version 4.10.2 -> 6.5 -> 7.0.2 -> 7.3
* `Dependency` Upgraded Groovy-Json plugin version 3.0.7 -> 3.0.8
* `Dependency` Upgraded Gson version 2.8.2 -> 2.8.9
* `Dependency` Upgraded Java version 8 -> 11 -> 16
* `Dependency` Upgraded Joda Time version 2.9.9 -> 2.10.13
* `Dependency` Upgraded Junit version 4.12 -> 4.13.2
* `Dependency` Upgraded Kotlin Gradle plugin version 1.3.10 -> 1.4.10 -> 1.6.0
* `Dependency` Upgraded Kotlinx Coroutines version 1.0.1 -> 1.5.2-native-mt
* `Dependency` Upgraded LeakCanary version 1.6.1 -> 2.7
* `Dependency` Upgraded LicensesDialog version 1.8.1 -> 2.2.0
* `Dependency` Upgraded Material Dialogs version 0.9.2.3 -> 0.9.6.0
* `Dependency` Upgraded OkHttp3 version 3.10.0 -> 5.0.0-alpha.2 -> 5.0.0-alpha.3
* `Dependency` Upgraded Reactivex RxJava2 RxAndroid version 2.0.1 -> 2.1.1
* `Dependency` Upgraded Reactivex RxJava2 version 2.1.2 -> 2.2.21
* `Dependency` Upgraded Retrofit2 Converter Gson version 2.3.0 -> 2.9.0
* `Dependency` Upgraded Retrofit2 Retrofit version 2.3.0 -> 2.9.0
* `Dependency` Upgraded Zip4j version 1.3.2 -> 2.9.1