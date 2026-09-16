// UI smoke: seed version history + trash data, open TrashActivity and VersionHistoryActivity so the PC side can take
// screenshots, then clean up. Markers are written to OUT for the PC-side runner to synchronize on.
var OUT = "/sdcard/history-ui-smoke-result.txt";
var lines = [];

function log(s) {
    lines.push(String(s));
    files.write(OUT, lines.join("\n") + "\n");
}

function bytesOf(text) {
    return new java.lang.String(text).getBytes("UTF-8");
}

var P = Packages.org.autojs.autojs.storage.history;
var OperationController = Packages.org.autojs.autojs.util.DialogUtils.OperationController;
var Intent = android.content.Intent;
var JFile = java.io.File;

var ctx = context.getApplicationContext();
var hr = new P.HistoryRepository(ctx);
var tr = new P.TrashRepository(ctx);
var root = files.getSdcardPath() + "/AutoJs6-smoke-history-ui";
var items = [];

function open(className) {
    var intent = new Intent();
    intent.setClassName(ctx.getPackageName(), className);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    ctx.startActivity(intent);
}

function cleanup() {
    for (var i = 0; i < items.length; i++) {
        try {
            if (tr.findTrashItem(items[i].getTrashId()) != null) {
                tr.deleteTrashItem(items[i]);
            }
        } catch (e) {
            log("cleanup item error: " + e);
        }
    }
    try {
        if (files.exists(root)) {
            hr.clearHistoryForPath(root);
            files.removeDir(root);
        }
    } catch (e) {
        log("cleanup error: " + e);
    }
}

log("ui smoke start " + new Date().toISOString());
cleanup();
files.createWithDirs(root + "/");

try {
    // A tracked script with a pinned manual snapshot and one SAVE_PRE revision.
    var demo = root + "/demo.js";
    files.write(demo, "console.log('v2');");
    hr.recordManualSnapshot(demo, bytesOf("console.log('v1');"), "UTF-8", false, "before refactor");
    hr.recordSavePre(demo, bytesOf("console.log('v1.5');"), "UTF-8", false, null);
    var notes = root + "/notes.txt";
    files.write(notes, "b");
    hr.recordSavePre(notes, bytesOf("a"), "UTF-8", false, null);

    // Trash: a file, a directory, and a broken item.
    var oldFile = root + "/old-script.js";
    files.write(oldFile, "old");
    items.push(tr.moveToTrashWithProgress(oldFile, null, null, new OperationController()));
    var proj = root + "/my.project";
    files.createWithDirs(proj + "/");
    files.write(proj + "/main.js", "m");
    items.push(tr.moveToTrashWithProgress(proj, null, null, new OperationController()));
    var broken = root + "/broken.txt";
    files.write(broken, "x");
    var brokenItem = tr.moveToTrashWithProgress(broken, null, null, new OperationController());
    items.push(brokenItem);
    new JFile(ctx.getFilesDir(), brokenItem.getBlobRelPath())["delete"]();

    open("org.autojs.autojs.ui.storage.TrashActivity");
    sleep(3000);
    log("TRASH_SHOWN");
    sleep(7000);

    open("org.autojs.autojs.ui.storage.VersionHistoryActivity");
    sleep(3000);
    log("VH_SHOWN");
    sleep(7000);
} catch (e) {
    log("ERROR " + e + " line " + e.lineNumber);
} finally {
    cleanup();
    log("DONE");
}
