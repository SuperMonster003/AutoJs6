# DEX Compiler R5 Performance Evidence - 2026-08-27

This report closes the benchmark-construction and numeric-threshold definition
items in DEX Compiler Roadmap R5.2. The measured candidate is **not promoted**:
latency, cache-state, route, and output-determinism gates pass, but the large
cold corpus exceeds the pre-existing combined-PSS proposal. The plugin remains
default-off, and no performance advantage is claimed.

The stable machine-readable records are:

- [Raw baseline](./dex-compiler-r5-performance-baseline-2026-08-27.json),
  454,193 bytes, SHA-256
  `af14542972a9979f4170f8edfee6d969bd0334a9cdfb7d86dd8b39d43a7fa371`.
- [Promotion evaluation](./dex-compiler-r5-performance-evaluation-2026-08-27.json),
  9,646 bytes, SHA-256
  `8fa28a9c6a011c1ac2faf2cb7601638a1469644b6a6ac823f74ea8dffcc0018f`.
- Raw campaign ID: `97192448-3b71-4d42-b180-43c515aae0c6`.
- Raw schema: `autojs.dex-compiler.r5-performance/v1`; evaluation schema:
  `autojs.dex-compiler.r5-performance-evaluation/v1`.

## Decision

| Gate | Rule | Result |
| --- | --- | --- |
| Cold latency | Plugin added P95 must be no more than `max(25% of built-in P95, 1000 ms)` in every corpus. | PASS, 3/3 |
| Cache-hit latency | Plugin added P95 must be no more than 100 ms over the built-in cache-hit path in every corpus. | PASS, 3/3 |
| Combined memory | Plugin combined host-plus-provider P95 PSS must regress by no more than 20% in every corpus/cache state. | **FAIL, 5/6** |
| Cache and adopted route | Every measured sample must prove its requested cache state and expected adopted route. | PASS, 120/120; cache-hit subset 60/60 |
| Output repeatability | Every route/corpus/cache summary must contain one positive output size and one valid SHA-256 across its ten measured samples. | PASS, 12/12 |

The only failed gate is `java8-2048x8 / COLD`: plugin combined P95 PSS is
344,540 KiB versus 279,425 KiB built-in, a 23.30% regression against a maximum
of 20%. The overage is 3.30 percentage points, or 9,230 KiB above the
335,310 KiB allowed ceiling after rounding to whole KiB.

These thresholds promote the proposal already recorded in
`docs/dev/dex-compiler-plugin-evaluation.md` to an executable policy. They were
not widened after observing the candidate. Passing this descriptive gate would
still not, by itself, authorize a user-facing performance-advantage claim,
default enablement, host compiler dependency removal, or statistical inference.

## Environment And Paired Artifacts

The campaign ran from `2026-08-27T00:37:03.6404324+08:00` through
`2026-08-27T01:04:38.6263056+08:00` on the user's preferred authorized physical
device.

| Field | Recorded value |
| --- | --- |
| Serial / device | `QV710AF65F`, Sony XQ-AT72 |
| Android / ABI | API 31, `arm64-v8a` |
| Build / security patch | `58.2.A.10.44A`, `2022-07-01` |
| Build fingerprint SHA-256 | `9042a9fbd1a1ff4678bf3cbca5775dfda4b9618c969721f6d0c7c7a528817fdb` |
| Battery / temperature | 100% and 34.7 C before; 100% and 34.7 C after |
| Host environment | Microsoft Windows `10.0.26200`, PowerShell `7.6.5` |
| Runner-visible Java | Java `1.8.0_311`, HotSpot 64-Bit Server VM `25.311-b11` |
| Android tools | ADB `37.0.1-15733141`, build-tools `37.0.0` |

The compiler executes on Android; the Java field records the host runner's
visible Java environment rather than a host-side compilation step.

| Artifact | Identity | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| AutoJs6 arm64 benchmark host | `org.autojs.autojs6`, 6.8.0 build 5276 | 42,427,353 | `32b787052c76030ac47a7c601dede8cc4e46b0a4c5d249f506fe57c6b4814419` |
| AutoJs6 instrumentation | `org.autojs.autojs6.test` | 1,711,264 | `03aca02f2785e9cf3f214a2bfd1855a72249cade930ab78a823d7cfd48554677` |
| DEX Compiler non-debuggable Release | `io.github.supermonster003.autojs6.plugin.dexcompiler`, 1.0.0 build 4 | 6,636,134 | `9b1a9c1666d703ba82b33fcd6d1c3ca3de3a321ff815badac8554bb2648dfc9e` |

All three APKs have the same V2 signer certificate SHA-256:
`31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213`.
The host and instrumentation APKs were built from an isolated host worktree so
unrelated live Explorer/Archive changes in the primary AutoJs6 checkout were
not included.

## Fixed Corpus

The instrumentation creates Java 8 classfiles directly on the device with the
versioned deterministic generator `ON_DEVICE_DETERMINISTIC_JAVA8_CLASSFILE_V1`.
It regenerates each corpus twice before measurement and requires byte-for-byte
identity.

| Corpus | Classes | Static methods per class | Declared methods | Input bytes | Input SHA-256 |
| --- | ---: | ---: | ---: | ---: | --- |
| `java8-1x8` | 1 | 8 | 9 | 742 | `d2d6a87e61292ee3a14f71673b62b623d50587d31c3616cc7416f59d750c036b` |
| `java8-128x8` | 128 | 8 | 1,152 | 93,334 | `119347d75cb8e9017805872b45f8cb2ab9b37401d958699a4fcea75b2a53a015` |
| `java8-2048x8` | 2,048 | 8 | 18,432 | 1,474,582 | `4b6a52e4124df5ffb4854fe9adc2dc63520339290124ebbbcbef1cb42e07255f` |

Each generated method has executable bytecode, and every timed sample resolves
the generated class and invokes its static method, expecting `42`. The benchmark
therefore verifies the real `DexClassLoader` result rather than only compiler
completion or structural DEX parsing.

## Measurement Contract

- Routes: current AutoJs6 built-in compiler and the paired production plugin.
- States: fresh persistent-cache miss (`COLD`) and proven persistent-cache hit
  (`CACHE_HIT`).
- Timing boundary: immediately before `runtime.loadJar` through class
  resolution and one static invocation. Corpus generation, cache preparation,
  external memory polling, cleanup, and package restoration are outside this
  boundary.
- Process state: the host and plugin service are force-stopped before every
  timed sample. A plugin cache hit still performs the authenticated provider
  handshake because capability identity participates in final cache-key
  selection.
- Sampling: two warmups followed by ten measured samples per
  corpus/route/cache cell, 144 timed invocations total and 120 measured samples.
- Ordering: the first route alternates within paired rounds for each corpus and
  cache state to reduce time and thermal bias.
- Stabilization: 750 ms before and after the timed load plus a one-second gap
  between samples.
- Percentiles: R7 linear interpolation; the report retains minimum, median,
  P95, maximum, mean, and population standard deviation.
- Memory: external 100 ms requested inter-poll delay using exact process PIDs,
  plus in-process host PSS and Java/native heap snapshots. Combined PSS is the
  largest simultaneously observed host-plus-provider value, not the sum of two
  independently selected peaks.
- Built-in cache proof: the artifact exists before the timed request and has
  the same filename, size, and SHA-256 afterward. Modification time is recorded
  but not asserted because ART may touch it without changing bytes.
- Plugin cache proof: an exact semantic generation is primed, the requested
  key is retained by the fixture, and the timed request must adopt
  `CACHE_HIT`. Cold generations are invalidated after the timed load without an
  unmeasured recompilation.

The implementation is split between
`DexCompilerR5PerformanceAndroidTest.kt`,
`tools/dex/run-dex-compiler-r5-performance.ps1`, and
`tools/dex/evaluate-dex-compiler-r5-performance.ps1`. Both scripts require
PowerShell 7. The runner also requires the explicit
`-ConfirmDeviceMutation` switch before it installs or force-stops anything.

## Descriptive Results

All time values are milliseconds; PSS values are KiB. Each row is based on ten
measured samples after two discarded warmups.

| Corpus | State | Route | Time P50 | Time P95 | Combined PSS P50 | Combined PSS P95 |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| `java8-1x8` | COLD | BUILTIN | 596.5 | 711.6 | 185,886 | 188,818 |
| `java8-1x8` | COLD | PLUGIN | 640.1 | 670.3 | 205,737 | 207,924 |
| `java8-1x8` | CACHE_HIT | BUILTIN | 593.1 | 646.0 | 185,507 | 188,062 |
| `java8-1x8` | CACHE_HIT | PLUGIN | 459.1 | 472.5 | 194,568 | 196,218 |
| `java8-128x8` | COLD | BUILTIN | 1,752.4 | 1,893.5 | 208,110 | 217,372 |
| `java8-128x8` | COLD | PLUGIN | 1,299.1 | 1,364.7 | 228,156 | 238,627 |
| `java8-128x8` | CACHE_HIT | BUILTIN | 1,752.2 | 1,905.3 | 202,096 | 214,568 |
| `java8-128x8` | CACHE_HIT | PLUGIN | 478.8 | 524.4 | 194,926 | 196,238 |
| `java8-2048x8` | COLD | BUILTIN | 8,065.3 | 9,033.5 | 262,542 | 279,425 |
| `java8-2048x8` | COLD | PLUGIN | 4,779.0 | 5,162.4 | 294,175 | 344,540 |
| `java8-2048x8` | CACHE_HIT | BUILTIN | 8,443.8 | 9,060.8 | 259,967 | 281,347 |
| `java8-2048x8` | CACHE_HIT | PLUGIN | 597.3 | 656.3 | 195,890 | 198,656 |

The paired P95 comparisons and gate results are:

| Corpus | State | Plugin latency delta | Latency gate | Plugin combined-PSS delta | Memory gate |
| --- | --- | ---: | --- | ---: | --- |
| `java8-1x8` | COLD | -41.3 ms (-5.80%) | PASS | +19,106 KiB (+10.12%) | PASS |
| `java8-1x8` | CACHE_HIT | -173.5 ms (-26.86%) | PASS | +8,156 KiB (+4.34%) | PASS |
| `java8-128x8` | COLD | -528.9 ms (-27.93%) | PASS | +21,255 KiB (+9.78%) | PASS |
| `java8-128x8` | CACHE_HIT | -1,380.8 ms (-72.47%) | PASS | -18,329 KiB (-8.54%) | PASS |
| `java8-2048x8` | COLD | -3,871.1 ms (-42.85%) | PASS | +65,115 KiB (+23.30%) | **FAIL** |
| `java8-2048x8` | CACHE_HIT | -8,404.5 ms (-92.76%) | PASS | -82,691 KiB (-29.39%) | PASS |

These results are descriptive. They show no P95 latency regression in this
campaign, but they do not establish a general performance advantage or a
confidence interval across devices.

## Cache, Route, And Output Evidence

- All 120 measured samples proved the requested cache state and adopted the
  expected route (`120/120`, rate 1.0).
- All 60 measured cache-hit samples proved their hit (`60/60`, rate 1.0).
- Every measured plugin cache-hit sample adopted `CACHE_HIT` (`30/30`, rate
  1.0). Built-in cache-hit samples remained on the `BUILTIN` route and proved
  unchanged cached bytes.
- The provider process was observed with positive PSS for every plugin sample
  and was absent from every built-in sample. The host process was observed with
  positive PSS for every sample.
- All 12 measured summaries contain a single stable output size and SHA-256.
  Cold and cache-hit output identities match within each route/corpus pair.

| Corpus | Route | Output bytes | Output SHA-256 |
| --- | --- | ---: | --- |
| `java8-1x8` | BUILTIN | 828 | `76d560924af36a60bb5a7ecb9d5d73feb1c35312a116507bf26e5e1bccd99431` |
| `java8-1x8` | PLUGIN | 948 | `3bfe760ac1c93cc91f06641ce7996f990cc8eae8804e2341ad698652a43f8a6f` |
| `java8-128x8` | BUILTIN | 30,152 | `b0bcd1a1e5bd6ecfd1dc7919898f1af99c16363a9bd0addab15aefe99158dcf1` |
| `java8-128x8` | PLUGIN | 30,272 | `05bb6da4ed3ab098933b8555b8a888799c60470a995f1be08e04f688ef509179` |
| `java8-2048x8` | BUILTIN | 486,188 | `c4f27c321be6a017ad822a0047096aa11d8cf7403c6bb22d9c614aa0a9f513bf` |
| `java8-2048x8` | PLUGIN | 486,308 | `a0d283fc697e73d60f217e3d9005c34fce3ba472a5b68530603cdcf0557d48ac` |

Different compiler versions may legitimately emit different DEX bytes, so the
gate requires repeatability within a route and executable equivalence rather
than byte identity between built-in and plugin outputs.

## Harness Qualification And Restoration

Two exploratory campaigns qualified the harness before the baseline:

- Campaign `4e37a398-63ca-4e02-998b-65caabfa3c55`: one corpus, 4/4 timed
  samples, `EXPLORATORY_SMOKE`, PASS, exact package restoration.
- Campaign `cc6274d6-9bcd-4a14-bc2c-b3834f882853`: all three corpora, 12/12
  timed samples, `EXPLORATORY_SMOKE`, PASS, exact package restoration.

During harness development, setup-only attempts exposed Android build-tools
output-format differences, exact-PID requirements for Sony `dumpsys meminfo`,
and strict-mode handling of scalar command results. Those attempts were not
baseline campaigns and were excluded from every performance total. The runner
was hardened and both successful exploratory campaigns were completed before
the 144-sample baseline began.

For the accepted baseline:

- Status is `PASS`, with 144/144 timed invocations and no cleanup or restoration
  errors.
- The fixture-specific no-backup workspace was removed.
- The host package existed before the run. Its original device-side APK bytes
  were snapshotted and restored; direct post-run verification produced
  SHA-256 `fca09ff81ba528d7f1b45803a0e1ee38a484ef779c6b008ae20790aa70a05b2c`.
- The instrumentation and plugin packages did not exist before the run and were
  absent afterward.
- No recovery snapshot was needed or retained by the accepted campaign.

## Reproduction

Build the paired host, instrumentation, and non-debuggable plugin Release APKs,
then run from the AutoJs6 repository:

```powershell
./tools/dex/run-dex-compiler-r5-performance.ps1 `
  -Serial QV710AF65F `
  -HostApk <paired-arm64-host-apk> `
  -HostTestApk <paired-androidTest-apk> `
  -PluginApk <paired-non-debuggable-plugin-release-apk> `
  -OutputPath ./docs/dev/dex-compiler-r5-performance-baseline-YYYY-MM-DD.json `
  -WarmupSamples 2 `
  -MeasuredSamples 10 `
  -PssPollMilliseconds 100 `
  -BetweenSamplesSeconds 1 `
  -ConfirmDeviceMutation
```

Evaluate the immutable raw report separately:

```powershell
./tools/dex/evaluate-dex-compiler-r5-performance.ps1 `
  -InputReport ./docs/dev/dex-compiler-r5-performance-baseline-YYYY-MM-DD.json `
  -OutputPath ./docs/dev/dex-compiler-r5-performance-evaluation-YYYY-MM-DD.json
```

Pass `-FailOnNotPromoted` when a non-promotion decision should fail an automated
gate with exit code 2. Neither script changes the plugin's default selection.

## Boundaries And Follow-up

- This is one API 31 arm64 physical-device baseline, not the R5.3 V1.1 device
  matrix or the release-device matrix required before removing host compiler
  dependencies.
- Ten measured observations per cell support a descriptive R7 P95 but do not
  support a broad statistical-inference claim. A later release gate may add
  confidence intervals without rewriting this raw campaign.
- This benchmark excludes ordinary app startup, compressed/installed APK delta,
  temporary-storage high-water mark, cancellation latency, and V1.1 classpath
  matrix coverage; their existing independent gates remain in force.
- External PSS uses sequential `dumpsys meminfo <exact-pid>` polls. The requested
  inter-poll delay is 100 ms, but command execution time means it is not an exact
  10 Hz sampling guarantee.
- R5.2 benchmark construction and numeric policy definition are complete. The
  default-enable checklist's performance/resource prerequisite remains open
  because the large cold combined-PSS gate failed. The supported response is to
  reduce or explain the resource tail and rerun a new immutable campaign, not
  to relabel this result as a pass.
