// Module calls of Arrayx / Numberx / Mathx (roadmap P2.1), appended to bench/calls_prelude.js by
// device_bench.py --calls jsox. With the switch of the new script module layer on they reach the new modules, with it
// off the old augment ones. "ok" cases succeed; the other cases fail with an argument error (arity, type, range) or a
// RangeError of the implementation, compared with the "ref" cases of the prelude (Rhino's own errors).
time("ok Mathx.sum", () => { try { Mathx.sum([1, 2, 3]); } catch (e) { } });
time("ok Mathx.sum-fraction", () => { try { Mathx.sum([1.111, 2.222], 1); } catch (e) { } });
time("ok Mathx.randInt", () => { try { Mathx.randInt(1, 10); } catch (e) { } });
time("ok Numberx.clamp", () => { try { Numberx.clamp(5, 1, 3); } catch (e) { } });
time("ok Numberx.parsePercent", () => { try { Numberx.parsePercent("50%"); } catch (e) { } });
time("ok Arrayx.distinct", () => { try { Arrayx.distinct([1, 1, 2]); } catch (e) { } });
time("ok Arrayx.sorted", () => { try { Arrayx.sorted([3, 1, 2]); } catch (e) { } });
time("fail Numberx.clamp-arity", () => { try { Numberx.clamp(); } catch (e) { } });
time("fail Arrayx.distinct-type", () => { try { Arrayx.distinct(1); } catch (e) { } });
time("fail Mathx.randInt-type", () => { try { Mathx.randInt("x"); } catch (e) { } });
time("fail Numberx.toFixedNum-range", () => { try { Numberx.toFixedNum(1, 101); } catch (e) { } });
