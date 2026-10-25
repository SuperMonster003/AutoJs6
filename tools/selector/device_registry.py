"""The node method registry of pickup / detect on a device (ROADMAP selector appendix C Q16): builds
`NodeMethodRegistry.uiObject` in the host process through a script and prints whether it was built, how many names it
holds and whether the methods whose signature names a class of a later API (`getExtraRenderingInfo`, API 30) are in it.
Before the fix of Q16 the build threw NoClassDefFoundError on API 24-29, and every pickup / detect failed with it.
Needs no accessibility service (the registry is built by reflection only); no taps.

Usage: py -3.12 -I tools/selector/device_registry.py -s SERIAL [-s SERIAL ...]
"""
import argparse
import os
import sys

# py -I leaves the directory of the script off sys.path; the shared helpers live next to it.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _device as d  # noqa: E402

SCRIPT = r"""
var out = { sdk: android.os.Build.VERSION.SDK_INT };
// Rhino does not let a script catch a java.lang.Error (NoClassDefFoundError), so the registry is built in a FutureTask,
// whose get() reports it as the cause of an ExecutionException.
var task = new java.util.concurrent.FutureTask(new java.util.concurrent.Callable({
    call: function () {
        return org.autojs.autojs.core.automator.pick.NodeMethodRegistry.INSTANCE.getUiObject();
    },
}));
task.run();
try {
    var names = task.get().getNames();
    out.names = names.size();
    out.extraRenderingInfo = names.contains('getExtraRenderingInfo');
    out.text = names.contains('text') && names.contains('getHintText');
} catch (e) {
    var t = e.javaException || e;
    t = t.getCause && t.getCause() ? t.getCause() : t;
    out.error = String(t.getClass ? t.getClass().getName() : e.name) + ': ' + String(t.getMessage ? t.getMessage() : e.message).slice(0, 160);
}
files.write(AJ6SEL.out, JSON.stringify(out));
console.log('AJ6SEL done');
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", action="append", required=True)
    args = parser.parse_args()
    d.WORK.mkdir(exist_ok=True)
    (d.WORK / "aj6sel_registry_src.js").write_text(SCRIPT, encoding="utf-8")
    failed = 0
    for s in args.serial:
        info = d.device_info(s)
        res, lines = d.run_json(s, "__pycache__/aj6sel_registry_src.js", {}, 60, name="aj6sel_registry.js")
        head = f"{info['model']} API {info['sdk']} versionCode {info['versionCode']} (build {info['build']})"
        if res is None:
            failed += 1
            print(f"{head}: FAIL no result file")
            for line in d.script_errors(lines):
                print(f"  {line[:200]}")
        elif "error" in res:
            failed += 1
            print(f"{head}: FAIL registry not built: {res['error']}")
        else:
            ok = res.get("text") is True
            failed += 0 if ok else 1
            print(f"{head}: {'PASS' if ok else 'FAIL'} registry built, {res['names']} names,"
                  f" getExtraRenderingInfo {'in it' if res['extraRenderingInfo'] else 'left out'}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
