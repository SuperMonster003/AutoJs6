# Code generation data fixture

This debug-only View application supplies reproducible screens and an independent oracle for the layout-code-generation dataset. It is never a host dependency or production distribution. Build with `:test-apps:codegen-fixture:assembleDebug`; install the APK with `adb install -t -r`.

```
adb shell am start -S -n org.autojs.test.codegen.fixture/.MainActivity --es screen list --el seed 42 --es mutations shuffle,retext --es stateId list-42-s1 --es runId capture-42
adb exec-out run-as org.autojs.test.codegen.fixture cat files/ready.json
adb exec-out run-as org.autojs.test.codegen.fixture cat files/truth/list-42-s1.json
adb exec-out run-as org.autojs.test.codegen.fixture cat files/receipts.jsonl
```

`screen` is one of `login`, `list`, `settings`, `grid`, `tabs`, `dialog`, `nested-scroll`, `no-id`. The list uses RecyclerView; nested scroll panels start between their limits so either scroll action is available. Six tagged controls per family remain present after every mutation. Untagged filler rows supply deletion and insertion cases. All target bounds must be fully visible before readiness is published.

`seed` is a signed 64-bit integer. `mutations` is an ordered comma-separated sequence (at most 32): `shuffle`, `insert`, `delete`, `retext`, `locale`, `strip-ids`, `rename-ids`, `wrap`, `sibling`. Repeating `locale` toggles languages; repeating `retext` advances content. Repeated exhausted deletions and redundant ID/wrapper/sibling mutations are recorded in `skippedMutations`, never reported as applied. `no-id` starts without content resource IDs, so its ID mutations are skipped. System window IDs are outside fixture control.

`stateId` is optional; its default is derived from SHA-256 of screen, seed, and ordered mutations. Explicit state IDs are safe filename tokens of at most 96 characters. `runId` is an optional freshness token. On each launch `ready.json` and `error.json` are cleared. After two equal geometry checks 50 ms apart, truth is written atomically, followed by readiness. Poll readiness until both the requested `stateId` and `runId` match; read `error.json` on failure. Do not reuse a readiness file from a previous process. A failure to lay out all targets reports an error instead of silently dropping clipped targets.

Truth schema:

```json
{
  "format": 1,
  "stateId": "list-42-s1",
  "screenFamily": "list",
  "seed": 42,
  "mutations": ["shuffle", "retext"],
  "appliedMutations": ["shuffle", "retext"],
  "skippedMutations": [],
  "targets": [
    {"key": "list.mouse", "bounds": [0, 0, 90, 48], "depth": 5,
     "className": "android.widget.Button", "capabilities": ["click", "long-click"]}
  ]
}
```

The example bounds and depth are illustrative. Actual bounds are screen coordinates, including a dialog's window offset, from `getGlobalVisibleRect`; depth follows accessibility parents. Targets are sorted by key. Truth has no timestamps or run token, and the same inputs on the same device/configuration yield identical bytes. Collection still validates every truth-to-snapshot join and rejects ambiguity. The tags use `View.setTag(R.id.codegen_truth, key)` and never become node properties. Business labels, duplicate action resource IDs, dynamic counts, and stable names are ordinary visible fixture data, not oracle identifiers.

`ready.json` contains `format`, `stateId`, `runId`, and the relative `truth` path. `receipts.jsonl` appends `key`, `action`, `args`, `time` (Unix milliseconds), and `stateId` for actual user/automation actions after readiness. Action names are `click`, `long-click`, `set-text`, `scroll-forward`, and `scroll-backward`. Setting text records the resulting `text`; switches record `checked`; scrolling records `x`, `y`, `oldX`, and `oldY`. Initial population and scroll positioning do not create receipts. Scroll animation can produce more than one receipt; verify the actual position change rather than equating receipt count to action count.

Stable target suffixes (each prefixed by `screen + "."`):

| Screen | Target suffixes |
| --- | --- |
| login | email, password, remember, sign-in, help, guest |
| list | mouse, keyboard, camera, speaker, headphones, charger |
| settings | wifi, bluetooth, notifications, sync, location, dark-mode |
| grid | photos, music, videos, books, downloads, documents |
| tabs | overview, activity, files, open, share, archive |
| dialog | name, remember, allow, deny, details, later |
| nested-scroll | news, messages, events, tasks, notes, history |
| no-id | search, filter, sort, favorite, export, refresh |

The fixture unit tests exercise all eight families, 48 seeds, target retention, deterministic ordered mutations, honest no-op reporting, and input rejection. Device collection should additionally compare oracle bytes across two launches and associate all targets with captured accessibility nodes for every family and mutation.

Run the repeatable device smoke from the repository root (the device must have the debug fixture and an enabled AutoJs6 accessibility service):

```
python test-apps/codegen-fixture/tools/smoke.py --adb /path/to/adb --serial DEVICE_SERIAL --output build/codegen-model/fixture-smoke
```

The smoke starts the capture worker, force-stops only the fixture between repeated launches, compares exact oracle bytes for all eight families, and joins all six targets against actual host snapshots. It then executes the host's public selector/action APIs on fixture controls to verify click, long-click, escaped text, and both scroll directions against appended receipts. It leaves the host's accessibility service running and never opens a UiAutomation connection. The output directory contains captured fixture snapshots, action receipts, and `report.json`; none of these generated files need to be committed.
