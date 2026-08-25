# R8 compiler host control plane

## Scope

AutoJs6 exposes the independent R8 provider only through the explicit
`runtime.loadJarWithR8(...)` script entry. The existing `runtime.loadJar(...)` and
`runtime.loadJarWithClasspath(...)` paths retain their D8/dx behavior and never select R8
implicitly. R8 use is default-off: a user must choose one eligible provider component in
Developer options before an explicit call can dispatch.

The public entry has three useful overloads. Every call supplies at least one keep-rule file. The
full overload also carries an ordered classpath, consumer-rule files, and a parallel integer array
that assigns each consumer-rule file to one classpath ordinal. Empty keep rules, mismatched arrays,
invalid ordinals, missing selection, discovery or binding failures, protocol errors, remote
failures, cancellation, and artifact-validation failures terminate the R8 request. None of those
conditions can continue through D8 or dx.

## Frozen API and source boundary

The host consumes the immutable R8 compiler API 0.1.0 AAR through the local
`libs:r8-compiler-api-0_1_0` wrapper. Its SHA-256 is
`e9df49b7e49992615a15bc0af2372a4525f02b4a2a915a560ddab3128bb2f066`. The shared tagged-wire
source remains pinned at
`5d5c672ef0c7907b1a0cf6fd041ff85dc7c864aa07628316db5b9a9abb9e87c8`. Host Gradle wiring does
not reach into the sibling provider source project, and the R8 implementation does not import the
DEX provider namespace.

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

## Failure boundary

The host state machine has only `SELECTED`, `NEGOTIATED`, `DISPATCHED`, `COMPLETED`, and `FAILED`.
Its failure directive always propagates an R8 failure and always sets semantic fallback to false.
The script route maps cancellation to script interruption; all other non-published outcomes become
an explicit R8 load failure whose message records that no D8/dx fallback was attempted.

The focused JVM suite drives the real dispatcher and transaction runner through selection,
binding, negotiation, dispatch, adoption, cache publication and hit, corrupt-cache redispatch,
remote failure, malformed or duplicate callbacks, invalid bundle and DEX data, timeout,
cancellation, Binder-death injection, descriptor and session cleanup failures, explicit script
argument shapes, Rhino array conversion, and the class-loader handoff boundary.

## Local evidence and non-claims

The invocation-bound G3 v4 gate covers 11 App Debug JVM suites and exactly 49 tests with no
failure, error, or skip. It compiles the exact 25-file production R8 package and 13-file test
package, the public script integration, the Android class-loader seam, the Developer-options
selector, and all 11 localized resource sets. It also pins the frozen AAR and tagged-wire source,
requires a single new public script method family with three overloads, and verifies the absence of
source-project, DEX-namespace, local-compiler, and dispatcher-bypass coupling.

This local evidence claims explicit script and Developer-options host integration. It does not
claim a published provider, cross-APK Binder/PFD behavior on a device, provider installation, real
Android process death, release signing, publication, or public availability. No ADB, install,
connected-device, or protected-device operation is part of this slice.
