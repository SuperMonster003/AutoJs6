// A new directory and new MediaStore rows only; never touch user files or clear history.
// Keep an Activity visible so cached-process freezing does not suspend the probe.
ui.layout(<vertical bg="#164234"><text text="SDK 37 storage" textColor="#ffffff" textSize="22sp" padding="16"/></vertical>);
threads.start(function () {
    const File = java.io.File;
    const Environment = android.os.Environment;
    const MediaStore = android.provider.MediaStore;
    const resolver = context.getContentResolver();
    const historyPackage = Packages.org.autojs.autojs.storage.history;
    const directory = new File(Environment.getExternalStorageDirectory(), "sdk37-storage-" + config.caseId);
    requireProbe(directory.mkdir(), "Could not reserve a new shared-storage test directory");
    const path = new File(directory, "中文-file.js").getAbsolutePath();
    const original = "// sdk37 " + config.caseId + " 中文\nlet value = 1;\n";

    probe("files", function () {
        files.write(path, original);
        requireProbe(files.read(path) === original, "files.write/read mismatch");
        let copied = new File(directory, "copy.js").getAbsolutePath();
        requireProbe(files.copy(path, copied), "files.copy failed");
        requireProbe(files.rename(copied, "renamed.js"), "files.rename failed");
        let renamed = new File(directory, "renamed.js").getAbsolutePath();
        requireProbe(files.read(renamed) === original && files.remove(renamed), "Copied bytes or removal mismatch");
        return { path: String(path), names: files.listDir(directory.getAbsolutePath()), bytes: new File(path).length() };
    });
    probe("explorer", function () {
        let p = Packages.org.autojs.autojs.model.explorer;
        let page = new p.ExplorerFileProvider().getExplorerPage(new p.ExplorerDirPage(directory.getAbsolutePath(), null)).blockingGet();
        let children = strings(page);
        requireProbe(children.length === 1, "Explorer did not list the fresh file");
        requireProbe(Packages.org.autojs.autojs.model.script.PathChecker.check(path) === 0, "PathChecker rejected the file");
        return { children: children.length, path: String(page.getPath()) };
    });
    probe("volumes", function () {
        let all = context.getSystemService("storage").getStorageVolumes(), values = [];
        for (let i = 0; i < all.size(); i++) {
            let v = all.get(i), d = v.getDirectory();
            values.push({ uuid: String(v.getUuid()), path: d ? String(d) : null, state: String(v.getState()), primary: v.isPrimary(), removable: v.isRemovable() });
        }
        let mediaVolumes = strings(MediaStore.getExternalVolumeNames(context));
        requireProbe(values.some(v => v.primary && v.state === "mounted"), "Primary storage is not mounted");
        requireProbe(mediaVolumes.indexOf("external_primary") >= 0, "Missing primary MediaStore volume");
        values.filter(v => v.removable && v.state === "mounted").forEach(function (volume) {
            let folder = new File(volume.path, "sdk37-" + config.caseId);
            requireProbe(folder.mkdir(), "Cannot create a test directory on removable volume");
            let file = new File(folder, "\u4e2d\u6587.txt");
            try {
                files.write(file.getAbsolutePath(), original);
                requireProbe(files.read(file.getAbsolutePath()) === original, "Removable storage bytes changed");
                volume.readWrite = true;
            } finally { requireProbe(file.delete() && folder.delete(), "Removable test cleanup failed"); }
        });
        return { storageManager: values, mediaStore: mediaVolumes };
    });
    probe("history", function () {
        let repo = new historyPackage.HistoryRepository(context);
        let dao = historyPackage.HistoryDatabase.Companion.getInstance(context).historyDao();
        repo.recordSavePre(path, new java.lang.String(original).getBytes("UTF-8"), "UTF-8", false);
        let entry = dao.findFileByPath(path);
        requireProbe(entry !== null, "No history entry");
        let revisions = dao.listRevisionsAsc(entry.getFileId());
        requireProbe(revisions.size() === 1, "Expected one fresh revision");
        let restored = String(new java.lang.String(repo.readRevisionBytes(revisions.get(0)), "UTF-8"));
        requireProbe(restored === original, "History bytes changed");
        files.write(path, original + "// modified\n");
        files.write(path, restored);
        requireProbe(files.read(path) === original, "Restored disk bytes changed");
        repo.clearHistoryForPath(path);
        requireProbe(dao.findFileByPath(path) === null, "Test history cleanup failed");
        return { revisions: 1, restored: true };
    });
    probe("trash", function () {
        let repo = new historyPackage.TrashRepository(context);
        let controller = new Packages.org.autojs.autojs.util.DialogUtils.OperationController();
        let item = repo.moveToTrashWithProgress(path, null, null, controller);
        requireProbe(!files.exists(path) && repo.findTrashItem(item.getTrashId()) !== null, "Trash move did not persist");
        repo.restoreTrashItemToPath(item, new File(path), controller);
        requireProbe(files.read(path) === original && repo.findTrashItem(item.getTrashId()) === null, "Trash restore mismatch");
        return { restored: true, blob: String(item.getBlobRelPath()) };
    });
    let imagePath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "sdk37-" + config.caseId + ".png").getAbsolutePath();
    let scannedUri = null;
    probe("media", function () {
        let bitmap = android.graphics.Bitmap.createBitmap(16, 12, android.graphics.Bitmap.Config.ARGB_8888);
        bitmap["eraseColor(int)"](android.graphics.Color.GREEN);
        let wrapped = Packages.org.autojs.autojs.core.image.ImageWrapper.ofBitmap(runtime, bitmap);
        images.save(wrapped, imagePath, "png");
        wrapped.recycle();
        media.scanFile(imagePath);
        let deadline = Date.now() + 15000;
        let lastDimensions = null;
        while (Date.now() < deadline) {
            let cursor = resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ["_id", "width", "height", "_display_name"], "_display_name=?", ["sdk37-" + config.caseId + ".png"], null);
            try {
                if (cursor && cursor.moveToFirst()) {
                    lastDimensions = { width: cursor.getInt(1), height: cursor.getInt(2) };
                    // The row can become visible before the asynchronous scanner fills metadata.
                    if (lastDimensions.width === 16 && lastDimensions.height === 12) {
                        scannedUri = android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0));
                        return { uri: String(scannedUri), width: 16, height: 12 };
                    }
                }
            } finally { if (cursor) cursor.close(); }
            sleep(200);
        }
        throw new Error("media.scanFile did not publish complete image metadata: " + JSON.stringify(lastDimensions));
    });
    probe("mediaTrash", function () {
        requireProbe(scannedUri !== null, "Missing new MediaStore row");
        let values = new android.content.ContentValues();
        values["put(java.lang.String,java.lang.Integer)"]("is_trashed", java.lang.Integer.valueOf(1));
        requireProbe(resolver.update(scannedUri, values, null, null) === 1, "Could not trash own image");
        let args = new android.os.Bundle(); args.putInt("android:query-arg-match-trashed", 1);
        let cursor = resolver.query(scannedUri, ["is_trashed", "date_expires", "_data", "relative_path"], args, null);
        let trashedPath, expires;
        try {
            requireProbe(cursor && cursor.moveToFirst() && cursor.getInt(0) === 1, "Trashed row is not readable when explicitly included");
            trashedPath = String(cursor.getString(2)); expires = cursor.getLong(1);
            requireProbe((trashedPath.indexOf("/.trash-storage/") >= 0) === config.expectTrashDirectory,
                "Unexpected platform trash layout: " + trashedPath);
        }
        finally { if (cursor) cursor.close(); }
        values["put(java.lang.String,java.lang.Integer)"]("is_trashed", java.lang.Integer.valueOf(0));
        requireProbe(resolver.update(scannedUri, values, null, null) === 1, "Could not restore own image");
        requireProbe(files.exists(imagePath), "Restored image file missing");
        requireProbe(resolver.delete(scannedUri, null, null) === 1, "Could not remove test image row");
        scannedUri = null;
        return { trashRestore: true, trashedPath: trashedPath, dateExpires: expires, restoredPath: String(imagePath) };
    });
    probe("contacts", function () {
        // Observation only: the directory-provider change is not a MediaStore permission gate.
        let allowed = context.checkSelfPermission("android.permission.READ_CONTACTS") === 0;
        let outcome;
        try {
            let cursor = resolver.query(android.provider.ContactsContract.Directory.CONTENT_URI, ["_id", "displayName"], null, null, null);
            try { outcome = { count: cursor ? cursor.getCount() : null }; }
            finally { if (cursor) cursor.close(); }
        } catch (error) { outcome = { error: String(error) }; }
        requireProbe(allowed ? outcome.count >= 0 : outcome.error && outcome.error.indexOf("READ_CONTACTS") >= 0,
            "Contacts baseline did not respect the actual READ_CONTACTS permission");
        return { readContacts: allowed, directories: outcome };
    });
    probe("permissionEntry", function () {
        let permission = new Packages.org.autojs.autojs.permission.AllFilesAccessPermission(context);
        requireProbe(permission.has() === Environment.isExternalStorageManager(), "Host permission status differs from platform");
        let intent = new android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
        intent.setData(android.net.Uri.fromParts("package", context.getPackageName(), null));
        let component = intent.resolveActivity(context.getPackageManager());
        requireProbe(component !== null, "All-files settings entry cannot be resolved");
        return { allFiles: permission.has(), component: String(component.flattenToShortString()) };
    });
    // Only files created above are removed; failed cleanup remains visible in the receipt / snapshots.
    try {
        if (scannedUri !== null) resolver.delete(scannedUri, null, null);
        files.remove(imagePath);
        files.remove(path);
        requireProbe(directory.delete(), "Test directory cleanup failed");
    } catch (error) { receipt.error = "Cleanup: " + error; }
    finishProbe();
});
