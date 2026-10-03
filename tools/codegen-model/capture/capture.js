/*
 * Persistent capture worker for controlled fixture data. Started once through RunIntentActivity.
 * The collector writes atomic requests under the host's own external-files/codegen-model folder.
 * Only the exact configured fixture package may be captured. Private truth never enters this code.
 */
// Each launched copy lives beside its own config. A previous worker finishing late cannot
// overwrite this run's response or snapshot, even after an interrupted desktop collector.
let control = new java.io.File(engines.myEngine().getSource().getFullPath()).getParent() + "/";
let config = JSON.parse(files.read(control + "config.json"));
if (config.format !== 1 || !/^[A-Za-z0-9._-]+$/.test(config.runId) ||
    config.fixturePackage !== "org.autojs.test.codegen.fixture" ||
    control.slice(-config.runId.length - 1) !== config.runId + "/") throw new Error("Invalid worker configuration");
let serviceApi = org.autojs.autojs.core.accessibility;
let accessibilityTool = new serviceApi.AccessibilityTool(context);
let requestPath = control + "request.json";
let responsePath = control + "response.json";
let lastRequest = "";
let idleAt = Date.now();
let stopped = false;
let menuWasShowing = null;
function atomicJson(path, value) {
    let temporary = path + ".tmp";
    files.write(temporary, JSON.stringify(value));
    let source = new java.io.File(temporary);
    if (!source.renameTo(new java.io.File(path))) throw new Error("Could not publish capture response");
}
atomicJson(control + "worker-ready.json", { format: 1, runId: config.runId });
try {
    while (Date.now() - idleAt < 180000) {
        let request = null;
        try { if (files.exists(requestPath)) request = JSON.parse(files.read(requestPath)); } catch (_) {}
        if (!request || request.runId !== config.runId || request.requestId === lastRequest) {
            sleep(30);
            continue;
        }
        lastRequest = request.requestId;
        idleAt = Date.now();
        if (request.command === "stop") { stopped = true; break; }
        let response = { format: 1, runId: config.runId, requestId: request.requestId, stateId: request.stateId, ok: false };
        try {
            if (!/^[A-Za-z0-9._-]+$/.test(String(request.requestId)) ||
                !/^[A-Za-z0-9._-]+$/.test(String(request.stateId))) throw new Error("Invalid capture identity");
            if (request.command === "prepare-inspector") {
                let inspector = org.autojs.autojs.AutoJs.getInstance().getLayoutInspector();
                inspector.clearCapture();
                menuWasShowing = org.autojs.autojs.ui.floating.FloatyWindowManger.isCircularMenuShowing();
                ui.run(function () { org.autojs.autojs.ui.floating.FloatyWindowManger.showCircularMenuIfNeeded(context); });
                response.ok = true;
                atomicJson(responsePath, response);
                continue;
            }
            if (request.command === "read-inspector") {
                // Read the result of a real layout-inspector UI capture; do not synthesize one here.
                let captured = org.autojs.autojs.AutoJs.getInstance().getLayoutInspector().getCapture();
                if (!captured || String(captured.getRoot().getPackageName()) !== config.fixturePackage) {
                    throw new Error("Capture the fixture using the layout-inspector menu first");
                }
                let savedInspector = serviceApi.LayoutSnapshotFiles.INSTANCE.save(captured.getRoot());
                if (!files.copy(savedInspector.getAbsolutePath(), control + "inspector.layout.json")) {
                    throw new Error("Could not copy inspector snapshot");
                }
                savedInspector.delete();
                response.ok = true;
                atomicJson(responsePath, response);
                continue;
            }
            if (request.command !== "capture") throw new Error("Unknown capture command");
            let deadline = Date.now() + 5000;
            let raw = null;
            while (Date.now() < deadline) {
                // Use the same cache invalidation as LayoutInspector before each fresh tree.
                accessibilityTool.clearCache();
                let service = serviceApi.AccessibilityService.Companion.getInstance();
                raw = service && service.getRootInActiveWindow();
                if (raw && String(raw.getPackageName()) === config.fixturePackage && raw.refresh()) break;
                raw = null;
                sleep(50);
            }
            if (!raw) throw new Error("Fixture is not the active accessible window");
            let root = serviceApi.NodeInfo.Companion.capture(context, raw);
            let saved = serviceApi.LayoutSnapshotFiles.INSTANCE.save(root);
            let destination = control + "snapshot.layout.json";
            if (!files.copy(saved.getAbsolutePath(), destination)) throw new Error("Could not copy the captured layout");
            // Only remove the exact file returned by our successful save.
            saved.delete();
            response.ok = true;
            response.snapshot = destination;
            response.packageName = String(raw.getPackageName());
        } catch (error) {
            response.error = String(error);
        }
        atomicJson(responsePath, response);
    }
} finally {
    if (menuWasShowing === false) {
        ui.run(function () { org.autojs.autojs.ui.floating.FloatyWindowManger.hideCircularMenuIfNeeded(); });
    }
    atomicJson(control + "worker-stopped.json", { format: 1, runId: config.runId, requested: stopped });
}
