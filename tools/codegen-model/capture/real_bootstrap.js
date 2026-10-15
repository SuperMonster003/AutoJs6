/* Create external app storage as the app UID. ADB-created Android/data directories can be
 * unreadable to their owning app on scoped-storage devices. This staging folder holds code
 * and configuration only; all captured layouts are written by the worker in app storage.
 */
let boot = new java.io.File(engines.myEngine().getSource().getFullPath()).getParent() + "/";
let config = JSON.parse(files.read(boot + "config.json"));
if (config.format !== 1 || !/^[a-f0-9]{32}$/.test(config.runId) ||
    !boot.endsWith("/Download/autojs6-codegen-bootstrap/" + config.runId + "/")) {
    throw new Error("Invalid bootstrap configuration");
}
let directory = new java.io.File(context.getExternalFilesDir(null), "codegen-real/" + config.runId);
if (!directory.mkdirs() && !directory.isDirectory()) throw new Error("Cannot create private capture directory");
let control = directory.getAbsolutePath() + "/";
if (!files.copy(boot + "config.json", control + "config.json") ||
    !files.copy(boot + "capture.js", control + "capture.js")) {
    throw new Error("Cannot stage capture worker");
}
engines.execScriptFile(control + "capture.js");
