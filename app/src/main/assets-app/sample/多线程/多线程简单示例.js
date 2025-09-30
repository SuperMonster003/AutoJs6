// 启动一个线程
threads.start(function () {
    // 在线程中每隔 1 秒打印"线程 1"
    while (true) {
        log('线程 1');
        sleep(1000);
    }
});

// 启动另一个线程
threads.start(function () {
    // 在线程中每隔 2 秒打印"线程 1"
    while (true) {
        log('线程 2');
        sleep(2000);
    }
});

// 在主线程中每隔 3 秒打印"主线程"
for (var i = 0; i < 10; i++) {
    log('主线程');
    sleep(3000);
}
// 打印 100 次后退出所有线程
threads.shutDownAll();
