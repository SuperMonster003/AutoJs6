// Measures how long Rhino (interpreted mode, the host's setting) needs to parse and compile the pre-6.6.0
// JS modules that every script start used to require. Compile only: the module factories are not executed.
let dir = "/sdcard/Download/aj6-augment-bench/obsolete";
let names = files.listDir(dir).filter(n => n.endsWith(".js")).sort();
let lines = 0;
let bytes = 0;
let results = [];
let sources = {};
names.forEach(n => {
    let src = files.read(dir + "/" + n);
    sources[n] = src;
    bytes += src.length;
    lines += src.split("\n").length;
});
for (let pass = 0; pass < 3; pass++) {
    let passTotal = 0;
    names.forEach(n => {
        let t0 = java.lang.System.nanoTime();
        try {
            new Function("require", "module", "exports", sources[n]);
        } catch (e) {
            console.log("AJ6BENCH compile-error " + n + ": " + e);
        }
        let dt = (java.lang.System.nanoTime() - t0) / 1e6;
        passTotal += dt;
        if (pass === 0) results.push([n, dt]);
    });
    console.log("AJ6BENCH pass" + pass + " compile_ms=" + passTotal.toFixed(1));
}
console.log("AJ6BENCH files=" + names.length + " lines=" + lines + " bytes=" + bytes);
results.sort((a, b) => b[1] - a[1]).slice(0, 8).forEach(r => console.log("AJ6BENCH top " + r[0] + " " + r[1].toFixed(1)));
