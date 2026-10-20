// Cost of a failing call (roadmap P1.4, red line R2), measured inside the script: every case runs W warm-up
// iterations, then up to N timed ones (at least MIN, stopping after LIMIT_MS), and prints microseconds per iteration
// (each iteration is one arrow function call around a try / catch). base64 is served by the new script module layer
// when its switch (ROADMAP T8) is on and by the old augment module otherwise; the Rhino and script cases are the same
// either way and serve as the reference. The "part" cases reach single steps of the module error path through
// Rhino's Java bridge; "bridge" is a trivial bridged static call of the same shape, to subtract from them.
let N = 2000;
let MIN = 100;
let W = 200;
let LIMIT_MS = 3000;

function time(label, fn) {
    let warm = java.lang.System.nanoTime();
    for (let i = 0; i < W && (i < 20 || java.lang.System.nanoTime() - warm < LIMIT_MS * 1e6 / 3); i++) fn();
    let t0 = java.lang.System.nanoTime();
    let n = 0;
    while (n < N && (n < MIN || java.lang.System.nanoTime() - t0 < LIMIT_MS * 1e6)) {
        fn();
        n++;
    }
    let us = (java.lang.System.nanoTime() - t0) / 1e3 / n;
    console.log("AJ6BENCH error " + label + " " + us.toFixed(1) + " us");
}

let u;
let M = org.autojs.autojs.runtime.api.modules.ScriptMessages;
let A = org.autojs.autojs.runtime.api.modules.ScriptArgs;
let RS = org.mozilla.javascript.ScriptRuntime;

time("empty-try", () => { try { u = undefined; } catch (e) { } });
time("rhino-undefined-property", () => { try { u.x; } catch (e) { } });
time("rhino-builtin-type-error", () => { try { Object.defineProperty(1, "x", {}); } catch (e) { } });
time("rhino-builtin-range-error", () => { try { new Array(-1); } catch (e) { } });
time("script-throw-type-error", () => { try { throw new TypeError("x"); } catch (e) { } });
time("rhino-java-exception", () => { try { java.lang.Integer.parseInt("x"); } catch (e) { } });
time("module-success", () => { try { base64.encode("hello"); } catch (e) { } });
time("module-arity", () => { try { base64.encode(); } catch (e) { } });
time("module-null-argument", () => { try { base64.encode(null); } catch (e) { } });
time("module-unknown-charset", () => { try { base64.encode("a", "no-such-charset"); } catch (e) { } });
time("module-java-exception", () => { try { base64.decode("="); } catch (e) { } });
time("module-arity-read-message", () => { try { base64.encode(); } catch (e) { e.message; } });
time("module-arity-read-stack", () => { try { base64.encode(); } catch (e) { e.stack; } });
time("rhino-undefined-property-read-stack", () => { try { u.x; } catch (e) { e.stack; } });

time("part bridge", () => A.isNullish(null));
time("part messages-failedToCall", () => M.failedToCall("base64.encode", "x"));
time("part messages-arity", () => M.arity(1, 2));
let ID = org.autojs.autojs6.R.string.error_failed_to_call_method_with_cause;
// Explicit signatures: Rhino would pick the varargs overloads (getString(int, Object...)) for these calls.
let PATTERN = context["getString(int)"](ID);
let LOCALE = context.getResources().getConfiguration().getLocales().get(0);
time("part resources-getString", () => context["getString(int)"](ID));
let format = java.lang.String["format(java.util.Locale,java.lang.String,java.lang.Object[])"];
time("part string-format", () => format(LOCALE, PATTERN, ["base64.encode", "x"]));
time("part construct-type-error", () => RS.typeError("x"));
time("part construct-java-exception", () => new java.lang.IllegalStateException("x"));

let depth = (fn) => { try { fn(); } catch (e) { return e.rhinoException ? e.rhinoException.getStackTrace().length : -1; } return -2; };
console.log("AJ6BENCH error java-frames rhino-undefined-property=" + depth(() => u.x) +
    " module-arity=" + depth(() => base64.encode()) + " rhino-builtin-type-error=" + depth(() => Object.defineProperty(1, "x", {})));
