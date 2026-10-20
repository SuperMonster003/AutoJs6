// Since docs/augment/ROADMAP.md P2.5, continuation.await / continuation.delay are implemented in Kotlin
// (runtime.api.modules.bootstrap.ContinuationPrototype) as the prototype of the global `continuation` object.
// This file only keeps `require('continuation')` working (documented in api/modules.md) and returns that same object.
module.exports = Object.getPrototypeOf(continuation);
