// Calls of the modules of roadmap P3.4, second group (app, autojs, device, files, storages), appended to
// bench/calls_prelude.js by device_bench.py --calls app. They reach the modules of the script module layer (with its
// switch off they reached the old augment ones until P5.2). "ok" cases succeed without side effects; the "fail" cases
// fail with an argument error, compared with the "ref" cases of the prelude (Rhino's own errors).
time("ok app.getPackageName-null", () => { try { app.getPackageName(null); } catch (e) { } });
time("ok app.parseUri", () => { try { app.parseUri("https://example.com/a"); } catch (e) { } });
time("ok autojs.version.isAtLeast", () => { try { autojs.version.isAtLeast("6.0.0"); } catch (e) { } });
time("ok autojs.getLanguageTag", () => { try { autojs.getLanguageTag(); } catch (e) { } });
time("ok device.width", () => { try { device.width; } catch (e) { } });
time("ok device.isScreenPortrait", () => { try { device.isScreenPortrait(); } catch (e) { } });
time("ok files.join", () => { try { files.join("a", "b"); } catch (e) { } });
time("ok files.path", () => { try { files.path("a.txt"); } catch (e) { } });
time("fail app.intent-type", () => { try { app.intent(1); } catch (e) { } });
time("fail startActivity-type", () => { try { startActivity(1); } catch (e) { } });
time("fail autojs.setRootMode-type", () => { try { autojs.setRootMode({}); } catch (e) { } });
time("fail device.vibrate-type", () => { try { device.vibrate({}); } catch (e) { } });
time("fail files.open-arity", () => { try { files.open(); } catch (e) { } });
time("fail storages.create-type", () => { try { storages.create(null); } catch (e) { } });
