// Requires Android R (API 30)
// zh-CN: 需要安卓 11 (API 30, R) 及以上系统
let capt = automator.captureScreen();
toastLog(`Screenshot size: ${capt.getWidth()} × ${capt.getHeight()}`, 'long');