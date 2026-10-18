# Portable WebView and navigation components - 2026-10-18

This batch adds WebView, Drawer, Toolbar and ViewPager to the portable native UI
contract used by Node main, CommonJS/ESM/nested Workers and Bun. Documentation,
declarations, Ace suggestions, offline docs and the genuine APK Builder Runtime
Kit are synchronized. This is local development acceptance, not publication.

## Delivered behavior

- WebView: HTML and URL loading, decoded JSON results from evaluateJavascript,
  JavaScript/DOM-storage settings, history, reload/stop, native page/title/progress
  and error events. JavaScript and DOM storage default to disabled. Android
  networking, file/content and web permission policies remain effective; there is
  no Java interface injection or automatic certificate/permission bypass.
- Drawer: one content child, start/end panel layout parameters, open/close,
  lock mode and native slide/state/open/close events. Invalid duplicate sides or
  content layouts are rejected before a subsequent Android layout pass.
- Toolbar: title/subtitle, resource navigation icon, accessibility label, menu
  descriptors, navigation/menu events and overflow-menu commands. Native theme
  presentation is retained; a Toolbar is not automatically an Activity ActionBar.
- ViewPager: a fixed page list, current index, offscreen limit, page count,
  smooth/immediate switching and native page events. Offscreen descendants remain
  searchable and mutable through handles. Cleanup traverses all pages, including
  those absent from the current Android child hierarchy.
- Twelve new property names bring the writable surface to 58; eleven read-only
  async accessors and twelve component commands are declared. Commands and
  assignments share a queue per wrapper. Identity serialization remains data-only.
  Typed createView options also accept child/page trees during creation while
  scalar set() continues to reject child replacement.
- WebViews are released on root/execution cleanup, renderer exit and failed
  inflation. Pending JavaScript results fail on destruction or the bounded native
  callback timeout. No implicit page Promise awaiting is provided.

## Lifecycle correction found during regression

The Redmi interop suite exposed a live borrowed View accepting dispose after a
rotation sequence; the isolated factory test passed. Source inspection identified
that renderer teardown releases its physical AndroidView claims while the Compose
session still retains its bindings. The host now keeps an execution-local borrower
on each accepted native binding until replacement or session close, independently
of renderer recreation. It prevents disposal (including borrowed descendants),
showLayout reuse and a second session borrowing the same View during that gap.
The existing renderer's process-wide ownership checks remain in place.

A later real Activity relaunch exposed a second bug: legacy List state contains
selectedIndex=-1, and selection callbacks also contain text/value fields that
are event data rather than List/Tabs View properties. The restoration path now
accepts the native unselected index, filters those event-only fields and lets the
Tabs factory consume its selection. Equal text/value writes no longer emit a
duplicate initialization input event during restoration. The live provider test explicitly recreates
its Activity before selection and again after input/tab changes. Native property
tests also clear a Spinner selection in every runtime mode. Replacing Tabs content
now destroys nested WebViews, and creation rollback tracking releases its strong
references after a completed inflation; the disposal test covers tab replacement.


The rotation regression now repeatedly tries to dispose the borrowed View while
its session is active, keeps the exact two-click/state assertions, and then verifies
normal disposal after closing the session. The four-case interop class passes on
both physical devices with this change. Host and INRT builds, the Runtime Kit,
Builder APK and packaged outputs were rebuilt after the correction.

## Accepted physical verification

Both devices are native arm64 with 4096-byte pages:

| Suite | Redmi 22120RN86C, API 33 | Xiaomi 23046RP50C, API 35 |
| --- | ---: | ---: |
| New components | 4 | 4 |
| Common XML widgets/properties/images | 7 | 7 |
| Native View/Compose interop | 4 | 4 |
| UI/Compose runtime behavior | 7 | 7 |
| Legacy UI provider | 9 | 9 |
| Node npm/conformance/Workers | 44 | 44 |
| Bun Binder/runtime | 13 | 13 |
| Genuine APK production and installed execution | 4 | 4 |
| Ace default-setting completion | - | 5 |
| Selected accepted total | 92 | 97 |

The 189 total counts the selected passing JUnit results once per device/suite,
not every retry or every inner runtime-mode iteration. New component scenarios
run in Node CJS, Node ESM, CJS Worker, ESM Worker, nested Worker and Bun. They
verify HTML/URL title changes, decoded objects, browser history, native callbacks,
actual menu/panel interaction, offscreen page lookup, retained WebView DOM after
rotation, invalid-operation recovery and native WebView destruction. Local HTML
and data URLs are used; external web servers, download/file-chooser flows and web
media permissions are not claimed as tested.

The production RemoteApkBuildClient generates and installs Node and Bun apps on
both devices. Each has application versionCode 1, embedded runtimeVersionCode
5564 and an independently verified signing certificate different from the host.
Binary Manifest runtime permissions are checked, and every packaged classes*.dex
matches the final Runtime Kit template by SHA-256. The apps exercise the four new
components before the existing XML/AndroidView/Compose click and rotation flow.
Bun loads the actual SDK as an encrypted project dependency. No synthetic APK or
applicationId replacement substitutes for the production builder.

## Local verification

- Host: 4717 JVM cases collected, 4691 passed, 26 existing skips; zero failures.
- Node 96, Bun 59, Ace 187, APK Builder 39 and Offline Docs 2 JVM cases passed.
- Current NDK syntax, Node example/type tasks and desktop CJS/ESM/Worker wrappers
  pass. Bun host client: 27 tests passed. Its frozen official native bytes remain
  unchanged and APK runtime-integrity checks pass.
- TypeScript 5.1.3 and 6.0.3: strict checks of 70 synchronized runtime declaration
  files. Ace: 362 queries across 11 runtime routes plus five physical popup tests,
  including XML tags/attributes, Promise signatures and JSOX isolation/regression.
- Native coverage checks compare all 58 writable properties, 16 additional widget
  factories and 12 commands with native handlers, serializers and both type sets.
  Compose coverage remains 55 constructors/12 operations; native View RPCs are 8.
- The broader host gate covers 91 registration routes, 1351 exports and 1522 Ace
  queries without failures. These counts measure host docs/types/completion and
  do not mean 1351 direct Node/Bun implementations.
- All 163 documentation modules regenerate and compare cleanly. Offline assets
  match their source; provenance pins documentation commit 311c2e5ad933fc7c884edbc230f7e01c1812dbd0.
  Ten-language note generation, Python checks and git diff whitespace checks pass.

## Preserved failures and cleanup

Failed attempts remain in the ignored evidence directory and are excluded from
accepted totals. Fixture corrections include matching Android's uppercase action
menu display while asserting the original event title/id, checking Ace's actual
XML attribute help text, replacing the obsolete WebView-denial assertion with
real rendering/close acceptance, and using bounded current-node actions in image,
geometry and shared UI helpers. A snackbar-only run showed a landscape window
without its transient snackbar while auto-rotate was enabled. The action fixture
now freezes/restores orientation; deliberate Activity recreation remains covered
separately. A failed Bun interaction interrupted its Binder
call and disabled the uncertain lane for that host process; its protection was
not weakened. The corrected fixtures run in fresh instrumentation processes.

Both devices were restored to their original APK SHA-256 values and settings
(rotation, microphone permission flags and media volume); owned packaged apps
that were absent initially were removed. Existing application data was retained.
The five original host draft changes are preserved separately from these commits.
Ignored .bak/ui-components-20261018 retains selected/failed logs, result JSON,
Ace screenshots, exact APK hashes, certificates, source-input hashes, original
state and restoration receipts. Verified original APK backup copies are removed
only after restoration. Custom desktop fixture directories are moved under their
repository's .bak. The existing oi-check checkout/signing files are preserved.

## Remaining boundaries

This remains an explicit portable component/command contract. Arbitrary Java
reflection, complete Rhino XML expression binding, custom synchronous WebViewClient
or WebChromeClient callbacks, downloads/file selection and automatic Activity
ActionBar integration are not implemented by this batch. Bridge grants and budgets
still apply. Other ABIs/OEMs, physical 16 KB execution and release publication are
not claimed. See runtimeApis for the exact asynchronous method/property semantics.
