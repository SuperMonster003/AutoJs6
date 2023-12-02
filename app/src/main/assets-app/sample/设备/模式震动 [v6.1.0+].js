let delay = 240;

// 'SOS' in Morse (async)
// zh-CN: 'SOS' 的摩斯电码 (异步)
// The morse code is '···   ---   ···'
// zh-CN: 摩斯密码为 '···   ---   ···'
device.vibrate([delay, 100, 100, 100, 100, 100, 300, 300, 100, 300, 100, 300, 300, 100, 100, 100, 100, 100]);

toast(`This is a toast for test (should've appeared before vibration finished)`, 'l', 'f');