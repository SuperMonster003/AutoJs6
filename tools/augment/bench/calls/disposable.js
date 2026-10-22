// Calls of the object of threads.disposable() (docs/augment/ROADMAP.md appendix C Q52), appended to bench/calls_prelude.js
// by device_bench.py --calls disposable. The object is of the same class with the switch of the new script module layer on
// or off; only threads.disposable itself reaches the new module or the current augment one. The "ok" waits return at once
// because the value is set before them (an unbounded one never returned before the Q52 fix, so this group needs a fixed
// host); the "fail" cases fail with an argument error, compared with the "ref" cases of the prelude.
let benchDisposable = threads.disposable();
benchDisposable.setAndNotify(1);
time("ok disposable-create-set-get", () => { try { let d = threads.disposable(); d.setAndNotify(1); d.blockedGet(); } catch (e) { } });
time("ok disposable.blockedGet", () => { try { benchDisposable.blockedGet(); } catch (e) { } });
time("ok disposable.blockedGet-timed", () => { try { benchDisposable.blockedGet(1000); } catch (e) { } });
time("ok disposable.blockedGetOrThrow", () => { try { benchDisposable.blockedGetOrThrow(java.lang.IllegalStateException, 1000, 0); } catch (e) { } });
time("fail threads.disposable-arity", () => { try { threads.disposable(1); } catch (e) { } });
time("fail disposable.blockedGet-arity", () => { try { benchDisposable.blockedGet(1, 2); } catch (e) { } });
