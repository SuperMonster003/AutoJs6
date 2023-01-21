/**
 * Tests for AutoJs6 (v6.1.0 and above) in "ui" thread.
 * A toast message should display when all tests passed (or not) within 3 seconds.
 * Created by SuperMonster003 on May 7, 2022.
 */

// noinspection BadExpressionStatementJS
'ui';

const isFunc = o => typeof o === 'function';
const hasError = (f, thisArg) => !!(new Function('{try{arguments[0].call(arguments[1])}catch(e){return 1}}')(f, thisArg));
const noError = (f, thisArg) => !hasError(f, thisArg);
const assert = (value, message) => {
    console.assert(value, message);
    console.verbose(`${message} passed`);
};

const JavaScriptEngine = org.autojs.autojs.engine.JavaScriptEngine;
const JsLinearLayout = org.autojs.autojs.core.ui.widget.JsLinearLayout;

// @Test auto

assert(noError(auto.waitFor, auto), 'auto.waitFor()');
assert(typeof auto.service /* nullable */ === 'object', 'auto.service');

// @Test Promise and timers

let timeoutId = setTimeout(() => toastLog('Timed out', 'long'), 3e3);
Promise.resolve().then(() => {
    toastLog('All passed');
    clearTimeout(timeoutId);
    ui.finish();
});

// @Test images

// FIXME by SuperMonster003 on May 7, 2022.
//  ! Auto.js freezes or won't respond when calling images.requestScreenCapture()
// assert(noError(images.requestScreenCapture, images), 'images.requestScreenCapture()');
// assert(noError(() => images.captureScreen()), 'images.captureScreen()');

// @Test dialogs

assert(noError(() => dialogs.build({customView: '<vertical><vertical/></vertical>'}).show().dismiss()), 'dialogs.build()');

// @Test threads

assert(noError(() => threads.start(() => sleep(9e3)).safeJoin(-1)), 'threads.start() or threads.safeJoin()');

// @Test device

assert(device.height * device.width > 0, 'device.height or device.width');

// @Test selector

assert(selector() instanceof UiSelector, 'selector()');
assert(noError(() => idMatches('rex').findOnce()), 'idMatches()');

// @Test engines

assert(engines.myEngine() instanceof JavaScriptEngine, 'engines.myEngine()');
assert(Array.isArray(engines.all()), 'engines.all()');
assert(typeof engines.myEngine().getSource().getName() === 'string', 'engines.myEngine().getSource().getName()');

// @Test floaty

// FIXME by SuperMonster003 on May 7, 2022.
//  ! Auto.js freezes or won't respond when calling floaty.rawWindow()
// assert(noError(() => floaty.rawWindow('<vertical/>')), 'floaty.rawWindow()');
assert(noError(() => floaty.closeAll()), 'floaty.closeAll()');

// @Test continuation

assert(typeof continuation.enabled === 'boolean', 'continuation.enabled');

// @Test ui

assert(ui.isUiThread(), 'ui.isUiThread()');
assert(noError(() => ui.run(() => void 0)), 'ui.run()');
assert(noError(() => ui.post(() => void 0)), 'ui.post()');
assert(hasError(() => ui.layout('</>')), 'ui.layout() without "ui" statement');
// noinspection HtmlUnknownAttribute
assert(ui.inflate('<vertical><text text="123"/></vertical>') instanceof JsLinearLayout, 'ui.inflate()');
assert(isFunc(ui.finish), 'ui.finish()');