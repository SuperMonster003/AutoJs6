module.exports = {
    fill() {
        if (!Object.getOwnPropertyDescriptors) {
            /**
             * @param {Object} o
             * @return {Object.<string,PropertyDescriptor>} <!-- or {PropertyDescriptorMap} -->
             */
            Object.getOwnPropertyDescriptors = function (o) {
                let _descriptor = {};
                Object.getOwnPropertyNames(o).forEach((k) => {
                    _descriptor[k] = Object.getOwnPropertyDescriptor(o, k);
                });
                return _descriptor;
            };
        }

        if (!Array.prototype.flat) {
            Object.defineProperty(Array.prototype, 'flat', {
                value(depth) {
                    return (function _flat(arr, d) {
                        return d <= 0 ? arr : arr.reduce((a, b) => {
                            return a.concat(Array.isArray(b) ? _flat(b, d - 1) : b);
                        }, []);
                    })(this.slice(), depth || 1);
                },
            });
        }
    },
};