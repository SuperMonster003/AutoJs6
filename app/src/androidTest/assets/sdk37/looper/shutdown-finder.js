auto();
var ready = new java.util.concurrent.CountDownLatch(1);
var worker = threads.start(function () {
    ready.countDown();
    id("org.autojs.autojs6:id/sdk37_nonexistent_cancel_probe").findOne();
    runtime.putProperty("sdk37.unreachable", true);
});
runtime.putProperty("sdk37.worker", worker);
if (!ready.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new Error("Finder did not start");
sleep(300);
threads.shutDownAll();
worker.join(3000);
if (worker.isAlive()) throw new Error("Shutdown did not stop a blocking selector");
runtime.putProperty("sdk37.completed", true);
