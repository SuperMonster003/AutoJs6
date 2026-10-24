// ROADMAP P4.2: the Promise behaviors of docs/augment/P4-2-promise-eval.md that only the engine shows (timers, threads,
// coroutines, Flow), in a project with the continuation feature. Run by device_promise_check.py; one "AJ6P42 <id> <value>"
// line per item. The rejected await shows the error toast of popException (existing behavior).
var MARK = 'AJ6P42';
var MAIN = String(java.lang.Thread.currentThread().getName());

function out(id, value) {
    console.log(MARK + ' ' + id + ' ' + value);
}

function onMain() {
    return String(java.lang.Thread.currentThread().getName()) === MAIN;
}

function javaThread(f) {
    new java.lang.Thread(new java.lang.Runnable({ run: f })).start();
}

out('impl', [Object.keys(Promise.prototype).join('/'), typeof Promise.any, Object.getPrototypeOf(Promise) === Function.prototype].join(' '));

// Order of reactions against the timers.
var order = [];
setTimeout(function () { order.push('timeout0'); }, 0);
Promise.resolve().then(function () { order.push('p1'); }).then(function () { order.push('p2'); });
setImmediate(function () { order.push('immediate'); });
continuation.delay(100);
out('A2-order', order.join());

// A foreign thenable: its then is called at once (promise.js) or in a job.
var seq = [];
new Promise(function (r) { r({ then: function (f) { seq.push('then'); f(5); } }); });
seq.push('after');
continuation.delay(50);
out('A5-thenable', seq.join());

// Adoption of an own Promise against a plain chain.
var ticks = [];
var own = Promise.resolve('x');
Promise.resolve().then(function () { return own; }).then(function () { ticks.push('adopted'); });
Promise.resolve().then(function () { ticks.push('t1'); }).then(function () { ticks.push('t2'); }).then(function () { ticks.push('t3'); }).then(function () { ticks.push('t4'); });
continuation.delay(100);
out('A4-adopt', ticks.join());

// Coroutines.
out('K1-await', Promise.resolve(7).await());
out('K1-await-later', new Promise(function (r) { setTimeout(function () { r('t'); }, 20); }).await());
var rejected = Promise.reject(new Error('no')).await();
out('K1-await-rejected', (rejected instanceof Error) + ' ' + rejected.message);
var got = [];
for (var i = 0; i < 3; i++) got.push(new Promise(function (r) { setTimeout(function () { r(got.length); }, 10); }).await());
out('K2-loop', got.join());
out('K3-threads', new Promise(function (r) { threads.start(function () { r('from thread'); }); }).await());
out('K3-java-thread', new Promise(function (r) { javaThread(function () { r('from java thread'); }); }).await());

// A Promise of the host settled on another thread through ScriptPromiseAdapter (as tts / toJsPromise).
var adapter = new org.autojs.autojs.runtime.api.ScriptPromiseAdapter();
var adapted = ResultAdapter.promise(adapter);
javaThread(function () { java.lang.Thread.sleep(20); adapter.resolve('adapter'); });
out('K5-adapter', adapted.await());

// The thread the callbacks run on when another thread settles.
var where = [];
var jp = new Promise(function (r) { javaThread(function () { java.lang.Thread.sleep(20); r(1); }); });
jp.then(function () { where.push('java:' + (onMain() ? 'main' : 'other')); });
var tp = new Promise(function (r) { threads.start(function () { sleep(20); r(1); }); });
tp.then(function () { where.push('threads:' + (onMain() ? 'main' : 'other')); });
continuation.delay(300);
out('T1-callback-thread', where.sort().join());

// Flow: await() of a chain is Kotlin calling the await of a Promise.
try {
    out('K4-flow-await', flow.of(5).await());
} catch (e) {
    out('K4-flow-await', 'error ' + String(e).slice(0, 90));
}
try {
    out('K4-flow-toPromise-await', flow.of(6).toPromise().await());
} catch (e) {
    out('K4-flow-toPromise-await', 'error ' + String(e).slice(0, 90));
}

// wait() on the script thread.
out('W1-wait', Promise.resolve(1).wait());
out('W1-wait-timer', new Promise(function (r) { setTimeout(function () { r(2); }, 20); }).wait());
out('W1-wait-thread', new Promise(function (r) { threads.start(function () { r(3); }); }).wait());
try {
    Promise.reject(new Error('w')).wait();
} catch (e) {
    out('W1-wait-rejected', 'threw ' + e.message);
}

// The unhandled rejection warning: device_promise_check.py looks for the console line.
Promise.reject(new Error('aj6p42 unhandled'));
continuation.delay(50);

out('done', '');
