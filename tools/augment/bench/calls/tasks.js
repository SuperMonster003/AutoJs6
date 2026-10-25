// Calls of the modules of roadmap P3.4, third group (settings, sysprops, powerManager, tasks, workManager), appended to
// bench/calls_prelude.js by device_bench.py --calls tasks. They reach the modules of the script module layer (with its
// switch off they reached the old augment ones until P5.2). "ok" cases only read; the "fail" cases fail with an argument
// error, compared with the "ref" cases of the prelude (Rhino's own errors). No case writes: the old settings.setEnabled
// (switch off, until P5.2) stored any truthy value, so its case omits the value (an arity error in both layers).
time("ok tasks.timeFlagToDays", () => { try { tasks.timeFlagToDays(5); } catch (e) { } });
time("ok tasks.daysToTimeFlag", () => { try { tasks.daysToTimeFlag([0, 2]); } catch (e) { } });
time("ok sysprops.getInt-default", () => { try { sysprops.getInt("aj6.no.such.prop", 1); } catch (e) { } });
time("ok settings.isEnabled", () => { try { settings.isEnabled("stable_mode"); } catch (e) { } });
time("ok powerManager.isIgnoring", () => { try { powerManager.isIgnoringBatteryOptimizations(); } catch (e) { } });
time("fail tasks.getTimedTask-type", () => { try { tasks.getTimedTask("x"); } catch (e) { } });
time("fail tasks.daysToTimeFlag-type", () => { try { tasks.daysToTimeFlag([1, "x"]); } catch (e) { } });
time("fail workManager.addDailyTask-type", () => { try { workManager.addDailyTask(1); } catch (e) { } });
time("fail settings.setEnabled-arity", () => { try { settings.setEnabled("stable_mode"); } catch (e) { } });
time("fail sysprops.getInt-type", () => { try { sysprops.getInt("a", "x"); } catch (e) { } });
