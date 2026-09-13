"ui";

// Observes the production JsWebView chooser without replacing its WebChromeClient.
const config = __SDK37_WEB_FILES_CONFIG__;
const output = new java.io.File(context.getFilesDir(), "sdk37-web-files-" + config.caseId + ".json");
const receipt = { caseId: config.caseId, target: context.getApplicationInfo().targetSdkVersion,
    callbacks: [], results: [], errors: [], ready: false };
function save() { files.write(output.getAbsolutePath(), JSON.stringify(receipt)); }
try {
    android.webkit.WebView.setWebContentsDebuggingEnabled(true);
    ui.layout(<vertical bg="#ffffff"><text text="SDK 37 WebView file access" padding="8"/><webview id="web" w="*" h="*"/></vertical>);
    activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    let contextUtils = Packages.org.autojs.autojs.util.ContextUtils;
    let themed = new android.view.ContextThemeWrapper(activity, android.R.style.Theme_Material_Light);
    let parent = new android.widget.FrameLayout(themed);
    let child = ui.inflate(<text text="Parent theme context probe"/>, parent, false);
    let webActivity = contextUtils.findActivity(ui.web.getContext());
    let childActivity = contextUtils.findActivity(child.getContext());
    receipt.contextChecks = {
        webActivity: webActivity != null && webActivity.equals(activity),
        childTheme: child.getContext().equals(themed),
        childActivity: childActivity != null && childActivity.equals(activity),
    };
    if (!receipt.contextChecks.webActivity || !receipt.contextChecks.childTheme || !receipt.contextChecks.childActivity) {
        throw new Error("Root Activity / child theme context regression: " + JSON.stringify(receipt.contextChecks));
    }
    ui.web.events.on("show_file_chooser", function (event, web, callback, params) {
        let intent = params.createIntent();
        receipt.callbacks.push({ time: Date.now(), mode: params.getMode(), permissionMode: params.getPermissionMode(),
            accept: Array.from(params.getAcceptTypes()).map(String), action: String(intent.getAction()),
            type: String(intent.getType()), flags: intent.getFlags() });
        save();
    });
    ui.web.events.on("file_chooser_result", function (event, request, code, data) {
        let uri = data && data.getData();
        receipt.results.push({ time: Date.now(), request: request, code: code,
            uri: uri ? String(uri) : null, flags: data ? data.getFlags() : 0,
            readGranted: uri ? context.checkUriPermission(uri, android.os.Process.myPid(), android.os.Process.myUid(), 1) === 0 : false,
            writeGranted: uri ? context.checkUriPermission(uri, android.os.Process.myPid(), android.os.Process.myUid(), 2) === 0 : false });
        receipt.persistedAfter = String(context.getContentResolver().getPersistedUriPermissions());
        save();
    });
    receipt.persistedBefore = String(context.getContentResolver().getPersistedUriPermissions());
    // This locally supplied HTML has an HTTPS base origin, with no external network / TLS fixture.
    ui.web.loadDataWithBaseURL("https://appassets.androidplatform.net/sdk37/" + config.caseId,
        config.html, "text/html", "UTF-8", null);
    receipt.ready = true;
} catch (error) { receipt.errors.push(String(error) + "\n" + error.stack); }
save();
