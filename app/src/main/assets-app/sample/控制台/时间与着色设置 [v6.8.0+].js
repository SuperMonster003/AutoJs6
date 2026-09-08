/* 时间前缀, 时间格式, 日志着色以及状态栏避让均可通过 console.setXxx 设置, */
/* 也可在浮动控制台的 "更多" 菜单 -> "设置" 中调整. */

console.setTitle('时间与着色');
console.setTimeVisible(true);
console.setTimeFormat('HH:mm:ss');
console.show();

console.log('显示时间前缀 (HH:mm:ss)');
sleep(1500);

console.setTimeFormat('HH:mm:ss.SSS');
console.info('切换为毫秒精度的时间格式');
sleep(1500);

console.setColorful(false);
console.warn('关闭日志着色后, 所有级别使用同一颜色');
console.error('错误级别同样不再着色');
sleep(1500);

console.setColorful(true);
console.setTimeVisible(false);
console.log('恢复默认: 无时间前缀, 按级别着色');
sleep(1500);

console.setAvoidStatusBar(false);
console.setPosition(0, 0);
console.log('关闭状态栏避让后, setPosition(0, 0) 将贴到屏幕左上角');
sleep(2500);

console.hide();
