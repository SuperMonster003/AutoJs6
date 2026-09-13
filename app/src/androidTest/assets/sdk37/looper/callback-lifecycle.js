"use strict";

var count = 0;
var interval = setInterval(function () {
    if (++count !== 1) runtime.putProperty("sdk37.unreachable", true);
    if (!clearInterval(interval)) throw Error("Could not clear the executing interval");
}, 30);
var immediate = setImmediate(function () {
    runtime.putProperty("sdk37.unreachable", true);
});
if (!clearImmediate(immediate)) throw Error("Could not cancel the immediate callback");
runtime.timers.mainTimer.postDelayed(new java.lang.Runnable({ run: function () {
    if (count !== 1) throw Error("Unexpected interval count: " + count);
    runtime.putProperty("sdk37.completed", true);
} }), 500);
