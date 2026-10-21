'ui';
// ROADMAP P2.5 smoke: continuation.delay and Promise.await (continuation.await, Kotlin since P2.5) in UI mode, with the
// Promise of dialogs.confirm, which waits for the maintainer to tap a button on the phone. Run by device_bootstrap_check.py.
// Every suspension after the first one is a regression check of appendix C Q23: a coroutine resumed from another thread
// (the resume is posted to the main looper) suspends again in continuation.delay, which formerly crashed the app; the
// one resumed by the timer of continuation.delay suspends again in await, which formerly exited the script.
var MARK = 'AJ6P25U';
ui.layout(
    <frame>
        <text text="AutoJs6 P2.5 smoke" textSize="18sp" layout_gravity="center"/>
    </frame>,
);
console.log(MARK + ' enabled ' + continuation.enabled + ' ui ' + ui.isUiThread());
var cont = continuation.create();
threads.start(function () {
    sleep(100);
    cont.resume('from thread');
});
console.log(MARK + ' create.await ' + cont.await());
var t = Date.now();
continuation.delay(300);
console.log(MARK + ' delay ' + (Date.now() - t >= 280));
var answer = dialogs.confirm('AutoJs6 P2.5', 'Tap OK (or CANCEL) to finish the smoke test.').await();
console.log(MARK + ' dialogs.confirm.await ' + answer);
console.log(MARK + ' done');
ui.finish();
