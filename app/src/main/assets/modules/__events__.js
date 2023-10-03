// noinspection JSUnusedGlobalSymbols

/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Events}
 */
module.exports = function (scriptRuntime, scope) {
    let _ = {
        Events: (/* @IIFE */ () => {
            /**
             * @extends Internal.Events
             */
            const Events = function () {
                /* Empty body. */
            };

            Events.prototype = {
                constructor: Events,
                __asEmitter__(obj, thread) {
                    let emitter = thread ? this.emitter(thread) : this.emitter();
                    for (let key in emitter) {
                        if (obj[key] === undefined && typeof emitter[key] === 'function') {
                            obj[key] = emitter[key].bind(emitter);
                        }
                    }
                    return obj;
                },
            };

            Object.setPrototypeOf(Events.prototype, scriptRuntime.events);

            return Events;
        })(),
        Keys: (/* @IIFE */ () => {
            /**
             * @implements Internal.Keys
             */
            const Keys = function () {
                /* Empty body. */
            };

            Keys.prototype = {
                constructor: Keys,
                home: KeyEvent.KEYCODE_HOME,
                menu: KeyEvent.KEYCODE_MENU,
                back: KeyEvent.KEYCODE_BACK,
                volume_up: KeyEvent.KEYCODE_VOLUME_UP,
                volume_down: KeyEvent.KEYCODE_VOLUME_DOWN,
            };

            return Keys;
        })(),
        scopeAugment() {
            Object.assign(scope, {
                keys: new _.Keys(),
            });
        },
    };

    /**
     * @type {Internal.Events}
     */
    const events = new _.Events();

    _.scopeAugment();

    return events;
};