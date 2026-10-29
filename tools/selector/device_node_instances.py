"""AccessibilityNodeInfo instances across a selector loop on a device (ROADMAP selector P5.1, red line 2): on the main
screen of AutoJs6, runs rounds of the queries of this theme (find / findOnce with the refresh on hit / prefer / the id
fast path / findOne polling / pickup with a compass and a result type / children / parent / a string selector) and counts
the reachable AccessibilityNodeInfo instances through dalvik.system.VMDebug (the memory gate's measure,
tools/memory/README.md: GC first, reachable instances only) at the start, after the warm-up rounds, half-way and at the
end. Passes when the end is within --limit (+50, the gate of S7 / S7b) of the end of the warm-up: below API 33 the
framework pools recycled nodes per process and the AccessibilityCache refills with the window, a node leak grows by
hundreds per run. Prints the counts and the rounds only (no node text).

Usage: py -3.12 -I tools/selector/device_node_instances.py -s SERIAL [--warmup 20] [--rounds 300] [--limit 50]
Needs the accessibility service of AutoJs6 running; VMDebug's counts are a hidden API (available to the host on API
24 / 28, -1 where not); no taps (the screen is switched with am start).
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _device as d  # noqa: E402

SCRIPT = r"""
var out = { sdk: device.sdkInt, rounds: AJ6SEL.rounds, warmup: AJ6SEL.warmup, nodes: 0, misses: 0 };
var NodeInfo = java.lang.Class.forName('android.view.accessibility.AccessibilityNodeInfo');
var countOf = (function () {
    try {
        var m = java.lang.Class.forName('dalvik.system.VMDebug').getMethod('countInstancesOfClass', java.lang.Class, java.lang.Boolean.TYPE);
        return function () {
            java.lang.System.gc();
            java.lang.System.runFinalization();
            return Number(m.invoke(null, NodeInfo, java.lang.Boolean.TRUE));
        };
    } catch (e) {
        out.vmdebugError = String(e).slice(0, 160);
        return function () { return -1; };
    }
})();
auto.waitFor();
function round() {
    out.nodes += className('android.widget.FrameLayout').find().size();
    var first = className('android.widget.TextView').findOnce();
    if (first) {
        out.nodes += first.children().size();
        if (first.parent()) out.nodes++;
    } else {
        out.misses++;
    }
    if (textMatch(/.+/).prefer('visible', 'top').findOnce() === null) out.misses++;
    if (id('aj6sel_none').findOnce() !== null) out.misses++;
    if (id('aj6sel_none').findOne(30) !== null) out.misses++;
    if (text('__aj6sel_none__').findOne(30) !== null) out.misses++;
    if (pickup(className('android.widget.TextView'), 'p1', 'className') === null) out.misses++;
    if (select('className=android.widget.TextView prefer=visible,top').findOnce() === null) out.misses++;
    out.nodes += className('android.widget.TextView').find().size();
}
out.start = countOf();
var started = Date.now();
for (var i = 0; i < AJ6SEL.warmup; i++) round();
out.warm = countOf();
for (var j = 0; j < AJ6SEL.rounds; j++) {
    round();
    if (j === Math.floor(AJ6SEL.rounds / 2)) out.mid = countOf();
}
out.end = countOf();
out.elapsedMs = Date.now() - started;
out.foreground = String(currentPackage());
files.write(AJ6SEL.out, JSON.stringify(out));
console.log('AJ6SEL done');
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--warmup", type=int, default=20)
    parser.add_argument("--rounds", type=int, default=300)
    parser.add_argument("--limit", type=int, default=50, help="largest allowed growth from the end of the warm-up")
    args = parser.parse_args()
    info = d.device_info(args.serial)
    print(f"device {info['model']} API {info['sdk']} versionCode {info['versionCode']} (build {info['build']})")
    if not d.a11y_enabled(args.serial):
        sys.exit("the accessibility service of AutoJs6 is not enabled")
    d.WORK.mkdir(exist_ok=True)
    (d.WORK / "aj6sel_nodes_src.js").write_text(SCRIPT, encoding="utf-8")
    d.screen_main(args.serial)
    wait = 60 + args.rounds + args.warmup
    res, lines = d.run_json(args.serial, "__pycache__/aj6sel_nodes_src.js", {"warmup": args.warmup, "rounds": args.rounds},
                            wait, name="aj6sel_nodes.js")
    d.adb(args.serial, "shell", "rm", "-f", f"{d.DEVICE_DIR}/aj6sel_nodes.js", f"{d.DEVICE_DIR}/aj6sel_nodes.out.json")
    if res is None:
        print("FAIL no result file")
        for line in d.script_errors(lines):
            print(f"  {line[:200]}")
        sys.exit(1)
    on_screen = res.get("foreground") == d.PACKAGE
    print(f"rounds {res['warmup']} + {res['rounds']} in {res['elapsedMs']} ms, nodes seen {res['nodes']}, misses {res['misses']}, "
          f"foreground {'AutoJs6' if on_screen else 'other'}")
    counts = [res.get(k) for k in ("start", "warm", "mid", "end")]
    print("AccessibilityNodeInfo start / warm-up end / half-way / end: " + " / ".join(str(c) for c in counts))
    if res.get("vmdebugError") or -1 in counts:
        print(f"FAIL VMDebug unavailable: {res.get('vmdebugError', 'count -1')}")
        sys.exit(1)
    growth = res["end"] - res["warm"]
    ok = growth <= args.limit and res["misses"] == 0 and on_screen
    print(f"growth from the warm-up end: {growth:+d} (limit +{args.limit}); {'PASS' if ok else 'FAIL'}")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
