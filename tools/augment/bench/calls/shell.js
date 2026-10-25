// Calls of the modules of roadmap P3.4, fourth group (shell, shizuku, plugins), appended to bench/calls_prelude.js by
// device_bench.py --calls shell. They reach the modules of the script module layer (with its switch off they reached the
// old augment ones until P5.2). "ok" cases run no command; the "fail" cases fail with an argument error, compared with
// the "ref" cases of the prelude (Rhino's own errors).
time("ok shell.getCommand", () => { try { shell.getCommand("ls", true); } catch (e) { } });
time("ok shizuku.getCommand-array", () => { try { shizuku.getCommand(["a", "b"]); } catch (e) { } });
time("ok shell.isRootAvailable", () => { try { shell.isRootAvailable(); } catch (e) { } });
time("ok plugins.extend", () => { try { plugins.extend; } catch (e) { } });
time("fail shell-type", () => { try { shell(1); } catch (e) { } });
time("fail shell.spawn-type", () => { try { shell.spawn(1); } catch (e) { } });
time("fail shell.checkAccess-range", () => { try { shell.checkAccess("x"); } catch (e) { } });
time("fail Tap-type", () => { try { Tap("a", 1); } catch (e) { } });
time("fail shizuku-type", () => { try { shizuku(null); } catch (e) { } });
time("fail plugins.tools.call-type", () => { try { plugins.tools.call(1); } catch (e) { } });
