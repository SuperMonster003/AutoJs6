"use strict";

var cancelled = setTimeout(function () {
    runtime.putProperty("sdk37.unreachable", true);
}, 60 * 1000);
if (!clearTimeout(cancelled)) throw Error("Failed to cancel the future callback");
if (clearTimeout(cancelled)) throw Error("The cancelled callback was still registered");
runtime.putProperty("sdk37.completed", true);
