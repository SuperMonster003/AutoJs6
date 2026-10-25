// Calls of the global functions of roadmap P3.0 (global and legacy), appended to bench/calls_prelude.js by
// device_bench.py --calls global. They reach the modules of the script module layer (with its switch off they reached
// the old augment ones until P5.2). "ok" cases succeed; the "fail" cases fail with an argument error (arity, type,
// range) or, for TODO, a Java exception, compared with the "ref" cases of the prelude (Rhino's own errors).
let benchTrue = () => true;
time("ok isInteger", () => { try { isInteger(1); } catch (e) { } });
time("ok isPrimitive", () => { try { isPrimitive("a"); } catch (e) { } });
time("ok isObjectSpecies", () => { try { isObjectSpecies({}); } catch (e) { } });
time("ok random", () => { try { random(); } catch (e) { } });
time("ok random-range", () => { try { random(1, 10); } catch (e) { } });
time("ok isStopped", () => { try { isStopped(); } catch (e) { } });
time("ok getScaleBaseX", () => { try { getScaleBaseX(); } catch (e) { } });
time("ok cX", () => { try { cX(0.5); } catch (e) { } });
time("ok wait-once", () => { try { wait(benchTrue, 1); } catch (e) { } });
time("ok requiresAutojsVersion", () => { try { requiresAutojsVersion(461); } catch (e) { } });
time("ok Intent", () => { try { Intent; } catch (e) { } });
time("fail isInteger-arity", () => { try { isInteger(); } catch (e) { } });
time("fail random-arity", () => { try { random(1, 2, 3); } catch (e) { } });
time("fail wait-range", () => { try { wait(benchTrue, -1); } catch (e) { } });
time("fail setScaleBaseX-type", () => { try { setScaleBaseX("a"); } catch (e) { } });
time("fail sleep-type", () => { try { sleep(null); } catch (e) { } });
time("fail TODO-java", () => { try { TODO(); } catch (e) { } });
