# Bun file and persistent storage delivery - 2026-10-19

The Node-compatible Bun file and storage interfaces, documentation, declarations and Ace completion are implemented and verified. Both physical devices passed host file/storage comparison, cross-runtime persistence and genuine packaged-app restart checks. Final accepted physical coverage is 42 JUnit executions; preliminary successful host reruns are retained separately and are not added to this total. No acoustic tone or media playback was used.

## Delivered behavior

- `host.files` covers all 15 usable names in Node's public files facade. `join`, `getName` and `getExtension` remain synchronous POSIX helpers. The other 12 methods return Promises and access the executing host application's paths. `files.control` is a separate default-off grant. `open` and `toFile` explicitly reject unsupported Rhino/Java handles.
- Text conversion and encoding use the runtime Buffer implementation, including utf8, utf16le, latin1, ascii, hex, base64 and base64url. Node's nullish defaults, strict-true directory filters, parent-directory creation, missing-file booleans and nonrecursive removal are retained. File URLs use Android POSIX semantics independently of the desktop test OS.
- Relative host paths use the script's host working directory, with the executing application's filesDir as the fallback. The file bridge does not use the Bun plugin's temporary workspace. Android continues to decide access to absolute paths. Local Bun/node:fs calls still run under the plugin UID and have their original workspace lifecycle.
- `host.storage` and `host.storages` reproduce the actual Node public facade and declarations through `tools/host-client/sync-storage.mjs`. They use the same six native get/put/remove/clear/keys/contains routes and package/working-directory backend. `storage.control` is independently default-off. Node/Bun scripts sharing the same host identity and working directory can exchange stored JSON across new executions.
- `create`/`open` return a local store object; storage I/O is asynchronous, including historical `putSync`, `removeSync`, `clearSync`, `selfRemoveSync` aliases. Missing values preserve the caller's default or undefined; stored null/false/zero remain distinct. `names` and `all` retain explicit unsupported results.
- Both grants are exposed in Plugin Center and accepted in packaged `bun.hostCapabilities`, with all ten languages plus explicit English. Grants do not expose generic Java or internal Agent routes.

## Candidate versions and source ownership

| Component | Candidate |
| --- | --- |
| AutoJs6 | 6.8.0, Android build 5579 |
| Bun Runtime | 0.4.0, build 263 |
| Node.js Runtime | 1.15.0, unchanged |
| TypeScript declarations | 4.62.0 |
| Ace | 2.24.0, build 299 |
| Documentation | 6.8.0, revision 140 |
| Offline Docs | 6.8.19, build 171 |
| APK Builder | 1.2.9, build 84, paired host 5579 |

Documentation source is committed as `65951ff9e23bdf4de2f07da65bb3dd2225c1b650` and both Offline Docs provenance files pin it. The Runtime Kit is `autojs6-runtime-v6.8.0+5579-local-arm64-v8a`. Its `local` source label is a local development label, not a published source-commit attestation. Exact installed APK hashes, the SDK used by APK production and the template DEX bytes bind the tests to their local artifacts. No push, publication, registry upload or catalog deployment was performed. Node source/native payloads and Bun native payloads were not changed.

Concurrent committed Shell and localization work remains in the current host/documentation bases. The initial unrelated Simplified Chinese source/draft hunks remain outside this task's host commit. Documentation synchronization uses the current canonical source, including the separately committed Shell documentation; it does not revert that work.

## Physical verification

| Device | Environment | Accepted JUnit executions |
| --- | --- | --- |
| 22120RN86C (bek749scrwv4wo8h) | Android API 33, arm64-v8a, 4096-byte pages | Host 3, Bun 13, APK production 1, packaged persistence 1 |
| 23046RP50C (968e9f18) | Android API 35, arm64-v8a, 4096-byte pages | Host 3, Bun 13, APK production 1, packaged persistence 1, Ace 6 |

The shared Node/Bun file program checks all 15 usable functions, seven encoding round trips, Unicode, file URLs, copy/move behavior, strict directory filters and explicit failures. The host independently checks the Bun-written file bytes. Storage tests create fresh Node and Bun executions in both directions, check JSON values/defaults and aliases, and verify different working directories do not see the same stored key. Separate tests reject missing grants and malformed/raw operations, and confirm an oversized write leaves the original destination unchanged.

The two APK Builder outputs run with their own package identity and explicitly declared file/storage/UI grants. Each app writes a file and a stored counter, is force-stopped, then is launched again. The file remains and the counter advances from 1 to 2. Proofs locate the file under the packaged application's own `/files/project/` directory. Both APK signatures verify and differ from the host certificate; every packaged DEX matches the actual runtime template.

Ace's six physical tests ran with the normal/default completion configuration, including new file/store member lists and actual `files.read` / `putSync` popups showing Promise return types. Runtime isolation and JSOX regression checks also passed, with preferences and rotation restored. Each phone also passed all 13 Bun runtime instrumentation tests, including execution, cancellation, workspace and bridge regressions.

## Desktop/build verification

- 40 Bun SDK tests passed. The new file/storage test file is included in CI.
- `check-bun-storage.mjs` matches all 15 usable Node file functions, 12 host file routes, six shared storage routes and generated declarations. Existing runtime bridge and automation checks passed.
- TypeScript 5.1.3 and 6.0.3 strict positive/negative checks passed. All 70 canonical runtime declaration assets were synchronized into the declaration package and Ace.
- Ace passed 409 semantic queries, including both storage aliases and resolved store members.
- Bun's 38 Python tests, Markdown checks and unchanged compatibility-matrix check passed.
- Documentation normalization and generation checks passed for 164 modules. Both Offline Docs APK inventories passed with the current source provenance.
- Host debug/androidTest and Runtime Kit, Bun debug/androidTest and native-payload integrity, Ace debug/androidTest, and the paired APK Builder assembled successfully.

| JVM suite | Discovered | Skipped |
| --- | ---: | ---: |
| AutoJs6 | 4724 | 26 |
| AutoJs6-Plugin-Bun-Runtime | 59 | 0 |
| AutoJs6-Plugin-Ace-Editor | 187 | 0 |
| AutoJs6-Plugin-APK-Builder-Template | 40 | 0 |
| AutoJs6-Plugin-Offline-Docs | 2 | 0 |

Total: 5012 discovered, 4986 passed, 26 skipped, zero failures/errors. An initial desktop file-URL check exposed Windows-specific URL handling; the client now explicitly uses Android POSIX URL rules. The native file core accepts plain arguments so its actual filesystem behavior is testable without Android JSON stubs; malformed wire requests are additionally checked on the devices.

## Boundaries and cleanup

This is coverage of the public Node file/storage families, not complete Rhino semantics or all remaining Bun APIs. File I/O in Bun is asynchronous and uses the host UID. The existing 64 KiB request/256 KiB result budgets include base64 and JSON overhead: single reads admit up to 196512 raw bytes and single writes less than 48 KiB, depending on the path/envelope. Oversized calls reject rather than truncating; use native runtime file APIs for larger files accessible to the plugin. No project archive writeback was added.

Storage retains Node's existing name normalization (lowercase letters, unsupported characters replaced with underscores, first 64 normalized characters). Distinct original names can therefore share a namespace; the API guide documents this inherited behavior. The backend's 64 KiB value/256 KiB store limits and Bun's tighter request-envelope budget remain. Abort/timeout stops waiting and cannot undo already-committed I/O. Remaining database, sensor, scheduling and specialized module gaps still require separate implementation work.

Original APKs and device settings are restored from fresh per-device snapshots and verified by hashes/settings readback. Task-only installed packages and owned external proof/APK/SDK files are removed or restored, while local evidence remains under ignored `.bak/bun-storage-20261019/`. Storage fixtures clear their test values; the packaged application and its private data are removed. Original APK backup copies are deleted only after restoration succeeds. The pre-existing isolated checkout and its signing/local files are untouched. No test requires sound.
