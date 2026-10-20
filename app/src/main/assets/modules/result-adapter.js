// Since docs/augment/ROADMAP.md P2.5, ResultAdapter is implemented in Kotlin (runtime.api.modules.bootstrap.ResultAdapterFunction)
// and defined as the global `ResultAdapter` before any script runs.
// This file only keeps `require('result-adapter')` working (documented in api/modules.md and d.ts) and returns the same object.
module.exports = ResultAdapter;
