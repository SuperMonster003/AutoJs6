/**
 * 编辑图片 [v6.8.0+]
 *
 * gallery 模块由 3-Maple Image 插件 (3.4.0 及以上) 提供, 需先在插件中心安装并启用.
 * 本示例选一张图片并在插件的编辑器里编辑 (裁剪, 旋转, 调整等). 保存后由 AutoJs6 写出结果,
 * 默认保存在原图旁 (名称_edited.扩展名); 也可用 output 指定输出路径, 用 overwrite 覆盖已有文件.
 * 示例使用 Async 形态 (返回 Promise), 在 UI 线程等不能阻塞的地方也可以调用.
 */

if (!gallery.isAvailable()) {
    toastLog('3-Maple Image 插件不可用, 请在插件中心安装或启用后重试');
    exit();
}

gallery.pickAsync({ mimeTypes: ['image/jpeg', 'image/png', 'image/webp'] })
    .then((images) => {
        if (!images) {
            toastLog('已取消选图');
            return null;
        }
        console.log('编辑:', images[0].path);
        /* 用户离开编辑器而未保存时得到 null. keepExif 保留拍摄信息, 位置信息仍会移除. */
        return gallery.editAsync(images[0].path, { format: 'jpeg', quality: 90, keepExif: true });
    })
    .then((output) => {
        if (!output) {
            console.log('未保存');
            return null;
        }
        toastLog('已保存: ' + output);
        /* viewAsync 在查看器显示后兑现. */
        return gallery.viewAsync(output);
    })
    .then((session) => {
        if (session) session.on('close', (event) => console.log('查看器已关闭, 原因:', event.reason));
    })
    .catch((e) => {
        if (e instanceof gallery.GalleryError) {
            /* code 为 PERMISSION_REQUIRED / FILE_NOT_FOUND / OUTPUT_EXISTS / DECODE_FAILED 等. */
            console.error('失败 [' + e.code + ']:', e.message);
        } else {
            throw e;
        }
    });
