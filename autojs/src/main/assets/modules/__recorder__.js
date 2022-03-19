module.exports = function () {

    let _ = {
        /**
         * @type {Object.<string,number>}
         */
        keys: {},
        /**
         * @type {number[]}
         */
        anonymity: [],
        /**
         * @param {?number} [ts]
         * @return {number}
         */
        ts(ts) {
            return typeof ts === 'number' ? ts : Date.now();
        },
        /**
         * @param {string} [key]
         * @return {boolean}
         */
        has(key) {
            return key in this.keys;
        },
        clear() {
            this.keys = {};
            this.anonymity.splice(0);
        },
        /**
         * @param {string} [key]
         * @param {?number} [ts]
         * @return {number}
         */
        add(key, ts) {
            key === undefined
                ? this.anonymity.push(this.ts(ts))
                : this.keys[key] = this.ts(ts);
            return this.ts(ts);
        },
        /**
         * @param {string} [key]
         * @param {boolean} [isErrorSuppressed=false]
         * @return {number|void}
         */
        get(key, isErrorSuppressed) {
            if (key === undefined) {
                return this.anonymity.pop();
            }
            if (!this.has(key) && !isErrorSuppressed) {
                throw Error(`key "${key}" does not exist`);
            }
            return this.keys[key];
        },
        /**
         * @param {string} [key]
         * @param {?number} [ts]
         * @return {number}
         */
        save(key, ts) {
            return this.add(key, ts);
        },
        /**
         * @param {string} [key]
         * @param {?number} [ts]
         * @return {number}
         */
        load(key, ts) {
            return this.ts(ts) - this.get(key);
        },
        /**
         * @param {string} key
         * @returns {boolean}
         */
        remove(key) {
            return delete this.keys[key];
        },
        /**
         * @param {string|function} [key]
         * @param {?number|ThisType<any>} [ts]
         * @return {number}
         */
        shortcut(key, ts) {
            if (typeof key === 'function') {
                let k = `${key.name || 'anonymous'}@${Date.now()}`;
                this.save(k);
                key.call(typeof ts === 'object' ? ts : null);
                let res = this.load(k);
                this.remove(k);
                return res;
            }
            return typeof key !== 'undefined'
                ? this.has(key) ? this.load(key, ts) : this.save(key, ts)
                : this.anonymity.length ? this.load() : this.save();
        },
    };

    /**
     * Record or get the record for a time gap
     * @param {string|function} [key]
     * @param {number|ThisType<any>} [ts]
     * @return {number|void} - timestamp
     */
    const recorder = function (key, ts) {
        return _.shortcut(key, ts);
    };

    /**
     * @param {string} [key]
     * @param {number} [ts=Date.now()]
     */
    recorder.save = (key, ts) => _.save(key, ts);
    /**
     * @param {string} [key]
     * @param {number} [ts=Date.now()]
     * @return {number}
     */
    recorder.load = (key, ts) => _.load(key, ts);
    recorder.isLessThan = (key, compare) => _.load(key) < compare;
    recorder.isGreaterThan = (key, compare) => _.load(key) > compare;
    recorder.has = key => _.has(key);
    recorder.remove = key => _.remove(key);
    recorder.clear = () => _.clear();

    return recorder;

};