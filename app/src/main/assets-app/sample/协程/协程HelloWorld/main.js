// 注意, 要使用协程这个特性, 必须使用项目功能, 并且在 project.json 配置好 features 属性

// delay 不同于 sleep, 不会阻塞当前线程
function delay(millis) {
    let cont = continuation.create();
    setTimeout(() => cont.resume(), millis);
    cont.await();
}

// 异步 IO 例子, 在另一个线程读取文件, 读取完成后返回当前线程继续执行
function read(path) {
    let cont = continuation.create();
    threads.start(() => {
        try {
            cont.resume(files.read(path));
        } catch (err) {
            cont.resumeError(err);
        }
    });
    return cont.await();
}

// 使用 Promise 和协程的例子
function add(a, b) {
    return new Promise((resolve) => resolve(a + b));
}

toastLog('Hello, Continuation!');

// 1 秒后发出提示
setTimeout(() => toastLog('1 秒后...'), 1e3);

// 可尝试把 delay 换成 sleep, 看会发生什么
delay(2e3);
toastLog('2 秒后...');

let sum = add(1, 2).await();
toastLog('1 + 2 = ' + sum);

try {
    toastLog('读取文件 hello.txt: ' + read('./hello.txt'));
} catch (err) {
    console.error(err);
}
