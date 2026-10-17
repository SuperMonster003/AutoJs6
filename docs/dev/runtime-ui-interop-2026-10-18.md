# Node.js/Bun UI interoperability delivery - 2026-10-18

This development batch adds execution-owned native View handles, asynchronous
AndroidView factories and XML Compose container attachment to Node main,
CommonJS/ESM/nested Workers and Bun. It also runs actual independently signed
Node/Bun project APKs produced by the APK Builder plugin. Nothing was published.

## Delivered behavior

- `ui.context`, `inflate`, `createView`, `findView` and `view` expose opaque
  identities and asynchronous native attributes/events. XML strings and JSON
  descriptors use the existing supported widget renderer, including Frame and
  Compose containers. XML is bounded and refuses DTD/entity declarations.
- `compose.AndroidView` borrows an execution-owned View or the result of an
  asynchronous JavaScript factory. The factory receives data and asynchronous
  constructors, not an Android Context or an Android main-thread callback.
  Stable component identity retains the factory result.
- `compose.attach` mounts into a managed Compose container. Replacing its session,
  rotating the Activity, closing the window, disposing a detached container and
  ending the execution release/reconnect the appropriate owners. Parented,
  duplicate, foreign and disposed View handles and containment cycles fail.
- Delivered View identities survive Worker exit within the parent execution.
  Worker-owned windows/attached compositions close on exit. Undelivered late View
  creations are disposed. `ui.view` restores methods after structuredClone;
  coordinate ownership of event consumption, since each View queue has one reader.
- Bun packaged apps use a dedicated normal-permission service, with execution and
  cancellation keys scoped to the Binder caller UID. The ordinary host service
  remains protected by the original signature permission. AIDL transaction order
  and official Bun native payloads are unchanged.
- Bun project `bun.hostCapabilities` explicitly selects the offered capabilities.
  Unknown/duplicate entries are rejected. The launcher registers Bun, resolves the
  packaged service, and decrypts bounded script dependencies while constructing
  the workspace archive. The generated binary Manifest carries the runtime
  permission. Node retains its existing independent packaged-runtime entry.

## Coordinated versions and coverage

| Repository | Development version |
| --- | --- |
| AutoJs6 | 6.8.0, build 3910, Android versionCode 5562 |
| Node.js Runtime | 1.8.0, build 256 |
| Bun Runtime | 0.2.7, build 254 |
| TypeScript Declarations | 4.53.0 |
| Ace Editor | 2.15.0, build 290 |
| Documentation | 6.8.0, revision 130 |
| Offline Docs | 6.8.10, build 161 |
| APK Builder | 1.2.0, build 75, paired host 5562 |

The shared UI/Compose facade remains identical in Node and Bun. The focused gate
checks 55 Compose constructors (54 catalog components plus AndroidView), 12
Compose RPC operations, seven additional View operations and 12 Worker modules.
The general host documentation/type/Ace gate still reports 91 registration
routes, 1351 explicit exports and 1522 completion queries with zero failures.
Those host counts are not a claim of complete Rhino parity in Node or Bun.

The 70 copied runtime declaration files pass TypeScript 5.1.3 and 6.0.3 with zero
diagnostics. New smoke cases cover View attributes, context factories, attach,
structured-cloned identities and rejected Java/object/string substitutes. Ace's
desktop runtime gate passes 317 queries across 11 routes; real editor popups also
check attach, inflate and typed native View members under default preferences.
The existing JSOX/default-environment tests remain part of the device suite.

## Physical validation

Both devices are physical ARM64 devices with 4096-byte pages:

| Device | Android | Interop | Existing UI/Compose | Legacy UI | Node suites | Bun suite | Ace | APK production/execution |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Redmi 22120RN86C, bek749scrwv4wo8h | 13 / API 33 | 4 | 7 | 9 | 44 | 13 | - | 2 + 2 |
| Xiaomi 23046RP50C, 968e9f18 | 15 / API 35 | 4 | 7 | 9 | 44 | 13 | 5 | 2 + 2 |

This is 167 distinct accepted JUnit executions across the two devices. Parameter
loops inside the four interop tests exercise Node main, imported modules, CJS/ESM
Workers, nested Workers and Bun. Rotation is checked in Node, Worker and Bun.
Repeat runs are not added to the total.

The APK production tests use the real installed APK Builder and a genuine
`generateRuntimeKit` output from this host candidate. They do not substitute a
synthetic application or change a Gradle applicationId to imitate packaging.
The generated apps have certificates different from the host, application
versionCode 1, and report runtimeVersionCode 5562 from inside their own UI
context. Native AndroidView clicks, Compose callbacks/reactive state, landscape
rotation and window closure pass for Node and Bun on both devices. Bun imports
the actual SDK from its encrypted packaged project dependency.

Failures retained during development:

- The initial Bun APK was rejected by the old standalone launcher and missing
  engine registration. The launcher/registry and encrypted dependency path were
  completed before acceptance.
- The next Bun candidate exposed a missing permission in the template Manifest.
  Adding a permission to project JSON alone was insufficient. The template is
  corrected and the producer now asserts the final binary Manifest permission.
- Rotation exposed a test race between separate accessibility lookup and click
  calls. The fixture now waits for a successful action on the current node and
  the actual landscape display, retaining exact callback/state assertions.
  The revised tests pass; the initial failed logs remain in the evidence folder.
- A generated Node catalog had CRLF line endings and failed its existing metadata
  gate. Canonical LF generation and the kit/catalog version/hash binding pass.
  A first Builder unit fixture incorrectly used an Android JSON stub; its pure
  directive/filename tests and real APK assertions now cover the actual boundary.

## Other verification and device restoration

- Host Debug/test APKs and the complete Inrt Runtime Kit matrix build. The kit
  verifies native alignment; this is not new 16 KB runtime execution evidence.
- Host JVM suite: 4717 collected, 4691 passed, 26 existing skips, zero failures.
- Plugin JVM suites: Node 96, Bun 59, Ace 187, Builder 39, Offline Docs 2, all pass.
- Shared desktop CJS/ESM/Worker tests, late View disposal and bridge coverage pass.
  Bun client tests: 25 passed. Current NDK syntax, Node declaration/example/smoke
  tasks, Bun payload integrity and Bun lint pass.
- Documentation generation/check: 163 modules; host coverage Python tests 8,
  Bun generators 38, Offline Docs generators 3 pass. Ten-language release notes
  and generated documentation are synchronized.

Both devices' original APKs were restored and checked against their original
SHA-256 values. Only task-created test/fixture packages were removed. Permission
flags, media volume, rotation and MediaProjection release were checked against
the pre-test snapshot. Existing app data and the host's five user draft changes
are preserved.

Local evidence is retained under ignored `.bak/interop-20261018/`: `evidence.json`,
the actual device logs including failed candidates, 14 distinct tested APKs and
their SHA-256 manifest, generated project receipts, certificate comparisons,
Ace screenshots and restoration receipts. Temporary original APK backup copies
are removed only after successful hash-verified restoration. The pre-existing
`.bak/oi-check` build checkout and ignored signing configuration are retained.

## Remaining boundaries

Java View/Context objects, arbitrary Java class construction, E4X syntax, native
property proxies and native overlay adjustment chrome are not exported by these
portable handles. The XML vocabulary is the supported widget set, not Rhino's
complete XML inflater. Bun retains its Android/ABI requirements and its bounded
request/result, concurrency, timeout and per-execution call budgets. Event reads
count toward those budgets. Existing Android grants and installed compatible
runtime/Compose plugins are still required. Packaged Bun capabilities are
configured in project.json; this batch does not add a project settings UI.

APK/Runtime Kit publication, other ABIs/OEMs and 16 KB physical execution are
outside this batch's acceptance. The separate historical reports retain their
original scope and results.
