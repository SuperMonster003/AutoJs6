// Calls of the modules of roadmap P4.1 (ai, opencc, pinyin, pinyin4j, mediainfo, memory, and the plugin modules mail /
// installer / epub / compose that hand their calls to the current augment modules), appended to bench/calls_prelude.js
// by device_bench.py --calls bridged. They reach the modules of the script module layer (with its switch off they reached
// the old augment ones until P5.2). No case reaches a plugin: the "ok" cases are answered by the host, the "fail" cases
// fail with an argument error the new layer checks itself (D10), compared with the "ref" cases of the prelude, and the
// "coded" cases fail with the coded error of the implementation (not a ratio of R2: the old layer ran the same code).
time("ok pinyin.compact", () => { try { pinyin.compact([["a", "b"], ["c"]]); } catch (e) { } });
time("ok pinyin4j.of-empty", () => { try { pinyin4j.of(""); } catch (e) { } });
time("ok memory.retained", () => { try { memory.retained(); } catch (e) { } });
time("ok mail.default", () => { try { u = mail.default; } catch (e) { } });
time("fail opencc-arity", () => { try { opencc(); } catch (e) { } });
time("fail opencc.convert-type", () => { try { opencc.convert("a", 1); } catch (e) { } });
time("fail opencc.convert-range", () => { try { opencc.convert("a", "nope"); } catch (e) { } });
time("fail pinyin-arity", () => { try { pinyin(); } catch (e) { } });
time("fail pinyin.compact-type", () => { try { pinyin.compact(1); } catch (e) { } });
time("fail pinyin.fromCodePoint-range", () => { try { pinyin.fromCodePoint(1e20); } catch (e) { } });
time("fail pinyin-option", () => { try { pinyin("a", { mode: "nope" }); } catch (e) { } });
time("fail pinyin4j.of-option", () => { try { pinyin4j.of("a", { tone: "nope" }); } catch (e) { } });
time("fail mediainfo-range", () => { try { mediainfo(""); } catch (e) { } });
time("fail mediainfo.get-type", () => { try { mediainfo.get("/a", "General", "x", 1); } catch (e) { } });
time("fail memory.collect-type", () => { try { memory.collect("1"); } catch (e) { } });
time("fail memory.collect-range", () => { try { memory.collect(-1); } catch (e) { } });
time("fail memory.watch-type", () => { try { memory.watch(1); } catch (e) { } });
time("fail ai.ask-type", () => { try { ai.ask(1); } catch (e) { } });
time("fail ai.ask-range", () => { try { ai.ask([]); } catch (e) { } });
time("fail ai.session-type", () => { try { ai.session(1); } catch (e) { } });
time("coded mail.connect", () => { try { mail.connect(1); } catch (e) { } });
time("coded installer.install", () => { try { installer.install(); } catch (e) { } });
time("coded epub.open", () => { try { epub.open(); } catch (e) { } });
time("coded compose.state", () => { try { compose.state(); } catch (e) { } });
