# F.6 fine-grained update evidence

Date: 2026-10-08. Roadmap F.6 is decided by performance evidence. The P5.3 campaign
measured a 1000-row first frame and real scrolling, but it did not measure a state
change while a large render-layer tree stays mounted. P5.3 therefore recorded that
repeated unchanged large-tree rebuilds had not been shown to dominate updates. This
record closes that gap before any API change.

## Decision input

The opt-in `list-updates` section of `.python/compose_performance.py` runs
`ComposePerformanceDeviceTest#thousandItemListStateUpdates`. Both workloads mount the
same tree: a value label, a 32 dp color swatch, an update button and a 1000-row
LazyColumn whose rows never change. One button click changes the label and swatch.

- Render layer: the click writes one `compose.state`; the scheduled render rebuilds
  the whole tree in JavaScript and the host reconciler suppresses unchanged rows.
- Handle layer: the tree is built once; the click updates the label and swatch
  handles directly.

The endpoint is the P5.3 visibility confirmation: matching accessibility text, two
display callbacks and a screenshot pixel of the swatch. It is an instrumented upper
bound, not a presentation timestamp or pure renderer time.

| Item | Value |
| --- | --- |
| Device | Owned API35 x86_64 google_apis AVD, `sdk_gphone64_x86_64`, build `AE3A.240806.043` |
| Host | Isolated `org.autojs.autojs6.compose.spike` 6.8.0 / 5322 debug, source `6a166c3841` plus this harness |
| Plugin | Compose UI 1.1.0 / 36 minified release |
| Sampling | 2 excluded warmups, then 10 samples per layer, 8 ms polling |

Cells are median / p90 in ms, linear interpolation at `(n - 1) * 0.9`.

| Measure | Render layer | Handle layer |
| --- | --- | --- |
| State write to pixel confirmation | 722.50 / 732.29 | 156.44 / 178.53 |
| State write to semantics text | 667.92 / 675.04 | 97.55 / 121.39 |
| Render callback construction | 483.45 / 497.05 | not applicable |
| Script mutation loop | 0.12 / 0.49 | 1.02 / 1.52 |
| Renders per click | 1 in every sample | 0 in every sample |

Every render-layer click produced exactly one render, so the cost is not repeated
scheduling. About 483 ms of the 566 ms difference is JavaScript reconstruction of
1000 unchanged rows; the rest is host traversal, snapshot validation and patch
diffing over the rebuilt tree. In `"ui";` mode this work runs on the Android main
thread. The handle layer shows that the same visible change costs about the same as
a small counter update when unchanged rows are not rebuilt.

Decision: the evidence justifies F.6. Host snapshot comparison already suppresses
unchanged native operations, so the remaining cost must be removed on the script
side by letting render skip unchanged fragments (`compose.memo`).

Raw records and per-invocation logs remain in the host's ignored
`build/compose-performance/f6-api35/` directory. They contain synthetic benchmark
labels and timings, not user script text or screenshots.

## compose.memo implementation

`compose.memo(fn, deps, options?)` is valid only while a session's render function
runs. A slot is identified by its scope (the render, or the enclosing memo fragment),
by `options.key` or call order within that scope, and by the function literal
(Rhino `JSFunction.getDescriptor()`, so closures of one literal share it). The
previous node is reused when every dependency is unchanged (`Object.is` for
primitives, identity for objects), no `compose.state` read while the fragment was
built has been written since, and the node is still in the session's previous tree.
A reused fragment replays its state reads, so the render stays subscribed to them.
Otherwise the fragment runs again and reconciles like ordinary render output.

The cache is per session. It is committed only after the render and its publication
succeed, kept unchanged by a failed render, cleared when the renderer rejects a
frame, and released on close. Limits: 256 dependencies per call and 5000 fragments
per render; duplicate keys in one scope raise `DUPLICATE_KEY`.

The host contract, renderer protocol and plugin APK are unchanged. The script API
needs the host build 6.8.0 / 5323.

## Result

The same `list-updates` section ran with a third workload whose 1000 rows are keyed
`compose.memo` fragments with the row index as dependency. Measured source: the memo
implementation in the working tree of the feature commit, built before the version
bump (APK versionCode 5322), on the same owned API35 AVD, plugin 1.1.0 / 36,
2 warmups and 10 samples per layer.

| Measure | Render layer | Memo rows | Handle layer |
| --- | --- | --- | --- |
| State write to pixel confirmation | 752.78 / 791.42 | 307.22 / 322.22 | 155.81 / 173.66 |
| State write to semantics text | 692.60 / 729.37 | 247.74 / 255.48 | 99.28 / 112.53 |
| Render callback construction | 489.53 / 554.89 | 99.94 / 107.55 | not applicable |

Memo rows cut the visible update by about 59% and render construction by about 80%
against the whole-tree render in the same run. The remaining difference to the handle
layer is the JavaScript loop of 1000 memo calls and host traversal of the retained
rows. One fragment around a whole unchanged subtree would also skip that loop; that
layout was not measured here.

Functional device coverage: `ComposeScriptUiDeviceTest` (8/8 on the same AVD,
including `memoizedKeyedRowsSkipUnrelatedRendersAndRebuildOnlyTheEditedRow`) shows that
an unrelated render rebuilds none of 300 keyed rows, and a reverse plus single-row edit
rebuilds exactly one row, keeps the moved handles and reaches the real renderer.
JVM: the selected Compose host suite (208) and the contract API suite (29) pass,
including five `compose.memo` cases for keyed reuse, state tracking, scope and
literal identity, failed or rejected frames, and argument errors.
