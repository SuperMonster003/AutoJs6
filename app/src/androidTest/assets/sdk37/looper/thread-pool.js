"use strict";

var done = new java.util.concurrent.CountDownLatch(3);
var count = new java.util.concurrent.atomic.AtomicInteger();
var pool = threads.pool({ corePoolSize: 1, maxPoolSize: 2 });
try {
    for (var i = 0; i < 3; i++) {
        pool.execute(new java.lang.Runnable({ run: function () {
            if (android.os.Looper.myLooper() == null) throw Error("Pool worker has no looper");
            count.incrementAndGet();
            done.countDown();
        } }));
    }
    if (!done.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw Error("Pool tasks did not run");
    if (count.get() !== 3) throw Error("Pool lost work");
    runtime.putProperty("sdk37.completed", true);
} finally {
    pool.shutdown();
}
