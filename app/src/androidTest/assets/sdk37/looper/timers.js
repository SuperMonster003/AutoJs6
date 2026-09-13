var order = [];
var removed = false;
var ticks = 0;
var emitter = events.emitter();
emitter.on("tick", function (value) { order.push(value); });
var cancelled = setTimeout(function () { removed = true; }, 400);
if (!clearTimeout(cancelled)) throw new Error("Timeout was not removed");
setImmediate(function () { emitter.emit("tick", "immediate"); });
var interval = setInterval(function () {
    if (++ticks === 3) clearInterval(interval);
}, 30);
setTimeout(function () {
    if (removed || ticks !== 3 || order.join(",") !== "immediate") {
        throw new Error("Timer or event dispatch was lost or duplicated");
    }
    emitter.removeAllListeners();
    runtime.putProperty("sdk37.completed", true);
}, 600);
