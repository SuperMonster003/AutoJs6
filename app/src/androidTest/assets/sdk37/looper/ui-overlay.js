"ui";
ui.layout(<vertical><text id="status" text="SDK37 Looper" /></vertical>);
var overlay = floaty.rawWindow(<text id="label" text="SDK37 overlay" bg="#446688" />);
overlay.setPosition(30, 120);
var ticks = 0;
var startedAt = Date.now();
var firstTickAt = 0;
var timer = setInterval(function () {
    if (++ticks === 1) firstTickAt = Date.now();
    ui.status.setText("tick " + ticks);
}, 40);
events.on("exit", function () { overlay.close(); });

function finish() {
    clearInterval(timer);
    var firstTickAfterMs = firstTickAt ? firstTickAt - startedAt : -1;
    android.util.Log.i("Sdk37UiOverlay", "ticks=" + ticks + " firstTickAfterMs=" + firstTickAfterMs);
    if (ticks < 2 || String(ui.status.getText()) !== "tick " + ticks) {
        throw new Error("UI callbacks stopped: ticks=" + ticks + ", status=" + ui.status.getText() + ", firstTickAfterMs=" + firstTickAfterMs);
    }
    overlay.close();
    runtime.putProperty("sdk37.completed", true);
    activity.finish();
}

// Observe 600 ms of callbacks counted from the first tick: under instrumentation the UI activity of a
// cold process can take most of a fixed window to appear, which is not the "callbacks stopped" case
// this probe is about. Without any tick within 5 s the check fails with the diagnostics above.
var waitForFirstTick = setInterval(function () {
    if (ticks >= 1 || Date.now() - startedAt > 5000) {
        clearInterval(waitForFirstTick);
        setTimeout(finish, 600);
    }
}, 20);
