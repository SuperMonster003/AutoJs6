// noinspection JSUnusedGlobalSymbols

let _ = {
    init(__runtime__, scope) {
        this.runtime = __runtime__;
        this.scope = scope;

        this.rtEvents = __runtime__.events;
        this.events = Object.create(this.rtEvents);
    },
    getModule() {
        return this.events;
    },
    selfAugment() {
        Object.assign(this.events, {
            __asEmitter__(obj, thread) {
                let emitter = thread ? events.emitter(thread) : events.emitter();
                for (let key in emitter) {
                    if (obj[key] === undefined && typeof emitter[key] === 'function') {
                        obj[key] = emitter[key].bind(emitter);
                    }
                }
                return obj;
            },
        });
    },
    scopeAugment() {
        this.scope.keys = {
            home: KeyEvent.KEYCODE_HOME,
            menu: KeyEvent.KEYCODE_MENU,
            back: KeyEvent.KEYCODE_BACK,
            volume_up: KeyEvent.KEYCODE_VOLUME_UP,
            volume_down: KeyEvent.KEYCODE_VOLUME_DOWN,
        };
    },
};

let $ = {
    getModule(__runtime__, scope) {
        _.init(__runtime__, scope);

        _.selfAugment();
        _.scopeAugment();

        return _.getModule();
    },
};

/**
 * @param {com.stardust.autojs.runtime.ScriptRuntime} __runtime__
 * @param {org.mozilla.javascript.Scriptable} scope
 * @return {Internal.Events}
 */
module.exports = function (__runtime__, scope) {
    return $.getModule(__runtime__, scope);
};