# Bun and Node Worker database delivery - 2026-10-19

The public database/sqlite facade is available in Bun and Node.js CJS/ESM/nested Workers, with shared implementation, declarations, documentation and Ace completion. Final accepted physical coverage is **127 JUnit executions** on Redmi and Xiaomi. Four genuine APK Builder outputs (Bun and Node Worker on each device) passed database checks and two launches separated by force-stop. No acoustic tone or actual media playback was used.

## Delivered API and corrected behavior

- Bun exposes `host.database.open` and callable `host.sqlite` / `host.sqlite.open`, with `database.control` as an independent default-off Plugin Center grant and packaged `bun.hostCapabilities` entry. All supported locales are synchronized.
- Node main threads, Workers and the Bun SDK use the same self-contained public factory. Worker imports support `database`, `sqlite`, `autojs6:database` and `autojs6:sqlite`; native `node:sqlite` remains the native Node module. The host facade returns Promise-based open/exec/run/get/all/transaction/close operations and data snapshots.
- Object open options now preserve timeoutMs/AbortSignal and reject a missing name. The pre-change desktop reproduction recorded timeoutMs=10000 instead of 37, an unforwarded signal, and an empty object becoming the database name `undefined`. The new facade tests verify the corrected cases before transport.
- Native query binding now preserves SQL NULL, integer, real and boolean parameter types instead of converting query parameters to strings. BLOB results retain the existing base64 object shape. Multirow INSERT/REPLACE returns the actual change count, ignored inserts return 0/null, and leading SQL comments do not defeat command classification.
- Database cleanup runs on its SQLite executor thread, rolling back unfinished manual transactions before closing. Worker exit closes handles opened through that Worker, including late open responses. Cancelled, timed-out or otherwise unclaimed Bun opens release their eventual native handles. database.close no longer closes another resource type just because its ID is supplied.
- Named database persistence and package/host-working-directory isolation reuse the Node backend. Main Node and Bun share data in both directions under the same host scope; packaged apps use their own identities and data.

The internal transaction used to keep INSERT metrics on one SQLite connection initially changed `OR FAIL` behavior. A new physical regression reproduced the lost successful prefix row. The final implementation preserves SQLite's conflict policy while retaining accurate metrics: `OR FAIL` keeps earlier successful rows, `OR ROLLBACK` removes the current statement's rows in the autocommit case, and explicit operation-array transactions remain atomic. This matches the [SQLite conflict-resolution contract](https://www.sqlite.org/lang_conflict.html). Both phones and all four final packaged outputs passed these added checks. The failed candidate and logs are archived separately.

## Candidate versions

| Component | Candidate |
| --- | --- |
| AutoJs6 | 6.8.0, Android 5580 |
| Node.js Runtime | 1.16.0, build 264 |
| Bun Runtime | 0.5.0, build 264 |
| TypeScript declarations | 4.63.0 |
| Ace | 2.25.0, build 300 |
| Documentation | 6.8.0, revision 141 |
| Offline Docs | 6.8.20, build 173 |
| APK Builder | 1.2.10, build 85, paired host 5580 |

Runtime Kit: `autojs6-runtime-v6.8.0+5580-58ec97ab7875171009d26a8160778721a1cc5f60-arm64-v8a`. Its source basis is host commit `58ec97ab7875171009d26a8160778721a1cc5f60` plus this delivery's working changes; the basis hash is not claimed to be the final content commit. Tested APK hashes, per-phase install receipts and actual template DEX hashes identify the local candidates. Documentation source `09d3a488e03444d84bafcd51eb5ac970e83919bc` is pinned in both Offline Docs provenance files. Node's native bridge was rebuilt; Node/V8 versions and Bun's native payload bytes/support matrix were not changed. Nothing was pushed, published or uploaded to a registry/catalog.

## Physical verification

| Device | Environment | Final accepted JUnit executions |
| --- | --- | --- |
| 22120RN86C (bek749scrwv4wo8h) | API 33, arm64-v8a, 4096-byte pages | Database 6, legacy database 5, Node/npm 34, Bun 13, APK production 1, packaged restart 1 |
| 23046RP50C (968e9f18) | API 35, arm64-v8a, 4096-byte pages | Database 6, legacy database 5, Node/npm 34, Bun 13, APK production 1, packaged restart 1, Ace 7 |

The six database tests verify real typed SQL and Unicode, BLOB output, multirow/ignored inserts, both conflict policies, successful mixed transactions and rollback, Node/Bun cross-execution persistence, working-directory isolation, explicit grants/raw-method rejection, foreign resource IDs, the four-handle budget, unclaimed-open cleanup, and unfinished-transaction cleanup. CJS, ESM and nested Workers exercise the same database program and rollback their abandoned manual transaction on exit.

The existing five-case host database suite passed on both phones. Full Node Runtime Android conformance plus npm ecosystem tests passed 34/34 per phone. An initial run failed the old `require('sqlite')`-must-throw expectation in the Worker package-resolution fixture; it now positively checks both database facades and aliases in CJS/ESM while preserving the remaining unsupported-module and npm-resolution checks. Original failed logs remain separate from accepted runs. Bun's 13-case runtime class also passed on each phone.

APK production uses the actual paired Builder template and the exact SDK hash. Each device runs a Bun APK and a Node Worker APK, each twice with force-stop between launches. All eight launches check SQL behavior; each app's persistent counter advances from 1 to 2. Each proof records its own package identity and runtime build. All four signatures verify and differ from the host certificate; every packaged DEX matches the actual final Runtime Kit template. After the conflict-policy correction, the affected host/legacy-database and packaged checks were repeated; unchanged plugin and editor results were retained with their original receipts.

Ace's seven physical cases include default-setting database method popups, nullable/Promise results and object-option completion, together with runtime isolation and JSOX regression. Preferences and rotation are restored by the fixture. Media facade cases in Node conformance use fake transports; no tone file is played on the devices.

## Desktop and build verification

- Bun SDK: 44 tests passed. Node database facade: 4 tests passed. Real desktop Worker lifecycle: 2 tests passed, covering CJS/ESM/nested imports and termination before an open reply. Existing Worker module regression also passed. New tests are in the respective CI workflows.
- `check-bun-database.mjs` verifies the common factory, both aliases, declarations and all seven native methods. Existing runtime, storage and automation coverage checks passed.
- TypeScript 5.1.3 and 6.0.3 strict smoke checks passed; 70 canonical runtime declaration assets match in the declaration project and Ace. Node's root `typeCheckNodeTypescriptSmoke` task passed.
- Ace passed 413 semantic queries. Documentation normalization/current-generation checks passed for 164 modules; Offline Docs debug/release APK inventories passed with pinned provenance.
- Node's current NDK ARM64 compile command passed `-fsyntax-only`. Host debug/androidTest and Runtime Kit, Node/Bun debug/androidTest, Ace and paired Builder assembled. Bun native integrity/ZIP alignment gates passed. Node, Bun and Ace debug lint passed.
- Node Python tests: 2 passed. Bun Python tests: 38 passed. Generated Markdown and the unchanged Bun compatibility matrix passed their checks.

| JVM suite | Discovered | Skipped |
| --- | ---: | ---: |
| AutoJs6 | 4724 | 26 |
| AutoJs6-Plugin-NodeJs-Runtime | 96 | 0 |
| AutoJs6-Plugin-Bun-Runtime | 59 | 0 |
| AutoJs6-Plugin-Ace-Editor | 187 | 0 |
| AutoJs6-Plugin-APK-Builder-Template | 40 | 0 |
| AutoJs6-Plugin-Offline-Docs | 2 | 0 |

Total: 5108 discovered, 5082 passed, 26 skipped, zero failures/errors. These are local candidate checks, not a new release or native 16 KiB runtime acceptance.

## Limits, remaining scope and cleanup

The APIs expose the existing asynchronous host database contract: named databases rather than Java objects/cursors, JSON primitive bind parameters, up to four handles per execution, 16 KiB SQL, 128 parameters, 64 transaction operations, 200 all-result rows and 256 KiB result JSON. Bun's original 64 KiB request/256 KiB result envelopes, per-call/per-execution budgets and separate grant still apply. Abort/timeout cannot undo completed SQL. Database names retain the Node lowercase/.db mapping, so case and an omitted suffix do not create separate stores. Native Node/Bun SQLite APIs have separate contracts.

Sensor, scheduling, other Worker facade and specialized module gaps remain for later delivery. Prior historical reports are not rewritten as broader parity claims.

Devices are restored from fresh APK/settings snapshots with hash and settings verification. Task-created packaged apps and owned external proof/APK/SDK files are removed or restored. UUID-scoped database fixtures close handles and remove their own database directories; pre-existing regression fixture behavior is preserved. Verified original APK backup copies are removed after restoration. Logs, failed attempts, final JSON evidence, signatures and tested artifacts remain in ignored `.bak/bun-database-20261019/`; the existing isolated checkout/signing files are untouched. Initial unrelated Simplified Chinese draft hunks remain outside the host commit.

The final host release-note date normalization to 2026/10/19 and report edits were text-only changes after executable validation; no additional device acceptance is inferred from them.
