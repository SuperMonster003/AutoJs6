'ui';
// ROADMAP P2.5 smoke: Promise.await (continuation.await, Kotlin since P2.5) in UI mode, with the Promise of
// dialogs.confirm, which waits for the maintainer to tap a button on the phone. Run by device_bootstrap_check.py.
// Only one suspension: in UI mode a continuation resumed from a timer callback (continuation.delay, any Promise, which
// settles through setImmediate) cannot suspend again, before and after P2.5 (Timer.callFunction exits the script on
// the main looper when the second ContinuationPending reaches it); continuation.delay alone in UI mode printed
// "delay true" on both builds.
var MARK = 'AJ6P25U';
ui.layout(
    <frame>
        <text text="AutoJs6 P2.5 smoke" textSize="18sp" layout_gravity="center"/>
    </frame>,
);
console.log(MARK + ' enabled ' + continuation.enabled + ' ui ' + ui.isUiThread());
var answer = dialogs.confirm('AutoJs6 P2.5', 'Tap OK (or CANCEL) to finish the smoke test.').await();
console.log(MARK + ' dialogs.confirm.await ' + answer);
console.log(MARK + ' done');
ui.finish();
