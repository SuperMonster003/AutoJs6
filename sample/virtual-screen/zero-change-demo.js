/**
 * 零改动脚本示例 —— 本文件里没有任何虚拟屏相关代码.
 *
 * 这是一个完全普通的 AutoJs6 脚本: 启动应用、截图、找色、点击、滑动、控件查找、返回键.
 * 想让它在后台虚拟屏上运行, **不要改这个文件**, 只需任选一种方式配置运行模式:
 *
 *   1) 文件菜单 →「在虚拟屏幕运行」(单次)
 *   2) 设置 → 脚本运行 → 打开「在虚拟屏幕运行脚本」(之后所有脚本默认生效)
 *   3) 所在项目 project.json 里写 {"useFeatures": ["virtualScreen"]}
 *   4) 外部 Intent/Tasker 带 extra: virtual_screen_mode=1
 *
 * 配置后, captureScreen / click / swipe / selector / app.launchPackage 等所有屏幕 API
 * 会自动作用于虚拟屏, 物理主屏保持可用; 脚本本身一行都不用动.
 *
 * 前置: Shizuku 已运行并授权 AutoJs6; Android 12+ (推荐 14+ 以获得独立焦点).
 * 文档: docs/virtual-screen/README.md
 */

'auto';

// 控件树读取需要无障碍服务; 截图与点击在虚拟屏模式下不依赖它.
auto.waitFor();

app.launchPackage('com.android.settings');
sleep(1500);

// 虚拟屏模式下 requestScreenCapture 会直接返回已授权, 不会弹 MediaProjection 对话框.
// 主屏运行时这一行照常走原来的授权流程, 因此同一个脚本在两种模式下都能用.
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

// 找色 / 找图用的就是这张虚拟屏上的画面.
let point = images.findColor(img, '#ffffff', { threshold: 8 });
if (point !== null) {
    console.log('找到颜色点:', point.x, point.y);
    click(point.x, point.y);
    sleep(400);
}
img.recycle();

// 坐标是脚本原有的坐标系; 默认虚拟屏尺寸跟随物理屏, 无需换算.
swipe(540, 1500, 540, 500, 600);
sleep(400);

// 控件选择器会自动只看到虚拟屏上的窗口.
let node = selector().clickable(true).findOne(1000);
if (node !== null) {
    console.log('控件:', node.text());
} else {
    console.log('本次没有找到可点击控件');
}

back();
console.log('完成 —— 这段代码里没有任何 virtualScreen.* 调用');
