/* Executes only fixture selectors after the driver confirms the target window is ready. */
let directory = context.getExternalFilesDir(null).getAbsolutePath() + "/codegen-fixture-smoke/";
let config = JSON.parse(files.read(directory + "config.json"));
let fixturePackage = "org.autojs.test.codegen.fixture";
if (config.format !== 1 || !/^[a-f0-9]{32}$/.test(String(config.runId))) throw new Error("Invalid smoke configuration");
function publish(name, value) {
    let temporary = new java.io.File(directory + name + ".tmp");
    files.write(temporary.getAbsolutePath(), JSON.stringify(value));
    if (!temporary.renameTo(new java.io.File(directory + name))) throw new Error("Could not publish smoke result");
}
publish("ready.json", { runId: config.runId });
let result = { runId: config.runId, ok: false, executed: [] };
try {
    let request = null;
    let deadline = Date.now() + 60000;
    while (Date.now() < deadline) {
        try { request = JSON.parse(files.read(directory + "go.json")); } catch (_) { request = null; }
        if (request && request.runId === config.runId) break;
        request = null;
        sleep(50);
    }
    if (!request) throw new Error("Fixture action gate timed out");
    let serviceApi = org.autojs.autojs.core.accessibility;
    let accessibilityTool = new serviceApi.AccessibilityTool(context);
    for (let action of request.actions) {
        accessibilityTool.clearCache();
        let service = serviceApi.AccessibilityService.Companion.getInstance();
        let active = service && service.getRootInActiveWindow();
        if (!active || String(active.getPackageName()) !== fixturePackage) throw new Error("Fixture is not foreground");
        let b = action.bounds;
        let matches = packageName(fixturePackage).bounds(b[0], b[1], b[2], b[3]).className(action.className).find();
        if (matches.size() !== 1) throw new Error("Expected one target for " + action.key + ": " + matches.size());
        let target = matches.get(0);
        let returned;
        switch (action.action) {
            case "click": returned = target.click(); break;
            case "long-click": returned = target.longClick(); break;
            case "set-text": returned = target.setText(action.text); break;
            case "scroll-forward": returned = target.scrollForward(); break;
            case "scroll-backward": returned = target.scrollBackward(); break;
            default: throw new Error("Unsupported fixture action");
        }
        if (!returned) throw new Error("Action returned false: " + action.key + " " + action.action);
        result.executed.push({ key: action.key, action: action.action });
        sleep(400); // Allow scroll animation and its actual position receipts to settle.
    }
    result.ok = true;
} catch (error) {
    result.error = String(error);
} finally {
    publish("result.json", result);
}
