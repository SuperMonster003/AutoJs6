# Agent P7 performance fixture

Host APK 6.8.0 / 5296 with production Agent 1.0.0 / 65 on the private API 37.1 / 16 KiB AVD. This host change adds instrumentation and reproduction documentation only; it does not change host runtime behavior, public APIs, dependencies or Rhino sources.

The repeatable command and measurement boundaries are in `test-apps/ai-agent-conformance/README.md`, section "Production-plugin performance baseline". The fixture force-stops the Agent, binds its real service and uses the real host capability broker. It updates every cell in a 200-control grid before each dump and verifies both the 200-node raw result and current-frame text in the next model request. Its deterministic model reports zero usage and does not call a real Provider.

Final run: 1/1 passed, 16.064 s. One first-use sample and four additional warmups precede 30 measured calls in a single task.

| Interval, ms | Mean | p50 | p95 | Measured maximum | First use |
| --- | ---: | ---: | ---: | ---: | ---: |
| Plugin decision to next model request | 188.489 | 189.438 | 218.337 | 220.059 | 208.409 |
| Host capability dispatch | 91.679 | 93.876 | 114.458 | 116.738 | 111.301 |

The original P7 300 ms baseline is checked at warmed p95. Timing includes real Binder and accessibility work and plugin context preparation; fixture mutation, pacing and memory probes occur outside the interval. Hardware, scheduling and cold-start effects are not hard real-time bounded. The initial one-cell-changing fixture had p95 142.133 ms and first use 309.723 ms; those results are retained rather than treating that easier workload as final acceptance.

Plugin `:agent` memory: baseline PSS 28081 KiB, final PSS 35439 KiB, sampled peak PSS 37286 KiB (36.412 MiB); baseline RSS 135964 KiB, final RSS 145736 KiB, kernel process peak RSS 151340 KiB (147.793 MiB). Ten probes include the baseline, every fifth model boundary and final completion. PID stability is asserted. PSS sampling can miss short peaks; RSS high-water includes initialization and loaded history. Host shared-process memory and actual Provider/model allocations are not measured by this fixture.

The plugin-side evidence is `docs/dev/p7-performance-evidence-2026-09-25.md` in the Agent repository. It records JVM compile/parse/validation and full-capacity per-record storage measurements, the context-packing optimization, before/after values and correctness regressions.

Host instrumentation build and Android-test-source lint analysis pass. Whole-host lint remains incomplete from earlier P7 work; this test does not close full conformance, six-device compatibility, E4 or P8 gates. Raw numeric logs are local under `build/p7-performance-*`; no private observations, model content or credentials are committed. The AVD's original settings are restored at session end. No real phone, carrier network, order or payment is involved.
