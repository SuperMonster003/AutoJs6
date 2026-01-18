/**
 * Create by TonyJiangWJ on Nov 15, 2023.
 * Modified by SuperMonster003 as of Jan 18, 2026.
 */
!function sampleForPaddleOcr() {

    let currentEngine = engines.myEngine();
    let runningEngines = engines.all();
    let currentSource = `${currentEngine.getSource()}`;
    if (runningEngines.length > 1) {
        runningEngines.forEach(compareEngine => {
            let compareSource = `${compareEngine.getSource()}`;
            if (currentEngine.getId() !== compareEngine.getId() && compareSource === currentSource) {
                // Force stop scripts with the same name.
                // zh-CN: 强制关闭同名的脚本.
                compareEngine.forceStop();
            }
        });
    }

    if (!requestScreenCapture()) {
        // Failed to request screenshot permission.
        toastError('请求截图权限失败');
        exit();
    }

    sleep(500);

    // Use slim model for faster inference.
    // zh-CN: 使用 slim 模型以获得更快速度.
    let useSlim = true;

    // CPU thread count.
    // zh-CN: CPU 线程数量.
    let cpuThreadNum = 4;

    // Whether to request OpenCL.
    // zh-CN: 是否请求 OpenCL.
    let useOpenCL = false;

    // Recognition results and screenshot information.
    // zh-CN: 识别结果和截图信息.
    let result = [];
    let img = null;
    let running = true;
    let capturing = false;

    // Get status bar height offset.
    // zh-CN: 获取状态栏高度偏移.
    let offset = /* -getStatusBarHeightCompat(); */ 0;

    // Draw OCR results.
    // zh-CN: 绘制 OCR 结果.
    let window = floaty.rawWindow(
        <canvas id="canvas" layout_weight="1"/>,
    );

    // Set float window position.
    // zh-CN: 设置悬浮窗位置.
    ui.post(() => {
        window.setPosition(0, offset);
        window.setSize(WIDTH, HEIGHT);
        window.setTouchable(false);
        window.exitOnClose();
    });

    // Operation buttons.
    // zh-CN: 操作按钮.
    let clickButtonWindow = floaty.rawWindow(
        <vertical>
            {/* Screenshot OCR */}
            <button id="captureAndOcr" text="截图识别"/>
            {/* Exit */}
            <button id="closeBtn" text="退出"/>
        </vertical>,
    );

    // OCR worker.
    // zh-CN: OCR 工作线程.
    function captureAndOcrAsync() {
        if (capturing) {
            // Busy, please wait.
            toastLog('正在处理中, 请稍候.');
            return;
        }
        capturing = true;

        // Clear last result immediately to avoid drawing stale boxes after capture.
        // zh-CN: 立即清空上一次结果, 避免截取完成后仍显示旧框.
        result = [];

        // Hide overlay windows before capturing to avoid them being captured into screenshot.
        // zh-CN: 截图前隐藏悬浮窗, 避免悬浮窗内容被截图到.
        ui.run(function () {
            clickButtonWindow.setPosition(WIDTH, HEIGHT);
            window.setPosition(WIDTH, HEIGHT);
        });

        threads.start(function () {
            try {
                // Wait for UI to apply window position changes (1-2 frames).
                // zh-CN: 等待 UI 应用窗口位置变化 (1-2 帧).
                sleep(150);

                img && img.recycle();
                img = images.captureScreen();
                if (!img) {
                    // Failed to capture screenshot.
                    toastError('截图失败');
                    return;
                }

                let start = new Date();
                result = ocr.paddle.detect(img, { useSlim, cpuThreadNum, useOpenCL });
                // OCR cost time.
                toastLog(`OCR 耗时 ${new Date() - start}ms`);
            } catch (e) {
                toastError(e);
                exit();
            } finally {
                capturing = false;

                // Restore overlay windows.
                // zh-CN: 恢复悬浮窗位置.
                ui.run(function () {
                    window.setPosition(0, offset);
                    window.setSize(WIDTH, HEIGHT);
                    window.setTouchable(false);

                    clickButtonWindow.setPosition(
                        cX(0.5) - ~~(clickButtonWindow.getWidth() / 2),
                        cY(0.65),
                    );
                });
            }
        });
    }

    clickButtonWindow['captureAndOcr'].click(function () {
        captureAndOcrAsync();
    });

    clickButtonWindow['closeBtn'].click(function () {
        exit();
    });

    clickButtonWindow.exitOnClose();

    let paint = new Paint();
    paint.setStrokeWidth(1);
    paint.setTypeface(Typeface.DEFAULT_BOLD);
    paint.setTextAlign(Paint.Align.LEFT);
    paint.setAntiAlias(true);
    paint.setStrokeJoin(Paint.Join.ROUND);
    paint.setDither(true);

    window.canvas.on('draw', function (canvas) {
        if (!running || capturing) return;

        // Clear canvas.
        // zh-CN: 清空画布.
        canvas.drawColor(0xFFFFFF, android.graphics.PorterDuff.Mode.CLEAR);
        if (result && result.length > 0) {
            for (let i = 0; i < result.length; i++) {
                let ocrResult = result[i];
                drawRectAndText(ocrResult.label, ocrResult.bounds, '#00ff00', canvas, paint);
            }
        }
    });

    // Keep script alive.
    // zh-CN: 保持脚本运行.
    keepAlive();

    events.on('exit', () => {
        // Mark stop to avoid canvas crash.
        // zh-CN: 标记停止, 避免 canvas 导致闪退.
        running = false;
        // Remove all listeners.
        // zh-CN: 撤销监听.
        window.canvas.removeAllListeners();
        // Recycle image.
        // zh-CN: 回收图片.
        img && img.recycle();
    });

    /**
     * Draw text and rectangle.
     * zh-CN: 绘制文本和方框.
     */
    function drawRectAndText(desc, rect, colorStr, canvas, paint) {
        let color = colors.parseColor(colorStr);

        paint.setStrokeWidth(1);
        paint.setStyle(Paint.Style.STROKE);
        // Invert color.
        // zh-CN: 反色.
        paint.setARGB(255, 255 - (color >> 16 & 0xff), 255 - (color >> 8 & 0xff), 255 - (color & 0xff));
        canvas.drawRect(rect, paint);

        paint.setARGB(255, color >> 16 & 0xff, color >> 8 & 0xff, color & 0xff);
        paint.setStrokeWidth(1);
        paint.setTextSize(20);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawText(desc, rect.left, rect.top, paint);

        paint.setTextSize(10);
        paint.setStrokeWidth(1);
        paint.setARGB(255, 0, 0, 0);
    }

    /**
     * Get status bar height.
     * zh-CN: 获取状态栏高度.
     */
    function getStatusBarHeightCompat() {
        let result = 0;
        let resId = context.getResources().getIdentifier('status_bar_height', 'dimen', 'android');
        if (resId > 0) {
            result = context.getResources().getDimensionPixelOffset(resId);
        }
        if (result <= 0) {
            result = context.getResources().getDimensionPixelOffset(R.dimen.dimen_25dp);
        }
        return result;
    }

    // Run once at start.
    // zh-CN: 启动后先执行一次.
    captureAndOcrAsync();

}();
