// Concatenated with a selected probe by run_framework_probe.py.
const config = __SDK37_FRAMEWORK_CONFIG__;
const receiptFile = new java.io.File(context.getFilesDir(), "sdk37-framework-" + config.caseId + ".json");
const receipt = { caseId: config.caseId, suite: config.suite, target: context.getApplicationInfo().targetSdkVersion,
    sdk: android.os.Build.VERSION.SDK_INT, rows: [], complete: false };
function saveReceipt() { files.write(receiptFile.getAbsolutePath(), JSON.stringify(receipt)); }
function requireProbe(condition, message) { if (!condition) throw new Error(message); }
function probe(name, action) {
    let item = { name: name, ok: false };
    receipt.rows.push(item);
    try { item.value = action(); item.ok = true; }
    catch (error) { item.error = String(error) + "\n" + error.stack; }
    saveReceipt();
}
function finishProbe() { receipt.complete = true; saveReceipt(); }
function strings(collection) {
    let result = [], iterator = collection.iterator();
    while (iterator.hasNext()) result.push(String(iterator.next()));
    return result;
}
