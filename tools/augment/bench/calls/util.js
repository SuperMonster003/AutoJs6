// Module calls of util (roadmap P2.3), appended to bench/calls_prelude.js by device_bench.py --calls util. With the
// switch of the new script module layer on they reach the new module, with it off the old augment one. "ok" cases
// succeed; the other cases fail with an argument error (arity, type, range), compared with the "ref" cases of the
// prelude (Rhino's own errors). The "big" case inspects 100 objects with nested values, printed alike by both
// implementations; the "huge" case 2000 of them, which the new module prints in full as documented (maxArrayItems 10000)
// while the old one printed the first 100 and a summary (a few iterations each, see LIMIT_MS).
let utilBig = [];
for (let i = 0; i < 100; i++) utilBig.push({ id: i, name: ["item", i].join(" "), tags: ["x", "y"], nested: { ok: i % 2 === 0, list: [i, i + 1] } });
let utilHuge = [];
for (let i = 0; i < 2000; i++) utilHuge.push({ id: i, name: ["item", i].join(" "), tags: ["x", "y"], nested: { ok: i % 2 === 0, list: [i, i + 1] } });
time("ok util.isString", () => { try { util.isString("x"); } catch (e) { } });
time("ok util.format", () => { try { util.format("%s: %d", "count", 3); } catch (e) { } });
time("ok util.inspect-small", () => { try { util.inspect({ a: 1, b: [1, 2], c: "x" }); } catch (e) { } });
time("ok util.checkStringArgument", () => { try { util.checkStringArgument("abc", "a.c"); } catch (e) { } });
time("ok util.java.array", () => { try { util.java.array("int", 4); } catch (e) { } });
time("ok util.versionCodes.search", () => { try { util.versionCodes.search(33); } catch (e) { } });
time("ok util.morseCode.getCode", () => { try { util.morseCode.getCode("SOS"); } catch (e) { } });
N = 20;
MIN = 3;
W = 3;
time("ok util.inspect-big", () => { try { util.inspect(utilBig); } catch (e) { } });
time("ok util.inspect-huge", () => { try { util.inspect(utilHuge); } catch (e) { } });
N = 2000;
MIN = 100;
W = 200;
time("fail util.isString-arity", () => { try { util.isString(); } catch (e) { } });
time("fail util.ensureType-type", () => { try { util.ensureType(1, "string"); } catch (e) { } });
time("fail util.ensureType-range", () => { try { util.ensureType(1, "nonsense"); } catch (e) { } });
time("fail util.inspect-options", () => { try { util.inspect(1, "options"); } catch (e) { } });
