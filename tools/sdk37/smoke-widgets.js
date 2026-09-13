// Real framework widget registrations hosted in the disposable script Activity.
ui.layout(<vertical bg="#164234"><text text="SDK 37 ScriptWidget" textColor="#ffffff" textSize="22sp" padding="16"/><frame id="container" w="*" h="180"/></vertical>);
const widgetManager = android.appwidget.AppWidgetManager.getInstance(context);
const component = new android.content.ComponentName(context, Packages.org.autojs.autojs.external.widget.ScriptWidget);
const host = new android.appwidget.AppWidgetHost(activity, parseInt(config.caseId.substring(0, 7), 16));
const ids = [];
for (let i = 0; i < 12; i++) ids.push(host.allocateAppWidgetId());
const pair = ids.slice(-2);
const prefs = context.getSharedPreferences("ScriptWidgets", 0);
const saved = prefs.getAll();
const directory = new java.io.File(android.os.Environment.getExternalStorageDirectory(), "sdk37-widget-" + config.caseId);
requireProbe(directory.mkdir(), "Cannot reserve widget script directory");
const marker = new java.io.File(context.getFilesDir(), "sdk37-widget-click-" + config.caseId + ".txt");
const first = new java.io.File(directory, "first-" + config.caseId + ".js");
const second = new java.io.File(directory, "second-" + config.caseId + ".js");
files.write(first.getAbsolutePath(), "files.write(" + JSON.stringify(String(marker)) + ", 'first');");
files.write(second.getAbsolutePath(), "files.write(" + JSON.stringify(String(marker)) + ", 'second');");
const provider = new Packages.org.autojs.autojs.external.widget.ScriptWidget();
const javaIds = java.lang.reflect.Array.newInstance(java.lang.Integer.TYPE, pair.length);
for (let i = 0; i < pair.length; i++) javaIds[i] = pair[i];
let hostView;
for (let id of pair) requireProbe(widgetManager.bindAppWidgetIdIfAllowed(id, component), "Run appwidget grantbind for the test host first");
host.startListening();
threads.start(function () {
    try {
        probe("widgetMappings", function () {
            requireProbe(pair.every(id => id >= 10), "Multi-digit widget IDs are required");
            prefs.edit().putString("path_" + ids[0], first.getAbsolutePath()).putInt("rc_" + ids[0], 99999).commit();
            pair.forEach(id => prefs.edit().putString("path_" + id, first.getAbsolutePath()).commit());
            ui.run(function () {
                provider.onUpdate(context, widgetManager, javaIds);
                hostView = host.createView(activity, pair[0], widgetManager.getAppWidgetInfo(pair[0]));
                hostView.setLayoutParams(new android.widget.FrameLayout.LayoutParams(-1, -1));
                ui.container.addView(hostView);
            });
            let retained = pair.map(id => ({ id: id, path: String(prefs.getString("path_" + id, null)), requestCode: prefs.getInt("rc_" + id, -1) }));
            receipt.widgetIds = retained;
            let field = context.getClassLoader().loadClass("org.autojs.autojs.external.widget.ScriptWidgets").getDeclaredField("ID_PATTERN");
            field.setAccessible(true);
            let pattern = field.get(null);
            receipt.widgetPattern = { source: String(pattern.pattern()), matches: pair.map(function (id) {
                let matcher = pattern.matcher("path_" + id);
                requireProbe(matcher.find(), "Widget pattern did not match a known key");
                return { widgetId: id, capturedId: String(matcher.group(1)) };
            }) };
            requireProbe(retained.every(row => row.path === String(first) && row.requestCode >= 0), "onUpdate lost a live multi-digit widget mapping: " + JSON.stringify(retained));
            requireProbe(retained[0].requestCode !== retained[1].requestCode, "Two widgets share a request code");
            requireProbe(!prefs.contains("path_" + ids[0]) && !prefs.contains("rc_" + ids[0]), "Obsolete mappings were not removed");
            return retained;
        });
        function clickAndObserve(expected, expectedName) {
            let label = null, ready = false;
            for (let i = 0; i < 50 && !ready; i++) {
                ui.run(function () {
                    let view = hostView.findViewById(Packages.org.autojs.autojs6.R.id.name);
                    label = view ? String(view.getText()) : null;
                    ready = label === expectedName;
                });
                if (!ready) sleep(100);
            }
            requireProbe(ready, "Widget label mismatch: " + label);
            ui.run(() => requireProbe(hostView.findViewById(Packages.org.autojs.autojs6.R.id.widget).performClick(), "Widget click was not handled"));
            for (let i = 0; i < 100; i++) {
                if (marker.exists() && files.read(marker.getAbsolutePath()) === expected) return { label: label, marker: expected };
                sleep(100);
            }
            throw new Error("No script marker after actual widget click");
        }
        probe("widgetFirstClick", () => clickAndObserve("first", "first-" + config.caseId));
        probe("widgetUpdateClick", function () {
            prefs.edit().putString("path_" + pair[0], second.getAbsolutePath()).commit();
            let one = java.lang.reflect.Array.newInstance(java.lang.Integer.TYPE, 1); one[0] = pair[0];
            ui.run(() => provider.onUpdate(context, widgetManager, one));
            return clickAndObserve("second", "second-" + config.caseId);
        });
        probe("widgetLargeIcon", function () {
            let size = new android.graphics.Point();
            activity.getWindowManager().getDefaultDisplay().getRealSize(size);
            let bitmap = android.graphics.Bitmap.createBitmap(size.x, Math.ceil(size.y * 1.6), android.graphics.Bitmap.Config.ARGB_8888);
            let bytes = bitmap.getAllocationByteCount(), error = null;
            try {
                let views = new android.widget.RemoteViews(context.getPackageName(), Packages.org.autojs.autojs6.R.layout.widget_script_shortcut);
                views.setImageViewIcon(Packages.org.autojs.autojs6.R.id.icon, android.graphics.drawable.Icon.createWithBitmap(bitmap));
                try { widgetManager["updateAppWidget(int,android.widget.RemoteViews)"](pair[0], views); }
                catch (failure) { error = String(failure); }
                let expectedRejection = config.expectWidgetIconRejected;
                requireProbe(expectedRejection ? error && error.indexOf("memory") >= 0 : error === null,
                    "Unexpected Icon memory-limit result: " + error);
                return { allocatedBytes: bytes, limit: Math.floor(1.5 * size.x * size.y * 4), rejected: error !== null, error: error };
            } finally { bitmap.recycle(); }
        });
        probe("widgetLargeBitmap", function () {
            let size = new android.graphics.Point();
            activity.getWindowManager().getDefaultDisplay().getRealSize(size);
            let bitmap = android.graphics.Bitmap.createBitmap(size.x, Math.ceil(size.y * 1.6), android.graphics.Bitmap.Config.ARGB_8888);
            let bytes = bitmap.getAllocationByteCount(), error = null;
            try {
                let views = new android.widget.RemoteViews(context.getPackageName(), Packages.org.autojs.autojs6.R.layout.widget_script_shortcut);
                views.setImageViewBitmap(Packages.org.autojs.autojs6.R.id.icon, bitmap);
                try { widgetManager["updateAppWidget(int,android.widget.RemoteViews)"](pair[0], views); }
                catch (failure) { error = String(failure); }
                requireProbe(error && error.indexOf("maximum bitmap memory usage") >= 0, "Oversized Bitmap was not rejected: " + error);
                return { allocatedBytes: bytes, rejected: true, error: error };
            } finally { bitmap.recycle(); }
        });
        // Leave the normal widget visible for the runner's screenshot, after the oversized Icon control.
        let one = java.lang.reflect.Array.newInstance(java.lang.Integer.TYPE, 1); one[0] = pair[0];
        ui.run(function () {
            provider.onUpdate(context, widgetManager, one);
            ui.container.removeAllViews();
            hostView = host.createView(activity, pair[0], widgetManager.getAppWidgetInfo(pair[0]));
            hostView.setLayoutParams(new android.widget.FrameLayout.LayoutParams(-1, -1));
            ui.container.addView(hostView);
        });
        sleep(700);
    } catch (error) { receipt.error = String(error) + "\n" + error.stack; }
    finally {
        try {
            // Restore only the widget preference snapshot on this exclusively owned emulator.
            let editor = prefs.edit().clear(), iterator = saved.entrySet().iterator();
            while (iterator.hasNext()) {
                let entry = iterator.next(), value = entry.getValue();
                if (value instanceof java.lang.String) editor.putString(entry.getKey(), value);
                else if (value instanceof java.lang.Integer) editor.putInt(entry.getKey(), value.intValue());
                else throw new Error("Unexpected ScriptWidgets preference type");
            }
            editor.commit();
            ui.run(() => { host.stopListening(); host.deleteHost(); });
            first.delete(); second.delete(); marker.delete();
            if (!directory.delete()) receipt.error = "Widget fixture cleanup failed";
        } catch (error) { receipt.error = "Widget cleanup: " + error; }
        finishProbe();
    }
});
