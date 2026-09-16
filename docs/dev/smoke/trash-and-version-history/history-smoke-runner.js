// Runner: evaluates the smoke script from a file so parse errors are reported instead of vanishing.
var OUT = "/sdcard/history-smoke-result.txt";
var SRC = "/sdcard/history-smoke.js";
try {
    var code = files.read(SRC);
    eval(code);
} catch (e) {
    files.write(OUT, "RUNNER ERROR: " + e + " line " + e.lineNumber + "\nDONE pass=0 fail=1\n");
}
