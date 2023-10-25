/**
 * Tests for AutoJs6 (v6.1.0 and above).
 * A toast message should display when all tests passed (or not) within 3 seconds.
 * Created by SuperMonster003 on May 7, 2022.
 */

const isFunc = o => typeof o === 'function';
const isObjOrFunc = o => isFunc(o) || typeof o === 'object' && o !== null;
const hasError = (f, thisArg) => !!(new Function('{try{arguments[0].call(arguments[1])}catch(e){return 1}}')(f, thisArg));
const noError = (f, thisArg) => !hasError(f, thisArg);
const assert = (value, message) => {
    console.assert(value, message);
    message && console.verbose(`${message} passed`);
};

const AtomicLong = java.util.concurrent.atomic.AtomicLong;
const JavaScriptEngine = org.autojs.autojs.engine.JavaScriptEngine;

// @Test Rhino basic

assert(/^xml$/.test(typeof (<></>)), 'xml type');
assert(isObjOrFunc(context), 'context');
assert(!isNullish(context.packageName), 'context getter');
// noinspection JSValidateTypes
assert(new java.lang.String('.').toString() === '.', 'java.lang.String#toString()');
assert(!isNullish(context.getPackageName()), 'context getter');
assert(isFunc(Array.isArray), 'Array.isArray()');

// @Test Rhino native

assert(isFunc(ArrayBuffer), 'ArrayBuffer()');
assert(isFunc(Continuation), 'Continuation()');
assert(isFunc(DataView), 'DataView()');
assert(isFunc(Float32Array), 'Float32Array()');
assert(isFunc(Float64Array), 'Float64Array()');
assert(isFunc(Int16Array), 'Int16Array()');
assert(isFunc(Int32Array), 'Int32Array()');
assert(isFunc(Int8Array), 'Int8Array()');
assert(isFunc(JavaAdapter), 'JavaAdapter()');
assert(isFunc(JavaImporter), 'JavaImporter()');
assert(isFunc(Namespace), 'Namespace()');
assert(isFunc(QName), 'QName()');
assert(isFunc(Uint16Array), 'Uint16Array()');
assert(isFunc(Uint32Array), 'Uint32Array()');
assert(isFunc(Uint8Array), 'Uint8Array()');
assert(isFunc(Uint8ClampedArray), 'Uint8ClampedArray()');
assert(isFunc(XML), 'XML()');
assert(isFunc(XMLList), 'XMLList()');
assert(isFunc(importClass), 'importClass()');
assert(isFunc(importPackage), 'importPackage()');
assert(isFunc(getClass), 'getClass()');
assert(isFunc(constructor), 'constructor()');
assert(isJavaPackage(Packages), 'Packages');

// @Test Rhino 1.7.15+ (snapshot on May 4, 2022)

assert(isFunc(Object.values), 'Object.values()');
assert(isFunc(Object.entries), 'Object.entries()');
assert(Array.isArray(Object.values({})), 'Object.values()');
assert(Array.isArray(Object.entries({})), 'Object.entries()');
assert(isFunc(Array.from), 'Array.from()');
assert(Array.from(new Set([ 1, 1, 1 ])).length === 1, 'Set');
assert(isFunc(Array.prototype.includes), 'Array.prototype.includes()');
assert([ 0 / 0 ].includes(NaN), 'Array.prototype.includes()');
assert(isFunc(Object.getOwnPropertyDescriptors), 'Object.getOwnPropertyDescriptors()');
// noinspection JSValidateTypes
assert(Math.min.apply(null, [
    java.lang.Float(0).floatValue(), +1, Number(2), Infinity, Number.MAX_VALUE, Number.EPSILON,
]) === 0, `should${'\u0020never' + String('\x20')}happen`);

// @Test polyfill

assert(isFunc(Array.prototype.flat), 'Array.prototype.flat');

// @Test auto

assert(isObjOrFunc(auto), 'auto');
assert(isFunc(auto.waitFor), 'auto.waitFor()');
assert(noError(auto.waitFor, auto), 'auto.waitFor()');
assert(Array.isArray(auto.windows), 'auto.windows');
assert(typeof auto.service /* nullable */ === 'object', 'auto.service');

// @Test global

assert(!isNullish(global), 'global');
assert(isFunc(toast), 'toast()');
assert(isFunc(toastLog), 'toastLog()');
assert(isFunc(sleep), 'sleep()');
assert(isFunc(isStopped), 'isStopped()');
assert(isFunc(isRunning), 'isRunning()');
assert(isFunc(exit), 'exit()');
assert(isFunc(global['stop']), 'stop()');
assert(isFunc(setClip), 'setClip()');
assert(isFunc(getClip), 'getClip()');
assert(isFunc(currentPackage), 'currentPackage()');
assert(isFunc(currentActivity), 'currentActivity()');
assert(isFunc(waitForActivity), 'waitForActivity()');
assert(isFunc(waitForPackage), 'waitForPackage()');
assert(isFunc(random), 'random()');
assert(isFunc(setScreenMetrics), 'setScreenMetrics()');
assert(isFunc(requiresApi), 'requiresApi()');
assert(isFunc(requiresAutojsVersion), 'requiresAutojsVersion()');
assert(isFunc(isInteger), 'isInteger()');
assert(isFunc(isNullish), 'isNullish()');
assert(isFunc(isPrimitive), 'isPrimitive()');
assert(isFunc(isReference), 'isReference()');
assert(isFunc(isJavaObject), 'isJavaObject()');
assert(isFunc(isJavaClass), 'isJavaClass()');
assert(isFunc(isJavaPackage), 'isJavaPackage()');
assert(isJavaClass(ImageWrapper), 'isJavaClass()');
assert(isJavaPackage(de), 'de');
assert(isJavaPackage(okhttp3), 'okhttp3');
assert(isJavaPackage(androidx), 'androidx');
assert(isJavaPackage(Packages.androidx), 'Packages');
assert(noError(() => sleep(-currentPackage().length)), 'sleep() and currentPackage()');
assert(noError(() => requiresApi(24)), 'requiresApi()');
assert(noError(() => requiresAutojsVersion('6.0.0')), 'requiresAutojsVersion()');
assert(isPrimitive(Symbol('test')), 'isPrimitive()');
assert(isNullish(void 0), 'isNullish()');

// @Test Promise and timers

assert(isFunc(setTimeout), 'setTimeout()');
assert(isFunc(clearTimeout), 'clearTimeout()');
assert(isFunc(setInterval), 'setInterval()');
assert(isFunc(clearInterval), 'clearInterval()');
assert(isFunc(setImmediate), 'setImmediate()');
assert(isFunc(clearImmediate), 'clearImmediate()');
assert(isObjOrFunc(Promise), 'Promise');
assert(isObjOrFunc(timers), 'timers');
assert(isFunc(Promise.resolve), 'Promise.resolve()');
assert(isFunc(setTimeout), 'setTimeout()');
let timeoutId = setTimeout(() => toastLog('Timed out', 'long'), 3e3);
Promise.resolve().then(() => {
    toastLog('All passed');
    clearTimeout(timeoutId);
    exit();
});

// @Test util

assert(isObjOrFunc(util), 'util');
assert(util.java.array('int', 3) instanceof java.lang.Object, 'util.java.array');
assert(typeof util.java.array('int', 3)[random(0, 2)] === 'number', 'util.java.array');
// noinspection JSIncompatibleTypesComparison
assert(util.getClass(java.lang.String) === java.lang.String, 'util.getClass()');
assert(util.getClassName(java.lang.String) === 'java.lang.String', 'util.getClassName()');
assert(isFunc(new (function () {
    let C = function () {
        /* Empty body. */
    };
    util.extend(C, (function () {
        let P = function () {
            /* Empty body. */
        };
        P.prototype.getName = () => context.packageName;
        return P;
    })());
    return C;
}())().getName), 'util.extend()');

// @Test automator

assert(isObjOrFunc(automator), 'automator');
assert(isFunc(click), 'automator.click()');
assert(isFunc(longClick), 'automator.longClick()');
assert(isFunc(press), 'automator.press()');
assert(isFunc(swipe), 'automator.swipe()');
assert(isFunc(gesture), 'automator.gesture()');
assert(isFunc(gestures), 'automator.gestures()');
assert(isFunc(gestureAsync), 'automator.gestureAsync()');
assert(isFunc(gesturesAsync), 'automator.gesturesAsync()');
assert(isFunc(scrollDown), 'automator.scrollDown()');
assert(isFunc(scrollUp), 'automator.scrollUp()');
assert(isFunc(input), 'automator.input()');
assert(isFunc(setText), 'automator.setText()');
assert(isFunc(back), 'runtime.automator.back()');
assert(isFunc(home), 'runtime.automator.home()');
assert(isFunc(powerDialog), 'runtime.automator.powerDialog()');
assert(isFunc(notifications), 'runtime.automator.notifications()');
assert(isFunc(quickSettings), 'runtime.automator.quickSettings()');
assert(isFunc(recents), 'runtime.automator.recents()');
assert(isFunc(splitScreen), 'runtime.automator.splitScreen()');

// @Test images

assert(isObjOrFunc(images), 'images');
assert(isFunc(requestScreenCapture), 'global.requestScreenCapture()');
assert(isFunc(captureScreen), 'global.captureScreen()');
assert(isFunc(findImage), 'global.findImage()');
assert(isFunc(findImageInRegion), 'global.findImageInRegion()');
assert(isFunc(findColor), 'global.findColor()');
assert(isFunc(findColorInRegion), 'global.findColorInRegion()');
assert(isFunc(findColorEquals), 'global.findColorEquals()');
assert(isFunc(findMultiColors), 'global.findMultiColors()');
assert(noError(images.requestScreenCapture, images), 'images.requestScreenCapture()');
try {
    assert(images.captureScreen() instanceof ImageWrapper);
    // captureScreen() for the second time instantly
    let capt = images.captureScreen();
    assert(typeof capt.getHeight() === 'number');
    assert(images.matchTemplate(capt, capt) !== undefined);
    assert(images.findAllPointsForColor(capt, '#bfbfbf') !== undefined);
    capt.recycle();
} catch (e) {
    assert(e.javaException instanceof SecurityException, 'images.captureScreen()');
}

// @Test colors
assert(isObjOrFunc(colors), 'colors');
assert(typeof colors.parseColor('#ffffff') === 'number', 'colors.parseColor()');
assert(typeof colors.toInt(colors.toString(-1)) === 'number', 'colors.toInt() and colors.toString()');
assert(colors.rgba('#005B4F91') === colors.argb('#91005B4F'), 'colors.rgba() and colors.argb()');
assert(colors.rgba(0x00, 0x5B, 0x4F, 0x91) === colors.argb(0x91, 0x00, 0x5B, 0x4F), 'colors.rgba() and colors.argb()');

// @Test dialogs

assert(isObjOrFunc(dialogs), 'dialogs');
assert(isFunc(rawInput), 'global.rawInput()');
assert(isFunc(alert), 'global.alert()');
assert(isFunc(confirm), 'global.confirm()');
assert(isFunc(prompt), 'global.prompt()');
assert(noError(() => dialogs.build({ customView: '<vertical><vertical/></vertical>' }).show().dismiss()), 'dialogs.build()');
assert(noError(() => dialogs.build({ title: '', content: '', positive: '', positiveColor: -1 }).show().dismiss()), 'dialogs.build()');

// @Test storages

assert(isObjOrFunc(storages), 'storages');
assert(noError(() => {
    let sto = storages.create('\ufffeee');
    sto.put('key', 'value');
    assert(sto.get('null', 'def') === 'def');
    assert(sto.get('key') === 'value');
    storages.remove('\ufffeee');
}));

// @Test threads

assert(isObjOrFunc(threads), 'threads');
assert(isFunc(sync), 'global.sync()');
assert(noError(() => threads.start(() => null)), 'threads.start()');
assert(noError(() => threads.start(() => sleep(10e3))), 'threads.start()');
assert(noError(() => threads.start(() => sleep(10e3)).interrupt()), 'threads.interrupt()');
assert(noError(() => threads.start(() => sleep(10e3)).safeJoin(-10)), 'threads.safeJoin()');
assert(threads.atomic(0) instanceof AtomicLong, 'threads.atomic()');
assert(noError(threads.shutDownAll, threads), 'threads.shutDownAll()');

// @Test device

assert(isObjOrFunc(device), 'device');
assert(device.height * device.width > 0, 'device.height and device.width');
assert(typeof device.brand === 'string' && device.brand.length > 0, 'device.brand');
assert(noError(() => device.vibrate([ 0, 1 ])), 'device.vibrate()');
assert(noError(device.cancelVibration, device), 'device.cancelVibration()');
assert(noError(device.cancelKeepingAwake, device), 'device.cancelKeepingAwake()');

// @Test files

assert(isObjOrFunc(files), 'files');
assert(isFunc(open), 'global.open()');
assert(files.join('', '', '', '') === '/', 'files.join()');
assert(files.join('a', 'b', 'c', 'd') === 'a/b/c/d', 'files.join()');
assert(files.exists('') === true, 'files.exists()');
assert(files.isDir(files.getSdcardPath()), 'files.isDir()');
assert(noError(() => files.listDir(files.cwd(), () => true)), 'files.listDir() and files.cwd()');

// @Test console

assert(isObjOrFunc(console), 'console');
assert(isFunc(print), 'global.print()');
assert(isFunc(log), 'global.log()');
assert(isFunc(err), 'global.err()');
assert(isFunc(openConsole), 'global.openConsole()');
assert(isFunc(clearConsole), 'global.clearConsole()');
assert(isFunc(console.setGlobalLogConfig), 'console.setGlobalLogConfig()');
assert(isFunc(console.verbose), 'console.verbose()');

// @Test app

assert(isObjOrFunc(app), 'app');
assert(isFunc(launchPackage), 'global.launchPackage()');
assert(isFunc(launch), 'global.launch()');
assert(isFunc(launchApp), 'global.launchApp()');
assert(isFunc(getPackageName), 'global.getPackageName()');
assert(isFunc(getAppName), 'global.getAppName()');
assert(isFunc(launchSettings), 'global.launchSettings()');
assert(new RegExp('-n \'[.\\w]+\\/Test\'' +
    ' --ei \'a\' 1' +
    ' --eia \'b\' 2,2' +
    ' --es \'c\' \'hello\'' +
    ' --ez \'d\' false' +
    ' --esa \'e\' \'r\',\'s\',\'t\'' +
    ' -c cat003' +
    ' -a \'android.intent.action.VIEW\'' +
    ' -f 335544320' +
    ' -t application\\/pics-rules' +
    ' -d protocol:\\/\\/xxx',
).test(app.intentToShell({
    action: 'VIEW',
    className: 'Test',
    packageName: context.packageName,
    extras: { a: 1, b: [ 2, 2 ], c: 'hello', d: false, e: [ 'r', 's', 't' ] },
    flags: [ 'ACTIVITY_NEW_TASK', 'ACTIVITY_CLEAR_TOP' ],
    type: 'application/pics-rules',
    data: 'protocol://xxx',
    category: 'cat003',
})), 'app.intentToShell()');
assert(!isNullish(app.getAppName(context.packageName)), 'app.getAppName()');
assert(typeof app.autojs.versionName === 'string', 'app.autojs.versionName');
assert(typeof app.autojs.versionCode === 'number', 'app.autojs.versionCode');
assert(typeof app.autojs.versionDate === 'string', 'app.autojs.versionDate');

// @Test selector

assert(isObjOrFunc(selector), 'selector');
assert(selector() instanceof UiSelector, 'selector()');
assert(isFunc(select), 'global.select()');
assert(isFunc(clickable), 'global.clickable()');
assert(isFunc(focus), 'global.focus()');
assert(isFunc(scrollBackward), 'global.scrollBackward()');
assert(isFunc(password), 'global.password()');
assert(isFunc(descMatches), 'global.descMatches()');
assert(isFunc(descMatch), 'global.descMatch()');
assert(isFunc(content), 'global.content()');
assert(isFunc(contentMatches), 'global.contentMatches()');
assert(isFunc(contentMatch), 'global.contentMatch()');
assert(isFunc(id), 'global.id()');
assert(isFunc(text), 'global.text()');
assert(isFunc(contextClick), 'global.contextClick()');
assert(isFunc(algorithm), 'global.algorithm()');
assert(isFunc(accessibilityFocus), 'global.accessibilityFocus()');
assert(isFunc(dismiss), 'global.dismiss()');
assert(isFunc(focusable), 'global.focusable()');
assert(isFunc(click), 'global.click()');
assert(isFunc(classNameStartsWith), 'global.classNameStartsWith()');
assert(isFunc(scrollTo), 'global.scrollTo()');
assert(isFunc(scrollRight), 'global.scrollRight()');
assert(isFunc(setSelection), 'global.setSelection()');
assert(isFunc(selection), 'global.selection()');
assert(isFunc(bounds), 'global.bounds()');
assert(isFunc(columnSpan), 'global.columnSpan()');
assert(isFunc(cut), 'global.cut()');
assert(isFunc(visibleToUser), 'global.visibleToUser()');
assert(isFunc(setProgress), 'global.setProgress()');
assert(isFunc(textMatches), 'global.textMatches()');
assert(isFunc(textMatch), 'global.textMatch()');
assert(isFunc(className), 'global.className()');
assert(isFunc(boundsInside), 'global.boundsInside()');
assert(isFunc(enabled), 'global.enabled()');
assert(isFunc(scrollUp), 'global.scrollUp()');
assert(isFunc(boundsContains), 'global.boundsContains()');
assert(isFunc(findOnce), 'global.findOnce()');
assert(isFunc(descEndsWith), 'global.descEndsWith()');
assert(isFunc(rowCount), 'global.rowCount()');
assert(isFunc(copy), 'global.copy()');
assert(isFunc(textContains), 'global.textContains()');
assert(isFunc(scrollable), 'global.scrollable()');
assert(isFunc(packageNameStartsWith), 'global.packageNameStartsWith()');
assert(isFunc(clearFocus), 'global.clearFocus()');
assert(isFunc(column), 'global.column()');
assert(isFunc(filter), 'global.filter()');
assert(isFunc(depth), 'global.depth()');
assert(isFunc(longClick), 'global.longClick()');
assert(isFunc(exists), 'global.exists()');
assert(isFunc(progress), 'global.progress()');
assert(isFunc(toString), 'global.toString()');
assert(isFunc(idStartsWith), 'global.idStartsWith()');
assert(isFunc(getClass), 'global.getClass()');
assert(isFunc(idEndsWith), 'global.idEndsWith()');
assert(isFunc(descStartsWith), 'global.descStartsWith()');
assert(isFunc(accessibilityFocused), 'global.accessibilityFocused()');
assert(isFunc(untilFindOne), 'global.untilFindOne()');
assert(isFunc(classNameEndsWith), 'global.classNameEndsWith()');
assert(isFunc(indexInParent), 'global.indexInParent()');
assert(isFunc(classNameContains), 'global.classNameContains()');
assert(isFunc(find), 'global.find()');
assert(isFunc(checked), 'global.checked()');
assert(isFunc(scrollLeft), 'global.scrollLeft()');
assert(isFunc(waitFor), 'global.waitFor()');
assert(isFunc(selected), 'global.selected()');
assert(isFunc(clearAccessibilityFocus), 'global.clearAccessibilityFocus()');
assert(isFunc(dismissable), 'global.dismissable()');
assert(isFunc(checkable), 'global.checkable()');
assert(isFunc(drawingOrder), 'global.drawingOrder()');
assert(isFunc(untilFind), 'global.untilFind()');
assert(isFunc(packageNameContains), 'global.packageNameContains()');
assert(isFunc(longClickable), 'global.longClickable()');
assert(isFunc(expand), 'global.expand()');
assert(isFunc(packageNameEndsWith), 'global.packageNameEndsWith()');
assert(isFunc(focused), 'global.focused()');
assert(isFunc(idContains), 'global.idContains()');
assert(isFunc(desc), 'global.desc()');
assert(isFunc(scrollForward), 'global.scrollForward()');
assert(isFunc(textEndsWith), 'global.textEndsWith()');
assert(isFunc(contentInvalid), 'global.contentInvalid()');
assert(isFunc(multiLine), 'global.multiLine()');
assert(isFunc(idMatches), 'global.idMatches()');
assert(isFunc(idMatch), 'global.idMatch()');
assert(isFunc(findOne), 'global.findOne()');
assert(isFunc(show), 'global.show()');
assert(isFunc(descContains), 'global.descContains()');
assert(isFunc(paste), 'global.paste()');
assert(isFunc(contextClickable), 'global.contextClickable()');
assert(isFunc(packageName), 'global.packageName()');
assert(isFunc(row), 'global.row()');
assert(isFunc(rowSpan), 'global.rowSpan()');
assert(isFunc(scrollDown), 'global.scrollDown()');
assert(isFunc(editable), 'global.editable()');
assert(isFunc(columnCount), 'global.columnCount()');
assert(isFunc(classNameMatches), 'global.classNameMatches()');
assert(isFunc(classNameMatch), 'global.classNameMatch()');
assert(isFunc(textStartsWith), 'global.textStartsWith()');
assert(isFunc(packageNameMatches), 'global.packageNameMatches()');
assert(isFunc(packageNameMatch), 'global.packageNameMatch()');
assert(isFunc(collapse), 'global.collapse()');
assert(isFunc(setText), 'global.setText()');
assert(noError(() => idMatches('rex').findOnce()), 'idMatches(rex: string)');
assert(noError(() => idMatches(/rex/).findOnce()), 'idMatches(rex: RegExp)');
assert(noError(() => idMatches(/\/rex\//).findOnce()), 'idMatches(rex: RegExp (with slashes))');

// @Test sensor

assert(isObjOrFunc(sensors), 'sensors');
assert(isObjOrFunc(sensors.register('gravity')), 'sensors.register()');

// @Test events

assert(isObjOrFunc(events), 'events');
assert(!isNullish(keys.home), 'keys.home');
assert(!isNullish(keys.menu), 'keys.menu');
assert(!isNullish(keys.back), 'keys.back');
assert(!isNullish(keys.volume_up), 'keys.volume_up');
assert(!isNullish(keys.volume_down), 'keys.volume_down');
assert(isFunc(events.observeKey), 'events.observeKey()');
assert(!isNullish(events.broadcast), 'events.broadcast');

// @Test tasks

assert(isObjOrFunc(tasks), 'tasks');
assert(Array.isArray(tasks.queryTimedTasks({ path: files.cwd() })), 'tasks.queryTimedTasks()');
assert(tasks.timeFlagToDays(55).join('') === '01245', 'tasks.timeFlagToDays()');
assert(tasks.daysToTimeFlag([ 0, 1, 2, 4 ]) === 23, 'tasks.daysToTimeFlag()');

// @Test plugins

assert(isObjOrFunc(plugins), 'plugins');
assert(isFunc(plugins.load), 'plugins.load()');

// @Test web

assert(isObjOrFunc(web), 'web');
assert(isFunc(newInjectableWebClient), 'global.newInjectableWebClient()');
assert(isFunc(newInjectableWebView), 'global.newInjectableWebView()');

// @Test recorder

assert(isObjOrFunc(recorder), 'recorder');
assert(noError(() => recorder.save('test')), 'recorder.save()');
assert(recorder.has('test'), 'recorder.has()');
assert(typeof recorder.load('test') === 'number', 'recorder.load()');
assert(recorder.isLessThan('test', Infinity), 'recorder.isLessThan()');
assert(noError(() => recorder.remove('test')), 'recorder.remove()');
assert(noError(recorder.clear, recorder), 'recorder.clear()');

// @Test engines

assert(isObjOrFunc(engines), 'engines');
assert(engines.myEngine() instanceof JavaScriptEngine, 'engines.myEngine()');
assert(Array.isArray(engines.all()), 'engines.all()');
assert(typeof engines.myEngine().getSource().getName() === 'string', 'engines.myEngine().getSource().getName()');

// @Test base64

assert(isObjOrFunc(base64), 'base64');
assert(base64.decode(base64.encode(context.packageName)) === context.packageName, 'base64\'s consistency');
assert(base64.encode('\x03', 'iso-8859-1') === 'Aw==', 'base64.encode()');
assert(base64.decode('//4DAA==', 'utf-16') === '\x03', 'base64.decode()');

// @Test media

assert(isObjOrFunc(media), 'media');
assert(typeof media.isMusicPlaying() === 'boolean', 'media.isMusicPlaying()');

// @Test floaty

assert(isObjOrFunc(floaty), 'floaty');
assert(noError(() => floaty.rawWindow('<vertical/>')), 'floaty.rawWindow()');
assert(noError(() => floaty.closeAll()), 'floaty.closeAll()');

// @Test http

assert(isObjOrFunc(http), 'http');
assert(isFunc(http.get), 'http.get()');
assert(isFunc(http.post), 'http.post()');
assert(isFunc(http.postJson), 'http.postJson()');
assert(isFunc(http.postMultipart), 'http.postMultipart()');

// @Test RootAutomator

assert(isObjOrFunc(RootAutomator), 'RootAutomator');
assert(noError(() => autojs.isRootAvailable()), 'autojs.isRootAvailable()');

// @Test continuation

assert(isObjOrFunc(continuation), 'continuation');
assert(typeof continuation.enabled === 'boolean', 'continuation.enabled');

// @Test shell

assert(isObjOrFunc(shell), 'shell');
assert(isFunc(Menu), 'global.Menu()');
assert(isFunc(Home), 'global.Home()');
assert(isFunc(Back), 'global.Back()');
assert(isFunc(Up), 'global.Up()');
assert(isFunc(Down), 'global.Down()');
assert(isFunc(Left), 'global.Left()');
assert(isFunc(Right), 'global.Right()');
assert(isFunc(OK), 'global.OK()');
assert(isFunc(VolumeUp), 'global.VolumeUp()');
assert(isFunc(VolumeDown), 'global.VolumeDown()');
assert(isFunc(Power), 'global.Power()');
assert(isFunc(Camera), 'global.Camera()');
assert(isFunc(Text), 'global.Text()');
assert(isFunc(Input), 'global.Input()');
assert(isFunc(Tap), 'global.Tap()');
assert(isFunc(Screencap), 'global.Screencap()');
assert(isFunc(KeyCode), 'global.KeyCode()');
assert(isFunc(SetScreenMetrics), 'global.SetScreenMetrics()');
assert(isFunc(Swipe), 'global.Swipe()');
assert(typeof shell('date', true).code === 'number', 'shell().code');

// @Test ui

assert(isObjOrFunc(ui), 'ui');
assert(ui.isUiThread() === false, 'ui.isUiThread()');
assert(isFunc(ui.run), 'ui.run()');
assert(isFunc(ui.post), 'ui.post()');
assert(isFunc(ui.layout), 'ui.layout()');
assert(hasError(() => ui.layout('</>')), 'ui.layout() without "ui" statement');
assert(isFunc(ui.Widget), 'ui.Widget');
// noinspection HtmlUnknownAttribute
assert(ui.inflate('<vertical><text text="123"/></vertical>') instanceof JsLinearLayout, 'ui.inflate()');

// @Test Numberx

assert(Numberx.ICU === 996, 'Numberx.ICU');
assert(isFunc(Numberx.clamp), 'Numberx.clamp');
assert(Numberx.clamp(20, 10, 30) === 20, 'Numberx.clamp');
assert(Numberx.clamp(20, [ 10, 30 ]) === 20, 'Numberx.clamp');
assert(Numberx.clamp(20, [ 10, 30, 40 ]) === 20, 'Numberx.clamp');
assert(Numberx.clamp(20, [ 20, 20 ]) === 20, 'Numberx.clamp');
assert(Numberx.clamp(20, [ 10 ]) === 10, 'Numberx.clamp');
assert(typeof Numberx.prototype === 'object', 'Numberx.prototype is object');
assert(isFunc(Numberx), 'Numberx is function');

// @Test i18n

assert(isFunc(i18n), 'i18n');
assert(isObjOrFunc(i18n.banana), 'i18n.banana');
assert(isFunc(i18n.loadAll), 'i18n.loadAll');
assert(noError(() => i18n.setLocale('zh')), 'i18n.setLocale');
assert(noError(() => i18n.getPath()), 'i18n.getPath');