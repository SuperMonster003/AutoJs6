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
