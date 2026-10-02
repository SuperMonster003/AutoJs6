// Prepare a real visible node for coordinate-independent live/offline region smoke tests.
// zh-CN: 从实际可见节点取得坐标, 用于实时/离线区域框选冒烟测试.
let targetPath = '/sdcard/inspector-target.txt';
let target = files.exists(targetPath) ? files.read(targetPath).trim() : '';
if (target) {
    app.openUrl(target);
    sleep(8000);
} else {
    app.startActivity({ action: 'android.settings.SETTINGS', flags: ['activity_new_task', 'activity_clear_task'] });
    sleep(2500);
}
let candidates = textMatches(/WLAN|Wi-Fi|Bluetooth/i).find();
if (candidates.size() == 0) candidates = textMatches(/.+/).find();
let chosen = null;
for (let i = 0; i < candidates.size(); i++) {
    let node = candidates.get(i);
    let b = node.bounds();
    if (b.top > device.height * 0.18 && b.bottom < device.height * 0.85 && b.width() > 0 && b.height() > 0) {
        chosen = node;
        break;
    }
}
if (!chosen) throw new Error('No visible node below the search bar');
let b = chosen.bounds();
let c = Packages.org.autojs.autojs.util.ContextUtils.withHostConfiguration(context);
let R = Packages.org.autojs.autojs6.R;
let names = ['text_show_layout_hierarchy', 'text_save_layout_to_file', 'text_switch_window',
    'text_inspector_select_region', 'text_inspector_clear_region'];
let labels = {};
names.forEach(function (name) { labels[name] = String(c.getString(R.string[name])); });
files.write('/sdcard/inspector-region-target.json', JSON.stringify({
    point: [b.centerX(), b.centerY()],
    region: [0, Math.max(device.height * 0.12, b.top - device.height * 0.08) | 0,
        Math.min(device.width - 80, Math.max(b.right + 80, device.width * 0.8)) | 0,
        Math.min(device.height * 0.9, b.bottom + device.height * 0.08) | 0],
    query: String(chosen.className()).split('.').pop(),
    packageName: String(chosen.packageName()),
    labels: labels,
}));
app.sendBroadcast('inspect_layout_bounds');
