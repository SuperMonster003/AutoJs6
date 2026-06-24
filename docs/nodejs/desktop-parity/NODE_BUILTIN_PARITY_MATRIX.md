# Node Builtin Desktop Parity Matrix

This matrix is generated from `NodeRuntimeCapabilities.kt` and the Phase 13 desktop parity task plan. It tracks current Safe Node builtin behavior against broader desktop Node.js compatibility goals.

## Status Vocabulary

- `partial`: available as a Safe Node subset or wrapper.
- `limited_network_gated`: present for controlled feature detection or facade behavior, but raw network I/O remains gated.
- `disabled`: registry-disabled with stable denial.
- `known_not_wrapped`: known to the CommonJS registry but not currently surfaced as a limited wrapper.
- `missing_from_registry`: in the desktop reference list but not explicitly classified by the current registry.

## Summary

- `disabled`: 10
- `limited_network_gated`: 6
- `missing_from_registry`: 2
- `partial`: 35

## Matrix

| Builtin | Status | Target profile | Source | Next step |
| --- | --- | --- | --- | --- |
| `assert` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `assert/strict` | `partial` | `safe_default` | `limited` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `async_hooks` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `buffer` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `child_process` | `disabled` | `desktop_compat_opt_in` | `commonjs-known`, `disabled` | P13-35 records the process-worker replacement decision as WorkerPool-preferred with no process_worker facade and child_process still denied; P13-40 must design a scoped runner before any opt-in promotion. |
| `cluster` | `disabled` | `permanently_unsupported` | `commonjs-known`, `disabled` | Keep stable denial unless a future Phase 13 task assigns an opt-in profile. |
| `console` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `constants` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `crypto` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `dgram` | `disabled` | `desktop_compat_opt_in` | `disabled` | P13-31 records UDP/dgram as stable-denied for v1.1; unicast client, server bind/listen, multicast, broadcast, packaged INTERNET policy, and fd leak proof remain future work. |
| `diagnostics_channel` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `dns` | `limited_network_gated` | `desktop_compat_opt_in` | `limited`, `commonjs-known`, `network` | P13-30 records staged limited facades as partial; real client sockets, server sockets, network DNS, TLS sessions, fd leak proof, and packaged live-network evidence remain open. |
| `dns/promises` | `limited_network_gated` | `desktop_compat_opt_in` | `limited`, `network` | P13-30 records staged limited facades as partial; real client sockets, server sockets, network DNS, TLS sessions, fd leak proof, and packaged live-network evidence remain open. |
| `domain` | `disabled` | `permanently_unsupported` | `commonjs-known`, `disabled` | Keep stable denial unless a future Phase 13 task assigns an opt-in profile. |
| `events` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `fs` | `partial` | `safe_default` | `limited`, `commonjs-known` | P13-32 records scoped filesystem profile and P13-33 records advanced fs APIs as partial; Safe profile stays working-directory scoped while expanded roots, packaged watcher/fd/FileHandle close-on-destroy, Android unsupported behavior notes, and full desktop fs semantics remain future work. |
| `fs/promises` | `partial` | `safe_default` | `limited`, `commonjs-known` | P13-32 records scoped filesystem profile and P13-33 records advanced fs APIs as partial; Safe profile stays working-directory scoped while expanded roots, packaged watcher/fd/FileHandle close-on-destroy, Android unsupported behavior notes, and full desktop fs semantics remain future work. |
| `http` | `limited_network_gated` | `desktop_compat_opt_in` | `limited`, `commonjs-known`, `network` | P13-29 records the controlled client subset as partial; raw server/listen, proxy/tunnel, custom TLS/session control, and packaged live-network evidence remain open. |
| `https` | `limited_network_gated` | `desktop_compat_opt_in` | `limited`, `commonjs-known`, `network` | P13-29 records the controlled client subset as partial; raw server/listen, proxy/tunnel, custom TLS/session control, and packaged live-network evidence remain open. |
| `inspector` | `disabled` | `debug_unsafe_lab` | `commonjs-known`, `disabled` | P13-37 records inspector debug-profile diagnostics as partial; Safe/default and release keep inspector denied while explicit debug-session UI, loopback ADB forwarding, DevTools attach, profiler artifact redaction, and packaged behavior evidence remain future work. |
| `module` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `net` | `limited_network_gated` | `desktop_compat_opt_in` | `limited`, `commonjs-known`, `network` | P13-30 records staged limited facades as partial; real client sockets, server sockets, network DNS, TLS sessions, fd leak proof, and packaged live-network evidence remain open. |
| `node:test` | `missing_from_registry` | `desktop_compat_opt_in` | - | Add explicit registry entry or document android_not_applicable. |
| `node:test/reporters` | `missing_from_registry` | `desktop_compat_opt_in` | - | Add explicit registry entry or document android_not_applicable. |
| `os` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `path` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `path/posix` | `partial` | `safe_default` | `limited` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `path/win32` | `partial` | `safe_default` | `limited` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `perf_hooks` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `process` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `punycode` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `querystring` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `readline` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `readline/promises` | `partial` | `safe_default` | `limited` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `repl` | `disabled` | `permanently_unsupported` | `commonjs-known`, `disabled` | Keep stable denial unless a future Phase 13 task assigns an opt-in profile. |
| `stream` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `stream/consumers` | `partial` | `safe_default` | `limited` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `stream/promises` | `partial` | `safe_default` | `limited` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `stream/web` | `partial` | `safe_default` | `limited` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `string_decoder` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `timers` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `timers/promises` | `partial` | `safe_default` | `limited` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `tls` | `limited_network_gated` | `desktop_compat_opt_in` | `limited`, `commonjs-known`, `network` | P13-30 records staged limited facades as partial; real client sockets, server sockets, network DNS, TLS sessions, fd leak proof, and packaged live-network evidence remain open. |
| `tty` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `url` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `util` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `util/types` | `partial` | `safe_default` | `limited` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `v8` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `vm` | `disabled` | `desktop_compat_opt_in` | `commonjs-known`, `disabled` | P13-36 records vm module diagnostics as partial; Safe/default keeps vm denied while vm.Script/context/timeout, escape denial, memory budget, and packaged behavior evidence remain future work. |
| `wasi` | `disabled` | `desktop_compat_opt_in` | `commonjs-known`, `disabled` | P13-38 records controlled WASI diagnostics as partial; pure WebAssembly stays available while raw WASI remains denied and controlled preopens, fd/env/args, resource budgets, worker integration, and packaged behavior remain future work. |
| `worker_threads` | `disabled` | `desktop_compat_opt_in` | `commonjs-known`, `disabled` | P13-34 records worker_threads opt-in diagnostics as partial; default denial, profile diagnostics, and native preflight are covered while native runnable evidence, crash/resource stress, packaged worker smoke, and final Desktop worker policy remain open. |
| `zlib` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless P13-43 promotes broader desktop conformance. |
| `zlib/promises` | `disabled` | `desktop_compat_opt_in` | `disabled` | Keep stable denial unless a future Phase 13 task assigns an opt-in profile. |

## Checks

| Check | Result | Message |
| --- | --- | --- |
| `builtins-covered` | passed | Desktop reference builtins must all appear in the generated parity matrix. |
| `disabled-builtins-visible` | passed | High-risk disabled builtins must stay visible in the matrix. |
| `network-builtins-gated` | passed | Network-shaped builtins must be marked limited_network_gated until P13 network promotion tasks land. |
| `docs-mention-builtins` | passed | Compatibility profile must keep the existing builtin policy documentation. |
| `task-checklist-current` | passed | Phase 13 task checklist must record P13-28 evidence. |
| `markdown-current` | passed | Generated markdown must expose status vocabulary and desktop gap state. |
