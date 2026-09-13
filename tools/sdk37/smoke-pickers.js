ui.layout(<vertical bg="#164234"><text text="SDK 37 storage pickers" textColor="#ffffff" textSize="22sp" padding="16"/></vertical>);
const pickerResults = {};
ui.emitter.on("activity_result", function (request, result, data) {
    pickerResults[request] = { code: result, uri: data && data.getData() ? String(data.getData()) : null, flags: data ? data.getFlags() : 0 };
});
threads.start(function () {
    const resolver = context.getContentResolver();
    const directory = new java.io.File(android.os.Environment.getExternalStoragePublicDirectory("Download"), "sdk37-" + config.caseId);
    const document = new java.io.File(directory, "sdk37-" + config.caseId + ".txt");
    const imagePath = new java.io.File(android.os.Environment.getExternalStoragePublicDirectory("Pictures"), "sdk37-picker-" + config.caseId + ".png");
    const content = "SDK37 picker " + config.caseId;
    const color = android.graphics.Color["rgb(int,int,int)"](parseInt(config.caseId.substring(0, 2), 16), parseInt(config.caseId.substring(2, 4), 16), parseInt(config.caseId.substring(4, 6), 16));
    let imageUri = null;
    function choose(name, intent, request, cancelled) {
        let focused = false;
        for (let i = 0; i < 100 && !focused; i++) {
            ui.run(() => { focused = activity.hasWindowFocus(); });
            if (!focused) sleep(100);
        }
        requireProbe(focused, "Script Activity did not regain focus before " + name);
        receipt.pending = { name: name, documentName: String(document.getName()) };
        saveReceipt();
        ui.run(() => activity.startActivityForResult(intent, request));
        let deadline = Date.now() + 45000;
        while (!pickerResults[request] && Date.now() < deadline) sleep(100);
        let result = pickerResults[request];
        delete receipt.pending;
        requireProbe(result !== undefined, "Picker did not return: " + name);
        requireProbe(result.code === (cancelled ? 0 : -1), "Unexpected picker result: " + JSON.stringify(result));
        requireProbe(cancelled ? result.uri === null : result.uri !== null && (result.flags & 1) !== 0, "Unexpected URI grant");
        return result;
    }
    try {
        requireProbe(directory.mkdir(), "Could not reserve the picker directory");
        files.write(document.getAbsolutePath(), content);
        let bitmap = android.graphics.Bitmap.createBitmap(18, 14, android.graphics.Bitmap.Config.ARGB_8888);
        bitmap["eraseColor(int)"](color);
        let wrapper = Packages.org.autojs.autojs.core.image.ImageWrapper.ofBitmap(runtime, bitmap);
        images.save(wrapper, imagePath.getAbsolutePath(), "png"); wrapper.recycle();
        media.scanFile(imagePath.getAbsolutePath());
        for (let i = 0; i < 60 && imageUri === null; i++) {
            let c = resolver.query(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ["_id"], "_display_name=?", [imagePath.getName()], null);
            try { if (c && c.moveToFirst()) imageUri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0)); }
            finally { if (c) c.close(); }
            if (imageUri === null) sleep(100);
        }
        requireProbe(imageUri !== null, "Picker image not indexed");
        function docIntent() {
            let intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).setType("text/plain").addCategory(android.content.Intent.CATEGORY_OPENABLE);
            intent.putExtra("android.provider.extra.INITIAL_URI", android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download/sdk37-" + config.caseId));
            return intent;
        }
        function photoIntent() { return new android.content.Intent("android.provider.action.PICK_IMAGES").setType("image/*"); }
        probe("documentSelect", function () {
            let result = choose("documentSelect", docIntent(), 801, false), uri = android.net.Uri.parse(result.uri);
            let input = resolver.openInputStream(uri), scanner = new java.util.Scanner(input, "UTF-8").useDelimiter("\\A");
            try { requireProbe(scanner.hasNext() && String(scanner.next()) === content, "Selected document bytes differ"); }
            finally { scanner.close(); }
            resolver.takePersistableUriPermission(uri, 1);
            let grants = resolver.getPersistedUriPermissions();
            requireProbe(strings(grants).some(value => value.indexOf(result.uri) >= 0), "Persistent document grant missing");
            resolver.releasePersistableUriPermission(uri, 1);
            return result;
        });
        probe("documentCancel", () => choose("documentCancel", docIntent(), 802, true));
        probe("photoSelect", function () {
            let result = choose("photoSelect", photoIntent(), 803, false), uri = android.net.Uri.parse(result.uri);
            let cursor = resolver.query(uri, ["_display_name"], null, null, null);
            try { requireProbe(cursor && cursor.moveToFirst(), "Selected photo metadata missing"); result.displayName = String(cursor.getString(0)); }
            finally { if (cursor) cursor.close(); }
            let input = resolver.openInputStream(uri);
            try {
                let image = android.graphics.BitmapFactory.decodeStream(input);
                requireProbe(image !== null, "Cannot decode selected photo");
                try { requireProbe(image.getWidth() === 18 && image.getHeight() === 14 && image.getPixel(0, 0) === color, "Wrong photo selected"); result.color = color; }
                finally { image.recycle(); }
            } finally { input.close(); }
            return result;
        });
        probe("photoCancel", () => choose("photoCancel", photoIntent(), 804, true));
    } catch (error) { receipt.error = String(error) + "\n" + error.stack; }
    finally {
        try {
            if (imageUri !== null) resolver.delete(imageUri, null, null);
            document.delete(); imagePath.delete();
            requireProbe(directory.delete(), "Picker fixture cleanup failed");
        } catch (error) { receipt.error = "Picker cleanup: " + error; }
        finishProbe();
    }
});
