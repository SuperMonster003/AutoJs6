# Node Profile Rollback Playbook

Schema: `autojs6-node-profile-rollback-policy-v1`

Node Profile v1.2 treats ESM, dynamic import, controlled network, WebSocket,
native network builtins, `worker_threads`, `child_process`, and allowlisted
Java interop as stable features. They have no capability-specific Gradle or
runtime kill switch. A rollback therefore requires an explicit profile/contract
release, not a hidden build variant.

## Runtime Profile Switches

| Profile | Default | Property | Purpose |
| --- | --- | --- | --- |
| `safe_default` | on | always on | Current stable Node runtime and scoped security baseline. |
| `pro_compat_opt_in` | off | `autojs.nodejs.profile.pro.enabled` | Metadata/review lane for broader Pro compatibility work. |
| `desktop_compat_opt_in` | off | `autojs.nodejs.profile.desktop.enabled` | Metadata/review lane for additional desktop parity. |
| `debug_unsafe_lab` | off | `autojs.nodejs.profile.debugUnsafeLab.enabled` | Debug-only research surfaces such as a future Inspector session. |

These profile properties do not disable or enable the stable capability set.
They may expose only separately documented non-stable work after its own
security and packaging review.

## Stable Feature Rollback

| Capability | Current state | Rollback action |
| --- | --- | --- |
| ESM / dynamic import | stable, built in | publish a versioned loader/profile demotion with migration notes and compatible errors |
| controlled network / WebSocket | stable, built in | publish a versioned provider/profile demotion; preserve permission and cleanup diagnostics |
| native network builtins | stable, built in | publish a versioned compatibility demotion; do not add a private Gradle switch |
| `worker_threads` | stable, built in | publish a versioned profile/contract demotion; preserve lifecycle cleanup and never use same-thread fallback |
| `child_process` | stable, built in | publish a versioned compatibility demotion and clearly separate the AutoJs `shell` bridge |
| Java interop | stable allowlist | remove or narrow reviewed allowlist entries through a versioned release; preserve exact denial codes and handle cleanup |

Project/package metadata cannot alter these states. Release and host AAR tasks
must contain the same stable contract as Debug builds.

## Existing Denial/Partial Boundaries

| Surface | Current action |
| --- | --- |
| filesystem expansion | keep `workingDirectory` scoped access and explicit authorized roots only |
| native addons | keep `.node`, `process.dlopen`, and private native bindings unsupported |
| Inspector | keep module/server/profiling authority disabled |
| raw WASI | keep denied; pure WebAssembly remains the supported path |
| UDP/`dgram` | keep denied until a separate socket/permission/resource design exists |
| unrestricted Java | keep reflection, class loading, process/native loading, and raw Android objects denied |

## Crash, Stress, And Security Triggers

| Trigger | Immediate action | Release action |
| --- | --- | --- |
| native crash | stop the execution, archive native/Doctor evidence, disable the affected launch surface operationally where possible | issue a versioned runtime/plugin/profile patch |
| provider crash | close owned resources and fail the request without replay | fix or demote the provider contract |
| worker/process/socket leak | stop admission for the affected execution and archive resource counters | fix lifecycle behavior or publish an explicit demotion |
| security corpus regression | fail CI/release and keep the vulnerable build unpublished | patch the boundary before release |
| packaged APK regression | fail packaging/install-run gates | rebuild and publish a corrected package/profile contract |

`safe_default` is the fallback policy baseline, but it includes the current
stable Node capability set. It is not a legacy “everything disabled” profile.

## Packaged APK Downgrade

Published APK authority is changed only through a new auditable package:

- update the versioned runtime/profile contract;
- remove or narrow affected declared permissions/metadata;
- preserve stable denial diagnostics;
- verify cleanup and Safe downgrade behavior;
- regenerate and lock API, capability catalog, runtime kit, manifest, and
  compatibility artifacts;
- document the behavioral change and migration.

Remote or undocumented packaged kill switches are rejected. A capability
demotion must remain visible in the profile, Doctor report, changelog, and
release artifacts.
