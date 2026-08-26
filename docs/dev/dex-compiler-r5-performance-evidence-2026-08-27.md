# DEX Compiler R5 Performance Evidence - 2026-08-27

This report closes the benchmark-construction and numeric-threshold definition
items in DEX Compiler Roadmap R5.2 and records the subsequent benchmark
correction and bounded-parallelism follow-up. The current candidate is **not
promoted**. Bounded D8 parallelism brings all six combined-PSS cells inside the
pre-registered 20% ceiling, and all three cold-latency cells pass, but the three
truthfully measured process-cold cache-hit cells exceed the 100 ms added-P95
ceiling. The plugin remains default-off, and no performance advantage is
claimed.

The current machine-readable records are:

- [Corrected bounded-parallelism raw baseline](./dex-compiler-r5-performance-baseline-bounded2-cachefix-avd-api33-2026-08-27.json),
  465,147 bytes, SHA-256
  `8a11f2cb2568a2500976d7bd89fa32af9ff674c48e58332a5ccda8f9cb17a645`.
- [Corrected promotion evaluation](./dex-compiler-r5-performance-evaluation-bounded2-cachefix-avd-api33-2026-08-27.json),
  10,135 bytes, SHA-256
  `2332dea508750806341fd45d1fc615357b6a75348ff1c14e845f518ea7748cc9`.
- Accepted raw campaign ID: `4acd2687-e372-4f9f-8428-265a2c34dd86`.
- Raw schema: `autojs.dex-compiler.r5-performance/v1`; evaluation schema:
  `autojs.dex-compiler.r5-performance-evaluation/v1`.

The original API 31 physical-device baseline and evaluation remain immutable at
[the original raw baseline](./dex-compiler-r5-performance-baseline-2026-08-27.json)
and [the original evaluation](./dex-compiler-r5-performance-evaluation-2026-08-27.json).
They are historical evidence of the first campaign, but the strengthened
evaluator now rejects that raw report because its built-in `CACHE_HIT` cells did
not prove that the loader preserved the prewarmed artifact. Those historical
files are not used for the current promotion decision.

## Current Decision

| Gate | Rule | Corrected result |
| --- | --- | --- |
| Cold latency | Plugin added P95 must be no more than `max(25% of built-in P95, 1000 ms)` in every corpus. | PASS, 3/3 |
| Cache-hit latency | Plugin added P95 must be no more than 100 ms over the built-in cache-hit path in every corpus. | **FAIL, 0/3** |
| Combined memory | Plugin combined host-plus-provider P95 PSS must regress by no more than 20% in every corpus/cache state. | PASS, 6/6 |
| Cache and adopted route | Every measured sample must prove its requested cache state and expected adopted route. | PASS, 120/120; cache-hit subset 60/60 |
| Output repeatability | Every route/corpus/cache summary must contain one positive output size and one valid SHA-256 across its ten measured samples. | PASS, 12/12 |

The three failed cells are all `CACHE_HIT` latency cells:

| Corpus | Built-in P95 | Plugin P95 | Added P95 | Maximum | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| `java8-1x8` | 5.5 ms | 238.2 ms | 232.7 ms | 100 ms | **FAIL** |
| `java8-128x8` | 6.3 ms | 235.4 ms | 229.2 ms | 100 ms | **FAIL** |
| `java8-2048x8` | 28.3 ms | 296.7 ms | 268.4 ms | 100 ms | **FAIL** |

The plugin cache is host-owned and verified, but its final semantic key is
capability-bound. A process-cold hit therefore still discovers and authenticates
the selected provider, starts its isolated process, negotiates capabilities, and
then consumes the host-private generation. That fixed cross-process cost is
visible now that the built-in side performs a real disk-cache hit rather than a
hidden recompilation.

These thresholds promote the proposal already recorded in
`docs/dev/dex-compiler-plugin-evaluation.md` to an executable policy. They were
not widened after observing either candidate. Passing this descriptive gate
would still not, by itself, authorize a user-facing performance-advantage claim,
default enablement, host compiler dependency removal, or statistical inference.

## Why The Original Cache Comparison Was Invalid

The first accepted campaign completed 144/144 samples and correctly recorded
its routes, process PSS, package restoration, and plugin cache hits. Its built-in
cache proof was insufficient, however:

1. The fixture primed a persistent built-in cache directory.
2. Every timed sample constructed a new production `AndroidClassLoader`.
3. The production two-argument constructor deliberately deletes every file in
   its cache directory.
4. The built-in path then recompiled the corpus into the same pathname.
5. For most samples D8 reproduced the same bytes, so checking only the artifact
   name, size, and SHA-256 could not distinguish a hit from recompilation.

The API 33 campaign `3d54691b-8d58-4d25-af8d-229dc14090de` exposed the defect
at sample 91: a medium built-in sample changed from 30,152 to 30,176 bytes. That
was not a corrupt cache hit. It was a fresh D8 output replacing the supposedly
pre-existing artifact.

The correction keeps production behavior unchanged:

- Existing Java and Kotlin callers still use the public two-argument
  `AndroidClassLoader(parent, cacheDir)` constructor, which clears the directory.
- A module-internal three-argument constructor is used only by the R5 fixture.
  It preserves the prewarmed loader directory only for built-in `CACHE_HIT`
  samples; priming, all cold samples, and plugin loader caches retain the normal
  clear-on-construction behavior.
- A built-in hit must keep the artifact filename, size, SHA-256, and modification
  time unchanged. All 30 corrected measured built-in hits reported a zero
  modification-time delta.
- The runner validates the exact proof string, initialization mode, adopted
  route, cache expectation, and zero modification-time delta before accepting a
  sample.
- The independent evaluator repeats those checks. It rejects the original raw
  baseline because the benchmark contract is missing
  `builtInCacheHitInitialization`, rather than re-evaluating its old boolean at
  face value.

## Memory Optimization Under Test

Plugin commit `ca4c1cc421f4388f773d1391c65aec8d21793dfb` bounds D8 internal
parallelism to two workers on both the API 26+ command path and the API 24/25
CLI-compatible path. The plugin's full local gate passed 15 suites / 90 tests,
lint reported 0 errors and 27 warnings, and Debug plus Release APK assembly
succeeded.

The historical physical campaign recorded a failing 344,540 KiB plugin versus
279,425 KiB built-in large-cold comparison. The corrected API 33 AVD campaign
records 252,208 KiB plugin versus 346,865 KiB built-in. The two campaigns are on
different architectures and therefore are not a before/after effect estimate.
The valid conclusion is narrower: in the corrected formal campaign, every
combined-PSS cell passes, including large cold at -27.29% versus its
same-campaign built-in comparator.

## Environment And Paired Artifacts

The corrected campaign ran from `2026-08-26T19:04:19.5577589+00:00` through
`2026-08-26T19:22:12.5524106+00:00` on the user-authorized `AVD_API_33` AVD.
This AVD was launched cold with no snapshot load/save and was not shared with the
other already-running emulator instances.

| Field | Recorded value |
| --- | --- |
| Serial / device | `emulator-5562`, Google `sdk_gphone64_x86_64` |
| Android / ABI | API 33, `x86_64` |
| Build / security patch | `TE1A.240213.009`, `2024-03-01` |
| Build fingerprint SHA-256 | `67d87afcf0f1cc33a95aa2e5c8bf7baa37153ff834015acab0f03e2a48dbf393` |
| Battery / temperature | 100% and 25.0 C before; 100% and 25.0 C after |
| Host environment | Microsoft Windows `10.0.26200`, PowerShell `7.6.5` |
| Runner-visible Java | Java `1.8.0_311`, HotSpot 64-Bit Server VM `25.311-b11` |
| Android tools | ADB `37.0.1-15733141`, build-tools `37.0.0` |

The compiler executes on Android; the Java field records the host runner's
visible Java environment rather than a host-side compilation step.

| Artifact | Identity | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| AutoJs6 x86_64 benchmark host | `org.autojs.autojs6`, 6.8.0 build 5276 | 43,221,999 | `5051a55d1a9136032e20ba84d4584f75a2a1f4e5b1d267a4a936face3c6780ca` |
| AutoJs6 instrumentation | `org.autojs.autojs6.test` | 1,711,930 | `fa6be30475620b50aadba51af0782e9a86142856f89f3db3e6490ae8c81b12f7` |
| DEX Compiler non-debuggable Release | `io.github.supermonster003.autojs6.plugin.dexcompiler`, 1.0.0 build 4 | 6,638,094 | `3b9418227d90fccac495519f61552a78ef8ece5946602930a9100f7ba77981ad` |

All three APKs have the same V2 signer certificate SHA-256:
`31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213`.
The host and instrumentation APKs were built from isolated worktree `C:\r5p`,
so unrelated live TypeScript and Explorer changes in the primary AutoJs6
checkout were not included.

## Fixed Corpus And Measurement Contract

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

The corrected contract is:

- Routes: current AutoJs6 built-in compiler and the paired production plugin.
- States: a fresh miss (`COLD`) and a proven pre-existing persistent-cache hit
  (`CACHE_HIT`).
- Timing boundary: immediately before `runtime.loadJar` through class
  resolution and one static invocation. Corpus generation, cache preparation,
  external memory polling, cleanup, and package restoration are outside this
  boundary.
- Process state: the host and plugin service are force-stopped before every
  timed sample. The runner requires three consecutive 100 ms PID-absent polls
  within ten seconds before starting instrumentation.
- Plugin process expectation: a plugin cache hit still performs the
  authenticated provider handshake because capability identity participates in
  final cache-key selection, so the provider must be externally observed for
  both plugin cache states.
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
- Built-in cache proof: the fixture preserves the prewarmed loader directory;
  artifact name, size, SHA-256, and modification time must remain unchanged.
- Plugin cache proof: an exact semantic generation is primed, the requested key
  is retained by the fixture, and the timed request must adopt `CACHE_HIT`.
  Cold generations are invalidated after the timed load without an unmeasured
  recompilation.

## Corrected Descriptive Results

All time values are milliseconds; PSS values are KiB. Each row is based on ten
measured samples after two discarded warmups.

| Corpus | State | Route | Time P50 | Time P95 | Combined PSS P50 | Combined PSS P95 |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| `java8-1x8` | COLD | BUILTIN | 314.5 | 346.7 | 190,448 | 190,747 |
| `java8-1x8` | COLD | PLUGIN | 269.7 | 292.4 | 210,052 | 210,578 |
| `java8-1x8` | CACHE_HIT | BUILTIN | 3.4 | 5.5 | 190,121 | 190,887 |
| `java8-1x8` | CACHE_HIT | PLUGIN | 208.1 | 238.2 | 200,757 | 201,130 |
| `java8-128x8` | COLD | BUILTIN | 1,285.2 | 1,420.6 | 204,714 | 219,109 |
| `java8-128x8` | COLD | PLUGIN | 1,013.6 | 1,050.2 | 214,368 | 220,044 |
| `java8-128x8` | CACHE_HIT | BUILTIN | 4.3 | 6.3 | 190,288 | 191,101 |
| `java8-128x8` | CACHE_HIT | PLUGIN | 204.6 | 235.4 | 200,673 | 200,978 |
| `java8-2048x8` | COLD | BUILTIN | 5,380.8 | 5,895.8 | 288,236 | 346,865 |
| `java8-2048x8` | COLD | PLUGIN | 4,492.0 | 4,625.5 | 245,843 | 252,208 |
| `java8-2048x8` | CACHE_HIT | BUILTIN | 9.5 | 28.3 | 190,690 | 191,167 |
| `java8-2048x8` | CACHE_HIT | PLUGIN | 254.4 | 296.7 | 201,089 | 201,269 |

The paired P95 comparisons and gate results are:

| Corpus | State | Plugin latency delta | Latency gate | Plugin combined-PSS delta | Memory gate |
| --- | --- | ---: | --- | ---: | --- |
| `java8-1x8` | COLD | -54.3 ms (-15.66%) | PASS | +19,831 KiB (+10.40%) | PASS |
| `java8-1x8` | CACHE_HIT | +232.7 ms | **FAIL** | +10,242 KiB (+5.37%) | PASS |
| `java8-128x8` | COLD | -370.4 ms (-26.08%) | PASS | +935 KiB (+0.43%) | PASS |
| `java8-128x8` | CACHE_HIT | +229.2 ms | **FAIL** | +9,877 KiB (+5.17%) | PASS |
| `java8-2048x8` | COLD | -1,270.2 ms (-21.55%) | PASS | -94,657 KiB (-27.29%) | PASS |
| `java8-2048x8` | CACHE_HIT | +268.4 ms | **FAIL** | +10,102 KiB (+5.28%) | PASS |

These results are descriptive. They show a passing cold-path and memory result
on this AVD plus a repeatable cache-hit latency miss. They do not establish a
general performance advantage or a confidence interval across devices.

## Cache, Route, And Output Evidence

- All 120 measured samples proved the requested cache state and adopted the
  expected route (`120/120`, rate 1.0).
- All 60 measured cache-hit samples proved their hit (`60/60`, rate 1.0).
- Every measured plugin cache-hit sample adopted `CACHE_HIT` (`30/30`, rate
  1.0).
- Every measured built-in cache-hit sample preserved its pre-existing artifact,
  used initialization mode `PRESERVED_FOR_PREEXISTING_ARTIFACT`, and reported a
  zero modification-time delta (`30/30`).
- The provider process was observed with positive PSS for every plugin sample
  and was absent from every built-in sample. The host process was observed with
  positive PSS for every sample.
- All 12 measured summaries contain a single stable output size and SHA-256.
  Cold and cache-hit output identities match within each route/corpus pair.

| Corpus | Route | Output bytes | Output SHA-256 |
| --- | --- | ---: | --- |
| `java8-1x8` | BUILTIN | 828 | `261b70effced33988e820dd4869a21b3a06e35eca538bba59c2bba836c857ead` |
| `java8-1x8` | PLUGIN | 948 | `7a0acf35caab6cf0c913ad94b0b44515af2178ae0645fc50b194c2c24e6bf2c9` |
| `java8-128x8` | BUILTIN | 30,152 | `f2612c6012d4ff34e726b1a6f4f39a19e43bdff5c71176d98b1bb34d24f06c17` |
| `java8-128x8` | PLUGIN | 30,272 | `f2cc4284fa880732076b20dc608caa5597db34721f34b120c605ae81eb222fcb` |
| `java8-2048x8` | BUILTIN | 486,188 | `57ad3302f355ec4fce45ed93ebbbf72b29da5ec5133c2c0e21b39480f987c373` |
| `java8-2048x8` | PLUGIN | 486,308 | `65789e527be78233607e8bdcdeb731f5a0345ffbde9eb223dfdb3d4908a28f9f` |

Different compiler versions may legitimately emit different DEX bytes, so the
gate requires repeatability within a route and executable equivalence rather
than byte identity between built-in and plugin outputs.

## Campaign Qualification, Failures, And Recovery

The following immutable formal records are retained rather than overwritten or
merged. Partial samples are excluded from every accepted total.

| Campaign | Device | Result | Stable report | SHA-256 |
| --- | --- | --- | --- | --- |
| `97192448-3b71-4d42-b180-43c515aae0c6` | API 31 arm64 `QV710AF65F` | Original 144/144 PASS; historical cache proof is now insufficient | [original baseline](./dex-compiler-r5-performance-baseline-2026-08-27.json) | `af14542972a9979f4170f8edfee6d969bd0334a9cdfb7d86dd8b39d43a7fa371` |
| `71fc7660-c57c-4597-8c47-a53b68d9f480` | API 31 arm64 `QV710AF65F` | BLOCKED at 2/144 by USB disconnect | [bounded2 attempt](./dex-compiler-r5-performance-baseline-bounded2-2026-08-27.json) | `e1bc042dd88132c867de0913df669d7eb38746c9912158bdfc5da5c57cee4882` |
| `ca325848-b14f-42ab-aca3-745baf3674ff` | API 31 arm64 `QV710AF65F` | BLOCKED at 94/144 by USB disconnect | [bounded2 retry](./dex-compiler-r5-performance-baseline-bounded2-retry1-2026-08-27.json) | `0a7e26b54e48d26a1eff1a3f1064706f0e8996ccad21451cdea68fad6c06b608` |
| `6b67c9ed-6430-49ad-9aab-e5ff806d0f10` | API 35 arm64 `968e9f18` | FAIL at 71/144; Shizuku repeatedly restarted the host provider, so process quiescence could not be established | [quiescence failure](./dex-compiler-r5-performance-baseline-bounded2-968e9f18-2026-08-27.json) | `6febb189a9cccda677f0574fc45fd9452bc1eb2948aa15260d70e3d68dac0409` |
| `3d54691b-8d58-4d25-af8d-229dc14090de` | API 33 x86_64 AVD | FAIL at 90/144; exposed hidden built-in recompilation through a 30,152 to 30,176-byte output change | [pre-correction AVD attempt](./dex-compiler-r5-performance-baseline-bounded2-avd-api33-2026-08-27.json) | `0edf15a2ee45eadec0452f782003cfc624ef957cc0e9f7c3922ff124c1d15e44` |
| `4acd2687-e372-4f9f-8428-265a2c34dd86` | API 33 x86_64 AVD | Corrected 144/144 PASS; current source for evaluation | [corrected baseline](./dex-compiler-r5-performance-baseline-bounded2-cachefix-avd-api33-2026-08-27.json) | `8a11f2cb2568a2500976d7bd89fa32af9ff674c48e58332a5ccda8f9cb17a645` |

The runner's three-poll quiescence gate was added in host commit
`65c69a95f97dda32a4e8f63640097bd9699d74a9`. It converted an observed Android
instrumentation teardown race into an explicit precondition and correctly
rejected devices whose unrelated Shizuku integration continuously revived the
host. No Shizuku package or service was stopped or modified.

Additional exploratory reports under `app/build/reports/` remain outside the
stable evidence set. They recorded:

- strict 4/4 and 12/12 corrected API 33 smoke campaigns;
- API 24/25 `EMFILE (Too many open files)` while loading the large corpus, with
  exact cleanup; and
- a Sony API 28 instrumentation crash while preparing the large corpus, also
  with exact cleanup.

For the accepted corrected baseline:

- status is `PASS`, with 144/144 timed invocations and no cleanup or restoration
  errors;
- the fixture-specific no-backup workspace was removed;
- all three packages were absent before the run and absent afterward; and
- no recovery snapshot was needed or retained. The run-owned AVD was then shut
  down; its launch explicitly disabled snapshot load and save.

The preferred physical device `QV710AF65F` disconnected during both
bounded-parallelism attempts and is still absent from ADB. Run-owned recovery
snapshots are therefore intentionally retained at:

- `C:\Users\wu2du\AppData\Local\Temp\autojs-dex-r5-9531b61a-ded9-4e3a-8f4a-99ce9a04c062`;
- `C:\Users\wu2du\AppData\Local\Temp\autojs-dex-r5-fce973f0-7a47-4e76-bcd3-f2db9a0a73d4`.

The device was manually restored after the first disconnect before the retry,
but both snapshots are retained conservatively. When the serial reconnects, the
second attempt's snapshot must be used to restore and verify the exact original
host APK SHA-256
`fca09ff81ba528d7f1b45803a0e1ee38a484ef779c6b008ae20790aa70a05b2c`
before either recovery directory is removed.

## Reproduction

Build the paired host, instrumentation, and non-debuggable plugin Release APKs,
then run from the AutoJs6 repository:

```powershell
./tools/dex/run-dex-compiler-r5-performance.ps1 `
  -Serial <authorized-serial> `
  -HostApk <paired-abi-host-apk> `
  -HostTestApk <paired-androidTest-apk> `
  -PluginApk <paired-non-debuggable-plugin-release-apk> `
  -OutputPath ./docs/dev/dex-compiler-r5-performance-baseline-unique-name.json `
  -WarmupSamples 2 `
  -MeasuredSamples 10 `
  -PssPollMilliseconds 100 `
  -BetweenSamplesSeconds 1 `
  -ConfirmDeviceMutation
```

Evaluate the immutable raw report separately:

```powershell
./tools/dex/evaluate-dex-compiler-r5-performance.ps1 `
  -InputReport ./docs/dev/dex-compiler-r5-performance-baseline-unique-name.json `
  -OutputPath ./docs/dev/dex-compiler-r5-performance-evaluation-unique-name.json
```

Pass `-FailOnNotPromoted` when a non-promotion decision should fail an automated
gate with exit code 2. Neither script changes the plugin's default selection.

## Boundaries And Follow-up

- The corrected run is one API 33 x86_64 AVD baseline, not the R5.3 V1.1 device
  matrix, a replacement physical-device result, or the release-device matrix
  required before removing host compiler dependencies.
- Ten measured observations per cell support a descriptive R7 P95 but do not
  support a broad statistical-inference claim. A later release gate may add
  confidence intervals without rewriting this raw campaign.
- This benchmark excludes ordinary app startup, compressed/installed APK delta,
  temporary-storage high-water mark, cancellation latency, and V1.1 classpath
  matrix coverage; their existing independent gates remain in force.
- External PSS uses sequential `dumpsys meminfo <exact-pid>` polls. The requested
  inter-poll delay is 100 ms, but command execution time means it is not an exact
  10 Hz sampling guarantee.
- R5.2 benchmark construction and numeric policy definition remain complete.
  The default-enable checklist's performance/resource prerequisite remains open
  because all three cache-hit latency gates fail.
- The supported next step is a separately reviewed cache-hit design or benchmark
  contract change with security invariants preserved, followed by a new
  immutable campaign. The current provider-authenticated semantic-key rule must
  not be bypassed merely to meet the latency number, and the pre-registered
  threshold must not be widened post hoc.
