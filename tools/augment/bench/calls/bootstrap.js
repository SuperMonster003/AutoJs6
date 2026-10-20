// Calls touched by roadmap P2.5 (startup modules moved to Kotlin, polyfills installed on first access), appended to
// bench/calls_prelude.js by device_bench.py --calls bootstrap. The array cases run before any observe name is read:
// before P2.5 every script had Array.prototype.push / pop / shift / unshift / splice wrapped by array-observe, since
// P2.5 they are Rhino's own until a script reads Object.observe / Array.observe (the "observed" cases read it first).
var benchArray = [];
time("ok array-push-pop", () => { try { benchArray.push(1); benchArray.pop(); } catch (e) { } });
time("ok array-splice", () => { try { benchArray.splice(0, 0, 1); benchArray.splice(0, 1); } catch (e) { } });
time("ok __exitIfError__", () => { try { __exitIfError__(() => 1); } catch (e) { } });
time("ok structuredClone", () => { try { structuredClone({ a: [1, 2], b: "x" }); } catch (e) { } });
time("ok ResultAdapter.promise", () => { try { ResultAdapter.promise(new org.autojs.autojs.runtime.api.ScriptPromiseAdapter()); } catch (e) { } });
var benchObserve = typeof Object.observe;
time("ok array-push-pop-observed", () => { try { benchArray.push(1); benchArray.pop(); } catch (e) { } });
