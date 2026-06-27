# Node Builtin Desktop Parity Matrix

This matrix is generated from `NodeRuntimeCapabilities.kt` and the Phase 13 desktop parity task plan. It tracks current Safe Node builtin behavior against broader desktop Node.js compatibility goals.

## Status Vocabulary

- `partial`: available as a Safe Node subset or wrapper.
- `limited_network_gated`: present for controlled feature detection or facade behavior, but raw network I/O remains gated.
- `experimental`: available only behind a release-forbidden build-time/debug opt-in.
- `disabled`: registry-disabled with stable denial.
- `known_not_wrapped`: known to the CommonJS registry but not currently surfaced as a limited wrapper.
- `missing_from_registry`: in the desktop reference list but not explicitly classified by the current registry.

## Summary

- `disabled`: 8
- `experimental`: 1
- `limited_network_gated`: 6
- `missing_from_registry`: 2
- `partial`: 35
- `partial_native`: 1

## Matrix

| Builtin | Status | Target profile | Source | Next step |
| --- | --- | --- | --- | --- |
| `assert` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless a future Phase 13 task promotes broader desktop conformance. |
| `assert/strict` | `partial` | `safe_default` | `limited` | Keep current Safe subset unless a future Phase 13 task promotes broader desktop conformance. |
| `async_hooks` | `partial` | `safe_default` | `limited`, `commonjs-known` | P13-43 records stdlib conformance as partial; representative Safe subset coverage, Android difference notes, builtin precedence, and npm/modern-core evidence are present while full desktop parity remains future work. |
| `buffer` | `partial` | `safe_default` | `limited`, `commonjs-known` | P13-43 records stdlib conformance as partial; representative Safe subset coverage, Android difference notes, builtin precedence, and npm/modern-core evidence are present while full desktop parity remains future work. |
| `child_process` | `experimental` | `desktop_compat_opt_in` | `commonjs-known`, `native opt-in` | P13-40 records child_process scoped-runner diagnostics as partial; Safe/default keeps child_process denied, while debug builds can enable Node's native builtin with `autojs.nodejs.child_process.experimental=true`. App-private allowlisted binaries, bounded stdio, timeout/cancel/kill cleanup, packaged evidence, and hostile corpus coverage remain future work before release/profile promotion. |
| `cluster` | `disabled` | `permanently_unsupported` | `commonjs-known`, `disabled` | Keep stable denial unless a future Phase 13 task assigns an opt-in profile. |
| `console` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless a future Phase 13 task promotes broader desktop conformance. |
| `constants` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless a future Phase 13 task promotes broader desktop conformance. |
| `crypto` | `partial` | `safe_default` | `limited`, `commonjs-known` | P13-43 records stdlib conformance as partial; representative Safe subset coverage, Android difference notes, builtin precedence, and npm/modern-core evidence are present while full desktop parity remains future work. |
| `dgram` | `disabled` | `desktop_compat_opt_in` | `disabled` | P13-31 records UDP/dgram as stable-denied for v1.1; unicast client, server bind/listen, multicast, broadcast, packaged INTERNET policy, and fd leak proof remain future work. |
| `diagnostics_channel` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless a future Phase 13 task promotes broader desktop conformance. |
| `dns` | `limited_network_gated` | `desktop_compat_opt_in` | `limited`, `commonjs-known`, `network` | P13-30 records staged limited facades as partial and a release-forbidden raw network module opt-in; release/default client sockets, server sockets, network DNS policy, TLS sessions, fd leak proof, and packaged live-network evidence remain open. |
| `dns/promises` | `limited_network_gated` | `desktop_compat_opt_in` | `limited`, `network` | P13-30 records staged limited facades as partial and a release-forbidden raw network module opt-in; release/default client sockets, server sockets, network DNS policy, TLS sessions, fd leak proof, and packaged live-network evidence remain open. |
| `domain` | `disabled` | `permanently_unsupported` | `commonjs-known`, `disabled` | Keep stable denial unless a future Phase 13 task assigns an opt-in profile. |
| `events` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless a future Phase 13 task promotes broader desktop conformance. |
| `fs` | `partial` | `safe_default` | `limited`, `commonjs-known` | P13-32 records scoped filesystem profile and P13-33 records advanced fs APIs as partial; Safe profile stays working-directory scoped while Android watcher coalescing, recursive-watch denial, and realpath differences are documented; expanded roots, packaged watcher/fd/FileHandle close-on-destroy, and full desktop fs semantics remain future work. |
| `fs/promises` | `partial` | `safe_default` | `limited`, `commonjs-known` | P13-32 records scoped filesystem profile and P13-33 records advanced fs APIs as partial; Safe profile stays working-directory scoped while Android watcher coalescing, recursive-watch denial, and realpath differences are documented; expanded roots, packaged watcher/fd/FileHandle close-on-destroy, and full desktop fs semantics remain future work. |
| `http` | `limited_network_gated` | `desktop_compat_opt_in` | `limited`, `commonjs-known`, `network` | P13-29 records the controlled client subset as ready; raw server/listen, proxy/tunnel, custom TLS/session control, and broader packaged install/run network evidence remain future work. |
| `https` | `limited_network_gated` | `desktop_compat_opt_in` | `limited`, `commonjs-known`, `network` | P13-29 records the controlled client subset as ready; raw server/listen, proxy/tunnel, custom TLS/session control, and broader packaged install/run network evidence remain future work. |
| `inspector` | `disabled` | `debug_unsafe_lab` | `commonjs-known`, `disabled` | P13-37 records inspector debug-profile diagnostics as partial; Safe/default and release keep inspector denied while explicit debug-session UI, loopback ADB forwarding, DevTools attach, profiler artifact redaction, and packaged behavior evidence remain future work. |
| `module` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless a future Phase 13 task promotes broader desktop conformance. |
| `net` | `limited_network_gated` | `desktop_compat_opt_in` | `limited`, `commonjs-known`, `network` | P13-30 records staged limited facades as partial and a release-forbidden raw network module opt-in; release/default client sockets, server sockets, network DNS policy, TLS sessions, fd leak proof, and packaged live-network evidence remain open. |
| `node:test` | `missing_from_registry` | `desktop_compat_opt_in` | - | Add explicit registry entry or document android_not_applicable. |
| `node:test/reporters` | `missing_from_registry` | `desktop_compat_opt_in` | - | Add explicit registry entry or document android_not_applicable. |
| `os` | `partial` | `safe_default` | `limited`, `commonjs-known` | P13-43 records stdlib conformance as partial; representative Safe subset coverage, Android difference notes, builtin precedence, and npm/modern-core evidence are present while full desktop parity remains future work. |
| `path` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless a future Phase 13 task promotes broader desktop conformance. |
| `path/posix` | `partial` | `safe_default` | `limited` | Keep current Safe subset unless a future Phase 13 task promotes broader desktop conformance. |
| `path/win32` | `partial` | `safe_default` | `limited` | Keep current Safe subset unless a future Phase 13 task promotes broader desktop conformance. |
| `perf_hooks` | `partial` | `safe_default` | `limited`, `commonjs-known` | P13-43 records stdlib conformance as partial; representative Safe subset coverage, Android difference notes, builtin precedence, and npm/modern-core evidence are present while full desktop parity remains future work. |
| `process` | `partial` | `safe_default` | `limited`, `commonjs-known` | P13-44 records process/env/argv parity as partial; argv/env/cwd/timing/resource/report/permission and lifecycle-mapped process.exit(code) evidence is present with stable denials for native process exit, signals, native bindings, raw environment inheritance, and full desktop process parity while packaged lifecycle evidence remains future work. |
| `punycode` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless a future Phase 13 task promotes broader desktop conformance. |
| `querystring` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless a future Phase 13 task promotes broader desktop conformance. |
| `readline` | `partial` | `safe_default` | `limited`, `commonjs-known` | P13-43 records stdlib conformance as partial; representative Safe subset coverage, Android difference notes, builtin precedence, and npm/modern-core evidence are present while full desktop parity remains future work. |
| `readline/promises` | `partial` | `safe_default` | `limited` | P13-43 records stdlib conformance as partial; representative Safe subset coverage, Android difference notes, builtin precedence, and npm/modern-core evidence are present while full desktop parity remains future work. |
| `repl` | `disabled` | `permanently_unsupported` | `commonjs-known`, `disabled` | Keep stable denial unless a future Phase 13 task assigns an opt-in profile. |
| `stream` | `partial` | `safe_default` | `limited`, `commonjs-known` | P13-43 records stdlib conformance as partial; representative Safe subset coverage, Android difference notes, builtin precedence, and npm/modern-core evidence are present while full desktop parity remains future work. |
| `stream/consumers` | `partial` | `safe_default` | `limited` | P13-43 records stdlib conformance as partial; representative Safe subset coverage, Android difference notes, builtin precedence, and npm/modern-core evidence are present while full desktop parity remains future work. |
| `stream/promises` | `partial` | `safe_default` | `limited` | P13-43 records stdlib conformance as partial; representative Safe subset coverage, Android difference notes, builtin precedence, and npm/modern-core evidence are present while full desktop parity remains future work. |
| `stream/web` | `partial` | `safe_default` | `limited` | P13-43 records stdlib conformance as partial; representative Safe subset coverage, Android difference notes, builtin precedence, and npm/modern-core evidence are present while full desktop parity remains future work. |
| `string_decoder` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless a future Phase 13 task promotes broader desktop conformance. |
| `timers` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless a future Phase 13 task promotes broader desktop conformance. |
| `timers/promises` | `partial` | `safe_default` | `limited` | Keep current Safe subset unless a future Phase 13 task promotes broader desktop conformance. |
| `tls` | `limited_network_gated` | `desktop_compat_opt_in` | `limited`, `commonjs-known`, `network` | P13-30 records staged limited facades as partial and a release-forbidden raw network module opt-in; release/default client sockets, server sockets, network DNS policy, TLS sessions, fd leak proof, and packaged live-network evidence remain open. |
| `tty` | `partial` | `safe_default` | `limited`, `commonjs-known` | P13-43 records stdlib conformance as partial; representative Safe subset coverage, Android difference notes, builtin precedence, and npm/modern-core evidence are present while full desktop parity remains future work. |
| `url` | `partial` | `safe_default` | `limited`, `commonjs-known` | P13-43 records stdlib conformance as partial; representative Safe subset coverage, Android difference notes, builtin precedence, and npm/modern-core evidence are present while full desktop parity remains future work. |
| `util` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless a future Phase 13 task promotes broader desktop conformance. |
| `util/types` | `partial` | `safe_default` | `limited` | Keep current Safe subset unless a future Phase 13 task promotes broader desktop conformance. |
| `v8` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless a future Phase 13 task promotes broader desktop conformance. |
| `vm` | `partial_native` | `desktop_compat_opt_in` | `limited`, `commonjs-known` | P13-36 promotes vm/node:vm to the native Node builtin for desktop npm compatibility; keep profile diagnostics, shadow-package protection, and follow-up evidence for memory, timeout, and packaged behavior. |
| `wasi` | `disabled` | `desktop_compat_opt_in` | `commonjs-known`, `disabled` | P13-38 records controlled WASI diagnostics as partial; pure WebAssembly is the supported v1.1 runtime path while raw and controlled WASI APIs stay stable-denied, with controlled preopens, fd/env/args, resource budgets, worker integration, and packaged behavior remaining future work. |
| `worker_threads` | `disabled` | `desktop_compat_opt_in` | `commonjs-known`, `disabled` | P13-34 records worker_threads opt-in diagnostics as partial; default denial, profile diagnostics, and native preflight are covered while native runnable evidence, crash/resource stress, packaged worker smoke, and final Desktop worker policy remain open. |
| `zlib` | `partial` | `safe_default` | `limited`, `commonjs-known` | P13-43 records stdlib conformance as partial; representative Safe subset coverage, Android difference notes, builtin precedence, and npm/modern-core evidence are present while full desktop parity remains future work. |
| `zlib/promises` | `disabled` | `desktop_compat_opt_in` | `disabled` | P13-43 records zlib/promises as a stable disabled gap while callback, sync, and stream zlib subsets stay covered. |

## Checks

| Check | Result | Message |
| --- | --- | --- |
| `builtins-covered` | passed | Desktop reference builtins must all appear in the generated parity matrix. |
| `disabled-builtins-visible` | passed | High-risk disabled builtins must stay visible in the matrix. |
| `experimental-builtins-visible` | passed | Release-forbidden experimental builtins must stay visible with their opt-in property. |
| `network-builtins-gated` | passed | Network-shaped builtins must be marked limited_network_gated until P13 network promotion tasks land. |
| `docs-mention-builtins` | passed | Compatibility profile must keep the existing builtin policy documentation. |
| `task-checklist-current` | passed | Phase 13 task checklist must record P13-28 evidence. |
| `markdown-current` | passed | Generated markdown must expose status vocabulary and desktop gap state. |
