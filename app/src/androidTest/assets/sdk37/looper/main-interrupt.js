var main = threads.currentThread();
var ready = new java.util.concurrent.CountDownLatch(1);
var worker = threads.start(function () {
    if (!ready.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new Error("Main did not start");
    main.interrupt();
});
runtime.putProperty("sdk37.worker", worker);
runtime.putProperty("sdk37.completed", true);
ready.countDown();
while (true) { Math.sqrt(37); }
runtime.putProperty("sdk37.unreachable", true);
