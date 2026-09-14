// SDK 37 gate wrapper: runs the legacy accessibility smoke inside this engine and tees
// every console line into a file that the gate reads back with run-as. ASCII only, because
// the gate passes this file as an intent extra through adb.
//
// The global console is an augmentable proxy that rejects property assignment, so the smoke
// script is evaluated inside a function whose local `console` shadows the global one.
"use strict";

var gateLog = "__GATE_LOG__";
var gateScript = "__GATE_SCRIPT__";
var globalConsole = console;

files.write(gateLog, "GATE_START " + gateScript + "\n");

function tee(kind, name) {
    return function () {
        var text = Array.prototype.map.call(arguments, String).join(" ");
        try {
            files.append(gateLog, kind + " " + text + "\n");
        } catch (ignored) {
        }
        var original = globalConsole[name];
        return typeof original === "function" ? original.apply(globalConsole, arguments) : undefined;
    };
}

(function () {
    var console = {
        log: tee("LOG", "log"),
        info: tee("LOG", "info"),
        verbose: tee("LOG", "verbose"),
        warn: tee("WARN", "warn"),
        error: tee("ERR", "error"),
        print: tee("LOG", "print"),
        show: function () { return globalConsole.show.apply(globalConsole, arguments); },
        hide: function () { return globalConsole.hide.apply(globalConsole, arguments); },
        clear: function () { return globalConsole.clear.apply(globalConsole, arguments); },
    };
    try {
        eval(files.read(gateScript));
        files.append(gateLog, "GATE_DONE ok\n");
    } catch (e) {
        files.append(gateLog, "GATE_DONE error " + String(e).replace(/\r?\n/g, " ") + "\n");
        throw e;
    }
})();
