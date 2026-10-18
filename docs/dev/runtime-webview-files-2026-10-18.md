# WebView camera, SAF and streamed downloads

Date: 2026-10-18 (Asia/Taipei). Local development candidates, not a release.

This batch fills the audited WebView photo capture, save/tree selection and
blob/data download gaps for Node.js CJS/main ESM, CJS/ESM/nested Workers and Bun.
It also fixes the project execution budget exposed by the complete packaged Bun
camera workflow. This is not a claim of complete Rhino API equivalence.

## Delivered behavior

- `chooseFiles` accepts open/multiple/directory/save and Android's 0/1/2/3 modes.
  Save returns a newly created document, directory returns a document tree, and
  `initialUri` remains a provider-dependent hint. Directory writes are requested
  by default and can be disabled; save always requires write access. API 37's
  native `permissionMode` is forwarded without inventing support in older WebViews.
- Image `captureEnabled` invokes the real system camera with a full-size temporary
  JPEG output. Declared but missing CAMERA permission uses the platform dialog.
  Denial/cancellation returns null. The default HTML picker and custom
  `onShowFileChooser(e) { return this.chooseFiles(e); }` path both work.
  Camera FileProvider authority is resolved from the installed application's
  manifest, including APK Builder's rewritten package. Completed camera handles
  remain valid while their View or transfer owns them, then their files are deleted.
- `readDocument(uri, offset, length)` returns Uint8Array, including in Workers.
  Offset defaults to 0; reads return at most 32768 bytes and an empty array at EOF.
- `download` handles same-page blob URLs, percent/base64 data URLs and readable
  content URIs, plus writable `destinationUri` or `directoryUri` output. Blob
  fetches use demand-driven chunks of at most 32 KiB without a JavaScript interface.
  Percent decoding preserves arbitrary binary bytes and literal plus signs.
- HTTP(S) default downloads retain DownloadManager and numeric IDs. Page/local/SAF
  transfers use opaque `web:` IDs; get/read/cancel accept either. Native local
  failures include an error string while reason remains numeric. Transfers stage
  their bytes before publishing a completed file. A destination with existing
  content is rejected. Removing a completed explicit SAF job preserves its file;
  removing a completed default managed job deletes the managed file.
- Navigation, closing the source window and View disposal cancel unfinished blob
  reads. Completed files survive source disposal. Newly acquired persistent URI
  grants are reference-counted across Views and transfers; pre-existing grants
  are preserved. Native local jobs do not resume after process termination.
- Bun `project.json` now accepts `bun.timeoutMs`, including runnable projects
  without APK metadata and packaged applications. The default remains 60000 ms;
  the existing runtime range is 1..600000 ms. An explicit execution argument
  `bun.timeoutMillis` takes precedence. The fixture declares 180000 ms and checks
  real project timeout enforcement plus explicit override separately.

## Synchronization and coverage

The portable Node facade is synchronized into the native bootstrap and Bun SDK.
Node/Bun declarations and all 70 selected Ace declaration assets include chooser
options, document reads, download IDs, destinations and state fields. Documentation,
offline HTML/search assets and ten-language release notes are updated. Offline
provenance points to the exact documentation commit
`93df3029f53a180307a43203cf99a3bf60688bc3`.

The cross-repository gate executes the real facades and the production blob stream
helper, verifies 11 WebView file/client methods and 40 callback names, and exercises
CJS/ESM/nested Worker forwarding. The stream regression checks a 2 MiB + 137-byte
payload, backpressure, cancellation, revoked blobs and unsupported streams. Strict
TypeScript 5.1.3 and 6.0.3 checks pass. Ace's semantic check performs 387 queries.
The full explicit-registration gate covers 1351 exports and 1522 queried paths
with no failures; its documented exclusions and overload limits still apply.

## Physical and packaged validation

| Device | Android/API | ABI | Kernel page size |
|---|---|---|---|
| Redmi 22120RN86C | 13 / 33 | arm64-v8a | 4096 bytes |
| Xiaomi 23046RP50C | 15 / 35 | arm64-v8a | 4096 bytes |

There are 153 selected passing instrumentation cases: per device, 44 Node runtime,
13 Bun runtime, 7 existing WebView, 5 new WebView file, 1 Bun project timeout and
4 genuine APK production/execution cases; Xiaomi additionally runs 5 Ace cases.
Development failures and repeat runs are retained separately and are not added
to this selected count.

Each new six-mode suite checks real camera output, cancellation, uploads and
temporary-file release; real system save and tree dialogs with read/write grants;
existing-content rejection; complete 2 MiB + 137-byte blob integrity; percent/base64
bytes; EOF/range reads; revoked sources; cancellation/navigation; and completed
files after View disposal. The page-link case separately clicks actual blob/data
anchors through Node, nested Worker and Bun and verifies download events and bytes.
Bun also exercises the default HTML camera picker without a custom Chrome client.

Four independently signed APK Builder applications (Node and Bun on both devices)
use application versionCode 1 and embedded runtimeVersionCode 5566. They perform
actual HTTP downloads, blob byte verification, system camera capture and upload,
content-URI photo copying into a system-selected document, full saved-photo
SHA-256 comparison against the uploaded JPEG, tree output and preserved SAF files.
Existing WebView clients, XML widgets, Drawer/Toolbar/ViewPager, AndroidView,
Compose state and callbacks across rotation remain in the same packaged flow.
All packaged runtime DEX hashes match the final Runtime Kit template, and their
signing certificates differ from the host's certificate.

Ace runs under its restored default preferences and checks real completion popups
and tooltips for Node/Bun/WebView document reads and directory options, plus
existing opt-in JSOX behavior. It restores its preferences and rotation afterward.

## Builds and local checks

| JVM suite | Cases | Skipped under existing conditions |
|---|---:|---:|
| AutoJs6 | 4717 | 26 |
| AutoJs6-Plugin-NodeJs-Runtime | 96 | 0 |
| AutoJs6-Plugin-Bun-Runtime | 59 | 0 |
| AutoJs6-Plugin-Ace-Editor | 187 | 0 |
| AutoJs6-Plugin-APK-Builder-Template | 39 | 0 |
| AutoJs6-Plugin-Offline-Docs | 2 | 0 |

Total: 5100 cases, 26 skipped, zero failures/errors. Host app/test APKs
and the real Runtime Kit build successfully. Node NDK syntax/native builds,
Node/Bun/Ace/Builder debug and test APKs, and Offline Docs debug/release APK content
gates pass. Additional checks include 27 Bun JS client tests, 38 Bun Python tests,
the unchanged Bun compatibility matrix, Node wrapper/Worker regressions, the API
extractor regressions, document freshness, declaration synchronization and all
related Markdown generators. TypeScript 5.1.3 was fetched into task-local ignored
storage after the machine's shared npm cache could not supply it.

## Corrections made during acceptance

The real DocumentsUI fixture needed its provider's document paths, and camera
automation needed the actual shutter and confirmation controls. WebView exposes
link labels as text or content descriptions; the final link fixture waits for the
page's click acknowledgment while retaining the native download and byte checks.
These harness failures are not counted as API passes. The complete packaged Bun
flow exposed the real 60-second project budget gap; the host now reads the explicit
project setting, and execution-only project parsing is verified independently.

## Candidate versions and evidence

| Repository | Candidate |
|---|---|
| AutoJs6 | 6.8.0, build 3914, Android versionCode 5566 |
| Node.js Runtime | 1.12.0, build 260 |
| Bun Runtime | 0.2.11, build 258 |
| TypeScript Declarations | 4.57.0 |
| Ace Editor | 2.19.0, build 294 |
| Documentation | 6.8.0, revision 134 |
| Offline Docs | 6.8.14, build 165 |
| APK Builder Template | 1.2.4, build 79, paired host 5566 |

Ignored evidence is under `.bak/webview-files-20261018`: `evidence.json`, installation
receipts and logs, selected APK hashes, `certificates.json`, numeric camera results,
`owned-file-cleanup.json`, `restoration.json`, and final scoped commit records.
The Runtime Kit records source base `500973b52a0566f30ac4dcf6a011c1c41f5ebb41`;
the candidate source hashes identify the local changes above that base. It is not
presented as an untouched build of that baseline commit.

Only photo sizes, dimensions and verification hashes are retained, not camera
pixels. Owned camera/document/download fixtures are removed. Both devices' original
APK bytes are restored and SHA-256 checked; task-only packaged apps are removed.
Original camera/audio permission flags, media volume and rotation settings are
restored, and no host projection remains active. The five pre-existing host prose
drafts remain outside this batch's commits.

## Boundaries

Video/audio capture intents are not part of this image-camera addition. Raw paths
and arbitrary private FileProvider content are not introduced. Native HTML folder
and save requests depend on WebView; explicit SAF selection works independently.
API 37 callback fields are compiled and checked structurally, not physically certified
on these API 33/35 devices. HTTP-to-SAF routing is implemented but the device matrix
above specifically exercises default HTTP and blob/data/content SAF output. Cloud
document providers, restart-resume, every OEM, 16 KiB devices and the full signed
release matrix are not certified by this batch. Bun's frozen native payload and
published compatibility status are unchanged. No push, tag or release was performed.
