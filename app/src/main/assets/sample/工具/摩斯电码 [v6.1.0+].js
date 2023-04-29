let str = 'HIM';
let morse = util.morseCode(str);

// The input string is not case-sensitive.
// zh-CN: 输入字符串不区分大小写.

console.log(`string: ${str}`);

// The morse code is '····   ··   --'.
// zh-CN: 摩斯密码为 '····   ··   --'.

console.log(`code: ${morse.code}`);

// The pattern could be used for device.vibrate().
// zh-CN: 模式参数可用于 device.vibrate().

console.log(`pattern: ${morse.pattern}`);

// Call vibrate() if you need your device play this morse code by vibration.
// zh-CN: 可调用 vibrate() 方法使设备按摩斯电码模式振动.

// morse.vibrate();

console.launch();