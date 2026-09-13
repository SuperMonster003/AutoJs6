"ui";
ui.layout(<vertical><text id="status" text="SDK37 Looper" /></vertical>);
var overlay = floaty.rawWindow(<text id="label" text="SDK37 overlay" bg="#446688" />);
overlay.setPosition(30, 120);
var ticks = 0;
var timer = setInterval(function () { ui.status.setText("tick " + (++ticks)); }, 40);
events.on("exit", function () { overlay.close(); });
setTimeout(function () {
    clearInterval(timer);
    if (ticks < 2 || String(ui.status.getText()) !== "tick " + ticks) throw new Error("UI callbacks stopped");
    overlay.close();
    runtime.putProperty("sdk37.completed", true);
    activity.finish();
}, 600);
