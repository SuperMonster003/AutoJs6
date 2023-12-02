// If you have exotic root or abnormal state for root access, you can force set root to root or non-root.
// zh-CN: 如果设备使用非常规 Root 方式或 Root 权限检测异常, 可设置强制 Root 或 强制非 Root 模式.

// depends on system
// zh-CN: 取决于系统检测结果
console.log(`Root access: ${autojs.isRootAvailable()}`);

// 1 or true or 'root' (force set root mode)
// zh-CN: 1 或 true 或 'root' (强制设置 Root 模式)
autojs.setRootMode(true);
console.log(`setRootMode(true)`)
// true
console.log(`Root access: ${autojs.isRootAvailable()}`);

// 0 or false or 'non-root' (force set non-root mode)
// zh-CN: 0 或 false 或 'non-root' (强制设置非 Root 模式)
autojs.setRootMode(false);
console.log(`setRootMode(false)`)
// false
console.log(`Root access: ${autojs.isRootAvailable()}`);

// -1 or 'auto' (auto detect)
// zh-CN: -1 或 'auto' (自动检测 Root 模式)
autojs.setRootMode('auto');
console.log(`setRootMode(auto)`)
// depends on system again
// zh-CN: 再次取决于系统检测结果
console.log(`Root access: ${autojs.isRootAvailable()}`);

console.launch();

// autojs.setRootMode() doesn't change preference settings.
// e.g. autojs.isRootAvailable(); // false
// autojs.setRootMode(true);
// autojs.isRootAvailable(); // true
// However, when running a new script, autojs.isRootAvailable() still returns false.
// To apply to preference settings, go to "AutoJs6 > Settings".
// The second parameter of autojs.setRootMode() also works.
// To forcibly set non-root mode: autojs.setRootMode('non-root', true);
// Also available for string param: autojs.setRootMode('non-root', 'write_into_pref');
// zh-CN:
// autojs.setRootMode() 仅在单个脚本实例运行期间有效 即不改变软件配置参数
// 例如 autojs.isRootAvailable() 返回 false
// 此时使用 autojs.setRootMode(true)
// autojs.isRootAvailable() 将返回 true
// 如果脚本结束后再次获取 autojs.isRootAvailable() 则依然返回 false
// 如需改变软件配置参数 可在 "AutoJs6 > 设置" 中更改
// 也可通过 autojs.setRootMode() 的第二项参数直接将修改应用到软件配置参数
// 如强制非 Root 模式: autojs.setRootMode('non-root', true);
// 也可使用字符串参数: autojs.setRootMode('non-root', 'write_into_pref');