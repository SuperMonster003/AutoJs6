// Repository-level device smoke for the trash / version history subsystem (roadmap P4.5, appendix B items 4, 8, 9
// plus P3.6 / P3.7 / P4.3 behaviour). Runs inside the AutoJs6 process through Rhino; results go to OUT.
// Rhino rules: "var" only (no const in loops), Kotlin "isX" getters are called as methods, companions via .Companion.

var OUT = "/sdcard/history-smoke-result.txt";
var lines = [];
var passCount = 0;
var failCount = 0;

function flush() {
    files.write(OUT, lines.join("\n") + "\n");
}

function log(s) {
    lines.push(String(s));
    flush();
}

function check(name, cond, detail) {
    if (cond) {
        passCount += 1;
    } else {
        failCount += 1;
    }
    log((cond ? "PASS " : "FAIL ") + name + (detail !== undefined ? " :: " + detail : ""));
}

function bytesOf(text) {
    return new java.lang.String(text).getBytes("UTF-8");
}

function textOf(bytes) {
    return String(new java.lang.String(bytes, "UTF-8"));
}

function javaClassName(e) {
    if (e && e.javaException) {
        return String(e.javaException.getClass().getName());
    }
    return String(e);
}

var P = Packages.org.autojs.autojs.storage.history;
var OperationController = Packages.org.autojs.autojs.util.DialogUtils.OperationController;
var Pref = Packages.org.autojs.autojs.core.pref.Pref;
var R = Packages.org.autojs.autojs6.R;
var JFile = java.io.File;
var Arrays = java.util.Arrays;

var ctx = context.getApplicationContext();
var hr = new P.HistoryRepository(ctx);
var tr = new P.TrashRepository(ctx);
var dao = P.HistoryDatabase.Companion.getInstance(ctx).historyDao();

var root = files.getSdcardPath() + "/AutoJs6-smoke-history";
var maxVersionsKey = String(ctx.getString(R.string.key_history_max_versions));
var originalMaxVersions = P.HistoryPrefs.INSTANCE.historyMaxVersions();

function revisionsOf(path) {
    var entry = dao.findFileByPath(path);
    if (entry == null) {
        return [];
    }
    var list = dao.listRevisionsAsc(entry.getFileId());
    var out = [];
    for (var i = 0; i < list.size(); i++) {
        out.push(list.get(i));
    }
    return out;
}

function cleanupAll() {
    try {
        if (files.exists(root)) {
            hr.clearHistoryForPath(root);
            files.removeDir(root);
        }
    } catch (e) {
        log("cleanup error: " + javaClassName(e));
    }
    Pref.putIntSync(maxVersionsKey, originalMaxVersions);
}

log("history smoke start " + new Date().toISOString() + " sdk=" + device.sdkInt + " model=" + device.model);
cleanupAll();
files.createWithDirs(root + "/");

try {
    // ---------------------------------------------------------------- item 8b: manual snapshots
    var aPath = root + "/a.js";
    files.write(aPath, "v1");
    var manual1 = hr.recordManualSnapshot(aPath, bytesOf("v1"), "UTF-8", false, "first");
    check("manual snapshot recorded", manual1 != null);
    check("manual snapshot is pinned", manual1 != null && manual1.getPinned() == true);
    check("manual snapshot op is MANUAL", manual1 != null && String(manual1.getOp()) == "MANUAL");
    check("manual snapshot label", manual1 != null && String(manual1.getLabel()) == "first");

    var manual2 = hr.recordManualSnapshot(aPath, bytesOf("v1"), "UTF-8", false, "again");
    var revsA = revisionsOf(aPath);
    check("same-content manual snapshot does not add a revision", revsA.length == 1, "count=" + revsA.length);
    check("same-content manual snapshot relabels the latest", revsA.length == 1 && String(revsA[0].getLabel()) == "again", revsA.length ? String(revsA[0].getLabel()) : "-");

    // ---------------------------------------------------------------- item 9: max versions = 1 keeps pinned
    Pref.putIntSync(maxVersionsKey, 1);
    check("pref max versions set to 1", P.HistoryPrefs.INSTANCE.historyMaxVersions() == 1);
    hr.recordSavePre(aPath, bytesOf("v2"), "UTF-8", false, null);
    hr.recordSavePre(aPath, bytesOf("v3"), "UTF-8", false, null);
    revsA = revisionsOf(aPath);
    var pinnedCount = 0;
    var unpinned = [];
    for (var i = 0; i < revsA.length; i++) {
        if (revsA[i].getPinned()) {
            pinnedCount += 1;
        } else {
            unpinned.push(revsA[i]);
        }
    }
    check("pinned snapshot survives max versions = 1", pinnedCount == 1, "pinned=" + pinnedCount);
    check("only the newest unpinned revision remains", unpinned.length == 1, "unpinned=" + unpinned.length);
    check("remaining unpinned revision is v3", unpinned.length == 1 && textOf(hr.readRevisionBytes(unpinned[0])) == "v3");
    Pref.putIntSync(maxVersionsKey, originalMaxVersions);

    // ---------------------------------------------------------------- P3.7 file: fileId at move time, history follows restore
    var aEntry = dao.findFileByPath(aPath);
    var itemA = tr.moveToTrashWithProgress(aPath, null, null, new OperationController());
    check("file moved to trash", !files.exists(aPath) && itemA != null);
    check("trash item records history fileId", itemA != null && aEntry != null && String(itemA.getFileId()) == String(aEntry.getFileId()));
    var bPath = root + "/b.js";
    tr.restoreTrashItemToPathWithoutOverwrite(itemA, new JFile(bPath), new OperationController());
    check("restored to a new name", files.exists(bPath) && files.read(bPath) == "v1");
    check("trash item removed after restore", tr.findTrashItem(itemA.getTrashId()) == null);
    var bEntry = dao.findFileByPath(bPath);
    check("history relocated to the restored path", bEntry != null && aEntry != null && String(bEntry.getFileId()) == String(aEntry.getFileId()));
    check("old path no longer tracked", dao.findFileByPath(aPath) == null);
    check("revisions preserved across restore", revisionsOf(bPath).length == 2, "count=" + revisionsOf(bPath).length);

    // ---------------------------------------------------------------- item 4: directory restore with auto rename
    var projPath = root + "/my.project";
    files.createWithDirs(projPath + "/sub/");
    files.write(projPath + "/main.js", "m1");
    hr.recordSavePre(projPath + "/main.js", bytesOf("m0"), "UTF-8", false, null);
    files.write(projPath + "/main.js", "m2");
    files.write(projPath + "/sub/x.txt", "x");
    var itemDir = tr.moveToTrashWithProgress(projPath, null, null, new OperationController());
    check("directory moved to trash", !files.exists(projPath) && itemDir != null && itemDir.isDirectory() == true);
    check("directory trash item has no fileId", itemDir != null && itemDir.getFileId() == null);
    files.createWithDirs(projPath + "/");
    var existing = Arrays.asList(["my.project"]);
    var renamed = String(P.TrashRestoreNaming.INSTANCE.nextIndexedName(existing, "my.project", true));
    check("auto rename picks my.project-1", renamed == "my.project-1", renamed);
    tr.restoreTrashItemToPathWithoutOverwrite(itemDir, new JFile(root, renamed), new OperationController());
    var restoredMain = root + "/" + renamed + "/main.js";
    check("renamed directory restored with content", files.exists(restoredMain) && files.read(restoredMain) == "m2");
    check("nested file restored", files.exists(root + "/" + renamed + "/sub/x.txt"));
    check("directory history relocated by prefix", dao.findFileByPath(restoredMain) != null && dao.findFileByPath(projPath + "/main.js") == null);
    check("directory trash item removed", tr.findTrashItem(itemDir.getTrashId()) == null);

    // ---------------------------------------------------------------- P3.6: broken item (blob missing)
    var cPath = root + "/c.txt";
    files.write(cPath, "c");
    var itemC = tr.moveToTrashWithProgress(cPath, null, null, new OperationController());
    var blobC = new JFile(ctx.getFilesDir(), itemC.getBlobRelPath());
    check("trash blob exists after move", blobC.isFile());
    blobC["delete"]();
    check("isBlobMissing detects the missing blob", tr.isBlobMissing(itemC) == true);
    var brokenList = tr.listTrashItemsWithMissingBlobs();
    var brokenFound = false;
    for (var j = 0; j < brokenList.size(); j++) {
        if (String(brokenList.get(j).getTrashId()) == String(itemC.getTrashId())) {
            brokenFound = true;
        }
    }
    check("broken item listed", brokenFound);
    var brokenError = "";
    try {
        tr.restoreTrashItemToPathWithoutOverwrite(itemC, new JFile(cPath), new OperationController());
    } catch (e) {
        brokenError = javaClassName(e);
    }
    check("restoring a broken item throws TrashBlobMissingException", brokenError.indexOf("TrashBlobMissingException") >= 0, brokenError);
    if (files.exists(cPath)) {
        files.remove(cPath);
    }
    tr.deleteTrashItem(itemC);
    check("broken item removable", tr.findTrashItem(itemC.getTrashId()) == null);

    // ---------------------------------------------------------------- P4.3: damaged archive restores partially
    var dPath = root + "/d";
    files.createWithDirs(dPath + "/");
    files.write(dPath + "/first.txt", "first");
    var big = java.lang.reflect.Array.newInstance(java.lang.Byte.TYPE, 200 * 1024);
    new java.util.Random(7).nextBytes(big);
    files.writeBytes(dPath + "/big.bin", big);
    var itemD = tr.moveToTrashWithProgress(dPath, null, null, new OperationController());
    var blobD = new JFile(ctx.getFilesDir(), itemD.getBlobRelPath());
    var raf = new java.io.RandomAccessFile(blobD, "rw");
    raf.setLength(raf.length() - 4096);
    raf.close();
    var partialError = "";
    try {
        tr.restoreTrashItemToPathWithoutOverwrite(itemD, new JFile(root, "d-restored"), new OperationController());
    } catch (e) {
        partialError = javaClassName(e);
    }
    check("damaged archive throws PartialFailure", partialError.indexOf("PartialFailure") >= 0, partialError);
    check("readable entry restored before the damage", files.exists(root + "/d-restored/first.txt") && files.read(root + "/d-restored/first.txt") == "first");
    check("half-written entry removed", !files.exists(root + "/d-restored/big.bin"));
    check("damaged item stays in the trash", tr.findTrashItem(itemD.getTrashId()) != null);
    tr.deleteTrashItem(itemD);

    // ---------------------------------------------------------------- cleanup pass never deletes broken items
    P.HistoryCleanup.INSTANCE.runAll(ctx);
    check("cleanup keeps restored history", dao.findFileByPath(bPath) != null);
} catch (e) {
    check("no unexpected exception", false, javaClassName(e) + " " + (e.javaException ? e.javaException.getMessage() : e.message) + " line " + e.lineNumber);
} finally {
    cleanupAll();
    log("DONE pass=" + passCount + " fail=" + failCount);
}
