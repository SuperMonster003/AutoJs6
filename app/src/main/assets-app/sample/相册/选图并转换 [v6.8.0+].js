/**
 * 选图并转换 [v6.8.0+]
 *
 * gallery 模块由 3-Maple Image 插件 (3.4.0 及以上) 提供, 需先在插件中心安装并启用.
 * 本示例打开插件的选图界面多选最多 5 张图片, 把每张图片转换为长边 1080 像素的 WebP 并保存在原图旁 (名称_converted.webp),
 * 最后用查看器打开转换结果. 选图界面首次使用时会请求照片权限, 用户拒绝时得到 PERMISSION_REQUIRED.
 */

if (!gallery.isAvailable()) {
    toastLog('3-Maple Image 插件不可用, 请在插件中心安装或启用后重试');
    exit();
}

/* 1. gallery.pick 返回图片对象数组, 用户取消时返回 null. */
let images;
try {
    images = gallery.pick({ multiple: true, maxCount: 5, title: '选择要转换的图片' });
} catch (e) {
    if (e instanceof gallery.GalleryError && e.code === 'PERMISSION_REQUIRED') {
        toastLog('未授予照片权限, 无法选图');
        exit();
    }
    throw e;
}
if (!images || images.length === 0) {
    toastLog('已取消选图');
    exit();
}
images.forEach((image) => {
    console.log(image.name, image.width + 'x' + image.height, image.mimeType, image.size + ' 字节');
});

/* 2. gallery.convert 无界面转换, 返回写出的图片信息; 默认文件名已存在时自动加序号, 如 名称_converted (1).webp. */
let outputs = [];
images.forEach((image) => {
    try {
        let written = gallery.convert(image.path, { format: 'webp', quality: 85, longEdge: 1080 });
        console.log('已转换:', written.name, written.width + 'x' + written.height, written.size + ' 字节');
        outputs.push(written.path);
    } catch (e) {
        if (!(e instanceof gallery.GalleryError)) throw e;
        /* code 为 UNSUPPORTED_FORMAT / DECODE_FAILED / LIMIT_EXCEEDED / ACCESS_DENIED 等. */
        console.warn('转换失败 [' + e.code + ']:', image.name, e.message);
    }
});

/* 3. gallery.view 启动查看器后立即返回会话 (EventEmitter); 会话关闭前脚本保持运行. */
if (outputs.length > 0) {
    let session = gallery.view(outputs);
    session.on('page', (page) => console.log('正在查看第 ' + (page.index + 1) + ' 张:', page.path));
    session.on('close', (event) => console.log('查看器已关闭, 原因:', event.reason));
}
