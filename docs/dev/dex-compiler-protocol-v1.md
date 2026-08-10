# DEX Compiler Protocol V1

This document records the frozen V1 contract and the R1.2 source-deployment
boundary. The protocol AAR is a build-time SDK. The in-repository conformance
provider is not a production D8 distribution; the real D8 provider lives in
the independent sibling project `AutoJs6-Plugin-DEX-Compiler`. The experiment
remains default-off. After the user explicitly selects an eligible same-signed
exact component, only raw JAR calls from `runtime.loadJar` prefer the provider.
Provider failure retains one built-in D8/dx fallback, cancellation does not
change compilers, and AAR and generated-class loading remain on built-in paths.

## Modules

| Module | Responsibility |
| --- | --- |
| `plugin-api:protocol-wire-api` | Versioned field-tagged binary envelope |
| `plugin-api:dex-compiler-api` | AIDL, models, codecs, negotiation, and quotas |
| `test-apps:dex-compiler-conformance` | Deterministic normal and hostile conformance services |
| `app/.../core/plugin/dex` | Experimental host policy, lifecycle, cache key, and output validation |

The API AAR contains no D8, R8, dx, archive implementation, network stack, UI,
or native library. V1 is ABI independent.

## Scope

V1 supports one bounded operation:

- Input: one read-only JAR snapshot.
- Compiler family: D8 only.
- Modes: `DEBUG` and `RELEASE`.
- Output: one write-only DEX indexed ZIP containing contiguous
  `classes.dex`, `classes2.dex`, and later entries.
- Runtime library model: `DEVICE_RUNTIME_BOOTCLASSPATH_V1`.
- Concurrency: one session per provider process.

V1 does not support AAR normalization, R8 shrinking, keep rules, mapping or
retrace data, caller supplied classpaths, desugared-library configuration,
network compilation, loading the result in the provider, or deterministic byte
identity claims.

## Binder Surface

`IDexCompilerProvider` exposes `getCompilerInfo`, `getCapabilities`, and
`openSession`. `openSession` receives one encoded request, one program
descriptor, one output descriptor, and one callback. It returns an
`IDexCompilerSession` with idempotent `cancel` and `close` methods.

The callback lane contains `onStarted`, `onProgress`, and exactly one of
`onCompleted`, `onFailed`, or `onCancelled`. Callback methods are one-way and
must be serialized by the provider. Completion is invalid before start,
progress is invalid before start, progress sequence numbers must increase, and
no event is accepted after a terminal event.

The wire payloads use [Plugin Protocol Wire V1](plugin-protocol-wire-v1.md).
Program and output bytes never enter a Binder byte array.

## Discovery And Trust

The service action is `org.autojs.plugin.DEX_COMPILER`. A candidate must be
enabled, exported, and protected by `org.autojs.permission.PLUGIN`.

Compiler output is later executed with host privileges, so general plugin
authorization is not sufficient. The R1.1 production route accepts only a
provider whose complete current signer set matches the host. An official trust
classification alone is insufficient for this opt-in route. User-granted and
generally trusted third-party providers are not eligible in either debug or
release builds.

Discovery freezes an exact component and its package name, UID, complete
current signer set, version code, and last-update time. The host rechecks that
identity before dispatch. Every callback must also originate from the pinned
UID. A changed package is rediscovered as a new candidate rather than silently
continuing an old session.

R1.0 added only the staging control plane: Developer options can retain one
explicitly selected eligible component, display whether the experiment is off
or which exact component is selected, and clear or replace that selection
without uninstalling the provider. R1.1 consumes that identity only while the
experiment is enabled. Disabled or unselected operation does not discover or
bind a compiler and stays on built-in D8/dx.

## R1 User Control Plane

Installation does not activate V1. A compatible host is build 5270 or later,
and the plugin's complete current signer set must match the host. The production
package and exact service identity are:

```text
host package: org.autojs.autojs6
plugin package: io.github.supermonster003.autojs6.plugin.dexcompiler
exact component: io.github.supermonster003.autojs6.plugin.dexcompiler/io.github.supermonster003.autojs6.plugin.dexcompiler.DexCompilerService
```

The user opens **Settings > About app and developer**, long-presses the app icon
to enter **Developer options**, and selects that component under **DEX compiler
> Raw JAR compiler provider**. The resulting summary is the authoritative
visible opt-in state. It proves only that the component is selected and eligible;
per-request provider use still requires runtime identity revalidation, handshake,
and dispatch. AutoJs6 never automatically selects a discovered provider.

Selecting **Built-in D8/dx** and restarting AutoJs6 is the emergency rollback.
This is sufficient; host uninstall, host-data clearing, and script deletion are
not part of the procedure. Disable before uninstalling the plugin. A later
reinstall must be compatible and same-signed, followed by a new explicit
selection; retained preference state must not be treated as automatic enablement.

The user-facing fallback statement is intentionally precise: a permitted failure
can claim at most one built-in attempt, dispatched Binder work is not retried,
and caller cancellation or interruption never changes compilers. Successful
script completion therefore cannot distinguish a provider result from the one
allowed built-in fallback. Diagnostic reports need the visible component state,
host/plugin versions, device API/ABI, input byte count and SHA-256, timestamp,
full exception, and redacted class-loader/runtime logs.

This control plane is a controlled preview while R1.1 production fault/rollback
gates and the R1.3 API 24/25/26/28/34/36 plus x86_64/arm64 matrix remain open.
The current API 31 arm64 real-provider evidence does not promote other rows.

## Descriptor Ownership

The host snapshots mutable or non-seekable input into host-private storage and
computes its size and SHA-256 before dispatch. The provider duplicates both
descriptors before asynchronous use and closes the received arguments. Only
the provider reads the program descriptor and only one provider worker writes
the output descriptor.

The output points at a host-owned staging transaction. A successful callback
does not publish it. The provider must close its producer before the callback;
the host reads the staging artifact, verifies reported size and SHA-256,
validates the DEX indexed ZIP, and only then allows cache publication. Failure,
timeout, cancellation, Binder death, identity drift, or a late callback
observed before the publication commit point rolls the transaction back.

The Android artifact store creates a reliable pipe and starts a
dedicated bounded drain before dispatch. The drain consumes at most the
negotiated byte ceiling, reports peer errors after EOF, and writes only into a
host-private staging inode. Provider completion metadata and the independently
drained size and SHA-256 must match before publication can be claimed. Drain
failure, timeout, cancellation, or a summary mismatch claims rollback instead.

Asynchronous abort ingress and the publication terminal claim share one commit
lock. Cancellation, timeout, Binder death, binding failure, or a transport or
callback violation registered before that commit point prevents publication,
even when validation is occupying the serialized worker lane. A signal that is
registered after the publication terminal CAS is late and cannot create a
published-artifact/local-fallback split brain.

The host never renames the drained staging inode into the cache. It copies the
already validated bytes into a second host-only file, verifies that copy,
fsyncs it, and atomically renames only that file. The bounded transfer and
dual-terminal publication state machine have pure JVM coverage, but the pipe
adapter still requires API 24-36 Binder, peer-error, cancellation, storage
exhaustion, and process-death evidence before default or third-party selection.
The initial R1.1 source deployment treated that publication as an ephemeral
handoff capability. The current R1.2 source deployment instead publishes a
generation-bound artifact and checksummed manifest, with strict file and
directory fsync around the atomic manifest commit. Recovery keeps only complete
validated generations and applies limits of 16 MiB per entry, 128 MiB total,
and 32 entries with LRU eviction. Lookup opens a descriptor before rechecking
size and SHA-256 from the same inode; corruption eviction is scoped to the exact
generation so a stale handle cannot delete its replacement.

The host can look up the cache only after the authenticated provider handshake
has finalized the semantic key and before it claims input/output descriptors or
calls `openSession`. A hit is never trusted as historical validation evidence:
the host reruns the current `DexIndexedZipValidator`. Successful class-loader
adoption retains the generation, while a non-interruption adoption failure
invalidates that exact generation. Production Runtime now passes the finalized
semantic attempt key through a process-scoped single-flight coordinator. Matching
callers share one producer, while each successful caller receives a fresh
capability. Interrupting any waiter, including the final waiter, only detaches
that caller and returns cancellation without local compiler fallback; the
producer may complete in the background and publish to the persistent cache.
Cooperative producer cancellation after the last waiter leaves remains R2. The
unsafe-executor or suspected blocked-Binder circuit is deliberately conservative:
once opened, it stays open for the current host process lifetime and does not
claim to forcibly stop work that is already blocked. These coordination semantics
have 14/14 pure JVM evidence.
The current structural validator also materializes output under a conservative
16 MiB host limit; removing that heap ceiling requires a separately tested
random-access or streaming validator.

## Negotiation And Cache Identity

The host selects the highest overlapping protocol version. Advertised provider
capability limits are validation gates: an unsupported or out-of-bounds request
is rejected rather than rewritten. The runtime-library fingerprint is a
domain-separated SHA-256 over the ordered library identities. Order is part of
the identity.

The request is now finalized only after the exact provider is pinned and its
info and capabilities have completed the Binder handshake. The negotiated
protocol version and the fingerprint reported by that same handshake are
applied to the final request before `openSession`. Provider capabilities and
ceilings validate or reject the finalized request; they are not rewritten into
it. This removes the earlier ordering problem where the caller had to guess a
provider fingerprint before discovery. The R1.1 raw-JAR adapter uses this
post-handshake finalization before dispatch.

The semantic cache key includes all inputs that may change executable output:

- Input content SHA-256 and canonicalization policy.
- Protocol version, compiler family and version.
- Minimum API, debug or release mode, and output format.
- Runtime-library model and ordered fingerprint.
- Every semantic compiler option.
- Provider package, signer, version code, and last-update time.

Provider output is never reused under a different provenance identity.

## Resource Ceilings

The protocol ceiling includes 128 MiB compressed input, 20,000 archive
entries, 256 MiB uncompressed input, 128 MiB total class bytes, 8 MiB per class,
256 MiB output, 64 DEX entries, 64 KiB diagnostics, and a 300 second maximum
deadline. The effective value is always the smallest host, provider, request,
and protocol limit.

The experimental host currently rejects requests above a separate 16 MiB
in-memory output ceiling before discovery. Its validator still materializes
the container, inflated DEX entries, and verified publication copy, so the
larger protocol ceiling is not a safe Android heap target. Streaming validation
and publication, or device-qualified heap-specific limits, remain a promotion
gate before this ceiling can be raised.

The conformance provider does not make these large values a test allocation target.
Conformance tests use small fixtures and separately verify rejection at the
boundaries. All normal and hostile service components share one process-wide
session gate, matching the advertised single concurrent session even when a
test binds different components explicitly.

## Host Output Validation

Before cache publication, the host validates:

- ZIP framing, flags, methods, CRCs, data descriptors, bounds, and compression
  ratios without trusting `ZipInputStream` alone. Declared per-entry and
  aggregate sizes and ratios are checked from the central directory before
  inflation.
- Exactly the contiguous `classes*.dex` entries and no other entry.
- DEX magic and negotiated version, file and header size, endian tag, Adler-32,
  and SHA-1 signature.
- Fixed-width section bounds, alignment, ordering, map-list consistency, and
  referenced class descriptors.
- Duplicate class definitions across DEX entries.

This is structural validation. It does not replace Android's instruction,
control-flow, encoded-value, annotation, or verifier checks. Loading the
artifact remains a separate trusted-host operation.

The current validator supports fixed 0x70-byte headers for DEX 035, 037, 038,
039, and 040. DEX 041 is rejected until its container/header rules receive a
separate implementation and corpus.

The V1 experimental harness requires `request.minApi` to equal the current host
runtime API and restricts accepted versions using the D8 output profile:

- API 24-25: DEX 035 and 037.
- API 26-27: DEX 035, 037, and 038.
- API 28-29: DEX 035, 037, 038, and 039.
- API 30 and newer: DEX 035, 037, 038, 039, and 040.

This check happens before cache publication. It prevents a provider from
returning a version outside the host's conservative D8 compatibility profile;
on older targets this also avoids deferring an unsupported-version failure
until `DexClassLoader`.

## Failure And Degradation

The R1.1 coordinator performs at most one plugin dispatch and never retries
Binder work after dispatch. It requires the enabled experiment and one explicit
same-signed exact component; V1 performs no automatic provider selection.
Disabled, unavailable, pre-dispatch failure, remote failure, invalid output, or
verified-artifact adoption failure claims at most one built-in D8/dx attempt.
Post-dispatch fallback first rolls back plugin output. Cancellation propagates
as interruption and never changes compilers or triggers fallback.

That cancellation rule also applies inside single-flight. Any interrupted caller,
including the final waiter, detaches without a local fallback, while the shared
producer may finish and populate the cache in the background. Last-waiter
cooperative cancellation is deferred to R2. A safety circuit opened by unsafe
executor rejection or a suspected blocked Binder call remains open for the
current process lifetime rather than speculatively recovering.

Production source wiring is intentionally limited to the raw JAR branch used by
`runtime.loadJar`. `loadAar`, `loadDex`, compatibility helpers, and
`GeneratedClassLoader.defineClass()` retain their existing built-in behavior.
A verified DEX ZIP is copied into host-owned class-loader storage, rehashed, made
read-only, and only then handed to `DexClassLoader`; an adoption failure can use
the same retained input snapshot for the single local fallback.

The source tree now also contains an explicitly opted-in
`DexCompilerRealProviderAndroidTest` entry that pins the real package, exact
service, and same signer; uses a unique raw JAR; expects `Published` followed by
`CacheHit`; invokes a fixture through the real `DexClassLoader`; and cleans the
exact generation. Its real-Harness method and production `AndroidClassLoader`
method each passed once on API 31 arm64 against the earlier host
`17092F2D…A97EB`, test `1FC07F35…63B89`, and plugin `E0E5D861…F9C5D`
APKs; post-test cache/workspace/opt-in cleanup, uninstall, and four-package
absence were verified. This historical result is retained as provenance; the
rebuilt current-APK evidence follows.

The current Android-test source adds two production-concurrency, two real
lifecycle, and three real-corpus methods. The concurrency probe increments only
after the host coordinator commits a remote dispatch; it is not a direct count
of provider `openSession` calls. Lifecycle covers a blocked raw request,
BUSY/QUEUE, descriptor and terminal ownership, plus explicitly consented
force-stop/Binder-death recovery. Corpus covers Java/Kotlin single-DEX, defined
duplicate loading, missing-dependency ART resolution, and a separate heavy
multi-DEX gate that generates 7,300 classes and 65,700 methods. That gate parses
every emitted DEX header, map, and class_defs table and executes one dynamically
selected class from each of `classes.dex` and `classes2.dex`.

The corrected current code/APK gate passed 16 host DEX suites/149 tests with no
failure/error/skipped result; Android-test Kotlin compilation and host/test APK
assembly also succeeded. The executed APK SHA-256 values were host arm64
`181E38E8…A0A47`, strict lifecycle test `70FAE1E8…39B0C`, and repaired
plugin `5B6AC53B…640C4B`, all with v2 signer certificate SHA-256
`31a681fc…c213`. On `QV710AF65F` API 31 arm64, the two concurrency methods,
strict BUSY/FD/callback/gate reuse, Java/Kotlin single-DEX and duplicate identity,
missing-dependency ART resolution, the 65,700-method primary/secondary multi-DEX
load, and strict Binder-death/EOF/no-provider-terminal/rebind method all passed
individually. Their respective durations were 1.479 s, 0.583 s, 2.177 s, 0.555 s,
0.524 s, 21.692 s, and 1.355 s; the workspace was empty after each lifecycle
gate. Final host/test/plugin uninstall operations all returned `Success`, the
fake provider remained absent, and the related process count was zero. This
satisfies R1.2 4/4 only. R1.1 remains 0/7 and R1.3 remains entirely open because
the result covers only one API/ABI cell.

Force-stop exposed a real stale session workspace containing a zero-byte
`program.jar` and empty `d8-output`. The repaired provider now runs a
process-once strict-canonical janitor before exposing its first service Binder,
anchored to the private cache root and fail-closed on traversal violations. The
plugin passed 10 suites/48 tests, including 6/6 workspace-recovery cases, lint
with 0 errors/27 warnings, and Debug/Release assembly. The old UUID existed
before update; first bind/kill/recovery reduced the workspace to an empty root,
and a normal D8/`DexClassLoader` run passed in 0.832 s while it remained empty.
This is the first partial R2 recovery result, not full host-death, callback,
cancellation, timeout, or terminal-matrix acceptance. Removing host compiler
dependencies is a later explicit decision.
