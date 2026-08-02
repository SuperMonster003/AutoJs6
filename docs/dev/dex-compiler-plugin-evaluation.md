# DEX Compiler Plugin Evaluation

This note evaluates moving the runtime JAR-to-DEX compiler out of the AutoJs6
application and into a standalone plugin. It records a phased decision and the
minimum boundary required before implementation. It does not change the current
runtime loading path.

## Decision

Proceed with a phased GO for a standalone D8 compiler plugin.

Implementation status on 2026-08-03: the V1 protocol pack, isolated fake
provider, and experimental host harness are implemented. The harness remains
disconnected from the default class-loading path while the Android runtime
gates in [the conformance matrix](compiler-ai-protocol-conformance.md) are
pending. The frozen implementation contract is documented in
[DEX Compiler Protocol V1](dex-compiler-protocol-v1.md).

The v1 protocol is limited to bounded bytecode-to-DEX conversion. It does not
assume byte-for-byte deterministic D8 output and does not expose R8 shrinking,
optimization, obfuscation, resource shrinking, mapping generation, retracing,
ProGuard rule processing, or keep-rule interpretation.
Shipping the R8 artifact inside the plugin is acceptable because D8 is
distributed by that artifact, but the public v1 capability is D8 only.

Host dependency removal is not authorized by this decision. The host keeps its
current compiler path until the plugin passes the compatibility, lifecycle,
performance, and output-verification gates below.

## Current Repository Evidence

The current host has a real extraction boundary rather than a speculative one:

- `app/build.gradle.kts` packages `com.android.tools:r8` as a runtime
  implementation dependency.
- `appDebugRuntimeClasspath` currently resolves R8 8.13.17. Its cached JAR is
  18,279,079 bytes and contains 7,164 `.class` entries. This is dependency input
  size, not a direct APK-size claim. The separately reported AGP build
  classpath uses its bundled R8 8.13.19; that build-time tool is not the
  packaged application dependency.
- The host also packages `com-android-dx-1_14.jar` and
  `com-legacy-android-dx-1_7_0.jar`. Their raw JAR sizes are 1,030,064
  bytes and 986,233 bytes, or about 1.92 MiB together before APK compression
  and shrinking.
- `app/src/main/java/org/autojs/autojs/rhino/AndroidClassLoader.kt` implements
  the active conversion path. Its `loadJar()` attempts D8 first and falls back
  to dx.
- The D8 path uses the current device API as `minApi`, adds readable runtime
  boot libraries, accepts multi-DEX output, and packages multiple
  `classes*.dex` files for `DexClassLoader`.
- `AndroidClassLoader.loadAar()` merges `classes.jar` and nested `libs/*.jar`
  entries before conversion. It keeps the first entry name encountered and
  excludes selected `META-INF` signature metadata; the current archive-order
  merge is not evidence of byte-for-byte canonical output.
- Its `GeneratedClassLoader.defineClass()` implementation still uses the dx path
  directly. This is a separate compatibility gate and prevents early removal of
  both dx archives.
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

AAR parsing is not part of the compiler protocol. Before plugin dispatch, the
host normalizes an AAR into one canonical JAR by merging `classes.jar` and
`libs/*.jar` under a frozen nested-JAR order, entry order, timestamp/extra-field
policy, and duplicate-entry policy. This is a proposed v1 prerequisite, not a
property established by the current archive-order merge. It keeps archive
policy and user-facing file handling outside the compiler process.

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
- AAR normalization and deterministic duplicate-entry handling.
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
3. Normalize AAR inputs before opening the program descriptor.
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

Verified cache entries may be reused without starting the plugin. An entry is
discarded when its content hash, provenance, protocol, options, or read-only
state does not match.

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
  crash, or verified-output failure falls back once to the current host D8/dx
  path. The reason is recorded without exposing private paths.
- After host compiler removal, an already verified matching cache entry or a
  prebuilt DEX file may still load. A JAR or AAR that requires conversion fails
  explicitly with `DEX_COMPILER_UNAVAILABLE`. There is no network download,
  silent compiler substitution, or remote compilation fallback.

Fallback after a dispatched plugin request must first roll back its output
transaction. The host must never load partial output. Repeated crashes disable
automatic plugin selection for the current process and surface a bounded
diagnostic.

## Verification Gates

The following gates are required before making the plugin the default:

- A fake compiler fixture proves version negotiation, descriptor ownership,
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
- Current AAR normalization produces equivalent loadable classes and resources
  remain subject to the existing documented behavior.
- Cold and warm compilation record latency, peak host RSS, peak plugin RSS,
  output size, descriptor count, and temporary-storage usage.
- Cancellation, plugin disable, update, uninstall, process death, host process
  death, and storage exhaustion leave no published partial output.
- Timeout tests record whether an in-progress D8 call continues after the host
  rolls back, so cancellation latency is not reported as compiler termination
  latency.
- The host independently rejects every malformed-output corpus item even when
  the fake plugin reports success.

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
- Publish the protocol AAR and a fake conformance plugin only.

### Phase 1: Optional D8 Path

- Add explicit experimental selection while retaining the current host path.
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
