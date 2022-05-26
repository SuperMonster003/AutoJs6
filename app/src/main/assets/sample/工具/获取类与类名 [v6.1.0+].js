// noinspection JSIncompatibleTypesComparison

let Clazz = android.os.BatteryManager;

console.log(util.getClass(Clazz));
console.log(util.getClassName(Clazz));

let test = o => console.log(`Test instance ${o ? `passed` : `failed`}`);

test(util.getClass(Clazz) === Clazz);
test(util.getClassName(Clazz) === 'android.os.BatteryManager');

// @Caution by SuperMonster003 on May 25, 2022.
//  ! This test won't pass.
//  ! Use util.getClass(C) instanceof P instead.
test(Clazz instanceof java.lang.Class);
test(util.getClass(Clazz) instanceof java.lang.Class);

test(new java.lang.Integer(0.5).getClass() === java.lang.Integer);
test(util.getClass(new java.lang.Integer(0.5)) === java.lang.Integer);
test(util.getClass(new java.lang.Integer(0.5)) === util.getClass(java.lang.Integer));

console.launch();