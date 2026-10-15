/* On-demand real-app capture. Each desktop run owns a separate directory and package allowlist.
 * Layouts use the inspector's NodeInfo/NodeInfoSnapshot envelope, without writing to layouts/.
 * No taps, application launches, uploads, screenshots, or automatic ground-truth decisions.
 */
let control = new java.io.File(engines.myEngine().getSource().getFullPath()).getParent() + "/";
let config = JSON.parse(files.read(control + "config.json"));
if (config.format !== 1 || !/^[a-f0-9]{32}$/.test(config.runId) ||
    !/^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$/.test(config.expectedPackage) ||
    !control.endsWith("/files/codegen-real/" + config.runId + "/")) {
    throw new Error("Invalid real capture configuration");
}
let api = org.autojs.autojs.core.accessibility;
let accessibilityTool = new api.AccessibilityTool(context);
let gson = new com.google.gson.GsonBuilder().disableHtmlEscaping().create();
let lastRequest = "";
let pending = null;
let idleAt = Date.now();
let requestedStop = false;
function atomicJson(name, value) {
    files.write(control + name + ".tmp", JSON.stringify(value));
    if (!new java.io.File(control + name + ".tmp").renameTo(new java.io.File(control + name))) {
        throw new Error("Cannot publish capture response");
    }
}
function clearPending() {
    if (pending !== null) {
        let file = new java.io.File(control + pending);
        if (file.exists() && !file.delete()) throw new Error("Cannot remove temporary layout");
        pending = null;
    }
}
atomicJson("worker-ready.json", { format: 1, runId: config.runId });
try {
    // An abandoned connection releases its single temporary layout after at most two minutes.
    while (Date.now() - idleAt < 120000) {
        let request = null;
        try {
            if (files.exists(control + "request.json")) request = JSON.parse(files.read(control + "request.json"));
        } catch (_) {}
        if (!request || request.runId !== config.runId || request.requestId === lastRequest) {
            sleep(50);
            continue;
        }
        lastRequest = request.requestId;
        idleAt = Date.now();
        if (request.command === "stop") { requestedStop = true; break; }
        let response = { format: 1, runId: config.runId, requestId: request.requestId,
                         stateId: request.stateId, ok: false };
        try {
            if (!/^r[0-9]+$/.test(String(request.requestId))) throw new Error("Invalid request identity");
            clearPending();
            if (request.command === "release") {
                response.ok = true;
            } else if (request.command === "capture") {
                let deadline = Date.now() + 5000;
                let raw = null;
                while (Date.now() < deadline) {
                    accessibilityTool.clearCache();
                    let service = api.AccessibilityService.Companion.getInstance();
                    raw = service && service.getRootInActiveWindow();
                    if (raw && raw.refresh() && String(raw.getPackageName()) === config.expectedPackage) break;
                    raw = null;
                    sleep(100);
                }
                if (!raw) throw new Error("Open the selected app, dismiss overlays and enable AutoJs6 accessibility");
                let root = api.NodeInfo.Companion.capture(context, raw);
                if (String(root.getPackageName()) !== config.expectedPackage) throw new Error("Active package changed");
                let stamp = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", java.util.Locale.US).format(new java.util.Date());
                let snapshot = new api.LayoutSnapshotFiles.LayoutSnapshot(1, "AutoJs6", stamp, api.NodeInfoSnapshot.Companion.of(root));
                let json = String(gson.toJson(snapshot));
                if (json.length > 16000000) throw new Error("Layout is too large");
                pending = "snapshot-" + request.requestId + ".layout.json";
                files.write(control + pending, json);
                response.ok = true;
                response.snapshot = pending;
                response.packageName = config.expectedPackage;
            } else {
                throw new Error("Unknown capture command");
            }
        } catch (error) {
            response.error = String(error);
        }
        atomicJson("response.json", response);
    }
} finally {
    clearPending();
    atomicJson("worker-stopped.json", { format: 1, runId: config.runId, requested: requestedStop });
}
