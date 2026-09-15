/*
 * SDK 37 packaged example 2: UI-mode script inside a user APK (Roadmap P3.4).
 * Records the edge-to-edge layout facts of Android 15+ (row 35: the content view starts at the
 * top edge and spans the full display) and answers the predictive-back callback (row 36) without
 * consuming it, so the activity finishes the way a user expects. The runner sends BACK after the
 * receipt reports ready.
 *
 * zh-CN: SDK 37 打包示例 2: 用户 APK 内的 UI 模式脚本 (Roadmap P3.4). 记录 Android 15+ 的边到边布局事实
 * (第 35 行: 内容视图从顶边开始并铺满整个显示区), 并响应预测性返回回调 (第 36 行) 而不消费它, 使 Activity 按
 * 用户预期结束. 运行器在回执报告 ready 后发送 BACK.
 */
"ui";
var common = require("./common.js");
var receipt = common.receipt;
var check = common.check;
receipt.example = "ui";
common.save();

ui.layout(
    <frame id="root">
        <vertical id="content" padding="16">
            <text id="status" text="SDK 37 UI example" textSize="18sp" />
        </vertical>
    </frame>
);

ui.emitter.on("back_pressed", function (event) {
    // Not consumed: the dispatcher finishes the activity through the predictive-back path.
    receipt.backPressed = true;
    common.save();
});

events.on("exit", function () {
    receipt.exited = true;
    common.save();
});

function measure() {
    var window = activity.getWindow();
    var decor = window.getDecorView();
    var content = activity.findViewById(android.R.id.content);
    var location = java.lang.reflect.Array.newInstance(java.lang.Integer.TYPE, 2);
    content.getLocationOnScreen(location);
    var metrics = new android.util.DisplayMetrics();
    activity.getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
    var insets = decor.getRootWindowInsets();
    var statusBar = -1;
    var navigationBar = -1;
    if (insets !== null) {
        if (common.api >= 30) {
            var bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
            statusBar = bars.top;
            navigationBar = bars.bottom;
        } else {
            statusBar = insets.getSystemWindowInsetTop();
            navigationBar = insets.getSystemWindowInsetBottom();
        }
    }
    return {
        contentTop: location[1],
        contentHeight: content.getHeight(),
        decorHeight: decor.getHeight(),
        displayHeight: metrics.heightPixels,
        statusBarInset: statusBar,
        navigationBarInset: navigationBar,
    };
}

ui.post(function () {
    var layout = measure();
    receipt.layout = layout;
    check("ui-layout-rendered", function () {
        return String(ui.status.getText()) === "SDK 37 UI example" && ui.status.getWidth() > 0;
    });
    check("ui-edge-to-edge", function () {
        if (common.api < 35) return "not enforced below API 35; recorded only";
        // Enforced edge-to-edge: the content view starts at the top edge and reaches the bottom edge.
        return layout.contentTop === 0 && layout.contentTop + layout.contentHeight === layout.displayHeight && layout.statusBarInset > 0;
    });
    common.commonChecks(function () {
        common.finish({ ready: true });
    });
}, 800);
