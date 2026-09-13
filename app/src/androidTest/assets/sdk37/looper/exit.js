events.on("exit", function () { runtime.putProperty("sdk37.exitEvent", true); });
var ready = new java.util.concurrent.CountDownLatch(1);
var worker = threads.start(function () { ready.countDown(); sleep(60000); });
runtime.putProperty("sdk37.worker", worker);
if (!ready.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new Error("Child did not start");
runtime.putProperty("sdk37.completed", true);
exit();
runtime.putProperty("sdk37.unreachable", true);
