// Opens a page of the host for a screenshot (docs/settings/ROADMAP.md P2, E3): the settings pages are not exported,
// so the activity AJ6_ACTIVITY is started from inside the app. Run by tools/settings/device_store_check.py ("page").
// zh-CN: 打开宿主的一个页面以便截屏 (ROADMAP P2, E3): 设置页未导出, 因此在应用内启动 AJ6_ACTIVITY.

var MARK = 'AJ6SET';
try {
    var intent = new android.content.Intent().setClassName(context.getPackageName(), AJ6_ACTIVITY);
    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(intent);
    console.log(MARK + ' PASS page-started ' + AJ6_ACTIVITY);
} catch (e) {
    console.log(MARK + ' FAIL page-started ' + AJ6_ACTIVITY + ' threw ' + e);
}
console.log(MARK + ' done');
