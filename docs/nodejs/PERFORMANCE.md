# AutoJs6 Node.js Performance

Last reviewed: 2026-08-21.

Performance is measured end to end across two processes. The runtime plugin owns
Node/V8 startup, isolate/environment reuse, module loading, and native memory. The
host owns discovery/trust caches, Binder sessions, workspace archive transfer,
module-source callbacks, and bridge dispatch.

## Measurement Rules

- Record host/plugin revisions, ABI, Android version, thermal state, and plugin state.
- Separate first discovery/bind, runtime-cold, runtime-warm, and script execution time.
- Use the same workspace and capability manifest across comparisons.
- Report median and tail latency; do not compare a skipped test.
- Correlate host and plugin execution IDs.
- Measure memory in the process that owns it; host native heap is not plugin native
  heap.

**NodeRuntimePluginR5InstrumentationTest** and
**NodeR6PersistentPluginSoakInstrumentationTest** are the retained end-to-end anchors.
Plugin-native microbenchmarks and symbol-level profiling belong in the plugin
repository. There is no host externalNativeBuild or native dependency report for Node.
