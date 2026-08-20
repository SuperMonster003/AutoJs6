# Node Builtin Desktop Parity Matrix

This matrix is generated from `NodeRuntimeCapabilities.kt` and the Phase 13 desktop parity task plan. It tracks current Safe Node builtin behavior against broader desktop Node.js compatibility goals.

## Status Vocabulary

- `stable_native`: available by default from the embedded Node runtime.
- `partial`: available as a Safe Node subset or wrapper.
- `limited_network_gated`: present for controlled feature detection or facade behavior, but raw network I/O remains gated.
- `disabled`: registry-disabled with stable denial.
- `known_not_wrapped`: known to the CommonJS registry but not currently surfaced as a limited wrapper.
- `missing_from_registry`: in the desktop reference list but not explicitly classified by the current registry.

## Summary

- `disabled`: 7
- `missing_from_registry`: 2
- `partial`: 35
- `partial_native`: 1
- `stable_native`: 8

## Matrix

| Builtin | Status | Target profile | Source | Next step |
| --- | --- | --- | --- | --- |
| `assert` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless a future Phase 13 task promotes broader desktop conformance. |
| `assert/strict` | `partial` | `safe_default` | `limited` | Keep current Safe subset unless a future Phase 13 task promotes broader desktop conformance. |
| `async_hooks` | `partial` | `safe_default` | `limited`, `commonjs-known` | P13-43 records stdlib conformance as partial; representative Safe subset coverage, Android difference notes, builtin precedence, and npm/modern-core evidence are present while full desktop parity remains future work. |
| `buffer` | `partial` | `safe_default` | `limited`, `commonjs-known` | P13-43 records stdlib conformance as partial; representative Safe subset coverage, Android difference notes, builtin precedence, and npm/modern-core evidence are present while full desktop parity remains future work. |
| `child_process` | `stable_native` | `safe_default` | `native-stable`, `commonjs-known` | Stable native Node builtin. It follows Android app-sandbox and caller validation semantics; it does not inherit the separate `shell` bridge allowlist or resource budget. |
| `cluster` | `disabled` | `permanently_unsupported` | `commonjs-known`, `disabled` | Keep stable denial unless a future Phase 13 task assigns an opt-in profile. |
| `console` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless a future Phase 13 task promotes broader desktop conformance. |
| `constants` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless a future Phase 13 task promotes broader desktop conformance. |
| `crypto` | `partial` | `safe_default` | `limited`, `commonjs-known` | P13-43 records stdlib conformance as partial; representative Safe subset coverage, Android difference notes, builtin precedence, and npm/modern-core evidence are present while full desktop parity remains future work. |
| `dgram` | `disabled` | `desktop_compat_opt_in` | `disabled` | P13-31 records UDP/dgram as stable-denied for v1.1; unicast client, server bind/listen, multicast, broadcast, packaged INTERNET policy, and fd leak proof remain future work. |
| `diagnostics_channel` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless a future Phase 13 task promotes broader desktop conformance. |
| `dns` | `stable_native` | `safe_default` | `native-stable`, `limited`, `commonjs-known`, `network` | Stable native Node network builtin. Android sandbox/manifest policy and caller resource handling remain authoritative; controlled bridge budgets do not wrap raw native I/O. |
| `dns/promises` | `stable_native` | `safe_default` | `native-stable`, `limited`, `network` | Stable native Node network builtin. Android sandbox/manifest policy and caller resource handling remain authoritative; controlled bridge budgets do not wrap raw native I/O. |
| `domain` | `disabled` | `permanently_unsupported` | `commonjs-known`, `disabled` | Keep stable denial unless a future Phase 13 task assigns an opt-in profile. |
| `events` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless a future Phase 13 task promotes broader desktop conformance. |
| `fs` | `partial` | `safe_default` | `limited`, `commonjs-known` | P13-32 records scoped filesystem profile and P13-33 records advanced fs APIs as partial; Safe profile stays working-directory scoped while Android watcher coalescing, recursive-watch denial, and realpath differences are documented; expanded roots, packaged watcher/fd/FileHandle close-on-destroy, and full desktop fs semantics remain future work. |
| `fs/promises` | `partial` | `safe_default` | `limited`, `commonjs-known` | P13-32 records scoped filesystem profile and P13-33 records advanced fs APIs as partial; Safe profile stays working-directory scoped while Android watcher coalescing, recursive-watch denial, and realpath differences are documented; expanded roots, packaged watcher/fd/FileHandle close-on-destroy, and full desktop fs semantics remain future work. |
| `http` | `stable_native` | `safe_default` | `native-stable`, `limited`, `commonjs-known`, `network` | Stable native Node network builtin. Android sandbox/manifest policy and caller resource handling remain authoritative; controlled bridge budgets do not wrap raw native I/O. |
| `https` | `stable_native` | `safe_default` | `native-stable`, `limited`, `commonjs-known`, `network` | Stable native Node network builtin. Android sandbox/manifest policy and caller resource handling remain authoritative; controlled bridge budgets do not wrap raw native I/O. |
| `inspector` | `disabled` | `debug_unsafe_lab` | `commonjs-known`, `disabled` | P13-37 records inspector debug-profile diagnostics as partial; Safe/default and release keep inspector denied while explicit debug-session UI, loopback ADB forwarding, DevTools attach, profiler artifact redaction, and packaged behavior evidence remain future work. |
| `module` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless a future Phase 13 task promotes broader desktop conformance. |
| `net` | `stable_native` | `safe_default` | `native-stable`, `limited`, `commonjs-known`, `network` | Stable native Node network builtin. Android sandbox/manifest policy and caller resource handling remain authoritative; controlled bridge budgets do not wrap raw native I/O. |
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
| `tls` | `stable_native` | `safe_default` | `native-stable`, `limited`, `commonjs-known`, `network` | Stable native Node network builtin. Android sandbox/manifest policy and caller resource handling remain authoritative; controlled bridge budgets do not wrap raw native I/O. |
| `tty` | `partial` | `safe_default` | `limited`, `commonjs-known` | P13-43 records stdlib conformance as partial; representative Safe subset coverage, Android difference notes, builtin precedence, and npm/modern-core evidence are present while full desktop parity remains future work. |
| `url` | `partial` | `safe_default` | `limited`, `commonjs-known` | P13-43 records stdlib conformance as partial; representative Safe subset coverage, Android difference notes, builtin precedence, and npm/modern-core evidence are present while full desktop parity remains future work. |
| `util` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless a future Phase 13 task promotes broader desktop conformance. |
| `util/types` | `partial` | `safe_default` | `limited` | Keep current Safe subset unless a future Phase 13 task promotes broader desktop conformance. |
| `v8` | `partial` | `safe_default` | `limited`, `commonjs-known` | Keep current Safe subset unless a future Phase 13 task promotes broader desktop conformance. |
| `vm` | `partial_native` | `desktop_compat_opt_in` | `limited`, `commonjs-known` | P13-36 promotes vm/node:vm to the native Node builtin for desktop npm compatibility; profile diagnostics, shadow-package protection, repeated-script budget, timeout, boundary-probe, and packaged fixture evidence are ready while VM remains a non-sandbox compatibility surface. |
| `wasi` | `disabled` | `desktop_compat_opt_in` | `commonjs-known`, `disabled` | P13-38 records controlled WASI diagnostics as partial; pure WebAssembly is the supported v1.1 runtime path while raw and controlled WASI APIs stay stable-denied, with controlled preopens, fd/env/args, resource budgets, worker integration, and packaged behavior remaining future work. |
| `worker_threads` | `stable_native` | `safe_default` | `native-stable`, `commonjs-known` | Stable native Node builtin with default-on runtime support; callers remain responsible for worker lifecycle and resource limits. |
| `zlib` | `partial` | `safe_default` | `limited`, `commonjs-known` | P13-43 records stdlib conformance as partial; representative Safe subset coverage, Android difference notes, builtin precedence, and npm/modern-core evidence are present while full desktop parity remains future work. |
| `zlib/promises` | `disabled` | `desktop_compat_opt_in` | `disabled` | P13-43 records zlib/promises as a stable disabled gap while callback, sync, and stream zlib subsets stay covered. |

## Checks

| Check | Result | Message |
| --- | --- | --- |
| `builtins-covered` | passed | Desktop reference builtins must all appear in the generated parity matrix. |
| `disabled-builtins-visible` | passed | High-risk disabled builtins must stay visible in the matrix. |
| `stable-native-builtins-visible` | passed | Default-on native builtins must stay visible as stable_native. |
| `network-builtins-stable` | passed | Default-on raw network builtins must be marked stable_native. |
| `docs-mention-builtins` | passed | Compatibility profile must document current stable and disabled builtin policy. |
| `task-checklist-current` | passed | Phase 13 task checklist must record P13-28 evidence. |
| `markdown-current` | passed | Generated markdown must expose status vocabulary and desktop gap state. |
