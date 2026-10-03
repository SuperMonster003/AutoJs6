/*
 * Debug-host smoke without instrumentation restarting the accessibility service.
 * Run with RunIntentActivity after installing an AppDebug APK and enabling accessibility.
 * Uses only CodeGenSmokeActivity; receipts and every generated snippet stay in app external files.
 */
let outDir = context.getExternalFilesDir(null).getAbsolutePath() + "/codegen-smoke/";
files.ensureDir(outDir);
let reportPath = outDir + "blackbox-result.json";
let report = { passed: false, cases: [], phase: "starting" };
function save() { files.write(reportPath, JSON.stringify(report, null, 2)); }
save();

let finished = false;
let activityName = "org.autojs.autojs.ui.floating.codegen.CodeGenSmokeActivity";
let watchdog = threads.start(function () {
    sleep(120000);
    if (!finished) {
        report.error = "Smoke timed out";
        save();
        engines.myEngine().forceStop();
    }
});
let page = null;
try {
    let intent = new android.content.Intent();
    intent.setClassName(context.getPackageName(), activityName);
    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(intent);
    waitUntil("Fixture resumed", function () {
        page = runtime.app.getCurrentActivity();
        return page && String(page.getClass().getName()) === activityName;
    });
    let accessibility = org.autojs.autojs.core.accessibility;
    waitUntil("Accessibility bound", function () { return accessibility.AccessibilityService.Companion.getInstance() !== null; });
    sleep(400);
    let cg = accessibility.codegen;
    let Request = cg.CodeGenRequest;
    let attrs = new java.util.HashSet();
    ["ID", "TEXT", "DESC"].forEach(function (name) { attrs.add(Request.Attr.valueOf(name)); });
    let styles = [Request.Style.CLASSIC, Request.Style.FLOW];
    let modes = [Request.Mode.WAIT.INSTANCE, new Request.Mode.WAIT_TIMEOUT(3000), Request.Mode.CHECK.INSTANCE];

    function capture() {
        let raw = accessibility.AccessibilityService.Companion.getInstance().getRootInActiveWindow();
        assert(raw !== null, "No active root");
        assert(String(raw.getPackageName()) === String(context.getPackageName()), "Fixture not active");
        return new accessibility.inspector.InspectorNode(accessibility.NodeInfo.Companion.capture(context, raw));
    }
    function select(root, predicate) {
        let result = new java.util.ArrayList();
        let nodes = accessibility.inspector.InspectorNodes.INSTANCE.preOrder(root);
        for (let i = 0; i < nodes.size(); i++) if (predicate(nodes.get(i))) result.add(nodes.get(i));
        return result;
    }
    function generate(root, targets, scope, mode, action, style) {
        let request = new Request(targets, scope, mode, action, Request.Receiver.AUTO, attrs, style);
        let result = new cg.CodeGenEngine().generate(root, request, new cg.CancellationSignal());
        assert(String(result.getClass().getSimpleName()) === "CodeGenResult", String(result));
        return String(result.getPreferred().getCode());
    }
    function generateOne(desc, mode, action, style) {
        let root = capture();
        let targets = select(root, function (n) { return String(n.desc()) === desc; });
        assert(targets.size() === 1, "Expected one " + desc);
        return generate(root, targets, Request.Scope.SINGLE, mode, action, style);
    }
    function execute(name, code) {
        report.phase = name;
        save();
        files.write(outDir + "blackbox-" + report.cases.length + ".js", code);
        Function(code)();
        report.cases.push({ name: name, code: code });
        save();
    }
    styles.forEach(function (style) {
        modes.forEach(function (mode) {
            page.resetReceipts();
            execute(String(style) + " click " + String(mode), generateOne("codegen-click", mode, Request.Action.CLICK.INSTANCE, style));
            waitUntil("One click", function () { return page.getClicks().get() === 1; });
            execute(String(style) + " long click " + String(mode), generateOne("codegen-click", mode, Request.Action.LONG_CLICK.INSTANCE, style));
            waitUntil("One long click", function () { return page.getLongClicks().get() === 1; });
            let text = "a\"b\\c";
            execute(String(style) + " text " + String(mode), generateOne("codegen-input", mode, new Request.Action.SET_TEXT(text), style));
            waitUntil("Exact text", function () { return ui.run(function () { return String(page.getInput().getText()); }) === text; });
        });
        page.resetReceipts();
        let root = capture();
        let buttons = select(root, function (n) { return String(n.id()).endsWith(":id/codegen_fixture_delete"); });
        assert(buttons.size() === 20, "Expected 20 duplicate buttons");
        let selected = new java.util.ArrayList();
        selected.add(buttons.get(3));
        execute(String(style) + " fourth duplicate", generate(root, selected, Request.Scope.SINGLE, Request.Mode.WAIT.INSTANCE, Request.Action.CLICK.INSTANCE, style));
        waitUntil("Fourth duplicate clicked", function () { return page.getItemClicks().get(3) === 1; });
        for (let i = 0; i < 20; i++) assert(page.getItemClicks().get(i) === (i === 3 ? 1 : 0), "Wrong single target");
        page.resetReceipts();
        execute(String(style) + " all duplicates", generate(root, buttons, Request.Scope.COLLECTION, Request.Mode.WAIT.INSTANCE, Request.Action.CLICK.INSTANCE, style));
        waitUntil("All duplicates clicked once", function () {
            for (let i = 0; i < 20; i++) if (page.getItemClicks().get(i) !== 1) return false;
            return true;
        });
        ui.run(function () { page.getScroll().scrollTo(0, 0); });
        sleep(200);
        execute(String(style) + " scroll forward", generateOne("codegen-scroll", new Request.Mode.WAIT_TIMEOUT(3000), Request.Action.SCROLL_FORWARD.INSTANCE, style));
        waitUntil("Forward scroll", function () { return ui.run(function () { return page.getScroll().getScrollY(); }) > 0; });
        sleep(400);
        let before = ui.run(function () { return page.getScroll().getScrollY(); });
        execute(String(style) + " scroll backward", generateOne("codegen-scroll", Request.Mode.CHECK.INSTANCE, Request.Action.SCROLL_BACKWARD.INSTANCE, style));
        waitUntil("Backward scroll", function () { return ui.run(function () { return page.getScroll().getScrollY(); }) < before; });
    });
    let root = capture();
    let titleId = String(context.getPackageName()) + ":id/title";
    let title = select(root, function (n) { return String(n.id()) === titleId; });
    assert(title.size() === 1, "Expected application title");
    let titleCode = generate(root, title, Request.Scope.SINGLE, Request.Mode.CHECK.INSTANCE, Request.Action.NONE.INSTANCE, Request.Style.CLASSIC);
    assert(titleCode.indexOf(titleId) >= 0, "Cross-package ID was not qualified");
    execute("Cross-package ID", titleCode + "\nif (!found) throw new Error('Title missing');");
    report.passed = true;
    report.phase = "complete";
} catch (error) {
    report.error = String(error);
    report.stack = error.stack ? String(error.stack) : "";
} finally {
    finished = true;
    watchdog.interrupt();
    save();
    if (page && String(page.getClass().getName()) === activityName) ui.run(function () { page.finish(); });
}

function assert(condition, message) { if (!condition) throw new Error(message); }
function waitUntil(message, condition) {
    let deadline = Date.now() + 8000;
    while (Date.now() < deadline) {
        if (condition()) return;
        sleep(100);
    }
    assert(condition(), message);
}
