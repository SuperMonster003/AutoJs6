var ready = new java.util.concurrent.CountDownLatch(1);
var count = threads.atomic();
var worker = threads.start(function () {
    var timer = setInterval(function () {
        if (count.incrementAndGet() === 3) { clearInterval(timer); ready.countDown(); }
    }, 30);
});
runtime.putProperty("sdk37.worker", worker);
if (!ready.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new Error("Worker timer did not run");
worker.join(3000);
if (worker.isAlive()) throw new Error("Worker timer did not become idle");
var sleeping = new java.util.concurrent.CountDownLatch(1);
worker = threads.start(function () { sleeping.countDown(); sleep(60000); });
runtime.putProperty("sdk37.worker", worker);
if (!sleeping.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new Error("Sleeper did not start");
threads.shutDownAll();
worker.join(3000);
if (worker.isAlive()) throw new Error("Shutdown did not stop sleep");
runtime.putProperty("sdk37.completed", true);
