if (!requestScreenCapture()) {
    toast('请求截图失败');
    exit();
}
var img = captureScreen();
// 0xffffff 为白色
toastLog('开始找色');
// 指定在位置 (90, 220) 宽高为 900 × 1000 的区域找色.
// 0xff00cc 是编辑器的深粉红色字体 (字符串) 颜色.
var point = findColor(img, '#ff00cc', {
    region: [ 90, 220, 900, 1000 ],
    threads: 8,
});
if (point) {
    toastLog('x = ' + point.x + ', y = ' + point.y);
} else {
    toastLog('没有找到');
}
