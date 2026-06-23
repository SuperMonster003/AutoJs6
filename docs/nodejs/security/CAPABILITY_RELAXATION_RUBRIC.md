# Capability Relaxation Rubric

This rubric is the Phase 13 gate for relaxing current Safe Node Profile
boundaries. It applies to high-risk Auto.js Pro and desktop Node.js surfaces
such as Java interop, raw network, broader filesystem access, `worker_threads`,
WASI, inspector, `vm`, `child_process`, native addon loading, root/Shizuku, UI
overlay, screenshot, recorder, and Android-side package installation.

The default `safe_default` profile must continue to fail closed unless a task
explicitly promotes a capability into that profile.

## Profiles

| Profile | Intended use | Release posture |
| --- | --- | --- |
| `safe_default` | Existing production-safe Node surface. | Default for normal scripts and packaged APKs. |
| `pro_compat_opt_in` | Auto.js Pro compatibility features that may require Android/Java authority. | Requires explicit project/package metadata and user-facing diagnostics. |
| `desktop_compat_opt_in` | Desktop Node.js compatibility features that may need broader OS resources. | Requires explicit project/package metadata and per-feature gates. |
| `debug_unsafe_lab` | Early validation for high-risk features. | Debug builds only; release builds must reject. |
| `permanently_unsupported` | Capabilities that remain unavailable after review. | Stable denial and migration guidance. |

## Promotion Stages

| Stage | Meaning | Minimum evidence |
| --- | --- | --- |
| `planned` | Tracked in a Phase checklist but not designed. | Task owner and desired profile. |
| `design_locked` | Boundary, API, permissions, errors, cleanup, tests, packaged behavior, and rollback are documented. | Design doc plus verifier. |
| `debug_poc` | Runtime proof exists only in debug or local validation builds. | Positive test, negative test, and explicit debug gate. |
| `provider_poc` | Fake or live provider proves the API shape but is not promoted. | Provider contract, fake transport, cleanup evidence. |
| `opt_in_promoted` | Available through `pro_compat_opt_in` or `desktop_compat_opt_in`. | Host gate, device gate, packaged smoke, security corpus, docs, types, rollback. |
| `default_promoted` | Available in `safe_default`. | Same as opt-in plus release-risk review and backward-compatibility evidence. |
| `rejected` | Intentionally unavailable. | Stable error code, tests, docs, migration alternative. |

## Required Checklist For Relaxing A Capability

Every relaxation task must answer these questions before promotion:

- Profile: Which of `pro_compat_opt_in`, `desktop_compat_opt_in`, or
  `debug_unsafe_lab` owns the feature?
- Capability declaration: Which `project.json`, package metadata, or Gradle
  property enables it?
- Android permissions: Which manifest or runtime permissions are required?
- User disclosure: Does the feature require notification, foreground service,
  settings screen, or runtime warning?
- Resource ownership: Which registry owns handles, sockets, files, workers,
  native pointers, Java objects, or provider subscriptions?
- Cleanup: What happens on normal completion, timeout, cancellation, service
  destroy, process crash, and next launch?
- Limits: What are the hard caps for concurrency, bytes, queue length, file
  descriptors, process count, memory, execution time, and message size?
- Packaged behavior: How does the feature work in APK builds, and what metadata
  is required?
- Negative path: What stable error appears when the profile, capability,
  provider, permission, ABI, Android version, or external dependency is absent?
- Rollback: Which single property, manifest switch, or profile policy disables
  the feature after release?

## Feature-Specific Requirements

| Capability | Extra requirements before promotion |
| --- | --- |
| Java/Android interop | Class/package allowlist, reflection denial, classloader denial, native library denial, object lifetime ownership, UI-thread dispatch policy. |
| `require("rhino").install()` | Must be layered on controlled Java interop, keep Safe profile disabled, and prevent `node_modules/rhino` shadowing bypass. |
| Raw network | Host/port policy, TLS error behavior, connection and byte budgets, background policy, fd cleanup, packaged `INTERNET` metadata. |
| Broader filesystem | Explicit authorized roots, SAF or user grant policy, symlink/realpath escape denial, `/proc`/`/sys`/`/dev` policy, scoped fallback. |
| `worker_threads` | Bridge module isolation, nested-worker policy, message budget, crash isolation, packaged behavior, second execution slot interaction. |
| WASI | Explicit preopen roots, env/args policy, fd budget, clock/random policy, no unrestricted host filesystem. |
| Inspector | Debug-only first, ADB-only binding, release denial, profile data privacy review. |
| `child_process` | Allowlisted executable roots, no arbitrary path execution, stdin/stdout/stderr budgets, timeout/kill cleanup. |
| Native addon | ABI allowlist, packaged-only or signed artifact policy, hash/integrity lock, no download-and-execute, crash evidence. |
| UI/floaty/screenshot/recorder | Foreground disclosure, Android permission flow, lifecycle cleanup, Back/Recents behavior where applicable. |
| Android-side package installation | Transactional install/update/remove/list/verify/prune, lock file, integrity check, lifecycle script denial by default. |

## Gate Requirements

A task cannot move to `opt_in_promoted` unless it includes:

- runtime implementation or stable rejection path;
- capability and permission manifest updates;
- TypeScript declaration updates;
- host verifier;
- fake provider or conformance test when a provider boundary exists;
- focused device instrumentation when Android behavior is involved;
- packaged APK smoke or explicit packaged rejection evidence;
- security corpus additions covering bypass attempts;
- migration/user documentation;
- rollback documentation.

## Release Rule

`debug_unsafe_lab` and critical-risk surfaces cannot be accepted in release
builds unless a later task explicitly promotes them to `opt_in_promoted` with
the full evidence set. Missing evidence must fail closed, not silently fall back
to broader authority.
