// Prelude of the module call benchmarks (device_bench.py --calls NAME): device_bench.py pushes this file followed by
// bench/calls/NAME.js as one script. Every time(label, fn) case runs W warm-up iterations, then up to N timed ones (at
// least MIN, stopping after LIMIT_MS), and prints microseconds per iteration, as bench/error_path.js does. A calls file
// measures the members of one module group (success and failing calls) next to the Rhino reference cases below, so that
// each P2 / P3 session adds its own file instead of editing a shared one.
let N = 2000;
let MIN = 100;
let W = 200;
let LIMIT_MS = 3000;

function time(label, fn) {
    let warm = java.lang.System.nanoTime();
    for (let i = 0; i < W && (i < 20 || java.lang.System.nanoTime() - warm < LIMIT_MS * 1e6 / 3); i++) fn();
    let t0 = java.lang.System.nanoTime();
    let n = 0;
    while (n < N && (n < MIN || java.lang.System.nanoTime() - t0 < LIMIT_MS * 1e6)) {
        fn();
        n++;
    }
    let us = (java.lang.System.nanoTime() - t0) / 1e3 / n;
    console.log("AJ6BENCH call " + label + " " + us.toFixed(1) + " us");
}

let u;
time("ref empty-try", () => { try { u = undefined; } catch (e) { } });
time("ref rhino-undefined-property", () => { try { u.x; } catch (e) { } });
time("ref rhino-java-exception", () => { try { java.lang.Integer.parseInt("x"); } catch (e) { } });
time("ref rhino-math-max", () => { try { Math.max(1, 5, 3); } catch (e) { } });
