# Capability Relaxation Rubric

This rubric preserves the Phase 13 promotion criteria for capabilities that
remain gated, disabled, or unsupported. In Node Profile v1.2, ESM, dynamic
import, controlled/native supported networking, WebSocket, `worker_threads`,
native `child_process`, and allowlisted Java interop have already graduated;
their rows below describe ongoing security boundaries rather than opt-in gates.

The default `safe_default` profile must continue to fail closed for any surface
that has not graduated into the stable baseline.

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
- Capability declaration: Which runtime permission or project/package metadata
  authorizes it? Stable builtins must not depend on Gradle properties.
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
- Rollback: Which runtime policy, manifest boundary, release revert, or other
  audited mechanism contains a regression after release?

## Feature-Specific Requirements

| Capability | Current boundary or extra requirements before promotion |
| --- | --- |
| Java/Android interop | Stable exact class/member allowlist, reflection/classloader/native-library denial, execution-owned handles, cleanup, and UI-thread dispatch policy. |
| `require("rhino").install()` | Stable only through `explicit: true`, layered on controlled Java interop, with no default global mutation and no `node_modules/rhino` shadowing bypass. |
| Native network | Stable `dns`, `dns/promises`, `http`, `https`, `net`, and `tls` follow Android sandbox/manifest policy and require caller-owned connection, byte, timeout, TLS, and fd cleanup limits; `dgram` remains disabled. |
| Broader filesystem | Explicit authorized roots, SAF or user grant policy, symlink/realpath escape denial, `/proc`/`/sys`/`/dev` policy, scoped fallback. |
| `worker_threads` | Stable bridge-module/Android-object isolation, nested-worker denial, worker/message/memory/time budgets, crash recovery, cleanup, and packaged runtime-kit behavior. |
| WASI | Explicit preopen roots, env/args policy, fd budget, clock/random policy, no unrestricted host filesystem. |
| Inspector | Debug-only first, ADB-only binding, release denial, profile data privacy review. |
| `child_process` | Stable native Node semantics inside Android UID/SELinux/manifest constraints; callers validate executable paths/arguments, bound stdio/timeouts, and kill or reap children. It does not inherit the `shell` bridge allowlist or budgets. |
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

P13-50 adds `RELAXED_CAPABILITY_SECURITY_CORPUS.json` as the shared capability
security corpus index. Graduated network, Java, worker, and child-process rows
must retain positive, negative, abuse, runtime-guard, packaged, cleanup, and
rollback regression coverage. Future filesystem relaxation, native addons,
Inspector, WASI, or other gated surfaces must add the same evidence before
promotion.

## Release Rule

`debug_unsafe_lab` and ungraduated critical-risk surfaces cannot be accepted in
release builds without the full evidence set. Missing evidence must fail
closed, not silently fall back
to broader authority.
