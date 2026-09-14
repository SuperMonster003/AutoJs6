"""Validate configuration evidence without mistaking surviving text for full state retention."""
import re
import hashlib


ACTIVITIES = {
    "script": "org.autojs.autojs.execution.ScriptExecuteActivity",
    "main": "org.autojs.autojs.ui.main.MainActivity",
    "edit-native": "org.autojs.autojs.ui.edit.EditActivity",
    "edit-ace": "org.autojs.autojs.ui.edit.EditActivity",
    "log": "org.autojs.autojs.ui.log.LogActivity",
}


def retained(state, route, seed, dirty=True, repeat=1):
    if not isinstance(state, dict):
        return False
    field = state.get("editor" if route.startswith("edit-") else "input")
    if route.startswith("edit-") and repeat > 1:
        expected = ((seed + "\n") * repeat).encode("utf-8")
        return (isinstance(field, dict) and field.get("textLength") == len(expected)
                and field.get("textSha256") == hashlib.sha256(expected).hexdigest()
                and field.get("draftDirty") is dirty)
    return (isinstance(field, dict) and field.get("text") == seed
            and (not route.startswith("edit-") or field.get("draftDirty", field.get("changed")) is dirty))


def validate_result(result):
    """Return failures, including editor cursor loss; never mutate the original receipt."""
    try:
        return _validate_result(result)
    except (KeyError, TypeError, AttributeError, ValueError):
        return ["Malformed configuration evidence"]


def _validate_result(result):
    errors = []
    if not isinstance(result, dict):
        return ["Missing result"]
    case, route, form, target = (result.get(k) for k in ("caseId", "route", "form", "target"))
    if not isinstance(case, str) or not re.fullmatch(r"[a-f0-9]{32}", case) or route not in ACTIVITIES or form not in ("phone", "tablet") or target not in (36, 37):
        return ["Invalid case, route, form or target"]
    receipt = result.get("receipt")
    if not isinstance(receipt, dict):
        return ["Missing receipt"]
    if (receipt.get("caseId") != case or receipt.get("target") != target or receipt.get("sdk") != 37
            or receipt.get("ready") is not True or receipt.get("complete") is not True):
        errors.append("Receipt identity, platform or completion mismatch")
    if receipt.get("errors") or result.get("error"):
        errors.append("Execution failed")
    if not re.fullmatch(r"[a-f0-9]{64}", result.get("installedApkSha256", "")):
        errors.append("Missing installed APK hash")
    required = {"portrait", "landscape", "keyboard-hidden"}
    if route == "script":
        required.add("requested-landscape")
    if form == "tablet":
        required.update(("split", "restored"))
    stages = result.get("stages")
    if not isinstance(stages, dict) or set(stages) != required or any(not isinstance(s, dict) for s in stages.values()):
        return errors + ["Missing or malformed stages"]
    portrait = stages["portrait"]
    activity = ACTIVITIES[route]
    seed = "// sdk37-unsaved-" + case
    editor_state = result.get("editorState", "dirty")
    repeat = result.get("editorRepeat", 1)
    if type(repeat) is not int or not 1 <= repeat <= 8192:
        return errors + ["Invalid document repetition count"]
    if editor_state not in ("dirty", "clean"):
        return errors + ["Invalid expected editor state"]
    for name, state in stages.items():
        if (state.get("activity") != activity or not state.get("instance")
                or state.get("taskId") != portrait.get("taskId")):
            errors.append(name + ": activity / task mismatch")
        focus = state.get("focusedWindow", "")
        if activity not in focus or "Application Error" in focus or "Application Not Responding" in focus:
            errors.append(name + ": production window not focused")
        if not retained(state, route, seed, dirty=editor_state == "dirty", repeat=repeat):
            errors.append(name + ": text / unsaved state lost")
        if state.get("multiWindow") is not (name == "split"):
            errors.append(name + ": multi-window state mismatch")
        if not isinstance(state.get("imeVisible"), bool):
            errors.append(name + ": missing IME observation")
        # A visible IME flag alone does not prove that the actual input remains usable.
        # A zero inset can mean fullscreen IME extraction; inspect its screenshot separately.
        if state.get("imeVisible") is True and state.get("imeBottom", 0) > 0:
            # A scrollable native editor's child can exceed its clipped viewport.
            bounds = state.get("editor" if route.startswith("edit-") else "input", {}).get("bounds", {})
            decor = state.get("decor", {})
            if not bounds or not decor:
                errors.append(name + ": missing input / window bounds")
            elif bounds.get("height", 0) <= 0 or bounds.get("width", 0) <= 0:
                errors.append(name + ": input viewport is empty")
            elif bounds.get("y", 0) + bounds.get("height", 0) > decor.get("y", 0) + decor.get("height", 0) - state["imeBottom"] + 2:
                errors.append(name + ": input overlaps the IME")
        if route.startswith("edit-"):
            editor = state.get("editor", {})
            if editor.get("ready") is not True or editor.get("type") != ("ACE" if route == "edit-ace" else "NATIVE"):
                errors.append(name + ": requested editor unavailable")
            if not editor.get("selection") or editor.get("selection") != portrait.get("editor", {}).get("selection"):
                errors.append(name + ": editor selection lost")
            if result.get("editorRange") is True and (editor.get("selectionStart"), editor.get("selectionEnd")) != (12, 32):
                errors.append(name + ": editor range lost")
        if route == "main" and state.get("page") != portrait.get("page"):
            errors.append(name + ": selected page lost")
        if route == "log" and state.get("controls"):
            controls = state["controls"]
            if controls.get("fab", {}).get("shown") and controls.get("submit", {}).get("shown"):
                fab, submit = controls["fab"]["bounds"], controls["submit"]["bounds"]
                if (max(fab["x"], submit["x"]) < min(fab["x"] + fab["width"], submit["x"] + submit["width"])
                        and max(fab["y"], submit["y"]) < min(fab["y"] + fab["height"], submit["y"] + submit["height"])):
                    errors.append(name + ": clear button overlaps console submit")
    initial = portrait.get("configuration", {})
    if (initial.get("orientation") != 1 or not isinstance(initial.get("smallestWidthDp"), int)
            or (initial.get("smallestWidthDp", 0) >= 600) != (form == "tablet")):
        errors.append("Initial form / orientation mismatch")
    if stages["landscape"].get("configuration", {}).get("orientation") != 2:
        errors.append("Rotation did not reach landscape")
    if portrait.get("imeVisible") is not True or stages["keyboard-hidden"].get("imeVisible") is not False:
        errors.append("IME show / hide evidence missing")
    if route.startswith("edit-") and portrait.get("editor", {}).get("chrome"):
        initial_chrome = portrait["editor"]["chrome"]
        restored_chrome = stages["keyboard-hidden"].get("editor", {}).get("chrome", {})
        for bar in ("editor_status_bar", "input_method_enhance_bar"):
            initial_height = initial_chrome.get(bar, {}).get("bounds", {}).get("height", 0)
            restored_height = restored_chrome.get(bar, {}).get("bounds", {}).get("height", 0)
            if restored_height < initial_height:
                errors.append("keyboard-hidden: editor chrome not restored: " + bar)
    if form == "tablet":
        full = stages["landscape"].get("configuration", {}).get("widthDp", 0)
        split = stages["split"].get("configuration", {}).get("widthDp", 0)
        if not 0 < split < full or stages["restored"].get("configuration", {}).get("widthDp") != full:
            errors.append("Split-screen resize / restore evidence missing")
        for command in ("splitStart", "splitExit"):
            if result.get(command, {}).get("exitCode") != 0:
                errors.append(command + " failed")
    events = receipt.get("events", [])
    if not isinstance(events, list) or any(not isinstance(e, dict) or e.get("activity") != activity for e in events):
        return errors + ["Malformed lifecycle events"]
    created = {e.get("instance") for e in events if e.get("event") == "create"}
    if route.startswith("edit-") and form == "tablet" and repeat == 8192 and editor_state == "dirty":
        if not any(e.get("event") == "save" and (e.get("extra") or {}).get("draftStorage") == "file" for e in events):
            errors.append("Missing file-backed draft save evidence")
    if not created or not {s.get("instance") for s in stages.values()} <= created:
        errors.append("Missing Activity creation evidence")
    if route == "script":
        if len(created) != 1 or len({s["instance"] for s in stages.values()}) != 1 or any(e.get("event") == "destroy" for e in events):
            errors.append("Running UI script was recreated")
        callbacks = [e for e in events if e.get("event") == "configuration"]
        if not callbacks:
            errors.append("Missing configuration callback evidence")
        requested = stages["requested-landscape"]
        if (requested.get("requestedOrientation") != 0
                or requested.get("configuration", {}).get("orientation") != (1 if form == "tablet" else 2)):
            errors.append("Requested orientation behavior mismatch")
    commands = receipt.get("commands", [])
    if (not isinstance(commands, list) or not commands or any(not isinstance(c, dict) for c in commands)
            or [c.get("id") for c in commands] != list(range(1, len(commands) + 1))
            or commands[0].get("action") != "launch" or commands[-1].get("action") != "finish"):
        errors.append("Missing, duplicate or reordered commands")
    cleanup = result.get("displayCleanup", [])
    if (not isinstance(cleanup, list) or len(cleanup) != 3
            or any(not isinstance(c, dict) or c.get("exitCode") != 0 for c in cleanup)
            or result.get("stop", {}).get("exitCode") != 0):
        errors.append("Host / display cleanup failed")
    return errors
