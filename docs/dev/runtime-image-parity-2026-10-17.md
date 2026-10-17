# Advanced image delivery for Node, Bun and Workers

Date: 2026-10-17 (Asia/Taipei). This batch closes the remaining registered image
function-name gaps from the runtime API audit. It is coordinated development
source, not a published release or a claim of complete Rhino/Java object parity.

## Coverage and behavior

The host Images augment registers **81 functions**. Node image/images, Bun
host.images and the Node CJS/ESM/nested Worker facades now provide counterparts
of all 81. Each public client has **83 callable members**: those functions, the
existing readImage bridge name and an ObjectFrame constructor. Type checks count
82 methods plus the constructor separately. Host work is asynchronous; size and
recycle bookkeeping, region builders and ObjectFrame geometry stay local.

The new host routes cover advanced filters and color spaces, multi-color searches,
counts and means, circles/concat, similarities, SIFT/ORB, native matrices, image
loading, compression, PNG quantization and capture options. Client adapters add the
remaining aliases and portable argument forms. They call real host/OpenCV/Image
Quantization implementations, without rhino.run, no-ops or fabricated results.

- Native channel information survives cvtColor and subsequent transforms. The
  RGBA -> RGB -> HSV -> RGB -> RGBA round trip is checked against actual pixels.
  Bilateral filtering preserves alpha. Straight-alpha range/mean/filter checks
  use 0x80ff0000 rather than only opaque images.
- Mat and feature descriptors use opaque, execution-owned handles. imread reads a
  BGR Mat; matToImage makes an independent RGB image. Features keep their grayscale
  working data after the input image is recycled. Images, Mats and features share
  the existing handle quota and release path. Delivered handles survive their
  creating Worker, but cannot be reused in another execution; undeliverable late
  Worker handles are recycled.
- SIFT/ORB options support regions, scale and feature counts. Matching restores
  scene coordinates after region/scale transforms, returns null on no match and
  reconstructs ObjectFrame methods in Workers. Result fields include corners,
  center, dimensions, rotation, bounds, matches, inliers and inlier points.
- PNG quantization reports encoded bytes, size, measured quality/error and native
  working-memory usage. Node retains PFD/Buffer transport, including Worker Buffer
  reconstruction; Bun retains its existing Base64 JSON limits. quantizeToFile
  streams to a temporary file descriptor and publishes only on success, preserving
  an existing destination when quantization fails. Execution cleanup cancels native
  quantization. PNG compression below quality 100 uses the quantization provider.
- Node load requires image and network. Bun file/URL loading, imread, saves and
  match drawings require image.files; quantizeToFile also requires image.process.
  Raw calls cannot bypass these checks. File-only Bun grants can now recycle their
  own image/Mat handles without enabling processing.
- The images/autojs6:images declarations now retain the image type namespace.
  Parameter types distinguish image, Mat and feature handles, preventing Mat or
  descriptor values from accidentally type-checking as bitmap pixel inputs.

Portable signatures are documented in runtimeApis and the canonical declarations.
Java Bitmap/Mat methods, arbitrary Java enums and all Rhino coercion/proportional
region overloads are not implied by the 81-function count. Regions here use image
pixels. isRecycled is local client bookkeeping; other threads/raw calls are not
reflected until an actual host operation validates the handle. Bun's 64 KiB request
and 256 KiB response limits remain; large encoded results should use file output.

## Physical validation

Both devices use native arm64-v8a and 4096-byte pages. The exact installed APKs are
hash-verified before testing. OpenCV 1.1.5 and Image Quantization 1.1.0 are rebuilt
from their existing sources for validation; those provider repositories are unchanged.

| Device / suite | Passed | Time |
| --- | --- | --- |
| Redmi 22120RN86C, Android 13/API 33: complete advanced image suite | 9 | 51.746 s |
| Xiaomi 23046RP50C, Android 15/API 35: same complete suite | 9 | 28.382 s |
| Redmi: existing image pixels/ownership and binary/JSON transport regressions | 2 | 11.458 s |
| Xiaomi: same existing image regressions | 2 | 6.414 s |
| Redmi: complete npm ecosystem, Android conformance and Worker desktop classes | 44 | 142.197 s |
| Xiaomi: same complete Node runtime classes | 44 | 50.310 s |
| Redmi: complete Bun runtime/Binder/lifecycle class | 13 | 28.699 s |
| Xiaomi: default Ace API/runtime popups and JSOX activation/removal | 4 | 73.052 s |
| Redmi: final straight-alpha filter/range/mean regression | 1 | 15.366 s |
| Xiaomi: same final straight-alpha regression | 1 | 7.065 s |

The main matrix passes **127 tests**; the final alpha checks add **2**, for **129
accepted test executions**. Early smoke/diagnostic runs are not counted. An initial
Bun regression invocation omitted its required API-level runner argument and failed
in setup; the accepted run supplies the measured API 33 and 4096-byte page size.

Four image fixtures each run in six modes per device: Node CJS main, main ESM
import, CJS Worker, ESM Worker, nested Worker and Bun. Additional cases check Worker
handle handoff, execution isolation, file/network/processing permissions, a shared
32-handle image/Mat/feature quota and real Android capture consent/options.

| Matching fixture | Redmi | Xiaomi |
| --- | --- | --- |
| SIFT inliers, identical across all six modes on each device | 203 | 199 |
| ORB inliers, identical across all six modes on each device | 181 | 183 |
| Largest absolute X or Y error at expected top-left (60, 50) | 0.831 px | 0.956 px |

These are fixture observations, not general accuracy guarantees. Checks also cover
the no-feature null result, independent Mat lifetime after recycle, PNG palette
encoding and decoded dimensions, real HTTP and file paths, native pixel/quality
budget failures, destination preservation and resource cleanup. Reported palette
quality is read from the provider, not assumed to equal maxQuality. No photographs,
microphone recordings or other personal media are used; fixture files are removed.

## Local gates and synchronized surfaces

- The structural gate reads all 81 actual host registrations and checks both
  runtime clients, all ten Worker modules, canonical host routes, development
  catalog operations and identical portable adapter bodies.
- Real embedded Node adapter and CJS/ESM/nested Worker tests pass, including Frame
  constructors, buffers, resource cleanup, regions, aliases and invalid paths.
  Existing image/OpenCC, Worker, recording and 59-dispatch host-wrapper checks pass.
- All 69 copied declaration files match their sources. Strict TypeScript 5.1.3
  and 6.0.3 checks pass, including native-resource rejection, namespaced aliases,
  ObjectFrame construction and typed quantization/feature results. Node example
  and TypeScript smoke Gradle tasks and Bun's own SDK type fixture pass.
- Ace's full LSP verifier passes, including 238 runtime API queries. Real popups
  show feature and quantization return types and result fields with default
  settings and normal budgets. No Java/native pointer completions are exposed.
- Android builds pass, with 96 Node, 59 Bun, 187 Ace and 2 Offline Docs JVM tests.
  The current NDK syntax command passes before C++ builds. Three release bridge
  payload records are refreshed from freshly stripped outputs. Bun runtime and
  supervisor integrity checks pass without rebuilding their native payloads.
- Host coverage remains 91 mounts, 1351 explicit exports and 1522 type/completion
  queries with zero failures; 8 extractor tests pass. These aggregate numbers do
  not mean all AutoJs6 APIs are implemented in Node/Bun.
- Documentation generation checks all 163 modules. Offline normalization and its
  3 Python tests, Node's 2 Python tests, Bun's 38 Python tests and compatibility
  matrix freshness checks pass. New notes cover all ten languages. The host's
  22 generated Markdown files match their sources; unrelated translation-count
  differences and the five original Simplified Chinese draft diffs are preserved.

## Delivery and restoration

| Repository | Version / commit |
| --- | --- |
| Host | 6.8.0, build 3908, versionCode 5560, this commit |
| Node Runtime | development 1.7.0/build 254, `4aa0d85a` |
| Bun Runtime | development 0.2.6/build 252, `1f52a8e` |
| Documentation | 6.8.0/code 128, `300e43f` |
| TypeScript Declarations | 4.51.0, `245f548` |
| Ace Editor | 2.13.0/build 288, `cb8bd68` |
| Offline Docs | 6.8.8/build 159, `d2f9ce9` |

An unrelated Explorer commit df95d822d arrived during this batch. The final host
build in the existing ignored `.bak/oi-check` is refreshed to that committed base
and overlaid only with owned source/test files. Explorer changes are preserved and
are not included again in this commit. No published runtime-kit/catalog snapshot
is rewritten, and no push, tag, npm publication or release is performed.

Logs, per-device numeric JSON, popup screenshots, tested APK hashes and restoration
receipts are in `.bak/image-parity-20261017`. The changed APKs on both devices,
RECORD_AUDIO permission and flags, media volume and rotation settings are restored
and verified; no MediaProjection remains. Restoration APK copies are deleted after
verification. Necessary validation artifacts and test fixtures remain under the
repositories' ignored `.bak` directories; the existing build directory is retained.

The remaining broader audit includes Android-object helpers, calendar/IntentTask,
complete UI/Compose and AI/flow/TTS/mail/EPUB surfaces. It also retains the explicit
Java-object/prototype and overload boundaries above instead of counting them as
direct cross-runtime parity.
