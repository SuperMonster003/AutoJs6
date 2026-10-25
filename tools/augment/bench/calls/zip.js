// Calls of the modules of roadmap P3.5, first group (zip, sqlite, crypto), appended to bench/calls_prelude.js by
// device_bench.py --calls zip. They reach the modules of the script module layer (with its switch off they reached the
// old augment ones until P5.2). "ok" cases create no file (a Zip4j handle is opened lazily, crypto works in memory); the
// "fail" cases fail with an argument error before any file is touched, compared with the "ref" cases of the prelude.
time("ok zip.open", () => { try { zip.open("aj6-bench.zip"); } catch (e) { } });
time("ok zip", () => { try { zip("aj6-bench.zip", undefined); } catch (e) { } });
time("ok crypto.digest", () => { try { crypto.digest("abc"); } catch (e) { } });
time("fail zip-arity", () => { try { zip(); } catch (e) { } });
time("fail zip.open-type", () => { try { zip.open("a.zip", 5); } catch (e) { } });
time("fail zip-range", () => { try { zip(""); } catch (e) { } });
time("fail zip.zipFiles-type", () => { try { zip.zipFiles("a.txt", null); } catch (e) { } });
time("fail sqlite-arity", () => { try { sqlite(); } catch (e) { } });
time("fail sqlite-type", () => { try { sqlite("a.db", 5); } catch (e) { } });
time("fail sqlite-range", () => { try { sqlite(""); } catch (e) { } });
