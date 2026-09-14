// Controlled by run_configuration_probe.py on an exclusively owned emulator.
(function () {
const config = __SDK37_CONFIGURATION_CONFIG__;
const application = context.getApplicationContext();
const output = new java.io.File(context.getFilesDir(), "sdk37-config-" + config.caseId + ".json");
const commandFile = new java.io.File(context.getFilesDir(), "sdk37-config-" + config.caseId + ".command");
const R = Packages.org.autojs.autojs6.R;
const receipt = { caseId: config.caseId, target: context.getApplicationInfo().targetSdkVersion,
    sdk: android.os.Build.VERSION.SDK_INT, ready: false, events: [], commands: [], errors: [] };
let current = null, interval = null, lastCommand = 0;
const watched = {};
function save() {
    const temporary = new java.io.File(output.getAbsolutePath() + ".tmp");
    files.write(temporary.getAbsolutePath(), JSON.stringify(receipt));
    if (!temporary.renameTo(output)) throw new Error("Receipt replacement failed");
}
function id(a) { return String(java.lang.System.identityHashCode(a)); }
function configuration(c) { return { orientation: c.orientation, widthDp: c.screenWidthDp, heightDp: c.screenHeightDp,
    smallestWidthDp: c.smallestScreenWidthDp, densityDpi: c.densityDpi, screenLayout: c.screenLayout,
    keyboard: c.keyboard, keyboardHidden: c.keyboardHidden, uiMode: c.uiMode }; }
function event(name, a, extra) {
    receipt.events.push({ time: Date.now(), event: name, activity: String(a.getClass().getName()), instance: id(a),
        configuration: configuration(a.getResources().getConfiguration()), extra: extra || null });
    save();
}
function bounds(view) {
    const p = java.lang.reflect.Array.newInstance(java.lang.Integer.TYPE, 2);
    view.getLocationOnScreen(p);
    return { x: p[0], y: p[1], width: view.getWidth(), height: view.getHeight() };
}
function walk(view, predicate) {
    if (predicate(view)) return view;
    if (view instanceof android.view.ViewGroup) {
        for (let i = 0; i < view.getChildCount(); i++) { let found = walk(view.getChildAt(i), predicate); if (found) return found; }
    }
    return null;
}
function input(a) {
    if (config.route.indexOf("edit-") === 0) return a.findViewById(R.id.editor_view).editor.getInputView();
    return walk(a.getWindow().getDecorView(), v => v instanceof android.widget.EditText && v.isShown());
}
function snapshot() {
    if (!current || current.isDestroyed()) return null;
    const a = current, decor = a.getWindow().getDecorView();
    const insets = decor.getRootWindowInsets(), ime = android.view.WindowInsets.Type.ime();
    let result = { time: Date.now(), activity: String(a.getClass().getName()), instance: id(a),
        taskId: a.getTaskId(), configuration: configuration(a.getResources().getConfiguration()),
        requestedOrientation: a.getRequestedOrientation(), multiWindow: a.isInMultiWindowMode(),
        softInputMode: a.getWindow().getAttributes().softInputMode, decor: bounds(decor),
        imeVisible: insets ? insets.isVisible(ime) : null, imeBottom: insets ? insets.getInsets(ime).bottom : null };
    if (config.route.indexOf("edit-") === 0) {
        const view = a.findViewById(R.id.editor_view), engine = view.editor;
        result.editor = { type: String(engine.getEngineType()), ready: engine.isReady(), text: String(engine.getTextSnapshot()),
            changed: engine.isTextChanged(), selection: String(engine.getSelection()), bounds: bounds(engine.getView()) };
    }
    const field = input(a);
    if (field) result.input = { className: String(field.getClass().getName()), focused: field.hasFocus(), bounds: bounds(field),
        text: field instanceof android.widget.EditText ? String(field.getText()) : null };
    if (config.route === "main") result.page = a.findViewById(R.id.viewpager).getCurrentItem();
    return result;
}
const lifecycle = new android.app.Application.ActivityLifecycleCallbacks({
    onActivityCreated: function(a, state) {
        if (String(a.getClass().getName()) !== config.activityClass) return;
        const callback = new android.content.ComponentCallbacks({
            onConfigurationChanged: function(c) { event("configuration", a, configuration(c)); },
            onLowMemory: function() { event("lowMemory", a); },
        });
        a.registerComponentCallbacks(callback);
        watched[id(a)] = { activity: a, callback: callback };
        event("create", a, { savedState: state != null });
    },
    onActivityStarted: function(a) {},
    onActivityResumed: function(a) {
        if (String(a.getClass().getName()) === config.activityClass) { current = a; event("resume", a); }
    },
    onActivityPaused: function(a) { if (watched[id(a)]) event("pause", a); },
    onActivityStopped: function(a) { if (watched[id(a)]) event("stop", a); },
    onActivitySaveInstanceState: function(a, state) { if (watched[id(a)]) event("save", a); },
    onActivityDestroyed: function(a) {
        if (!watched[id(a)]) return;
        event("destroy", a, { changingConfigurations: a.isChangingConfigurations() });
        a.unregisterComponentCallbacks(watched[id(a)].callback);
        delete watched[id(a)];
    },
});
function launch() {
    if (config.route === "script") {
        const script = '"ui"; ui.layout(<vertical bg="#ffffff" padding="24"><text text="SDK 37 configuration probe"/><input id="probe" text="initial"/><text text="' + config.caseId + '"/></vertical>); activity.getWindow().addFlags(128);';
        engines.execScript("SDK37 configuration " + config.caseId, script);
    } else {
        if (config.route.indexOf("edit-") === 0) {
            const types = Packages.org.autojs.autojs.ui.edit.editor.engine.EditorEngineType;
            Packages.org.autojs.autojs.ui.edit.editor.engine.EditorEnginePreferences.set(config.route === "edit-ace" ? types.ACE : types.NATIVE);
        }
        const intent = new android.content.Intent().setClassName(context.getPackageName(), config.activityClass).addFlags(0x10008000);
        if (config.route.indexOf("edit-") === 0) intent.putExtra("content", "// initial " + config.caseId).putExtra("name", "sdk37-" + config.caseId + ".js").putExtra("saveEnabled", false).putExtra("runEnabled", false);
        context.startActivity(intent);
    }
}
function execute(command) {
    if (command.action === "launch") launch();
    else if (command.action === "seed") {
        if (!current) throw new Error("No current activity");
        current.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (config.route.indexOf("edit-") === 0) {
            const engine = current.findViewById(R.id.editor_view).editor;
            if (!engine.isReady()) throw new Error("Editor is not ready");
            engine.setText(config.seed); engine.markTextAsChanged(); engine.jumpToEnd(); engine.requestEditorFocus();
        } else {
            if (config.route === "main") {
                current.findViewById(R.id.viewpager).setCurrentItem(current.getPluginsIndex(), false);
                current.findViewById(R.id.toolbar).getMenu().findItem(R.id.action_search).expandActionView();
            } else if (config.route === "log" && !input(current)) {
                current.onOptionsItemSelected(current.findViewById(R.id.toolbar).getMenu().findItem(R.id.action_input_bar));
            }
            const seedInput = input(current);
            if (!seedInput) throw new Error("Editable field missing");
            seedInput.setText(config.seed); seedInput.requestFocus();
        }
    } else if (command.action === "ime") {
        const imeInput = input(current);
        if (!imeInput) throw new Error("Input missing");
        imeInput.requestFocus();
        current.getSystemService(android.content.Context.INPUT_METHOD_SERVICE).showSoftInput(imeInput, 0);
    } else if (command.action === "orientation") current.setRequestedOrientation(command.value);
    else if (command.action === "finish") {
        if (current && !current.isDestroyed()) current.finish();
        for (let key in watched) watched[key].activity.unregisterComponentCallbacks(watched[key].callback);
        application.unregisterActivityLifecycleCallbacks(lifecycle);
        clearInterval(interval);
        receipt.complete = true;
    } else if (command.action !== "snapshot") throw new Error("Unknown command");
    receipt.commands.push({ id: command.id, action: command.action, snapshot: snapshot() });
}
try {
    application.registerActivityLifecycleCallbacks(lifecycle);
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
} catch (error) { receipt.errors.push(String(error) + "\n" + error.stack); save(); }
})();
