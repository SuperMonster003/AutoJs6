/* console.rawInput 读取一行文本, console.input 读取一行并作为 JavaScript 表达式求值. */
/* 两者都会阻塞脚本线程直到用户在输入栏提交内容, 因此不能在 UI 线程调用. */

console.show();

let name = console.rawInput('请输入你的名字:');
console.log('你好, %s!', name);

let a = console.input('请输入第一个数字:');
let b = console.input('请输入第二个数字:');
console.log('%s + %s = %s', a, b, a + b);

/* 输入栏默认仅在脚本等待输入时显示, 调用 console.setInputVisible(true) 可使其常驻. */
console.setInputVisible(true);
console.log('输入 exit 结束脚本, 其他内容将被原样打印');

let line;
while ((line = console.rawInput()) !== 'exit') {
    console.info('收到: %s', line);
}

console.setInputVisible(false);
console.log('再见');
