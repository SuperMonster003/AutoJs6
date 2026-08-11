# DEX Compiler Protocol V1

This document records the frozen V1 contract and the completed R1/R2 deployment
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

R1 acceptance is complete: the seven production-route gates and the canonical
API 24/25/26/28/31/34/36 real-provider matrix passed. This does not alter the
control plane: installation still does not enable the route, selection remains
explicit and same-signed, and the plugin is not promoted to the default compiler.

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
invalidates that exact generation. Production Runtime passes the finalized
semantic attempt key through a process-scoped single-flight coordinator. Matching
callers share one producer, while each successful caller receives a fresh
capability. Interrupting a waiter detaches only that caller and returns
cancellation without local compiler fallback. The producer continues while
another waiter remains; when the final waiter leaves before a terminal result, a
leader-owned one-shot handle cooperatively requests producer cancellation. That
request prevents late publication and eventually releases owned resources, but
an already entered D8 invocation may continue using CPU until it returns. The
unsafe-executor or suspected blocked-Binder circuit remains deliberately
conservative: once opened, it stays open for the current host process lifetime
and does not claim to forcibly stop work that is already blocked.
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

That cancellation rule also applies inside single-flight. Any interrupted caller
detaches without a local fallback. The shared producer continues while another
waiter remains; the final waiter to leave before a terminal result cooperatively
requests cancellation through the leader-owned handle. Cancellation wins before
publication when registered first, closes resources during terminal cleanup, and
does not claim to stop CPU work already executing inside D8. A safety circuit
opened by unsafe executor rejection or a suspected blocked Binder call remains
open for the current process lifetime rather than speculatively recovering.

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

The corrected code gate passed 16 host DEX suites/149 tests with no
failure/error/skipped result; wire 4/24 and fake-provider 5/25 were also green,
without treating the fake provider as end-to-end evidence. On `QV710AF65F` API
31 arm64, two production-concurrency, two real-lifecycle, and three real-corpus
methods passed individually. The separate heavy corpus generated 65,700 methods,
loaded primary and secondary DEX classes, and left the workspace clean. These
results close R1.2 4/4 and retain their single-device evidence boundary.

R1.1 then closed 7/7 with eight production-routing instrumentation methods on
API 34. They cover opt-in raw-JAR routing and exactly one fallback, compatible
JAR/AAR/DEX/`defineClass()` built-in routing, the pinned decision snapshot,
verified-adoption failure and exact cache eviction, cancellation without local
compiler startup, same-key concurrency outside the local conversion monitor,
and probe-fault/interruption ordering.

The final canonical real-provider campaign is
`f3c2b1af-be93-41e7-b541-f167f90e5cc1`. Its Gate passed 7/7: API 24/25 use the
D8 CLI fallback; API 26/28/34/36 use `D8Command` on x86_64 emulators; API 31
uses the authorized `QV710AF65F` arm64-v8a physical device with its user 0/user
10 topology preserved. The clean commits are host
`e39023758e3a66a24f0ce90466b5bc77a503515d` and plugin
`1f50d5333ab3a58c4c0f00fe06a20a5692aa3448`. Host/test/plugin APK SHA-256 values
are `505bc077ad3586b802ba41eec5b093a01b3e7be011e0de7bf0b83dbf0aa1b878`,
`4199d54d2f0fa8bd96d3c67d3c7073bf20a0c6f51f8248d2727e2dd75a75da24`, and
`65e432a7b4866cfb3db0392b87ac60e3dbab9161acc232b42b57a66abf08bd88`; all use
signer certificate SHA-256
`31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213`.
The journal head is
`a5abaf62803c6277aa015563cf16f2771eb0ffa9f9a226a20e7ac835ed54581b`, the
runner SHA-256 is
`ca89ac1637e5c690ced9e2e2d0b3d35aecf5c32584c6816883a21c363f040057`, and the
campaign identity is
`6b859228a4943d74ab67707fa6c9a785f77248768ec1a4807e5e214034383665`.
Failed and intentionally terminated earlier campaigns remain immutable evidence;
the final PASS does not rewrite them.

Force-stop exposed a real stale session workspace containing a zero-byte
`program.jar` and empty `d8-output`. The repaired provider runs a process-once
strict-canonical janitor before exposing its first service Binder, anchored to
the private cache root and fail-closed on traversal violations. R2 subsequently
unified terminal, stop, cleanup, last-waiter cancellation, and monotonic-deadline
ownership. The current gates are 17 host DEX suites/166 tests and 12 plugin
suites/65 tests, with no failure/error/skipped result and plugin lint at 0 errors.

The representative R2 closeout package at
`D:\idea-projects\AutoJs6-DEX-R2-Evidence-20260811\closeout-api34-dab3f857-650d-4107-a3b9-941a1f7e02c2\report.json`
passed real D8 failure/recovery, caller cancellation, and provider-process
interruption on one authorized API 34 x86_64 target. Each scenario records its
terminal observation, publication boundary, descriptor/workspace cleanup, and
next-session availability. The report SHA-256 is
`5d1549b9d5207e5dcd1c62a3c2caf863a748f49b8f9f2582f66bb2898be98eba`;
its 61-file manifest SHA-256 is
`927ee1abebe83a0901e7f767a56b377a2afd84a137d16b8d5d3784fcd54e639e`.
This closes R2 without creating a new device matrix and without equating result
termination with D8 CPU termination. Removing host compiler dependencies remains
a later explicit decision.
