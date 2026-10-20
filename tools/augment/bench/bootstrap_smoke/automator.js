// ROADMAP P2.5 smoke: automator.captureScreen, which waits through ResultAdapter.wait (Kotlin since P2.5); it needs the
// AutoJs6 accessibility service and Android 11+. Run by device_bootstrap_check.py.
var MARK = 'AJ6P25A';
try {
    var capture = automator.captureScreen();
    console.log(MARK + ' captureScreen ' + (capture.getWidth() > 0 && capture.getHeight() > 0));
    capture.recycle();
} catch (e) {
    console.log(MARK + ' captureScreen throws ' + e);
}
console.log(MARK + ' done');
