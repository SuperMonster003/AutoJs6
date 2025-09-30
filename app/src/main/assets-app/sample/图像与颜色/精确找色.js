if (!requestScreenCapture()) {
    toast('请求截图失败');
    stop();
}
var img = captureScreen();
toastLog('开始找色');
// 0x1d75b3 为编辑器默认主题蓝色字体 (if, var 等关键字) 的颜色
// 找到颜色与 0x1d75b3 完全相等的颜色
var point = findColorEquals(img, 0x006699);
if (point) {
    toastLog('x = ' + point.x + ', y = ' + point.y);
} else {
    toastLog('没有找到');
}
