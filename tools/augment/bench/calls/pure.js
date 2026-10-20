// Module calls of the pure logic modules of roadmap P2.2 (s13n, cvt, fmt, mime, nanoid, species, isNullish, base64),
// appended to bench/calls_prelude.js by device_bench.py --calls pure. With the switch of the new script module layer on
// they reach the new modules, with it off the old augment ones. "ok" cases succeed; the other cases fail with an argument
// error (arity, type, range), compared with the "ref" cases of the prelude (Rhino's own errors).
time("ok isNullish", () => { try { isNullish(null); } catch (e) { } });
time("ok species", () => { try { species([]); } catch (e) { } });
time("ok species.isString", () => { try { species.isString("a"); } catch (e) { } });
time("ok s13n.time", () => { try { s13n.time(2, "seconds"); } catch (e) { } });
time("ok s13n.point", () => { try { s13n.point(10, 20); } catch (e) { } });
time("ok s13n.throwable", () => { try { s13n.throwable("boom"); } catch (e) { } });
time("ok s13n.bytes", () => { try { s13n.bytes("1.5 KiB"); } catch (e) { } });
time("ok cvt.bytes", () => { try { cvt.bytes(1536, "KB"); } catch (e) { } });
time("ok fmt.bytes", () => { try { fmt.bytes(1536); } catch (e) { } });
time("ok mime", () => { try { mime("text/html; charset=UTF-8"); } catch (e) { } });
time("ok nanoid", () => { try { nanoid(); } catch (e) { } });
time("ok base64.encode", () => { try { base64.encode("hello"); } catch (e) { } });
time("fail species-arity", () => { try { species(); } catch (e) { } });
time("fail s13n.time-type", () => { try { s13n.time(null); } catch (e) { } });
time("fail s13n.point-type", () => { try { s13n.point("1"); } catch (e) { } });
time("fail cvt.bytes-range", () => { try { cvt.bytes(-1); } catch (e) { } });
time("fail fmt.bytes-arity", () => { try { fmt.bytes(); } catch (e) { } });
time("fail mime-type", () => { try { mime(123); } catch (e) { } });
time("fail nanoid-range", () => { try { nanoid(-1); } catch (e) { } });
