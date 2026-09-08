/* console.setXxx 与 console.show 的调用顺序不再影响结果, 也无需在两者之间 sleep. */

console.setSize(700, 500);
console.setPosition(100, 300);
console.show();
console.log('先设置后显示: 700 x 500, 位于 (100, 300)');
sleep(2000);

console.hide();
sleep(500);

console.show();
console.setSize(500, 700);
console.setPosition(300, 100);
console.log('先显示后设置: 500 x 700, 位于 (300, 100)');
sleep(2000);

console.setGravity('center');
console.log('setGravity("center") 居中显示');
sleep(2000);

console.hide();
