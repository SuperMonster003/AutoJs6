/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Storages}
 */
module.exports = function (scriptRuntime, scope) {
    const LocalStorage = org.autojs.autojs.core.storage.LocalStorage;

    let _ = {
        ProxyStorage: (/* @IIFE */ () => {
            /**
             * @implements Internal.LocalStorage
             */
            const ProxyStorage = function (name) {
                this._storage = new LocalStorage(scope.context, name);
            };

            ProxyStorage.prototype = {
                constructor: ProxyStorage,
                put(key, value) {
                    if (value === undefined) {
                        throw TypeError(`Value can't be undefined`);
                    }
                    this._storage.put(key, JSON.stringify(value));
                    return this;
                },
                get(key, def) {
                    let value = this._storage.getString(key, null);
                    return value ? JSON.parse(value) : def;
                },
                remove(key) {
                    this._storage.remove(key);
                    return this;
                },
                contains(key) {
                    return this._storage.contains(key);
                },
                clear() {
                    this._storage.clear();
                },
            };

            return ProxyStorage;
        })(),
        Storages: (/* @IIFE */ () => {
            /**
             * @implements Internal.Storages
             */
            const Storage = function () {
                /* Empty body. */
            };

            Storage.prototype = {
                constructor: Storage,
                create(name) {
                    return new _.ProxyStorage(name);
                },
                remove(name) {
                    this.create(name).clear();
                },
            };

            return Storage;
        })(),
    };

    // noinspection UnnecessaryLocalVariableJS
    /** @type {Internal.Storages} */
    const storages = new _.Storages();

    return storages;
};