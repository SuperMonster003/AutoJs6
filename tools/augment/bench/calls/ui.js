// Calls of the modules of roadmap P3.2 (ui, dialogs, console, notice, toast, floaty), appended to bench/calls_prelude.js
// by device_bench.py --calls ui. With the switch of the new script module layer on they reach the new modules, with it
// off the current augment modules. Nothing is shown, posted or written: the "ok" cases only read state or post an empty
// action to the UI thread, and every "fail" case is one the old modules also reject or ignore without showing anything
// (old toast / notice / floaty calls with wrong arguments may still show a toast, post a notification or open a
// settings page, so those are left out); they are compared with the "ref" cases of the prelude.
let benchAction = () => { };
time("ok ui.isUiThread", () => { try { ui.isUiThread(); } catch (e) { } });
time("ok ui.getStatusBarHeight", () => { try { ui.getStatusBarHeight(); } catch (e) { } });
time("ok ui.statusBarHeight", () => { try { ui.statusBarHeight; } catch (e) { } });
time("ok ui.post", () => { try { ui.post(benchAction); } catch (e) { } });
time("ok console.isShowing", () => { try { console.isShowing(); } catch (e) { } });
time("ok notice.isEnabled", () => { try { notice.isEnabled(); } catch (e) { } });
time("ok toast.dismissAll", () => { try { toast.dismissAll(); } catch (e) { } });
time("ok floaty.hasPermission", () => { try { floaty.hasPermission(); } catch (e) { } });
time("fail ui.run-type", () => { try { ui.run(1); } catch (e) { } });
time("fail ui.post-type", () => { try { ui.post(benchAction, "soon"); } catch (e) { } });
time("fail ui.getStatusBarHeight-type", () => { try { ui.getStatusBarHeight(1); } catch (e) { } });
time("fail dialogs.build-type", () => { try { dialogs.build(1); } catch (e) { } });
time("fail console.build-range", () => { try { console.build({ nope: 1 }); } catch (e) { } });
time("fail notice.cancel-type", () => { try { notice.cancel("x"); } catch (e) { } });
time("fail notice.getBuilder-arity", () => { try { notice.getBuilder(1); } catch (e) { } });
time("fail toast.dismissAll-arity", () => { try { toast.dismissAll(1); } catch (e) { } });
time("fail floaty.hasPermission-arity", () => { try { floaty.hasPermission(1); } catch (e) { } });
