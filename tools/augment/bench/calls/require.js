// Calls of require (roadmap P3.7), appended to bench/calls_prelude.js by device_r2.py --calls require. With the switch
// of the new script module layer on they reach the Kotlin require, with it off jvm-npm. The modules are written to
// ./aj6req and removed at the end. "ok" cases succeed: a cache hit, a JSON cache hit, a first load after deleting the
// cache entry, a core name and the asset module axios; "fail" cases are argument errors, compared with the "ref" cases;
// "miss" cases are modules not found from a module six directories below ./aj6req, compared by hand with the same case
// with the switch off (appendix C Q84).
let reqRoot = files.join(files.cwd(), 'aj6req');
files.removeDir(reqRoot);
files.createWithDirs(files.join(reqRoot, 'small.js'));
files.write(files.join(reqRoot, 'small.js'), 'module.exports = { name: "small" };');
files.write(files.join(reqRoot, 'data.json'), '{"x": 1}');
let reqDeep = files.join(reqRoot, 'd1/d2/d3/d4/d5/d6/miss.js');
files.createWithDirs(reqDeep);
files.write(reqDeep, "module.exports = { relative: () => require('./nope'), bare: () => require('aj6-nope-pkg') };");
let reqSmall = require.resolve('./aj6req/small');
let reqMiss = require('./aj6req/d1/d2/d3/d4/d5/d6/miss');
require('./aj6req/data.json');
time("ok require cached", () => { try { require('./aj6req/small'); } catch (e) { } });
time("ok require json cached", () => { try { require('./aj6req/data.json'); } catch (e) { } });
time("ok require fresh", () => { try { delete require.cache[reqSmall]; require('./aj6req/small'); } catch (e) { } });
time("ok require path", () => { try { require('path'); } catch (e) { } });
time("ok require axios", () => { try { require('axios'); } catch (e) { } });
time("fail require(1)", () => { try { require(1); } catch (e) { } });
time("fail require('')", () => { try { require(''); } catch (e) { } });
time("miss require ./nope", () => { try { reqMiss.relative(); } catch (e) { } });
time("miss require bare", () => { try { reqMiss.bare(); } catch (e) { } });
files.removeDir(reqRoot);
