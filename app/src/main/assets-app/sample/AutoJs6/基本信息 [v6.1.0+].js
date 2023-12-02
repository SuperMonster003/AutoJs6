let {versionName, versionCode, versionDate} = autojs;
Object.entries({versionName, versionCode, versionDate}).forEach((entry) => {
    let [key, value] = entry;
    console.log(`${key}: ${value}`);
});

console.verbose();

console.log(`RootMode: ${autojs.getRootMode()}`);
console.log(`Root: ${autojs.isRootAvailable() ? 'available' : 'not available'}`);

console.verbose();

console.log(`Display over other apps: ${autojs.canDisplayOverOtherApps()}`);
console.log(`Modify system settings: ${autojs.canModifySystemSettings()}`);
console.log(`Write secure settings: ${autojs.canWriteSecureSettings()}`);

console.launch();