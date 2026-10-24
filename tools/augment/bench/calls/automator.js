// Calls of the modules of roadmap P3.3 (auto, automator, selector, flow, events, keys), appended to bench/calls_prelude.js
// by device_bench.py --calls automator. With the switch of the new script module layer on they reach the new modules, with
// it off the current augment modules. "ok" cases neither touch the screen nor wait for a node or the accessibility service
// (reads of a constant and the service state, new selectors); the "fail" cases fail with an argument error before any
// action in both states, compared with the "ref" cases of the prelude. Global selector functions whose arguments match no
// overload are left out: the current module warns twice in the console for each of them. The option fields (appendix C
// Q65 (2)) are read by the current parsers before any wait or action in both states; targets never match a node. The
// options objects of the "fail" cases are created once, outside the timed calls: an object literal costs about 10 us on
// XQ-AT72 (P3.3), which the Rhino reference `undefined.x` does not have.
let benchFlowTimeout = flow.defaults().timeout;
let benchUnknownKey = { nope: 1 }, benchUnknownCompare = { compare: "nope" };
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
time("ok flow.defaults-options", () => { try { flow.defaults({ timeout: benchFlowTimeout }); } catch (e) { } });
time("fail flow.defaults-key", () => { try { flow.defaults(benchUnknownKey); } catch (e) { } });
time("fail flow.wait-option", () => { try { flow.wait("aj6-none", benchUnknownCompare); } catch (e) { } });
time("fail smartClick-option", () => { try { smartClick("aj6-none", benchUnknownKey); } catch (e) { } });
time("fail waitForEvent-type", () => { try { waitForEvent("nope"); } catch (e) { } });
// Appendix C Q69 (2): selector forms the new layer refuses before any search, without a Java exception. With the switch
// off the current module refuses them only once a node is found (or after console warnings), so they are timed with the
// switch on only (the "off" columns show "-"). "ok textMatches" compiles its pattern in both states.
let benchNewLayer = String(org.autojs.autojs.core.pref.keys.DeveloperKeys.NEW_SCRIPT_MODULE_LAYER.get()) === "true";
let benchDepthNaN = { depth: NaN }, benchPattern = { textMatches: "[" }, benchChildNaN = ["child", NaN], benchRange = { textMatches: "[z-a]" };
time("ok textMatches", () => { try { textMatches("a"); } catch (e) { } });
if (benchNewLayer) {
    time("fail pickup-result-type", () => { try { pickup("aj6-none", "nope"); } catch (e) { } });
    time("fail pickup-result-argument", () => { try { pickup("aj6-none", benchChildNaN); } catch (e) { } });
    time("fail pickup-compass", () => { try { pickup("aj6-none", "c"); } catch (e) { } });
    time("fail detect-compass", () => { try { detect(null, "c", "w"); } catch (e) { } });
    time("fail pickup-condition-key", () => { try { pickup(benchUnknownKey); } catch (e) { } });
    time("fail pickup-condition-value", () => { try { pickup(benchDepthNaN); } catch (e) { } });
    time("fail pickup-condition-pattern", () => { try { pickup(benchPattern); } catch (e) { } });
    time("fail depth-value", () => { try { depth(NaN); } catch (e) { } });
    time("fail textMatches-pattern", () => { try { textMatches("["); } catch (e) { } });
    // Appendix C Q70 (2): patterns the full syntax check refuses (before Q70 they were compiled by the core, about 2x).
    time("fail pickup-condition-range", () => { try { pickup(benchRange); } catch (e) { } });
    time("fail textMatches-interval", () => { try { textMatches("a{2,1}"); } catch (e) { } });
    time("fail textMatches-property", () => { try { textMatches("\\p{NoSuch}"); } catch (e) { } });
}
