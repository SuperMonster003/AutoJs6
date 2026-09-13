// Keep these disposable notifications posted until the runner captures SystemUI evidence.
ui.layout(<vertical bg="#164234"><text text="SDK 37 notifications" textColor="#ffffff" textSize="22sp" padding="16"/></vertical>);
threads.start(function () {
    const manager = context.getSystemService("notification");
    const channel = "sdk37-" + config.caseId;
    const baseId = parseInt(config.caseId.substring(0, 7), 16);
    receipt.notificationIds = { standard: baseId, custom: baseId + 1, oversized: baseId + 2, foreground: 191 };
    const serviceIntent = new android.content.Intent(context, Packages.org.autojs.autojs.external.foreground.AppForegroundService);
    function findActive(id) {
        let found = null;
        for (let n of manager.getActiveNotifications()) if (n.getId() === id) found = n.getNotification();
        return found;
    }
    function waitActive(id) {
        // Android can defer showing a foreground-service notification for ten seconds.
        for (let i = 0; i < 200; i++) { let value = findActive(id); if (value) return value; sleep(100); }
        throw new Error("Notification not posted: " + id);
    }
    function customViews(size, copies) {
        let bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
        bitmap["eraseColor(int)"](android.graphics.Color.GREEN);
        let views = new android.widget.RemoteViews(context.getPackageName(), Packages.org.autojs.autojs6.R.layout.widget_script_shortcut);
        views.removeAllViews(Packages.org.autojs.autojs6.R.id.widget);
        for (let i = 0; i < copies; i++) {
            let child = new android.widget.RemoteViews(context.getPackageName(), Packages.org.autojs.autojs6.R.layout.widget_script_shortcut);
            child.setImageViewIcon(Packages.org.autojs.autojs6.R.id.icon, android.graphics.drawable.Icon.createWithBitmap(bitmap));
            child.setTextViewText(Packages.org.autojs.autojs6.R.id.name, "SDK37 " + i);
            views.addView(Packages.org.autojs.autojs6.R.id.widget, child);
        }
        return { views: views, bytesPerImage: bitmap.getAllocationByteCount(), copies: copies };
    }
    try {
        probe("standard", function () {
            notice({ title: "SDK37 standard " + config.caseId, content: "Plain notification", bigContent: "Expanded notification " + config.caseId,
                notificationId: baseId, channelId: channel, isSilent: true });
            let n = waitActive(baseId);
            requireProbe(String(n.extras.getCharSequence("android.bigText")).indexOf(config.caseId) >= 0, "BigText payload missing");
            requireProbe(n.contentView === null && n.bigContentView === null, "Expected standard notification templates");
            return { posted: true, template: String(n.extras.getString("android.template")) };
        });
        probe("foreground", function () {
            ui.run(() => activity.startForegroundService(serviceIntent));
            let n = waitActive(191);
            requireProbe((n.flags & android.app.Notification.FLAG_FOREGROUND_SERVICE) !== 0, "Actual foreground notification missing");
            requireProbe(n.contentView === null && n.bigContentView === null, "Foreground notification is custom");
            return { posted: true, channel: String(n.getChannelId()), flags: n.flags };
        });
        probe("custom", function () {
            let fixture = customViews(32, 1);
            let builder = notice.getBuilder().setCustomContentView(fixture.views).setCustomBigContentView(fixture.views);
            notice(builder, { title: "SDK37 small " + config.caseId, notificationId: baseId + 1, channelId: channel, isSilent: true });
            let n = waitActive(baseId + 1);
            requireProbe(n.contentView !== null && n.bigContentView !== null, "Custom views were not retained");
            return { posted: true, bytesPerImage: fixture.bytesPerImage, copies: fixture.copies };
        });
        probe("oversized", function () {
            // Read this image's resource limits. Resource IDs are never assumed portable.
            let resources = android.content.res.Resources.getSystem();
            let stripId = resources.getIdentifier("config_notificationStripRemoteViewSizeBytes", "integer", "android");
            let warnId = resources.getIdentifier("config_notificationWarnRemoteViewSizeBytes", "integer", "android");
            requireProbe(stripId !== 0 && warnId !== 0, "Notification memory limit resources unavailable");
            let strip = resources.getInteger(stripId), warn = resources.getInteger(warnId);
            let size = 256, copies = Math.floor(strip / (size * size * 4)) + 2;
            requireProbe(copies <= 80, "Refusing an unexpectedly large notification fixture");
            let fixture = customViews(size, copies);
            receipt.customMemory = { strip: strip, warn: warn, bytesPerImage: fixture.bytesPerImage, copies: copies,
                expectedImageBytes: copies * fixture.bytesPerImage };
            saveReceipt();
            let builder = notice.getBuilder().setCustomContentView(fixture.views).setCustomBigContentView(fixture.views);
            notice(builder, { title: "SDK37 oversized " + config.caseId, notificationId: baseId + 2, channelId: channel, isSilent: true });
            // notify() acceptance alone does not mean SystemUI displayed the view.
            sleep(3500);
            return { notifyReturned: true, activeAfterInflation: findActive(baseId + 2) !== null };
        });
    } catch (error) { receipt.error = String(error) + "\n" + error.stack; }
    finishProbe();
    for (let i = 0; i < 600 && !files.exists(config.ackPath); i++) sleep(100);
    [baseId, baseId + 1, baseId + 2].forEach(id => manager.cancel(id));
    manager.deleteNotificationChannel(channel);
    context.stopService(serviceIntent);
    files.remove(config.ackPath);
    receipt.cleaned = true;
    saveReceipt();
});
