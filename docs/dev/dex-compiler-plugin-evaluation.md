# DEX Compiler Plugin Evaluation

This note evaluates moving the runtime JAR-to-DEX compiler out of the AutoJs6
application and into a standalone plugin. It records the phased decision and
the completed default-off R1/R2 deployment boundary.

## Decision

Proceed with a phased GO for a standalone D8 compiler plugin.

Implementation status on 2026-08-11: the V1 protocol pack, isolated
conformance test app, production D8 provider, and experimental host harness are
implemented. The production provider lives in the independent sibling project
`AutoJs6-Plugin-DEX-Compiler`; the conformance app remains under `test-apps`
only for deterministic hostile Binder scenarios. The host artifact
store now starts a bounded reliable-pipe drain before dispatch, enforces the
negotiated write ceiling, and checks the pipe peer error after EOF. Publication
requires the provider-reported size and SHA-256 to match the independently
drained size and SHA-256; the dual-summary gate permits only one publication or
rollback claim. R1.0 provides a default-off Developer options control plane that
retains one explicit eligible same-signed exact component, exposes its selected
or off state, and can be disabled without uninstalling the provider. R1.1 source
wiring consumes that identity only after explicit opt-in; automatic provider
selection remains disabled.

The host finalizes a compile request only after the selected provider completes
its Binder info/capability handshake. The negotiated protocol version and the
runtime-library fingerprint from that handshake are fixed before `openSession`,
while advertised capability limits validate or reject the request rather than
being written into it. This removes the previous fingerprint-ordering blocker
for the R1.1 raw-JAR adapter.

Publication has an explicit atomic commit boundary. Cancellation, timeout,
Binder death, binding failure, and callback or transport violations registered
while output validation is occupying the serialized lane are observed before
the publication claim and force rollback. Signals registered after that claim
are late and cannot race a second fallback outcome against a published cache
entry.

The R1.1 source path lets raw `runtime.loadJar` JARs prefer the explicitly
selected same-signed exact component. The route stays default-off, provider
failure retains one built-in D8/dx fallback, cancellation has no compiler
fallback, and `loadAar`, `loadDex`, and `GeneratedClassLoader.defineClass()` stay
on built-in paths. R1 is now closed: R1.1 production loading is 7/7, R1.2
automation is 4/4, the R1.3 canonical real-provider matrix is 7/7, and all three
R1 exit conditions are satisfied. The corrected host gate passed 16 DEX
suites/149 tests; wire 4/24 and fake-provider 5/25 remain supplemental rather
than end-to-end evidence. API 34 production routing passed 8/8, the earlier API
31 arm64 lifecycle/concurrency/corpus gates remain real-provider evidence, and
the canonical matrix covers API 24/25/26/28/31/34/36 across x86_64 emulators and
an arm64 physical device. The frozen implementation contract is documented in
[DEX Compiler Protocol V1](dex-compiler-protocol-v1.md).

The v1 protocol is limited to bounded bytecode-to-DEX conversion. It does not
assume byte-for-byte deterministic D8 output and does not expose R8 shrinking,
optimization, obfuscation, resource shrinking, mapping generation, retracing,
ProGuard rule processing, or keep-rule interpretation.
Shipping the R8 artifact inside the plugin is acceptable because D8 is
distributed by that artifact, but the public v1 capability is D8 only.

Host dependency removal is not authorized by this decision. The host retains
its compiler as the default-off path, the one bounded failure fallback, and the
implementation for AAR and generated-class loading until the compatibility,
lifecycle, performance, and output-verification gates below pass.

## R1 User Opt-In And Rollback Guide

The completed R1 route remains an explicit, default-off option after plugin installation.
The user-facing procedure is:

1. Install AutoJs6 build 5270 or later and the paired plugin APK. Their complete
   current signer sets must match. The fixed provider component is
   `io.github.supermonster003.autojs6.plugin.dexcompiler/io.github.supermonster003.autojs6.plugin.dexcompiler.DexCompilerService`.
2. In AutoJs6, open **Settings > About app and developer**, long-press the app
   icon to enter **Developer options**, then open **DEX compiler > Raw JAR
   compiler provider**. Select that exact component and confirm. Discovery never
   selects or enables a provider automatically.
3. Confirm that the preference summary says raw `runtime.loadJar` JARs prefer
   the selected exact component. This confirms selection and eligibility only;
   it does not prove that a particular compilation reached the provider.
4. Exercise the existing `runtime.loadJar(path)` script API with a trusted raw
   JVM JAR. The plugin adds no JavaScript global. AAR, precompiled DEX,
   compatibility helpers, and generated-class loading stay built-in.
5. For rollback, select **Built-in D8/dx**, confirm, then restart AutoJs6. Disable
   the route before uninstalling the plugin. Uninstalling the provider deletes
   its private app data and workspaces but does not require clearing host data.

Plugin absence, incompatibility, timeout, remote failure, invalid output, or
verified-artifact adoption failure can claim at most one built-in compiler
attempt. Binder work is never automatically retried after dispatch. Caller
cancellation or thread interruption propagates without local fallback, so final
script success by itself is not evidence that the plugin compiled the input.

Problem reports should include host/plugin versions, the complete selected
component summary, device model/API/ABI, input size and SHA-256, event time,
the full script exception, and redacted `AndroidClassLoader`/`AndroidRuntime`
logs. Canonical real-provider evidence covers API 24/25/26/28/31/34/36 on the
fixed R1 x86_64/arm64 cells. That acceptance does not turn every device into a
tested cell or alter the per-request identity, handshake, validation, and
fallback requirements. The localized step-by-step example and diagnostic
commands live in the sibling plugin README.

## Current Repository Evidence

The current host has a real extraction boundary rather than a speculative one:

- `app/build.gradle.kts` packages `com.android.tools:r8` as a runtime
  implementation dependency.
- `appDebugRuntimeClasspath` currently resolves R8 8.13.17. Its cached JAR is
  18,279,079 bytes and contains 7,164 `.class` entries. This is dependency input
  size, not a direct APK-size claim. The separately reported AGP build
  classpath uses its bundled R8 8.13.19; that build-time tool is not the
  packaged application dependency.
- Since 2026-09-18 the host packages only `com-legacy-android-dx-1_7_0.jar`
  (986,233 bytes raw); `com-android-dx-1_14.jar` (1,030,064 bytes raw) was
  removed by the APK size roadmap (P5.1).
- `app/src/main/java/org/autojs/autojs/rhino/AndroidClassLoader.kt` implements
  the active conversion path through `ClassFileDexer`. `loadJar()` and
  `loadAar()` run D8 on every API level, with one retry on a zip4j re-packed
  copy for `loadJar()`; `defineClass()` runs D8 on API 26+. On API 24 / 25 D8
  gets `java.nio.file` from `desugar_jdk_libs_nio`, and legacy dx stays as the
  fallback of D8 and as the first converter of generated classes; see
  `docs/dev/evidence/loadjar-d8-api24-20261101.md`.
- The D8 path uses the current device API as `minApi`, adds readable runtime
  boot libraries, accepts multi-DEX output, and packages multiple
  `classes*.dex` files for `DexClassLoader`.
- `AndroidClassLoader.loadAar()` merges `classes.jar` and nested `libs/*.jar`
  entries before conversion. It keeps the first entry name encountered and
  excludes selected `META-INF` signature metadata; the current archive-order
  merge is not evidence of byte-for-byte canonical output.
- Its `GeneratedClassLoader.defineClass()` implementation shares the same
  converter. The remaining compatibility gate is the API 24 / 25 legacy dx,
  tracked as APK size roadmap Q10 (nio desugaring versus dropping dynamic class
  loading on Android 7.x).
- The current artifact cache name is an MD5 of path and modification time, not
  content. The host also owns read-only safe staging motivated by Android 14,
  class loading, and class-loader lifetime. V1 deliberately strengthens the
  cache identity below.

The compiler implementation is large, rarely needed by most users, and already
has a narrow input/output shape. Those properties make D8 a high-value plugin
candidate. The dynamic class-loading path is also runtime-critical, so removal
must remain later than protocol validation.

## V1 Scope

The first protocol supports one normalized program archive per request:

- Input is one JAR containing JVM `.class` entries and optional non-class
  entries. Non-class entries are compiler input only; v1 does not promise to
  copy them into the DEX output.
- Output is one DEX-indexed ZIP container containing contiguous
  `classes.dex`, `classes2.dex`, and later entries when required.
- Compilation mode may be `DEBUG` or `RELEASE`, but both modes are D8 modes and
  neither performs shrinking, optimization, or obfuscation.
- The requested minimum API is explicit. For the existing runtime load path,
  v1 must use the current device API to preserve current behavior.
- Multi-DEX output is supported from the first real implementation.
- Cancellation, timeout, progress diagnostics, and exactly one terminal result
  are part of the contract.
- A pure JVM implementation reports `supportedAbis = emptyArray()`. A future
  implementation that adds native components must declare its actual ABIs and
  provide a Universal APK where practical.

AAR parsing is not part of the compiler protocol. R1.1 therefore does not
normalize or dispatch AAR input to the plugin: `AndroidClassLoader.loadAar()`
continues to merge and compile through the existing built-in path. Any future
plugin-backed AAR path would first need a separately frozen nested-JAR order,
entry order, timestamp/extra-field policy, and duplicate-entry policy.

The following are explicit v1 non-goals:

- R8 full mode or compatibility mode.
- Tree shaking, minification, optimization, repackaging, or name mapping.
- Android resource processing or resource shrinking.
- APK assembly, signing, alignment, installation, or execution.
- Kotlin, Java, or another source-language compiler.
- Maven dependency resolution, network downloads, or SDK management.
- Arbitrary compiler command lines, response files, plugin-supplied classpaths,
  or raw filesystem access.
- Loading or executing the produced DEX inside the plugin process.

## Ownership Boundary

The host owns:

- Plugin discovery, signer trust, protocol negotiation, and user selection.
- Resolving the original file or content URI and opening an exact read-only
  snapshot.
- Keeping AAR normalization and duplicate-entry handling on the built-in path
  for R1.1.
- Input quotas, output transactions, timeout policy, and cancellation routing.
- The Android runtime compatibility target used by a runtime load request.
- Freezing the runtime-library model and checking the library fingerprint
  returned by the plugin.
- Output validation, hashing, cache keys, read-only staging, and
  `DexClassLoader` creation.
- Deciding whether a compiled artifact may be loaded by a script runtime.

The plugin owns:

- Its D8 implementation and compiler release cadence.
- Reading the bounded program archive and invoking D8 with negotiated options.
- Producing only the negotiated DEX-indexed output format.
- Bounded diagnostics and compiler metadata.
- Cooperative cancellation checks at queue and D8-invocation boundaries,
  plus cleanup of plugin-private temporary files.

The plugin never receives a host-private path, raw file URI, raw content URI,
script runtime object, class loader, signing key, or destination path. A
successful compilation does not authorize execution.

## Proposed Protocol Shape

The protocol should be published as a small build-time API AAR with no D8, R8,
dx, archive, networking, or UI implementation dependency. A conceptual v1
Binder surface is:

```text
IDexCompilerProvider
  getInfo()
  getCapabilities()
  openSession(request, programFd, outputFd, callback) -> IDexCompilerSession

IDexCompilerSession
  cancel()
  close()

IDexCompilerCallback
  onStarted(metadata)
  onProgress(progress)
  onCompleted(result)
  onFailed(error)
  onCancelled()
```

Every wire Parcelable is size-prefixed and field-tagged, with an explicit
schema version and only primitives, strings, protocol parcelables, and declared
PFDs. Ordinary positional `Parcelable`, `Bundle`, Java serialization, arbitrary
class names, and undeclared Binder objects are forbidden. This encoding is what
makes an unknown optional field skippable.

`programFd` is read-only. `outputFd` is a host-owned, write-only output
transaction. Both sides duplicate a descriptor before asynchronous use and
close every owned descriptor on completion, cancellation, timeout, Binder
death, and session close.

Descriptor duplication provides independent ownership, not an independent
cursor: duplicated descriptors share one open-file position. Once dispatched,
the host does not concurrently read `programFd`, and exactly one plugin worker
writes `outputFd`. A pipe has exactly one consumer. If a reliable pipe is used,
the host checks the peer error after EOF before considering output complete.

Only one of `onCompleted`, `onFailed`, or `onCancelled` may be delivered. The
terminal callback is never blocked by progress backpressure. `cancel()` and
`close()` are idempotent. A Binder death after dispatch is not retried
automatically because the plugin may already have written a partial artifact.
`onStarted` occurs at most once and before progress, callbacks use one
serialized logical lane, and no callback is accepted after a terminal result.

The public R8 8.13.17 `D8.run` API has no cancellation token. V1 cancellation is
therefore best effort: it can stop queued work, prevent publication, close the
session, and discard late callbacks, but it cannot promise to interrupt an
in-progress D8 invocation. A timeout is a host transaction deadline, not proof
that compiler CPU work stopped. The host cannot kill another application
process; after rollback it unbinds and lets Android manage that process. A hard
kill boundary would require a dedicated provider process design validated on
the supported API range.

The host resolves an explicit service component, applies the existing
signature-level `org.autojs.permission.PLUGIN` binding permission, and checks
enablement, signer authorization, package version, and protocol compatibility
before binding. It pins package UID, signer digest, version code, and last-update
time for the session and rejects changes before dispatch. The provider checks
`Binder.getCallingUid()` against its authorized host identity; a package name
inside a Parcel is not caller proof.

The capability record includes:

- Protocol minimum and maximum major/minor versions.
- Compiler family and exact compiler version.
- Supported input and output formats.
- Supported D8 compilation modes and minimum API range.
- Multi-DEX support and any explicitly tested deterministic-output claim.
- Runtime-library model, whether an external classpath is accepted, and
  desugared-library configuration support.
- Maximum advertised input and output sizes.
- ABI requirements and minimum/maximum supported host versions.

An unknown required capability rejects the plugin. An unknown optional field is
ignored. Breaking parcel, lifecycle, or semantic changes require a new protocol
major version.

V1 preserves the current `DEVICE_RUNTIME_BOOTCLASSPATH` model and accepts no
caller-selected library path, external classpath, or desugared-library
configuration. The provider reports the ordered library identities and a
SHA-256 fingerprint used for the invocation. If a plugin process cannot read an
equivalent library set, the request is incompatible rather than silently
compiling with a different model. This gate matters because D8 requires library
and classpath resources for some Java 8 desugaring cases.

## File Descriptor And Cache Policy

The public Binder API transfers already-open descriptors, not paths or URIs.
The host applies these rules:

1. Resolve a source URI once and open the exact read-only object selected by the
   caller.
2. Copy non-seekable or mutable providers into a bounded host-private snapshot.
3. Reject non-JAR routing at the R1.1 adapter boundary; AAR stays built-in.
4. Create a host-private output transaction and expose only its write side.
5. Commit the output only after a successful terminal callback and independent
   verification. Every other terminal path rolls the transaction back.
6. Stage a verified artifact as read-only before creating a class loader.

The host cache key must include at least the SHA-256 of the exact normalized
program snapshot, canonicalization-policy version, protocol major version,
compiler family and version, minimum API, compilation mode, output mode,
runtime-library fingerprint, desugared-library configuration identity, and
every semantic option. A plugin package name, signer digest, version code, and
last-update time are also recorded as provenance. Path and modification time
alone are not a sufficient cache identity. Cache correctness does not depend on
a claim that separate D8 invocations are byte-identical.

The initial R1.1 source deployment used verified publication only as an
ephemeral handoff. The current R1.2 source deployment replaces that historical
boundary with a bounded persistent semantic cache. It stores generation-bound
artifact and checksummed manifest pairs, fsyncs data and the directory around
the atomic manifest commit, recovers complete generations, and applies limits
of 16 MiB per entry, 128 MiB total, and 32 entries with LRU eviction. Lookup
opens the artifact descriptor first and rechecks size and SHA-256 from that same
inode. A corrupt generation is evicted without deleting a newer replacement.

Cache lookup occurs only after the authenticated Binder handshake has finalized
the semantic key, but before input/output descriptor claims or `openSession`.
Every hit is revalidated with the current `DexIndexedZipValidator`; successful
class-loader adoption keeps the cached generation, while a non-interruption
adoption failure invalidates only that exact generation. Production Runtime
passes the finalized semantic attempt key through a process-scoped single-flight
coordinator, so matching callers share one producer and each successful caller
receives a fresh capability. Interrupting a waiter detaches only that caller and
returns cancellation without local compiler fallback. The producer continues
while another waiter remains; when the final waiter leaves before a terminal
result, a leader-owned one-shot handle cooperatively requests cancellation.
Cancellation prevents late publication and eventually releases owned resources,
but cannot promise to stop CPU work already executing inside D8. The
coordinator's unsafe-executor or suspected blocked-Binder circuit remains
deliberately conservative: once opened, it stays open for the current process
lifetime and does not claim to have forcibly stopped already blocked work.

The production coordination semantics have 14/14 pure JVM evidence. A separate
explicitly opted-in `DexCompilerRealProviderAndroidTest` entry targets the
real package, exact service, and same signer, then requests a unique raw JAR,
expects `Published` followed by `CacheHit`, executes a fixture with the real
`DexClassLoader`, and performs exact-generation cleanup. On API 31 arm64, its
real-Harness method and production `AndroidClassLoader` method each passed once
against the earlier host `17092F2D…A97EB`, test `1FC07F35…63B89`, and plugin
`E0E5D861…F9C5D` APKs; post-test cache, workspace, opt-in, uninstall, and
package-absence checks passed. This historical run is retained as provenance;
the rebuilt current-APK evidence is recorded below.

The current Android-test source adds seven separately opted-in methods:

- Two production-concurrency methods prove same-semantic-key coalescing with
  fresh caller capabilities, and follower interruption while the shared leader
  still publishes. Their debug probe counts a committed remote dispatch at the
  host coordinator boundary; it does not claim to count direct provider
  `openSession` invocations.
- Two real-lifecycle methods exercise a blocked raw AIDL session, BUSY/QUEUE,
  descriptor EOF and exactly-one terminal behavior, plus explicitly consented
  force-stop, Binder death, and exact authenticated recovery.
- Three real-corpus methods cover Java/Kotlin single-DEX and defined duplicate
  loading, the missing-dependency ART resolution boundary, and real D8
  multi-DEX. The heavy multi-DEX method is a separate long-running gate: it
  generates 7,300 classes with 65,700 methods, validates each emitted DEX header,
  map, and class_defs table, and executes one dynamically selected class from
  both `classes.dex` and `classes2.dex`.

The corrected host gate passed 16 DEX suites/149 tests with no
failure/error/skipped result; wire 4/24 and fake-provider 5/25 were green but are
not promoted to real-provider acceptance. On `QV710AF65F` API 31 arm64, the two
concurrency, two lifecycle, and three corpus methods passed individually. The
65,700-method corpus loaded classes from primary and secondary DEX, and cleanup
left the workspace empty. These results close the precisely scoped R1.2 4/4.

R1.1 subsequently closed 7/7 when all eight
`DexCompilerProductionRoutingAndroidTest` methods passed on API 34. They exercise
public raw-JAR routing, compatibility-path exclusion, retained snapshot use,
verified-adoption rollback and exact eviction, cancellation, concurrency, and
probe-fault/interruption ordering through the production boundary.

The final canonical campaign `f3c2b1af-be93-41e7-b541-f167f90e5cc1` passed
7/7 real-provider cells. API 24/25 use the D8 CLI fallback; API 26/28/34/36 use
`D8Command` on x86_64 emulators; API 31 uses the authorized `QV710AF65F`
arm64-v8a physical device with its multi-user topology preserved. Clean host
commit `e39023758e3a66a24f0ce90466b5bc77a503515d` and plugin commit
`1f50d5333ab3a58c4c0f00fe06a20a5692aa3448` produced host/test/plugin APKs
`505bc077…1b878`, `4199d54d…5da24`, and `65e432a7…bd88`, all signed by
`31a681fc…c213`. The Gate binds journal head `a5abaf62…54581b`, runner
`ca89ac16…0057`, and campaign identity `6b859228…3665`. Earlier failed and
intentionally terminated campaigns remain preserved rather than overwritten.

The force-stop run also exposed a real stale session workspace: an old UUID
containing a zero-byte `program.jar` and empty `d8-output` survived process death.
The plugin performs a process-once, strict-canonical janitor before exposing its
first service Binder. It anchors traversal to the private cache root and fails
closed on boundary or symbolic-link violations. R2 then unified terminal, stop,
cleanup, last-waiter cancellation, and monotonic-deadline ownership. The current
gates are 17 host DEX suites/166 tests and 12 plugin suites/65 tests, with no
failure/error/skipped result and plugin lint at 0 errors.

The representative R2 closeout package at
`D:\idea-projects\.bak\AutoJs6-DEX-R2-Evidence-20260811\closeout-api34-dab3f857-650d-4107-a3b9-941a1f7e02c2\report.json`
passed real D8 failure/recovery, caller cancellation, and provider-process
interruption on one authorized API 34 x86_64 target. The 52-command run records
terminal and publication state, descriptor/workspace cleanup, next-session
availability, clean preflight/postflight state, and 61 hashed files. Its report,
manifest, and runner SHA-256 values are respectively
`5d1549b9d5207e5dcd1c62a3c2caf863a748f49b8f9f2582f66bb2898be98eba`,
`927ee1abebe83a0901e7f767a56b377a2afd84a137d16b8d5d3784fcd54e639e`, and
`61437b2b2d7a02e35d43176a1c381a9fcd4b7fa540b5876ed9347661962c5f9e`.
This closes R2 without creating a new device matrix or treating terminal latency
as proof that D8 CPU work stopped.

## Proposed V1 Quotas

These are initial safety ceilings for the fixture and device trials, not a
promise that every device can compile at each maximum:

| Resource | Proposed limit |
| --- | ---: |
| Binder request and result metadata | 256 KiB each |
| Compressed program archive | 128 MiB |
| Archive entries | 20,000 |
| Total uncompressed archive bytes | 256 MiB |
| Maximum per-entry and aggregate compression ratio | 100:1 |
| Total `.class` bytes | 128 MiB |
| One `.class` entry | 8 MiB |
| Produced DEX container | 256 MiB |
| Produced `classes*.dex` entries | 64 |
| Retained diagnostics | 64 KiB |
| User-visible error detail | 512 characters |
| Default compile timeout | 120 seconds |
| Hard compile timeout | 300 seconds |
| Concurrent compile sessions | 1 per plugin process |

The host rejects oversized or structurally invalid input before binding when
possible. The plugin repeats all relevant checks because Binder callers and
host versions cannot be assumed correct. Additional requests receive `BUSY` or
remain in a bounded host queue; the plugin does not create an unbounded work
queue.

The Binder transaction buffer is currently 1 MiB shared by all in-flight
transactions in a process, not a per-call allowance. The 256 KiB metadata
ceiling preserves headroom but does not eliminate
`TransactionTooLargeException`; either side treats it as an ambiguous partial
failure, rolls back output, and does not retry the compilation automatically.

## Input And Output Verification

Pre-dispatch validation must reject at least:

- ZIP path traversal, absolute paths, NUL characters, encrypted entries,
  duplicate normalized names, unsupported compression, and declared-size
  overflow.
- Entry-count, compressed-size, uncompressed-size, class-size, per-entry ratio,
  and aggregate compression-ratio violations, including arithmetic overflow
  while accumulating any total.
- Missing or malformed class-file magic for entries ending in `.class`.
- A requested minimum API outside the plugin and host intersection.

Post-compile validation must reject at least:

- Empty output, unexpected ZIP entries, non-contiguous DEX names, duplicate
  names, or output beyond the transaction quota.
- Invalid or unnegotiated DEX version, magic, header size, file/container size,
  endian tag, checksum, or signature.
- Misaligned, overlapping, out-of-order, or out-of-range DEX sections, maps,
  offsets, counts, indices, and data items. The validator follows the platform
  DEX integrity constraints rather than checking the header alone.
- A class definition duplicated across output DEX files.
- Compiler metadata that does not match the negotiated session.

Validation is structural and does not make untrusted bytecode safe. The normal
AutoJs6 script and class-loading trust model still applies after compilation.

## Fallback And Degradation

The rollout has two distinct fallback states:

- Before host compiler removal, plugin absence, incompatibility, timeout,
  crash, remote error, invalid output, or verified-artifact adoption failure
  falls back once to the current host D8/dx path. Cancellation is interruption,
  not failure, and never changes compilers. The reason is recorded without
  exposing private paths.
- After host compiler removal, an already verified matching cache entry or a
  prebuilt DEX file may still load. A JAR or AAR that requires conversion fails
  explicitly with `DEX_COMPILER_UNAVAILABLE`. There is no network download,
  silent compiler substitution, or remote compilation fallback.

Fallback after a dispatched plugin request must first roll back its output
transaction. The host must never load partial output. Repeated crashes disable
automatic plugin selection for the current process and surface a bounded
diagnostic.

Single-flight cancellation follows the same rule: an interrupted caller only
detaches from its waiter, receives cancellation, and never falls back locally.
The producer continues while another waiter remains. When the final waiter leaves
before a terminal result, the leader-owned handle cooperatively requests
cancellation; a D8 invocation already in progress may still run, but its late
result cannot publish. When unsafe executor rejection or a suspected blocked
Binder call opens the process-local circuit, it remains conservatively open for
that process lifetime.

## Verification Gates

The following gates are required before making the plugin the default:

The API 31 arm64 methods described above close the four precisely scoped R1.2
automation items. Together with API 34 production-routing 8/8 and the canonical
7-cell real-provider Gate, they close R1.1 7/7 and R1.3 7/7. The remaining list
also contains promotion and host-dependency-removal gates beyond R1; R1 closure
does not make the plugin the default or authorize compiler removal.

- A conformance compiler fixture proves version negotiation, descriptor ownership,
  caller authentication, cancellation, timeout, late D8 completion, Binder
  death, duplicate terminal suppression, Binder-size failure, and atomic
  rollback.
- API 24, 25, 26, 28, 34, and 36 pass the same contract corpus.
- Java and Kotlin JARs, empty/resource-only JARs, duplicate entries, signed
  JARs, large inputs, multi-DEX inputs, malformed archives, and ZIP bombs have
  expected results.
- Java 8 default/static interface methods, missing-library diagnostics, and
  device boot-library visibility prove that the plugin library model matches
  the current host path.
- D8 output loads and executes the same corpus as the current host D8 path.
- Verified output is exercised through the real `DexClassLoader`, not only by
  structural DEX parsing or compiler success callbacks.
- R1.1 regression coverage proves that AAR stays on the existing built-in path
  and retains its documented loadable-class and resource behavior.
- Cold and warm compilation record latency, peak host RSS, peak plugin RSS,
  output size, descriptor count, and temporary-storage usage.
- Cancellation, plugin disable, update, uninstall, process death, host process
  death, and storage exhaustion leave no published partial output.
- Timeout tests record whether an in-progress D8 call continues after the host
  rolls back, so cancellation latency is not reported as compiler termination
  latency.
- The host independently rejects every malformed-output corpus item even when
  the conformance provider reports success.

The following additional gates are required before removing host compiler
dependencies:

- `GeneratedClassLoader.defineClass()` has a verified plugin-backed path or a
  deliberately retained host implementation. Removing dx while that path still
  calls `dexJar()` is forbidden.
- The legacy pre-Android 8 path passes representative API 24 device tests.
- Plugin cold-start overhead is measured against the current compiler. A
  proposed promotion target is no more than 25 percent or 1 second of added P95
  cold compile latency, whichever allowance is larger.
- Verified cache lookup adds no more than 100 ms P95 over the current warm path.
- The combined peak host and plugin RSS does not regress the current compiler
  baseline by more than 20 percent on the release device matrix.
- The compressed APK delta and installed-size delta are recorded for every ABI
  and Universal APK. Dependency removal proceeds only if the measured benefit
  remains material after shrinking and compression.
- If the product retains an 8 MiB release-APK promotion threshold, it is treated
  as a decision gate rather than an expected saving. This evaluation has no
  measured 8-11 MiB estimate.
- Ordinary app cold-start time must regress by less than 2 percent, and warm
  compilation must be no more than 15 percent slower than the current path,
  using repeated trials and reported confidence intervals.

## Estimated Benefit And Cost

| Area | Estimate | Basis |
| --- | --- | --- |
| Host APK and installed size | Unquantified potential benefit | About 1.92 MiB of direct dx archives are present before packaging, and the reachable runtime D8 implementation is additional. Shrinking and compression make the net release saving unknown. |
| Host process memory | Potential medium benefit | Compiler working state moves out of the host address space. After unbinding, the plugin process is independently reclaimable by Android, but immediate reclamation is not guaranteed. |
| Ordinary startup | Low benefit | The current compiler path is already demand-driven, so startup improvement may be small. |
| Compiler updates | High benefit | D8 compatibility updates can ship without replacing the host application. |
| Crash containment | Medium benefit | Compiler failures and temporary allocations occur in a separate process, while malicious output still requires host validation. |
| Runtime latency | Medium cost | Binding, descriptor snapshots, and cross-process startup add cold-path overhead. |
| Compatibility maintenance | High cost | Dynamic class generation, API 24 behavior, AAR normalization, multi-DEX loading, and fallback all require a long-lived corpus. |

No exact APK saving is claimed by this evaluation. R8 shrinking and APK
compression make dependency source sizes an unreliable release estimate. The
baseline and plugin candidate must be built with identical release settings and
compared by compressed APK size, installed size, method count, and package-level
DEX contribution. The raw R8 and dx JAR measurements establish extraction
potential only; they are not performance, APK, or installed-size measurements.

## Phased Rollout

### Phase 0: Baseline And Contract

- Freeze a representative JAR/AAR/generated-class corpus.
- Freeze canonical AAR normalization, duplicate handling, the runtime-library
  model, and cache provenance.
- Record current D8/dx behavior, output validity, latency, RSS, and APK size.
- Publish the protocol AAR and a conformance test app only.

### Phase 1: Optional D8 Path

- R1.0 complete: add default-off explicit same-signed exact-component selection
  and visible eligibility/off state while retaining the current host path.
- R1.1 complete: source wiring lets raw `runtime.loadJar` JARs
  prefer the selected provider, with one failure fallback, no cancellation
  fallback, and AAR and generated-class loading kept built-in.
- R1.2 automation complete: retain bounded persistent semantic-cache
  generations, recovery, LRU and exact corruption eviction; perform lookup only
  after authenticated key finalization and revalidate every hit with the current
  DEX ZIP validator. Production Runtime request coalescing is connected: waiter
  interruption detaches only that caller without local fallback, the producer
  continues while another waiter remains, and final-waiter departure requests
  cooperative cancellation through the leader-owned handle. An opened safety
  circuit stays conservative for the current process lifetime.
- Historical supplemental result: the explicit real-provider Harness and
  production class-loader happy paths passed once on API 31 arm64 with cleanup
  verified for earlier host/test APKs.
- Final R1 result: R1.1 is 7/7, R1.2 is 4/4, R1.3 is 7/7, and exit conditions
  are 3/3. API 34 production routing passed 8/8; canonical campaign
  `f3c2b1af-be93-41e7-b541-f167f90e5cc1` passed API 24/25/26/28/31/34/36
  with a real provider across x86_64 and arm64. The plugin is still not promoted
  to a default path.
- R2 complete: bounded failure summaries, process-once recovery, unified
  fail-closed terminal ownership, monotonic deadlines, and last-waiter
  cooperative cancellation passed 17/166 host and 12/65 plugin JVM gates plus a
  representative three-scenario API 34 real-provider closeout. This is not a new
  device matrix and does not equate result termination with D8 CPU termination.
- Phase 1 acceptance is complete; performance promotion, default enablement, and
  removal of host compiler dependencies remain later work.
- Use host-owned snapshots, output transactions, validation, and cache keys.
- Compare semantic load results, not byte-for-byte DEX identity.

### Phase 2: Default With Host Fallback

- Make the verified plugin the preferred JAR conversion path.
- Retain one bounded fallback to the current host compiler.
- Collect release-matrix and real-project compatibility evidence.

### Phase 3: Dependency Removal Decision

- Resolve the generated-class and API 24 gates.
- Measure final APK and installed-size savings.
- Remove host R8 and/or dx dependencies only when each remaining call site has
  an intentional replacement or retained implementation.
- Document the explicit missing-plugin degradation before release.

### Future R8 Reconsideration

R8 is a separate future decision, not a v1 feature flag. Reconsider it only
after D8 is stable and there is a concrete on-device shrinking use case with
defined library modeling, keep rules, mapping ownership, reproducibility,
retrace support, and output-signing integration. A future R8 service requires a
new capability family and may require a new protocol major version.

## Android Platform References

- [D8](https://developer.android.com/tools/d8) documents program inputs,
  `DEBUG`/`RELEASE` output, minimum API, multi-DEX output, and required library
  and classpath resources for desugaring.
- [R8 `AndroidApiLevel`](https://r8.googlesource.com/r8/+/c33e8e902c56db7a207c0f28e2403547a76fc5a6/src/main/java/com/android/tools/r8/utils/AndroidApiLevel.java)
  defines the conservative D8 output-version minimum API profile enforced by
  the experimental host.
- [Dalvik executable format](https://source.android.com/docs/core/runtime/dex-format)
  defines the DEX header and section layout.
- [DEX constraints](https://source.android.com/docs/core/runtime/constraints)
  defines structural and bytecode integrity requirements beyond the header.
- [DexClassLoader](https://developer.android.com/reference/dalvik/system/DexClassLoader)
  documents loading DEX-bearing JAR/APK files and the private optimized-output
  directory requirement before API 26.
- [ParcelFileDescriptor](https://developer.android.com/reference/android/os/ParcelFileDescriptor)
  documents descriptor ownership, duplication, access modes, and reliable-pipe
  error reporting.
- [Binder](https://developer.android.com/reference/android/os/Binder#getCallingUid())
  documents caller UID as the Binder security identity.
- [TransactionTooLargeException](https://developer.android.com/reference/android/os/TransactionTooLargeException)
  documents the shared Binder buffer and ambiguous partial-failure behavior.
