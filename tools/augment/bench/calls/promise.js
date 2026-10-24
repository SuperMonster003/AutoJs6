// Calls of the global Promise (roadmap P4.2 / appendix C Q95), appended to bench/calls_prelude.js by device_r2.py
// --calls promise. With the switch of the new script module layer on the global Promise is PromiseConstructor, with it
// off assets/modules/promise.js. The "ok" cases time the synchronous part of a call (then on a settled promise posts its
// reaction to the timer, run later by the looper or by the wait() of a later case); the "fail" cases throw the TypeError
// of the constructor, compared with the "ref" cases of the prelude.
let noop = function () { };
let settled = Promise.resolve(1);
time("ok new-Promise", () => { try { new Promise(noop); } catch (e) { } });
time("ok Promise.resolve", () => { try { Promise.resolve(1); } catch (e) { } });
time("ok then-pending", () => { try { new Promise(noop).then(noop); } catch (e) { } });
time("ok then-settled", () => { try { settled.then(noop); } catch (e) { } });
time("ok Promise.all-3", () => { try { Promise.all([1, settled, 3]); } catch (e) { } });
time("ok wait-settled", () => { try { settled.wait(); } catch (e) { } });
time("fail Promise-not-function", () => { try { new Promise(1); } catch (e) { } });
time("fail Promise-without-new", () => { try { Promise(noop); } catch (e) { } });
