'ui';
// ROADMAP P3.2 smoke, appendix C Q23 in UI mode without a tap: the coroutine of the main script suspends again after it
// was resumed by the timer of continuation.delay, by a Promise settled in a timer, by a setInterval callback and from
// another thread, and awaits the Promise of a dialog's dismiss event after continuation.delay (the script dismisses its
// own dialog). The two cases outside UI mode (setInterval, another thread) are in the coroutine smoke. Each line ends
// with true when its case passed. Run by device_bootstrap_check.py.
var MARK = 'AJ6P32S';
ui.layout(
    <frame>
        <text text="AutoJs6 P3.2 smoke" textSize="18sp" layout_gravity="center"/>
    </frame>,
);
console.log(MARK + ' enabled ' + continuation.enabled + ' ui ' + ui.isUiThread());

function later(value, ms) {
    return new Promise(function (resolve) {
        setTimeout(function () {
            resolve(value);
        }, ms);
    });
}

var t = Date.now();
continuation.delay(100);
continuation.delay(100);
console.log(MARK + ' delay twice ' + (Date.now() - t >= 190));

console.log(MARK + ' await twice ' + ([later('a', 50).await(), later('b', 50).await()].join() === 'a,b'));

var next = null, ticks = 0, got = [];
var interval = setInterval(function () {
    var c = next;
    next = null;
    if (c) c.resume(++ticks);
}, 50);
for (var i = 0; i < 3; i++) {
    next = continuation.create();
    got.push(next.await());
}
clearInterval(interval);
console.log(MARK + ' interval ' + (got.join() === '1,2,3'));

var cont = continuation.create();
threads.start(function () {
    sleep(100);
    cont.resume('thread');
});
var resumed = cont.await();
t = Date.now();
continuation.delay(100);
console.log(MARK + ' thread then delay ' + (resumed === 'thread' && Date.now() - t >= 90 && ui.isUiThread()));

continuation.delay(100);
var dialog = dialogs.build({ title: 'AutoJs6 P3.2', content: 'This dialog closes by itself.' });
var dismissed = new Promise(function (resolve) {
    dialog.on('dismiss', function () {
        resolve('dismissed');
    });
});
dialog.show();
setTimeout(function () {
    dialog.dismiss();
}, 300);
console.log(MARK + ' delay then dialog await ' + (dismissed.await() === 'dismissed'));
console.log(MARK + ' done');
ui.finish();
