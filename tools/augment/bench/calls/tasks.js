// Calls of the modules of roadmap P3.4, third group (settings, sysprops, powerManager, tasks, workManager), appended to
// bench/calls_prelude.js by device_bench.py --calls tasks. With the switch of the new script module layer on they reach
// the new modules, with it off the current augment modules. "ok" cases only read; the "fail" cases fail with an argument
// error, compared with the "ref" cases of the prelude (Rhino's own errors).
time("ok tasks.timeFlagToDays", () => { try { tasks.timeFlagToDays(5); } catch (e) { } });
time("ok tasks.daysToTimeFlag", () => { try { tasks.daysToTimeFlag([0, 2]); } catch (e) { } });
time("ok sysprops.getInt-default", () => { try { sysprops.getInt("aj6.no.such.prop", 1); } catch (e) { } });
time("ok settings.isEnabled", () => { try { settings.isEnabled("stable_mode"); } catch (e) { } });
time("ok powerManager.isIgnoring", () => { try { powerManager.isIgnoringBatteryOptimizations(); } catch (e) { } });
time("fail tasks.getTimedTask-type", () => { try { tasks.getTimedTask("x"); } catch (e) { } });
time("fail tasks.daysToTimeFlag-type", () => { try { tasks.daysToTimeFlag([1, "x"]); } catch (e) { } });
time("fail workManager.addDailyTask-type", () => { try { workManager.addDailyTask(1); } catch (e) { } });
time("fail settings.setEnabled-type", () => { try { settings.setEnabled("stable_mode", 1); } catch (e) { } });
time("fail sysprops.getInt-type", () => { try { sysprops.getInt("a", "x"); } catch (e) { } });
