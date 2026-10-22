"""Turns the Offline Documentation plugin on and off with its plugin center switch and checks the documentation page.

Usage: py -3.12 -I -u tools/plugin-center/offline_docs_switch_check.py -s SERIAL [--title PATTERN] [--timeout 15]

Checks, in order (the switch starts off: the script writes it off through run-as when it is on):
  1. docs-off-before  the documentation activity shows the "unavailable" view
  2. switch-on        tapping the switch in the plugin center enables the plugin: no "Failed to enable the plugin" dialog,
                      the switch stays on and the saved state is true (the enable handshake used to fail with
                      PLUGIN_DISABLED, because the saved state is off until the handshake succeeds)
  3. docs-on          the documentation activity loads the index page (the title of its WebView is printed)
  4. switch-off       tapping the switch again disables the plugin: the saved state is false
  5. docs-off         the documentation activity shows the "unavailable" view again
The original switch value is restored at the end ("unset", the default, comes back as true, which means the same).

The host must be a debug build (run-as) with the plugin installed. The script drives the host through adb like
tools/memory/ui_leak_walk.py, whose device helpers it uses; windows of other apps are left to the maintainer.
Output: one PASS / FAIL line per check with its key value, then the totals.
"""
import argparse
import pathlib
import re
import sys
import time

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "memory"))

from ui_leak_walk import (  # noqa: E402
    App, Device, OFFLINE_DOCS, PACKAGE, WalkError, Walker, label, log,
)

DOCS_ACTIVITY = "org.autojs.autojs.ui.doc.DocumentationActivity"
DEFAULT_TITLE = r"^(Offline Documentation|离线文档)$"


def version_of(dev, package):
    info = dev.shell(f"dumpsys package {package}")
    code = re.search(r"versionCode=(\d+)", info)
    name = re.search(r"versionName=(\S+)", info)
    return (code.group(1) if code else None), (name.group(1) if name else None), info


def docs_page(w, timeout):
    """Opens the documentation activity and returns ("index", title) or ("unavailable", text) or ("none", top)."""
    w.main()
    w.dev.shell(f"am start -n {PACKAGE}/{DOCS_ACTIVITY}")
    if not w.dev.wait_activity("DocumentationActivity", 10):
        raise WalkError(f"DocumentationActivity not shown (top: {w.dev.top_activity()})")
    unavailable = label("text_offline_docs_unavailable")
    result = ("none", "")
    deadline = time.time() + timeout
    while time.time() < deadline:
        nodes = w.dev.dump()
        shown = next((n for n in nodes if n.rid.endswith("/offline_docs_unavailable_title") and n.text in unavailable), None)
        if shown:
            result = ("unavailable", shown.text)
            break
        web = next((n for n in nodes if n.cls.endswith("WebView") and re.search(r"AutoJs6", n.text or n.desc)), None)
        if web:
            result = ("index", web.text or web.desc)
            break
        time.sleep(1)
    w.dev.back(pause=0.3)  # "press again to exit"
    w.dev.back()
    w.back_to("MainActivity")
    return result


def plugin_switch(w, title_pattern):
    """The switch node of the plugin row whose title matches [title_pattern], scrolled fully into view."""
    box = w.dev.wait(8, rid="plugin_center_recycler_view")
    if box is None:
        raise WalkError("plugin_center_recycler_view not found")
    title = w.scroll_to("plugin_center_recycler_view", rid="title", text=title_pattern)
    for _ in range(3):
        switches = [n for n in w.dev.find_all(rid="sw", fresh=False) if n.bounds[1] >= title.bounds[1] - 10]
        sw = min(switches, key=lambda n: n.bounds[1], default=None)
        if sw is not None and sw.bounds[3] <= box.bounds[3] - 4:
            return sw
        x = (box.bounds[0] + box.bounds[2]) // 2
        y = (box.bounds[1] + box.bounds[3]) // 2
        w.dev.swipe(x, y, x, y - (box.bounds[3] - box.bounds[1]) // 3, 300)
        title = w.dev.find(rid="title", text=title_pattern)
        if title is None:
            break
    raise WalkError("the switch of the Offline Documentation row not found")


def toggle(w, app, title_pattern, want, timeout):
    """Taps the plugin switch towards [want] ("true" / "false"); returns (ok, detail)."""
    w.open_from_drawer("plugin_center", "PluginCenterActivity")
    sw = plugin_switch(w, title_pattern)
    if sw.checked == (want == "true"):
        w.back_to("MainActivity")
        return False, f"switch already {'on' if sw.checked else 'off'} before the tap"
    w.dev.tap_node(sw, pause=1.0)
    failed_title = label("error_failed_to_enable_the_plugin")
    detail = "timeout"
    ok = False
    deadline = time.time() + timeout
    while time.time() < deadline:
        app.check_foreign_window()
        nodes = w.dev.dump()
        if any(n.text in failed_title for n in nodes):
            code = next((m.group(1) for n in nodes for m in [re.search(r"\(([A-Z_]+)\)", n.text)] if m), "?")
            detail = f"dialog \"Failed to enable the plugin\" ({code})"
            # The button shows its label in capitals: find it by id.
            dismiss = next((n for n in nodes if n.rid.endswith("/md_buttonDefaultNegative")), None)
            if dismiss:
                w.dev.tap_node(dismiss)
            break
        current = next((n for n in nodes if n.rid.endswith("/sw") and n.bounds[1] >= sw.bounds[1] - 40
                        and n.bounds[1] <= sw.bounds[3] + 40), None)
        saved = app.plugin_enabled(OFFLINE_DOCS)
        if current is not None and current.checked == (want == "true") and saved == want:
            ok, detail = True, f"switch {'on' if current.checked else 'off'}, saved {saved}"
            # A failure dialog would follow the handshake within its timeout: keep watching briefly.
            if want == "true":
                time.sleep(2)
                if any(n.text in failed_title for n in w.dev.dump()):
                    ok, detail = False, "dialog \"Failed to enable the plugin\" after the switch turned on"
            break
        detail = f"switch {'?' if current is None else ('on' if current.checked else 'off')}, saved {saved}"
        time.sleep(1)
    w.back_to("MainActivity")
    return ok, detail


def main():
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("-s", "--serial", required=True)
    ap.add_argument("--title", default=DEFAULT_TITLE, help="pattern of the plugin row title in the plugin center")
    ap.add_argument("--timeout", type=float, default=15.0, help="seconds to wait for a switch or a page")
    args = ap.parse_args()

    dev = Device(args.serial)
    host_code, _, host_info = version_of(dev, PACKAGE)
    if host_code is None:
        sys.exit(f"{PACKAGE} is not installed")
    if "DEBUGGABLE" not in host_info:
        sys.exit("the installed host is not a debug build (run-as needs one)")
    plugin_code, plugin_name, _ = version_of(dev, OFFLINE_DOCS)
    if plugin_code is None:
        sys.exit(f"{OFFLINE_DOCS} is not installed")
    log(f"device {args.serial} ({dev.prop('ro.product.model')}, API {dev.prop('ro.build.version.sdk')}), "
        f"host {host_code}, plugin {plugin_name} ({plugin_code})")

    app = App(dev, None)
    w = Walker(dev, app, "/sdcard/Scripts")
    title_pattern = re.compile(args.title)
    original = app.plugin_enabled(OFFLINE_DOCS)
    results = []

    def record(name, ok, detail):
        results.append(ok)
        log(f"{'PASS' if ok else 'FAIL'} {name:<16} {detail}")

    try:
        if original != "false" and not app.set_plugin_enabled(OFFLINE_DOCS, "false"):
            sys.exit("could not switch the plugin off before the check")
        app.start_main(cold=True)

        kind, text = docs_page(w, args.timeout)
        record("docs-off-before", kind == "unavailable", f"{kind}: {text}")
        ok, detail = toggle(w, app, title_pattern, "true", args.timeout)
        record("switch-on", ok, detail)
        kind, text = docs_page(w, args.timeout)
        record("docs-on", kind == "index", f"{kind}: {text}")
        ok, detail = toggle(w, app, title_pattern, "false", args.timeout)
        record("switch-off", ok, detail)
        kind, text = docs_page(w, args.timeout)
        record("docs-off", kind == "unavailable", f"{kind}: {text}")
    except WalkError as error:
        record("walk", False, str(error))
    finally:
        restore = "true" if original == "unset" else original
        if app.plugin_enabled(OFFLINE_DOCS) != restore:
            restored = app.set_plugin_enabled(OFFLINE_DOCS, restore)
            log(f"switch restored to {restore} (was {original}): {'ok' if restored else 'FAILED'}")
            app.start_main(cold=True)
        else:
            log(f"switch left at {restore} (was {original})")

    passed = sum(results)
    log(f"{passed}/{len(results)} passed")
    return 0 if results and passed == len(results) else 1


if __name__ == "__main__":
    sys.exit(main())
