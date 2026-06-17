// noinspection JSUnusedGlobalSymbols

(function () {
    function EventEmitter() {
        this._events = {};
    }

    EventEmitter.prototype.on = function (type, listener) {
        if (typeof listener !== 'function') {
            throw new TypeError('listener must be a function');
        }
        const key = String(type);
        (this._events[key] || (this._events[key] = [])).push(listener);
        return this;
    };

    EventEmitter.prototype.addListener = EventEmitter.prototype.on;

    EventEmitter.prototype.once = function (type, listener) {
        const self = this;
        function wrapper() {
            self.removeListener(type, wrapper);
            return listener.apply(this, arguments);
        }
        wrapper.listener = listener;
        return this.on(type, wrapper);
    };

    EventEmitter.prototype.emit = function (type) {
        const key = String(type);
        const args = Array.prototype.slice.call(arguments, 1);
        const listeners = (this._events[key] || []).slice();
        listeners.forEach((listener) => listener.apply(this, args));
        return listeners.length > 0;
    };

    EventEmitter.prototype.removeListener = function (type, listener) {
        const key = String(type);
        const listeners = this._events[key];
        if (!listeners) {
            return this;
        }
        this._events[key] = listeners.filter((fn) => fn !== listener && fn.listener !== listener);
        return this;
    };

    EventEmitter.prototype.off = EventEmitter.prototype.removeListener;

    EventEmitter.prototype.removeAllListeners = function (type) {
        if (type == null) {
            this._events = {};
        } else {
            delete this._events[String(type)];
        }
        return this;
    };

    EventEmitter.prototype.listeners = function (type) {
        return (this._events[String(type)] || []).slice();
    };

    EventEmitter.prototype.listenerCount = function (type) {
        return this.listeners(type).length;
    };

    EventEmitter.listenerCount = function (emitter, type) {
        return emitter.listenerCount(type);
    };

    module.exports = {
        EventEmitter,
        once(emitter, name) {
            return new Promise((resolve) => emitter.once(name, function () {
                resolve(Array.prototype.slice.call(arguments));
            }));
        },
    };
})();
