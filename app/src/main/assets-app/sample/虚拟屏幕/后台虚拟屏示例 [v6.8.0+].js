/**
 * 后台虚拟屏示例 [v6.8.0+]
 *
 * 在一块隐藏的显示屏上打开设置并截图, 主屏照常可用.
 *
 * 注意: 这是「显式 JS 模块」的高级示例. 常规用法无需改脚本 —— 见同目录
 * 「零改动脚本示例 [v6.8.0+].js」与 docs/virtual-screen/README.md.
 * 需要: Shizuku 已启动并授权 AutoJs6; 推荐 Android 14+ (虚拟屏拥有独立焦点).
 * 文档: docs/virtual-screen/README.md (应用内「项目文档」亦收录)
 */

let s = virtualScreen.state();
console.log('环境:', JSON.stringify({
    available: s.available,
    sdkInt: s.sdkInt,
    ownFocusSupported: s.ownFocusSupported,
}));

if (!s.available) {
    toast('请先启动 Shizuku 并授权 AutoJs6');
    exit();
}

let screen = virtualScreen.create({ width: 1080, height: 1920, dpi: 480 });
console.log('虚拟屏:', screen.state());

try {
    // 0 = 成功, 1 = 无启动入口, 2 = 启动失败, 3 = displayId 无效
    let code = screen.launch('com.android.settings');
    if (code !== 0) {
        throw new Error('启动失败, 返回码 ' + code);
    }
    sleep(1500);

    let img = screen.capture();                  // ImageWrapper, 可用于 images.* 全套能力
    img.saveTo('/sdcard/Download/virtual-screen.png');
    console.log('帧时间戳(ns):', screen.getFrameTime());

    let p = images.findColor(img, '#ffffff', { threshold: 8 });
    if (p) {
        screen.tap(p.x, p.y);                    // 坐标是虚拟屏的逻辑像素
    }
    screen.swipe(540, 1500, 540, 500, 600);
    screen.key(4);                               // 返回键
    img.recycle();

    toastLog('完成, 主屏未受干扰');
} finally {
    // 显式释放; 即使省略, 脚本退出时运行时也会自动释放.
    screen.release();
}
