# Node Profile Rollback Playbook

Schema: `autojs6-node-profile-rollback-policy-v1`

P13-51 defines the rollback playbook for Safe/Pro/Desktop/Debug profile work.
The policy is intentionally `playbook_partial` and `metadata_only_no_authority`:
it documents the switches and fallback actions required before relaxed
capability promotion, but it does not promote runtime authority.

## Profile Switches

| Profile | Default | Gradle property | Rollback action | Fallback |
| --- | --- | --- | --- | --- |
| `safe_default` | on | `always_on` | keep the Safe Node Profile | `safe_default` |
| `pro_compat_opt_in` | off | `autojs.nodejs.profile.pro.enabled=false` | disable Pro metadata and rebuild | `safe_default` |
| `desktop_compat_opt_in` | off | `autojs.nodejs.profile.desktop.enabled=false` | disable Desktop metadata and rebuild | `safe_default` |
| `debug_unsafe_lab` | off | `autojs.nodejs.profile.debugUnsafeLab.enabled=false` | disable debug unsafe lab and rebuild | `safe_default` |

Release builds keep these promotion switches disabled until the corresponding
promotion, privacy, and packaged gates are closed.

## Capability Switches

| Capability | Profile lane | Switch or current rollback | Rollback action |
| --- | --- | --- | --- |
| `network` | `desktop_compat_opt_in` | `autojs.nodejs.network.experimental=false` | disable controlled network provider experiments |
| `raw_network` | `desktop_compat_opt_in` | `autojs.nodejs.rawNetworkModules.experimental=false` | disable raw native Node network builtins and match the P13-50 corpus capability id |
| `raw_node_network_modules` | `desktop_compat_opt_in` | `autojs.nodejs.rawNetworkModules.experimental=false` | disable native Node network builtin experiments |
| `worker_threads` | `desktop_compat_opt_in` | `autojs.nodejs.worker_threads.experimental=false` | disable worker runtime experiments |
| `java_interop` | `pro_compat_opt_in` | `autojs.nodejs.javaInterop.experimental=false` | disable allowlist Java interop experiments |
| all relaxed capabilities | Pro/Desktop | `autojs.nodejs.relaxedCapabilities.enabled=false` | keep the relaxed-capability bundle disabled |
| `filesystem_relaxation` | `desktop_compat_opt_in` | `safe_profile_scoped_fs` | keep `workingDirectory` scoped fs only |
| `child_process` | `desktop_compat_opt_in` | `stable_denial_no_runtime_switch` | keep `child_process` denied and use the scoped `shell` bridge |
| `native_addon` | `desktop_compat_opt_in` | `stable_denial_no_runtime_switch` | keep `.node` and `process.dlopen` denied |
| `inspector` | `debug_unsafe_lab` | `debug_profile_only_no_runtime_switch` | keep inspector denied outside future debug session gate |
| `wasi` | `desktop_compat_opt_in` | `stable_denial_no_runtime_switch` | keep raw WASI denied |

Capabilities that are not promoted yet roll back by staying on their existing
stable denial or Safe-profile scoped behavior.

## Crash And Stress Triggers

| Trigger | Evidence | Required action |
| --- | --- | --- |
| `native_crash` | `NodeCrashEvidenceStore` | disable requested profile and relaunch with `safe_default` |
| `bridge_provider_crash` | `NodeCrashEvidenceStore` | disable the provider capability and relaunch with `safe_default` |
| `stress_regression` | `verifyNodePhase13FullHeavyResourceStress` | disable the profile or capability until stress gate recovers |
| `security_corpus_regression` | `RELAXED_CAPABILITY_SECURITY_CORPUS.json` | fail closed and keep the relaxed capability unpromoted |
| `packaged_apk_regression` | packaged capability metadata and crash bundle | ship a Safe-profile patch or remove promoted metadata |

`safe_default` is the mandatory fallback for crash and stress recovery.

## Packaged APK Downgrade

Published packaged APKs use
`publish_safe_profile_patch_or_remove_promoted_metadata` as the downgrade
strategy:

- remove `pro_compat_opt_in` or `desktop_compat_opt_in` metadata;
- remove relaxed `node.permissions`;
- rebuild the packaged APK with Safe profile defaults;
- keep stable denial errors for undeclared capabilities;
- record crash/stress evidence before re-enabling any profile or capability.

Remote packaged kill switches are explicitly rejected for v1.1 because packaged
rollback must be auditable through a published Safe-profile patch or removal of
promoted metadata. Runtime profile promotion rollback wiring and automatic crash circuit breaker implementation are deferred until effective profile switching is promoted. Settings UI, packaged builder UI, and privacy disclosure prompts are still future work.
