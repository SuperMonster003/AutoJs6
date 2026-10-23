// Calls of the modules of roadmap P3.3 (auto, automator, selector, flow, events, keys), appended to bench/calls_prelude.js
// by device_bench.py --calls automator. With the switch of the new script module layer on they reach the new modules, with
// it off the current augment modules. "ok" cases neither touch the screen nor wait for a node or the accessibility service
// (reads of a constant and the service state, new selectors); the "fail" cases fail with an argument error before any
// action in both states, compared with the "ref" cases of the prelude. Global selector functions whose arguments match no
// overload are left out: the current module warns twice in the console for each of them.
time("ok keys.home", () => { try { u = keys.home; } catch (e) { } });
time("ok auto.isRunning", () => { try { auto.isRunning(); } catch (e) { } });
time("ok selector", () => { try { selector(); } catch (e) { } });
time("ok text", () => { try { text("a"); } catch (e) { } });
time("ok events.emitter", () => { try { events.emitter(); } catch (e) { } });
time("fail click-arity", () => { try { click(); } catch (e) { } });
time("fail press-type", () => { try { press("a"); } catch (e) { } });
time("fail gesture-arity", () => { try { gesture(); } catch (e) { } });
time("fail auto.setMode-type", () => { try { auto.setMode(1); } catch (e) { } });
time("fail auto.setMode-range", () => { try { auto.setMode("x"); } catch (e) { } });
time("fail flow.wait-arity", () => { try { flow.wait(); } catch (e) { } });
time("fail pickup-type", () => { try { pickup("a", 1, "w", null); } catch (e) { } });
time("fail detect-arity", () => { try { detect(); } catch (e) { } });
time("fail events.__asEmitter__-type", () => { try { events.__asEmitter__(1); } catch (e) { } });
