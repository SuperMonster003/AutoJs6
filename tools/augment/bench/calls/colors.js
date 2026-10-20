// Module calls of colors (roadmap P2.3), appended to bench/calls_prelude.js by device_bench.py --calls colors. With the
// switch of the new script module layer on they reach the new module, with it off the old augment one. "ok" cases
// succeed; the other cases fail with an argument error (arity, type, range) or a RangeError of the implementation,
// compared with the "ref" cases of the prelude (Rhino's own errors).
time("ok colors.toInt-hex", () => { try { colors.toInt("#663399"); } catch (e) { } });
time("ok colors.toInt-name", () => { try { colors.toInt("burnt-orange"); } catch (e) { } });
time("ok colors.toHex", () => { try { colors.toHex(-3386112, 8); } catch (e) { } });
time("ok colors.red", () => { try { colors.red("#663399"); } catch (e) { } });
time("ok colors.rgb", () => { try { colors.rgb(255, 128, 9); } catch (e) { } });
time("ok colors.rgba-hex", () => { try { colors.rgba("#11335577"); } catch (e) { } });
time("ok colors.hsv", () => { try { colors.hsv(90, 0.8, 0.64); } catch (e) { } });
time("ok colors.toHsv", () => { try { colors.toHsv("#663399"); } catch (e) { } });
time("ok colors.setAlpha", () => { try { colors.setAlpha("#663399", 0.5); } catch (e) { } });
time("ok colors.isSimilar", () => { try { colors.isSimilar("orange", "dark-orange", 9); } catch (e) { } });
time("ok colors.luminance", () => { try { colors.luminance("#663399"); } catch (e) { } });
time("ok colors.toOklch", () => { try { colors.toOklch("#2196F3"); } catch (e) { } });
time("fail colors.toInt-arity", () => { try { colors.toInt(); } catch (e) { } });
time("fail colors.toInt-type", () => { try { colors.toInt({}); } catch (e) { } });
time("fail colors.toInt-range", () => { try { colors.toInt("nope"); } catch (e) { } });
time("fail colors.rgb-range", () => { try { colors.rgb(-1, 0, 0); } catch (e) { } });
