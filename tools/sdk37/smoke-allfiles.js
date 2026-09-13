// Permission revocation can kill the process. Run once for each state after restarting.
ui.layout(<vertical bg="#164234"><text text="SDK 37 storage permission" textColor="#ffffff" textSize="22sp" padding="16"/></vertical>);
threads.start(function () {
    const permission = new Packages.org.autojs.autojs.permission.AllFilesAccessPermission(activity);
    probe("allFilesState", function () {
        requireProbe(permission.has() === config.expectAllFiles && android.os.Environment.isExternalStorageManager() === config.expectAllFiles,
            "Permission helper / platform state does not match the explicit expectation");
        return { allFiles: permission.has(), platform: android.os.Environment.isExternalStorageManager(), pid: android.os.Process.myPid() };
    });
    probe("permissionEntry", function () {
        let focused = false;
        for (let i = 0; i < 100 && !focused; i++) {
            ui.run(() => { focused = activity.hasWindowFocus(); });
            if (!focused) sleep(100);
        }
        requireProbe(focused, "Permission Activity is not in the foreground");
        ui.run(() => requireProbe(permission.request(), "The production permission helper did not launch Settings"));
        return { requestedFromActivity: true };
    });
    finishProbe();
});
