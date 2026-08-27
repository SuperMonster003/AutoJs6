# R8 compiler host control plane

## Scope

AutoJs6 exposes the independent R8 provider only through the explicit
`runtime.loadJarWithR8(...)` compile/load entry and the explicit
`runtime.retraceR8Stack(...)` protocol 1.1 entry. The existing `runtime.loadJar(...)` and
`runtime.loadJarWithClasspath(...)` paths retain their D8/dx behavior and never select R8
implicitly. R8 use is default-off: a user must choose one eligible provider component in
Developer options before either explicit call can dispatch.

The compile entry has six overloads. Every call supplies at least one keep-rule file. The full
shape also carries an ordered classpath, consumer-rule files, and a parallel integer array that
assigns each consumer-rule file to one classpath ordinal. Three overloads additionally accept one
nonexistent report-export directory. The retrace entry accepts an obfuscated stack plus the exact
mapping and retrace-metadata pair exported from one verified compile. Empty keep rules, mismatched
arrays, invalid ordinals, missing selection, discovery or binding failures, protocol errors,
remote failures, cancellation, provenance mismatch, and artifact-validation failures terminate
the explicit request. None of those conditions can continue through D8, dx, or local retrace.

## Frozen API and source boundary

The host consumes the immutable R8 compiler API 0.2.0 AAR through the local
`libs:r8-compiler-api-0_2_0` wrapper. Its SHA-256 is
`ea1416913db1a93328c2fc8017f36a790e9e2ca04b8ad1c234d763da2d367424`. The shared tagged-wire
source remains pinned at
`5d5c672ef0c7907b1a0cf6fd041ff85dc7c864aa07628316db5b9a9abb9e87c8`. Host Gradle wiring does
not reach into the sibling provider source project, and the R8 implementation does not import the
DEX provider namespace.

API 0.2.0 keeps the three protocol 1.0 provider methods at Binder transaction IDs 1 through 3 and
appends `getRetraceCapabilities()` and `openRetraceSession(...)` at IDs 4 and 5. Compile schemas
and request semantics are unchanged. The freeze includes two byte-identical clean AAR builds, a
strict Java-visible JVM ABI golden, exact generated Binder method descriptors and transaction
constants, golden 1.1 wire/bundle hashes, and a detached Java 17 consumer compile against only
android.jar and the two AAR `classes.jar` payloads.

The Developer-options selector discovers only the exact frozen R8 service action. Eligibility is
decided by the shared selection policy: the retained exact component must still exist, expose the
required service, and have the same signer as the host. Selection is stored as an exact component,
not as a package wildcard. Disabling R8 preserves no implicit provider and remains the default.

## Runtime dispatch and artifact loading

`AndroidR8CompilerHost.runtimeDispatcher(...)` provides one process-wide dispatcher so selection,
binding, active-attempt exclusion, cancellation, and teardown are consistent across scripts. The
explicit runtime route resolves each input path once in canonical order, builds an
`R8CompilerHostCompileSpec`, and accepts only a published R8 transaction outcome.

Requests are fixed to protocol 1.0, `R8`, `R8_EXPLICIT`, `FULL_RELEASE`, and fallback policy
`NONE`. Program, ordered classpath, keep rules, consumer rules, minimum API, capability limits,
provider identity, and all semantic options participate in validation and cache identity. No host
filesystem path is serialized into the protocol request or output bundle.

The input descriptor is read-only and the output descriptor is write-only. Caller-owned
descriptors are closed immediately after `openSession`; a descriptor-close failure also cancels and
closes any returned session. Callback metadata is bounded and must follow one started event,
monotonic progress, and exactly one valid terminal event for the current request. Timeout,
interruption, Binder death, malformed metadata, remote failure, cancellation, duplicate terminals,
and cleanup failure all end the R8 attempt without semantic fallback.

A completed output bundle remains provisional until the host revalidates result semantics,
canonical bundle framing, every declared size and SHA-256, bounded text and retrace metadata,
contiguous `classes*.dex` entries, DEX header and file-size fields, endian tags, SHA-1 signatures,
and Adler-32 checksums. Mapping, seeds, usage, retrace metadata, and DEX ZIP are all mandatory and
validated before publication.

Only a fully verified bundle is copied into a new `partial-<uuid>` cache generation. A
same-directory rename to `entry-<semantic-sha>-<uuid>` is its visibility commit point. Cache lookup
rebinds the current request ID and repeats bundle and artifact validation; every returned descriptor
is hashed again before exposure. Corrupt generations are evicted and cause another attempt against
the same selected R8 provider, never D8/dx fallback.

For execution, the explicit route exposes only the verified R8 DEX ZIP to `AndroidClassLoader`.
The loader independently checks its length and SHA-256, adopts it atomically into a read-only
`r8_verified_` cache file, and creates the class loader from that immutable copy. This seam cannot
call the local D8/dx compiler methods. The other four verified artifacts remain transaction
evidence and are not treated as executable input.

When an export directory is supplied, the route exports only mapping, seeds, usage, and retrace
metadata. Each cache stream is copied into an isolated same-parent staging directory while its
size and SHA-256 are recomputed. Text and retrace metadata are decoded again, and the four-file
directory is published by one rename only after all checks pass. An existing destination is never
merged or overwritten, a failed export is removed before the route returns, and DEX never crosses
the report-export boundary. Export completes before the verified DEX is handed to the loader.

## Retrace dispatch and provenance

`AndroidR8CompilerHost.runtimeRetraceDispatcher(...)` owns the blocking retrace route. It applies
the same exact-component selection, same-signer identity pinning, post-bind reinspection, and
default-off policy as compile. It additionally requires protocol 1.1 in the provider's advertised
range and pins the complete retrace capability fingerprint before constructing a request.

The host resolves the mapping and metadata paths once, reads them through bounded sources, and
canonicalizes the stack to strict UTF-8 with LF termination. The three inputs are written in the
fixed `AJ6R8T01` bundle order. Every entry has an exact size and SHA-256; the request also binds the
full bundle digest and a domain-separated provenance ID derived from the mapping and metadata
digests. The metadata mapping digest, compiler version, mapping format, compile capability,
runtime-library fingerprint, input-set fingerprint, min API, and profile remain available for
provider verification. No path or mutable host object crosses Binder.

Retrace callbacks use dedicated protocol 1.1 schemas over the existing opaque callback and
session lifecycle surfaces. The host admits at most one started event, strictly increasing
progress, and exactly one matching terminal payload. It closes the remote session before adopting
output, then checks the raw output file against the terminal size and SHA-256 and validates strict
non-whitespace UTF-8/LF text. Timeout, interruption, Binder death, repeated terminal callbacks,
descriptor/session cleanup failure, and output mutation all fail closed. The script receives text
only after that adoption boundary; no local retrace fallback exists.

## Failure boundary

The compile host state machine has only `SELECTED`, `NEGOTIATED`, `DISPATCHED`, `COMPLETED`, and `FAILED`.
Its failure directive always propagates an R8 failure and always sets semantic fallback to false.
The script route maps cancellation to script interruption; all other non-published outcomes become
an explicit R8 load failure whose message records that no D8/dx fallback was attempted.

The focused JVM suite drives the real dispatcher and transaction runner through selection,
binding, negotiation, dispatch, adoption, cache publication and hit, corrupt-cache redispatch,
remote failure, malformed or duplicate callbacks, invalid bundle and DEX data, timeout,
cancellation, Binder-death injection, descriptor and session cleanup failures, explicit script
argument shapes, Rhino array conversion, and the class-loader handoff boundary.

The G10 JVM slice adds protocol 1.1 selection and identity drift, bounded input snapshotting,
mapping/metadata mismatch, immutable bundle payloads, callback ordering and duplicate terminal
rejection, selected-provider dispatch, protocol 1.0 rejection before session open, output mutation,
session cleanup failure, four-report atomic export, existing-destination refusal, changed-source
rehashing, the public script surface, and no-provider/no-fallback behavior. Provider JVM tests run
the embedded R8 retrace implementation against a real mapping and obfuscated frame.

## Local evidence and non-claims

The invocation-bound G3 v4 gate covers 11 App Debug JVM suites and exactly 49 tests with no
failure, error, or skip. It compiles the exact 25-file production R8 package and 13-file test
package, the public script integration, the Android class-loader seam, the Developer-options
selector, and all 11 localized resource sets. It also pins the frozen AAR and tagged-wire source,
requires a single new public script method family with three overloads, and verifies the absence of
source-project, DEX-namespace, local-compiler, and dispatcher-bypass coupling.

That G3 count remains historical evidence for the protocol 1.0 slice. The current G10 host tests
cover the six compile overloads, the retrace method, export, and protocol 1.1 boundaries; their
final counts are recorded with the G10 implementation commit rather than rewriting the G3 report.

This local evidence claims explicit script and Developer-options host integration. It does not
claim G10 cross-APK Binder/PFD execution on API 25, 28, or 37, provider installation, real Android
process death, release signing, new publication, or public availability. No ADB, install,
connected-device, or protected-device operation is part of the G10 local slice.
