"use strict";

var timer = setTimeout(function () {
    runtime.putProperty("sdk37.unreachable", true);
}, 60 * 1000);
var worker = threads.start(function () {
    sleep(300);
    if (!clearTimeout(timer)) throw Error("Worker could not cancel the main callback");
    runtime.putProperty("sdk37.completed", true);
});
runtime.putProperty("sdk37.worker", worker);
