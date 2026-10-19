# Shell process declaration and completion audit closure

Date: 2026-10-19. This delivery closes the five declaration/Ace gaps identified by the preceding sensor audit. Scope is the existing Rhino shell/shizuku API, its process return objects, constructor overloads, documentation and completion behavior. Host runtime code was not changed.

## Closed gaps

- shell.checkAccess, shell.isRootAvailable, shell.setDefaultOptions and shell.spawn now have explicit declarations and semantic/static completion entries. shizuku.spawn has the same process return contract.
- Shell.Process models all 25 native script-visible properties/methods, including readonly identity/status, nullable line reads and exit status, blocking/timed wait overloads, streams, chained stdin methods, termination and listener management. output/errorOutput callbacks receive string; exit receives number. Unknown events and unsupported emit are rejected by the declarations.
- Shell.SpawnOptions, Shell.Options and Shell.DefaultOptions describe the distinct option sets. Numeric root flags are valid for the legacy shell call but rejected by spawn; cmd belongs to the constructor. The old no-argument, boolean and Android Context constructors remain accepted alongside string/options constructors.
- new Shell(options) exposes its pipe methods. exitCode() preserves the Java Integer wrapper type; Shell.Process.exitCode remains a plain number or null.
- Ace's static source/index and fallback declarations are regenerated from the canonical declarations. The new returned-object probes prevent name-only coverage from hiding untyped process results.

## Additional default-editor issues fixed during validation

The first full Ace device run exposed a missing constructor-options popup: without optional Android declaration groups, an unresolved Context overload degraded contextual completion to global symbols. A typed Shell.AndroidContext interface preserves the native Context overload and its identity method in the core group. Constructor options are now checked in both default/all-group desktop modes and the actual device popup path.

The same review found that native AbstractShell.Result was absent from Ace's default return-type bridges. The bridge generator now extracts its interface from the native declarations, so shell(...), shizuku(...) and new Shell(...).execForResult(...) expose code/result/error with number/string types under default settings. This uses the native source contract rather than a separate hand-maintained result model.

## Accepted validation

- Full coverage gate: 91 registration routes, 1356 explicit exports and 1589 declaration/completion queries, zero failures. Internal/standard exceptions and the published extraction scope remain explicit. This is a documentation/type/completion gate, not a measurement of direct Node/Bun runtime parity.
- Added native-contract check: all 25 process members, three event names, six constructor keys, five spawn keys, two default keys and exact canonical/Ace file copies. The main coverage command invokes this check.
- TypeScript 5.1.3 and 6.0.3 each passed four smoke programs, including positive/negative shell contracts, and semantic checks of all 75 internal declaration bodies. Tests protect nullable outputs, event payloads, readonly fields, unsupported names/options and retained constructors.
- Ace desktop checks: 268 core/JSOX/completion queries and 428 runtime queries passed, as did static index/model/fallback freshness and real completer tests. Ace's 187 JVM cases and lint passed.
- Redmi bek749scrwv4wo8h, Android 13: 25 real shell checks passed. Xiaomi 968e9f18, Android 15: the same 25 passed. Tests cover pipe and PTY output, stdin, environment/cwd, exit codes/timeouts, a 20000-line event stream, cancellation, descendant cleanup, script keepalive and script-exit cleanup. Each phase uses an isolated owned script path, which is removed after hash verification.
- Xiaomi Ace device regression: 9 accepted JUnit cases passed on the final candidate, including shell options, callback types, readonly/nullable return hints, default result chains, existing runtime completion and JSOX. Preferences and rotation were restored. Earlier failed/superseded runs are retained separately and are not counted as accepted passes.
- Both devices reported no available Root/Shizuku execution grant. The explicit Shizuku-unavailable path was tested. Successful su/Shizuku execution is not claimed by this delivery.
- Coverage extractor: 8 tests. Declaration documentation: 13 Python tests. Documentation normalization/generation checked all 164 modules. Offline Docs debug/release APKs verified all 220 documentation files; two JVM cases passed.

No acoustic test tone, playback, microphone/camera recording or touch-triggered getevent test was used. No new host implementation or packaged-runtime change was made, so this delivery used the existing tested host build 5581 and did not rebuild the APK Builder or generate new packaged applications.

## Versions, sources and evidence

Declarations 4.64.1; Ace 2.26.1 / build 302; Offline Docs 6.8.22 / build 177; Documentation revision 143. Documentation commit `7b79ee8fe6a92569ccc1f5b8723678867cd3cd40` is pinned in both Offline Docs provenance files. Node, Bun and APK Builder repositories were unchanged. No remote publication or push was performed.

Ignored `.bak/shell-api-coverage-20261019/` retains pre-task repository snapshots, compiler/build output, final coverage JSON, per-device script/install receipts, actual APKs and popup evidence. Device restoration records verify original APK hashes, grants, volume, rotation and accessibility settings. Temporary original APK copies are removed only after successful restoration verification. The five original host draft files are byte-identical to their pre-task snapshots and remain uncommitted.

## Remaining runtime audit

Bun still has no explicit host shell capability. Node's shell facade has its own asynchronous execution/access contract; its streaming/Shizuku and Worker coverage remains separate work. These Rhino declaration fixes do not implement those runtime bridges. The larger Android-object, scheduling and dedicated plugin surfaces listed in the overall runtime audit also remain separate implementation work.

## Reproduce

Run `python tools/api-coverage/check.py`, `node tools/api-coverage/check-shell-contract.mjs`, `node tools/ace-completion/generate-editor-assets.mjs --check` and `node tools/ace-completion/test-completer.mjs`. Run the declarations repository's tools/verify-api-coverage.mjs with each compiler and Ace's verify-api-coverage / verify-runtime-api-coverage Gradle tasks. Device scripts: tools/shell-stream/device_shell_stream_test.py with isolated fixtures; UI class: AceApiCoverageDeviceTest.
