/* Here, importClass() is not recommended for intelligent code completion in IDE like WebStorm. */
/* The same is true of destructuring assignment syntax (like `let {Uri} = android.net`). */

const RootAutomator = com.stardust.autojs.core.inputevent.RootAutomator;

let _ = {
    init(__runtime__, scope) {
        this.runtime = __runtime__;
        this.scope = scope;

        this.RootAutomator = function (waitForReady) {
            _.checkRootAccess();

            this.__ra__ = Object.create(new RootAutomator(scope.context, ( /* @IIFE */ () => {
                if (typeof waitForReady === 'number') {
                    return waitForReady;
                }
                return Boolean(waitForReady);
            })()));

            void [
                'sendEvent', 'touch', 'setScreenMetrics', 'touchX', 'touchY', 'sendSync', 'sendMtSync', 'tap',
                'swipe', 'press', 'longPress', 'touchDown', 'touchUp', 'touchMove', 'getDefaultId', 'setDefaultId', 'exit',
            ].forEach(key => this[key] = this.__ra__[key].bind(this.__ra__));
        };
    },
    getModule() {
        return this.RootAutomator;
    },
    selfAugment() {
        Object.assign(this.RootAutomator, {});
    },
    checkRootAccess() {
        if (!autojs.isRootAvailable()) {
            throw Error('RootAutomator must be instantiated with root access');
        }
    },
};

let $ = {
    getModule(__runtime__, scope) {
        _.init(__runtime__, scope);

        _.selfAugment();

        return _.getModule();
    },
};

/**
 * @param {com.stardust.autojs.runtime.ScriptRuntime} __runtime__
 * @param {org.mozilla.javascript.Scriptable} scope
 * @return {Internal.RootAutomator}
 */
module.exports = function (__runtime__, scope) {
    return $.getModule(__runtime__, scope);
};