let delay = 240;

// 'SOS' in Morse (async)
// zh-CN: 'SOS' 的摩斯电码 (异步)
// The morse code is '···   ---   ···'
// zh-CN: 摩斯密码为 '···   ---   ···'
device.vibrate('SOS', delay);

toast(`This is a toast for test (should've appeared before vibration finished)`, 'l', 'f');

// If you need information about morse code, try code below.
// zh-CN: 如需获取摩斯电码的相关信息, 可尝试以下代码.

let morse = util.morseCode('SOS');
console.log(morse.toString());
// console.log('code: ' + morse.code);
// console.log('pattern: ' + morse.pattern);
// morse.vibrate(delay);