'ui';
// Appendix C Q23 (follow-up) smoke: listeners of EventEmitters dispatched by the main looper suspend, as the back_pressed
// listener of the sample "协程/UI模式中使用协程" does (it formerly failed with "Cannot capture continuation ..." and the
// script was ended). device_bootstrap_check.py sends KEYCODE_BACK after "registered" (ui.emitter, the Activity), then taps
// the middle of the screen after "back await" (the click event of this script's own full-screen view); the click listener
// shows a dialog whose show listener suspends and dismisses it (dialog events). Nothing is tapped on a dialog.
var MARK = 'AJ6P25L';
ui.layout(
    <frame id="area" bg="#4fc3f7">
        <text text="AutoJs6 listener smoke" textSize="18sp" textColor="white" layout_gravity="center"/>
    </frame>,
);
var log = function (s) {
    console.log(MARK + ' ' + s);
};
ui.emitter.on('back_pressed', function (e) {
    e.consumed = true;
    var t = Date.now();
    continuation.delay(200);
    // this is the emitter (two wrappers of one Java object are not ===, before and after Q23).
    log('back delay ' + (Date.now() - t >= 180) + ' ' + java.util.Objects.equals(this, ui.emitter));
    log('back await ' + Promise.resolve(3).await());
});
ui.area.on('click', () => {
    var later = new Promise(function (resolve) {
        setTimeout(function () {
            resolve('later');
        }, 100);
    });
    log('click await ' + later.await());
    // The show event of a dialog is dispatched by the main looper once it is shown; the listener dismisses it itself.
    dialogs.build({ title: 'AutoJs6 listener smoke' }).on('show', function (dialog) {
        continuation.delay(100);
        log('dialog delay ' + (dialog != null));
        dialog.dismiss();
        log('done');
        ui.finish();
    }).show();
});
log('registered ' + continuation.enabled);
