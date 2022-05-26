/* Here, importClass() is not recommended for intelligent code completion in IDE like WebStorm. */
/* The same is true of destructuring assignment syntax (like `let {Uri} = android.net`). */

const LocalStorage = com.stardust.autojs.core.storage.LocalStorage;

let _ = {
    Storage: ( /* @IIFE */ () => {
        let Storage = function (name) {
            this._storage = new LocalStorage(global.context, name);
        };

        Storage.prototype = {
            constructor: Storage,
            put(key, value) {
                if (value === undefined) {
                    throw TypeError('Value cannot be undefined');
                }
                this._storage.put(key, JSON.stringify(value));
            },
            get(key, def) {
                let value = this._storage.getString(key, null);
                return value ? JSON.parse(value) : def;
            },
            remove(key) {
                this._storage.remove(key);
            },
            contains(key) {
                return this._storage.contains(key);
            },
            clear() {
                this._storage.clear();
            },
        };

        return Storage;
    })(),
    init(__runtime__, scope) {
        this.runtime = __runtime__;
        this.scope = scope;
        this.storages = {};
    },
    getModule() {
        return this.storages;
    },
    selfAugment() {
        Object.assign(this.storages, {
            create(name) {
                return new _.Storage(name);
            },
            remove(name) {
                this.create(name).clear();
            },
        });
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
 * @return {Internal.Storages}
 */
module.exports = function (__runtime__, scope) {
    return $.getModule(__runtime__, scope);
};