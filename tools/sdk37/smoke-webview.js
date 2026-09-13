"ui";

// run_webview_probe.py supplies a fresh case ID, route and loopback fixture URL.
// Only for a disposable emulator with a debuggable host / previewer.
const config = __SDK37_WEBVIEW_CONFIG__;
const receiptFile = new java.io.File(context.getFilesDir(), "sdk37-webview-" + config.caseId + ".json");
const receipt = {
    caseId: config.caseId,
    route: config.route,
    sdk: android.os.Build.VERSION.SDK_INT,
    target: context.getApplicationInfo().targetSdkVersion,
    complete: false,
};
function save() { files.write(receiptFile.getAbsolutePath(), JSON.stringify(receipt)); }

try {
    android.webkit.WebView.setWebContentsDebuggingEnabled(true);
    ui.layout(<vertical bg="#ffffff"><text text="SDK 37 WebView probe" textSize="20sp" padding="12"/><webview id="web" w="*" h="*"/></vertical>);
    receipt.defaultUa = String(ui.web.getSettings().getUserAgentString());
    if (config.route === "ui") {
        if (config.uaMode === "custom") {
            ui.web.attr("userAgent", config.customUa);
        } else if (config.uaMode === "suffix") {
            ui.web.attr("userAgentString", receipt.defaultUa + config.suffix);
        } else if (config.uaMode === "reset") {
            ui.web.attr("userAgent", config.customUa);
            ui.web.getSettings().setUserAgentString(null);
        }
        receipt.settingsUa = String(ui.web.getSettings().getUserAgentString());
        receipt.viewClass = String(ui.web.getClass().getName());
        ui.web.loadUrl(config.url);
    } else if (config.route === "docs") {
        if (!Packages.org.autojs.autojs.util.DocsUtils.isOfflineSourceSelected()) {
            throw new Error("Select offline documentation before running this case");
        }
        let intent = new android.content.Intent(activity, Packages.org.autojs.autojs.ui.doc.DocumentationActivity);
        intent.putExtra("url", "https://offline-docs.autojs6.local/" + config.docsPage);
        activity.startActivity(intent);
    } else if (config.route === "html") {
        let folder = new java.io.File(context.getExternalFilesDir(null), "sdk37-webview-" + config.caseId);
        if (!folder.mkdir()) throw new Error("Cannot create a fresh preview fixture directory");
        let preview = new java.io.File(folder, "index.html");
        files.write(preview.getAbsolutePath(), config.html);
        receipt.previewPath = String(preview.getAbsolutePath());
        let uri = Packages.org.autojs.autojs.util.IntentUtils.getUriOfFile(
            context, preview.getAbsolutePath(), Packages.org.autojs.autojs.external.fileprovider.AppFileProvider.AUTHORITY);
        let parentUri = Packages.org.autojs.autojs.util.IntentUtils.getUriOfFile(
            context, folder.getAbsolutePath(), Packages.org.autojs.autojs.external.fileprovider.AppFileProvider.AUTHORITY);
        // HTML Previewer 1.1 uses the audited v2 Explorer Action contract, not ACTION_VIEW.
        let extras = Packages.org.autojs.plugin.explorer.api.ExplorerActionIntentExtras;
        let intent = new android.content.Intent(Packages.org.autojs.plugin.explorer.api.ExplorerActionPluginActions.EXECUTE);
        intent.setDataAndType(uri, "text/html");
        intent.setClassName(config.htmlPackage, config.htmlPackage + ".HtmlPreviewerActivity");
        let clip = android.content.ClipData.newRawUri("SDK 37 HTML preview", uri);
        clip.addItem(new android.content.ClipData.Item(parentUri));
        intent.setClipData(clip);
        intent.putExtra(extras.ACTION_ID, "html-previewer");
        intent["putExtra(java.lang.String,int)"](extras.PROTOCOL_VERSION, 2);
        intent["putExtra(java.lang.String,long)"](extras.HOST_VERSION_CODE, context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode);
        intent.putExtra(extras.DISPLAY_NAME, "index.html");
        intent["putExtra(java.lang.String,long)"](extras.SIZE, preview.length());
        intent.putExtra(extras.PARENT_URI, parentUri);
        intent.putExtra(extras.SOURCE_SURFACE, "main");
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION | android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        activity.startActivity(intent);
    } else {
        throw new Error("Unknown WebView route: " + config.route);
    }
    receipt.complete = true;
    receipt.ok = true;
} catch (error) {
    receipt.complete = true;
    receipt.ok = false;
    receipt.error = String(error) + "\n" + error.stack;
}
save();
