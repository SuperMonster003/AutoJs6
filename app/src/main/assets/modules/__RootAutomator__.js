/* Overwritten protection. */

let { autojs } = global;

/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.RootAutomator}
 */
module.exports = function (scriptRuntime, scope) {
    const RootAutomator = org.autojs.autojs.core.inputevent.RootAutomator;

    let _ = {
        /**
         * @extends Internal.RootAutomator
         */
        RootAutomator(waitForReady) {
            if (!autojs.isRootAvailable()) {
                throw Error('RootAutomator must be instantiated with root access');
            }

            this.__ra__ = Object.create(new RootAutomator(scope.context, (/* @IIFE */ () => {
                if (typeof waitForReady === 'number') {
                    return waitForReady;
                }
                return Boolean(waitForReady);
            })()));

            [
                'sendEvent', 'touch', 'setScreenMetrics',
                'touchX', 'touchY', 'sendSync', 'sendMtSync',
                'tap', 'swipe', 'press', 'longPress',
                'touchDown', 'touchUp', 'touchMove',
                'getDefaultId', 'setDefaultId', 'exit',
            ].forEach(key => this[key] = this.__ra__[key].bind(this.__ra__));
        },
    };

    return _.RootAutomator;
};