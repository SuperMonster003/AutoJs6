// Per-call overhead of a Kotlin module function versus Rhino's own Java bridge and a plain JS function.
// device.isScreenPortrait() is a function of the script module layer (until P5.2 the old layer registered it through
// Augmentable: LambdaFunction -> Method.invoke -> toJsValue);
// Object.getPrototypeOf(device) is the wrapped org.autojs.autojs.runtime.api.Device, so the same Java method
// is reached through NativeJavaMethod. The error path calls device.vibrate() with no arguments 1000 times.
let N = 200000;
let proto = Object.getPrototypeOf(device);
let jsFn = function () { return true; };

function bench(label, fn) {
    fn(); // warm up once
    let t0 = java.lang.System.nanoTime();
    for (let i = 0; i < N; i++) fn();
    let ms = (java.lang.System.nanoTime() - t0) / 1e6;
    console.log("AJ6BENCH " + label + " " + N + " calls: " + ms.toFixed(1) + " ms, " + (ms * 1e6 / N).toFixed(0) + " ns/call");
}

for (let round = 0; round < 2; round++) {
    bench("js-function", () => jsFn());
    bench("java-proto-method", () => proto.isScreenPortrait());
    bench("augment-function", () => device.isScreenPortrait());
    bench("augment-getter(device.width)", () => device.width);
    bench("augment-global(isNullish)", () => isNullish(null));
    bench("rhino-native(Math.max)", () => Math.max(1, 2));
}

let E = 1000;
let t0 = java.lang.System.nanoTime();
let caught = 0;
for (let i = 0; i < E; i++) {
    try { device.vibrate(); } catch (e) { caught++; }
}
let ms = (java.lang.System.nanoTime() - t0) / 1e6;
console.log("AJ6BENCH error-path " + E + " failing calls: " + ms.toFixed(1) + " ms, " + (ms * 1e3 / E).toFixed(0) + " us/call, caught=" + caught);
