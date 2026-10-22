// Calls of the runtime modules of roadmap P3.4, first group (timers, threads, continuation, engines), appended to
// bench/calls_prelude.js by device_bench.py --calls runtime. With the switch of the new script module layer on they reach
// the new modules, with it off the current augment modules. "ok" cases succeed (timers are cleared at once); the "fail"
// cases fail with an argument error, compared with the "ref" cases of the prelude (Rhino's own errors).
let benchNoop = () => undefined;
time("ok setTimeout-clear", () => { try { clearTimeout(setTimeout(benchNoop, 60000)); } catch (e) { } });
time("ok keepAlive-clear", () => { try { clearInterval(keepAlive()); } catch (e) { } });
time("ok sync", () => { try { sync(benchNoop); } catch (e) { } });
time("ok threads.disposable", () => { try { threads.disposable(); } catch (e) { } });
time("ok continuation.enabled", () => { try { continuation.enabled; } catch (e) { } });
time("ok engines.myEngine", () => { try { engines.myEngine(); } catch (e) { } });
time("fail setTimeout-type", () => { try { setTimeout(1); } catch (e) { } });
time("fail clearTimeout-arity", () => { try { clearTimeout(); } catch (e) { } });
time("fail setIntervalExt-type", () => { try { timers.setIntervalExt(benchNoop, "x"); } catch (e) { } });
time("fail threads.pool-range", () => { try { threads.pool({ corePoolSize: -1 }); } catch (e) { } });
time("fail continuation.create-type", () => { try { continuation.create(1); } catch (e) { } });
time("fail engines.execScript-type", () => { try { engines.execScript("a", "b", 1); } catch (e) { } });
