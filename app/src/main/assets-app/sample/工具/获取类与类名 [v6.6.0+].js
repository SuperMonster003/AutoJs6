// noinspection JSIncompatibleTypesComparison

let Clazz = android.os.BatteryManager;

console.log(util.getClass(Clazz));
console.log(util.getClassName(Clazz));

let test = o => console.log(`Test instance ${o ? `passed` : `failed`}`);

test(util.getClass(Clazz) === Clazz);
test(util.getClassName(Clazz) === 'android.os.BatteryManager');

// @Caution by SuperMonster003 on Nov 11, 2024.
//  ! This test won't pass.
//  ! Use `util.getClass(C) instanceof P` instead.
//  ! zh-CN:
//  ! 当前测试不会通过.
//  ! 需使用 `C.class instanceof P` 替代.
// test(Clazz instanceof java.lang.Class);
test(Clazz.class instanceof java.lang.Class);

test(new java.lang.Integer(0.5).getClass() === java.lang.Integer);
test(util.getClass(new java.lang.Integer(0.5)) === java.lang.Integer);
test(util.getClass(new java.lang.Integer(0.5)) === util.getClass(java.lang.Integer));

console.launch();