var duration = typeof SDK37_SOAK_MILLIS === "number" ? SDK37_SOAK_MILLIS : 120000;
var started = android.os.SystemClock.elapsedRealtime();
var ticks = 0;
var workerTicks = threads.atomic();
var worker = threads.start(function () { setInterval(function () { workerTicks.incrementAndGet(); }, 20); });
runtime.putProperty("sdk37.worker", worker);
var timer = setInterval(function () { ++ticks; }, 20);
setTimeout(function () {
    clearInterval(timer);
    threads.shutDownAll();
    worker.join(3000);
    var elapsed = android.os.SystemClock.elapsedRealtime() - started;
    if (elapsed < duration || ticks < duration / 200 || workerTicks.get() < duration / 200 || worker.isAlive()) {
        throw new Error("Sustained dispatch or cancellation failed: " + elapsed + "/" + ticks + "/" + workerTicks.get());
    }
    console.log("[SDK37-Looper] soak elapsed=" + elapsed + ", main=" + ticks + ", worker=" + workerTicks.get());
    runtime.putProperty("sdk37.completed", true);
}, duration);
