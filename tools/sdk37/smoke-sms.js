"ui";

// Only run through run_sms_probe.py on its explicitly selected disposable emulator.
const config = __SDK37_SMS_CONFIG__;
const receiptFile = new java.io.File(context.getFilesDir(), "sdk37-sms-" + config.caseId + ".json");
const stopFile = new java.io.File(context.getFilesDir(), "sdk37-sms-" + config.caseId + ".stop");
const receipt = { caseId: config.caseId, target: context.getApplicationInfo().targetSdkVersion,
    sdk: android.os.Build.VERSION.SDK_INT, ready: false, complete: false,
    broadcasts: [], notifications: [], queries: [], errors: [] };
let receiver = null, interval = null;
function save() { files.write(receiptFile.getAbsolutePath(), JSON.stringify(receipt)); }
function optionalString(value) { return value == null ? null : String(value); }
function query() {
    let cursor = null;
    try {
        cursor = context.getContentResolver().query(android.net.Uri.parse("content://sms"),
            ["_id", "address", "body", "date", "contains_otp"], "address = ?", [config.sender], "_id ASC");
        if (cursor == null) throw new Error("SMS query returned no cursor");
        let rows = [];
        while (cursor.moveToNext()) rows.push({ id: String(cursor.getLong(0)), address: String(cursor.getString(1)),
            body: String(cursor.getString(2)), date: Number(cursor.getLong(3)), containsOtp: cursor.getInt(4) });
        receipt.queries.push({ time: Date.now(), rows: rows });
    } catch (error) { receipt.errors.push("query: " + error); }
    finally { if (cursor != null) cursor.close(); }
}
function finish() {
    if (interval != null) clearInterval(interval);
    try { if (receiver != null) context.unregisterReceiver(receiver); }
    catch (error) { receipt.errors.push("unregister: " + error); }
    events.removeAllListeners("notification");
    runtime.events.removeNotificationObserver();
    receipt.complete = true;
    save();
    activity.finish();
}

try {
    ui.layout(<vertical bg="#ffffff"><text text="SDK 37 SMS / notification probe" textSize="20sp" padding="16"/></vertical>);
    activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    let keyguard = context.getSystemService(android.content.Context.KEYGUARD_SERVICE);
    receipt.deviceLocked = keyguard.isDeviceLocked();
    if (receipt.deviceLocked) throw new Error("Unlock the disposable emulator before observing notifications");
    receipt.permissions = {};
    ["READ_SMS", "RECEIVE_SMS"].forEach(function (name) {
        let granted = context.checkSelfPermission("android.permission." + name) === 0;
        receipt.permissions[name] = granted;
        if (!granted) throw new Error(name + " is not granted");
    });
    receipt.defaultSms = String(android.provider.Telephony.Sms.getDefaultSmsPackage(context));
    if (receipt.defaultSms === String(context.getPackageName())) throw new Error("Host must not be the default SMS app");
    let listener = Packages.org.autojs.autojs.core.notification.NotificationListenerService.getInstance();
    if (listener == null || !listener.getConnected()) throw new Error("Notification listener is not connected");
    events.observeNotification();
    events.on("notification", function (notification) {
        if (String(notification.getPackageName()) !== receipt.defaultSms) return;
        receipt.notifications.push({ time: Date.now(), packageName: String(notification.getPackageName()),
            title: optionalString(notification.getTitle()), text: optionalString(notification.getText()),
            extraText: optionalString(notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)),
            bigText: optionalString(notification.extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)) });
        save();
    });
    receiver = new android.content.BroadcastReceiver({ onReceive: function (ctx, intent) {
        let messages = android.provider.Telephony.Sms.Intents.getMessagesFromIntent(intent);
        let body = "", sender = null;
        for (let i = 0; i < messages.length; i++) {
            sender = String(messages[i].getOriginatingAddress());
            body += String(messages[i].getMessageBody());
        }
        if (sender !== config.sender) return;
        receipt.broadcasts.push({ time: Date.now(), action: String(intent.getAction()), address: sender, body: body });
        save();
    } });
    context.registerReceiver(receiver, new android.content.IntentFilter("android.provider.Telephony.SMS_RECEIVED"),
        android.content.Context.RECEIVER_EXPORTED);
    query();
    receipt.ready = true;
    save();
    interval = setInterval(function () {
        if (keyguard.isDeviceLocked()) receipt.errors.push("Device locked during notification observation");
        query();
        save();
        if (stopFile.exists()) finish();
    }, 1000);
} catch (error) {
    receipt.errors.push(String(error) + "\n" + error.stack);
    finish();
}
