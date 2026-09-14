// On-demand diagnostics observer, controlled only on an exclusively owned AVD.
(function () {
const config = __SDK37_EXIT_HISTORY_CONFIG__;
const app = context.getApplicationContext();
const output = new java.io.File(context.getFilesDir(), "sdk37-exit-" + config.caseId + ".json");
const commandFile = new java.io.File(context.getFilesDir(), "sdk37-exit-" + config.caseId + ".command");
const R = Packages.org.autojs.autojs6.R;
const receipt = { caseId: config.caseId, pid: android.os.Process.myPid(), target: context.getApplicationInfo().targetSdkVersion,
    ready: false, events: [], commands: [], errors: [] };
let current = null, interval = null, lastCommand = 0;
function save() {
    const temp = new java.io.File(output.getAbsolutePath() + ".tmp");
    files.write(temp.getAbsolutePath(), JSON.stringify(receipt));
    if (!temp.renameTo(output)) throw new Error("Receipt replacement failed");
}
function snapshot() {
    if (!current || current.isDestroyed()) return null;
    let value = { activity: String(current.getClass().getName()), instance: java.lang.System.identityHashCode(current) };
    if (value.activity.endsWith(".ProcessExitHistoryActivity")) {
        value.report = String(current.findViewById(R.id.report).getText());
        value.copyEnabled = current.findViewById(R.id.copy).isEnabled();
        value.refreshEnabled = current.findViewById(R.id.refresh).isEnabled();
        value.title = String(current.findViewById(R.id.toolbar).getTitle());
    }
    return value;
}
function open(className, extra) {
    const intent = new android.content.Intent().setClassName(context.getPackageName(), className).addFlags(0x10000000);
    if (extra) intent.putExtra("message", "SDK37 owned-emulator history probe");
    app.startActivity(intent);
}
const lifecycle = new android.app.Application.ActivityLifecycleCallbacks({
    onActivityCreated: function(a,s) {}, onActivityStarted: function(a) {},
    onActivityResumed: function(a) { current = a; receipt.events.push({ event: "resumed", activity: String(a.getClass().getName()), time: Date.now() }); },
    onActivityPaused: function(a) {}, onActivityStopped: function(a) {},
    onActivitySaveInstanceState: function(a,s) {}, onActivityDestroyed: function(a) { if (current === a) current = null; }
});
function execute(command) {
    let value = { id: command.id, action: command.action };
    if (command.action === "settings") open("org.autojs.autojs.ui.settings.PreferencesActivity");
    else if (command.action === "scroll-entry") {
        const fragment = current.getSupportFragmentManager().findFragmentById(R.id.fragment_preferences);
        const pref = fragment.findPreference("process_exit_history");
        if (!pref) throw new Error("History preference missing");
        value.preferenceTitle = String(pref.getTitle());
        fragment.scrollToPreference(pref);
    } else if (command.action === "refresh") current.findViewById(R.id.refresh).performClick();
    else if (command.action === "copy") {
        current.findViewById(R.id.copy).performClick();
        value.clipboard = String(current.getSystemService(android.content.Context.CLIPBOARD_SERVICE).getPrimaryClip().getItemAt(0).getText());
    } else if (command.action === "recreate") current.recreate();
    else if (command.action === "crash-report") open("org.autojs.autojs.ui.error.CrashReportActivity", true);
    else if (command.action === "self-exit") {
        receipt.selfExitRequested = { pid: android.os.Process.myPid(), time: Date.now() };
        save();
        java.lang.System.exit(0);
    } else if (command.action === "finish") {
        app.unregisterActivityLifecycleCallbacks(lifecycle);
        clearInterval(interval);
        receipt.complete = true;
    } else if (command.action !== "snapshot") throw new Error("Unknown action");
    value.snapshot = snapshot();
    receipt.commands.push(value);
}
app.registerActivityLifecycleCallbacks(lifecycle);
interval = setInterval(function() {
    ui.run(function() {
        try {
            if (commandFile.exists()) {
                let command = JSON.parse(files.read(commandFile.getAbsolutePath()));
                if (command.id > lastCommand) { lastCommand = command.id; execute(command); }
            }
            receipt.current = snapshot();
        } catch (error) { receipt.errors.push(String(error) + "\n" + error.stack); }
        save();
    });
}, 250);
receipt.ready = true;
save();
})();
