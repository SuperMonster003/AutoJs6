'ui';
// ROADMAP P2.5 smoke: continuation.delay and Promise.await (continuation.await, Kotlin since P2.5) in UI mode, with the
// Promise of dialogs.confirm, which waits for the maintainer to tap a button on the phone. Run by device_bootstrap_check.py.
var MARK = 'AJ6P25U';
ui.layout(
    <frame>
        <text text="AutoJs6 P2.5 smoke" textSize="18sp" layout_gravity="center"/>
    </frame>,
);
console.log(MARK + ' enabled ' + continuation.enabled + ' ui ' + ui.isUiThread());
var t = Date.now();
continuation.delay(300);
console.log(MARK + ' delay ' + (Date.now() - t >= 280));
var answer = dialogs.confirm('AutoJs6 P2.5', 'Tap OK (or CANCEL) to finish the smoke test.').await();
console.log(MARK + ' dialogs.confirm.await ' + answer);
console.log(MARK + ' done');
ui.finish();
