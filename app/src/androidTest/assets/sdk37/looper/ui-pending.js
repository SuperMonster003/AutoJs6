"use strict";

ui.run(function () {
    setTimeout(function () {
        var cancelled = setTimeout(function () {
            runtime.putProperty("sdk37.unreachable", true);
        }, 60 * 1000);
        if (!clearTimeout(cancelled)) throw Error("UI timer cancellation failed");
        runtime.putProperty("sdk37.completed", true);
    }, 300);
});
