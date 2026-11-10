/**
 * 零改动脚本示例 [v6.8.0+]
 *
 * 本文件没有任何虚拟屏代码. 想让整个脚本运行在后台虚拟屏上, 只需:
 *   文件菜单 →「在虚拟屏幕运行」, 或 设置 → 脚本运行 → 打开「在虚拟屏幕运行脚本」.
 * 之后 images.captureScreen / click / swipe / selector / app.launchPackage 会自动作用于虚拟屏,
 * 主屏保持可用, 脚本一行都不用改.
 *
 * 需要: Shizuku 已启动并授权 AutoJs6; Android 12+ (推荐 14+).
 * 文档: docs/virtual-screen/README.md
 */

'auto';

auto.waitFor();
app.launchPackage('com.android.settings');
sleep(1500);

// 虚拟屏模式下直接返回已授权, 不弹 MediaProjection 对话框.
if (!images.requestScreenCapture(false)) {
    toast('无法获取截图权限');
    exit();
}

let img = images.captureScreen();
if (img === null) {
    toast('截图失败');
    exit();
}

let shot = '/sdcard/Download/virtual-screen-capture.png';
img.saveTo(shot);
console.log('截图已保存:', shot);

let point = images.findColor(img, '#ffffff', { threshold: 8 });
if (point !== null) {
    click(point.x, point.y);
    sleep(400);
}
img.recycle();

swipe(540, 1500, 540, 500, 600);
let node = selector().clickable(true).findOne(1000);
console.log('控件:', node === null ? '未找到' : node.text());

back();
console.log('完成: 这段代码里没有任何 virtualScreen.* 调用');
