// ROADMAP P2.5 smoke: continuation.await / continuation.delay (Kotlin since P2.5) in a project with the continuation
// feature, with a Promise from the engine, a Promise settled later and a rejected one. Run by device_bootstrap_check.py.
var MARK = 'AJ6P25C';
console.log(MARK + ' enabled ' + continuation.enabled);
var t = Date.now();
var delayed = continuation.delay(300);
console.log(MARK + ' delay ' + (Date.now() - t >= 280) + ' ' + typeof delayed);
console.log(MARK + ' promise.await ' + Promise.resolve(7).await());
var later = new Promise(function (resolve) {
    setTimeout(function () {
        resolve('later');
    }, 100);
});
console.log(MARK + ' continuation.await ' + continuation.await(later));
var rejected = Promise.reject(new Error('no')).await();
console.log(MARK + ' rejected ' + (rejected instanceof Error ? 'Error ' + rejected.message : String(rejected)));
var cont = continuation.create();
threads.start(function () {
    sleep(100);
    cont.resume('from thread');
});
console.log(MARK + ' create.await ' + cont.await());
console.log(MARK + ' done');
