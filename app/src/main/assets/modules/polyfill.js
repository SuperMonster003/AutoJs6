module.exports = {
    fill() {
        // @Comment by SuperMonster003 on May 6, 2022.
        //  ! Already implemented in Rhino 1.7.15-SNAPSHOT as of Mar 18, 2022.

        // if (!Object.getOwnPropertyDescriptors) {
        //     /**
        //      * @param {Object} o
        //      * @return {Object.<string,PropertyDescriptor>} <!-- or {PropertyDescriptorMap} -->
        //      */
        //     Object.getOwnPropertyDescriptors = function (o) {
        //         let descriptor = {};
        //         Object.getOwnPropertyNames(o).forEach((k) => {
        //             descriptor[k] = Object.getOwnPropertyDescriptor(o, k);
        //         });
        //         return descriptor;
        //     };
        // }

        if (!Array.prototype.flat) {
            Object.defineProperty(Array.prototype, 'flat', {
                value(depth) {
                    return ( /* @IIFE */ function flat(arr, d) {
                        return d <= 0 ? arr : arr.reduce((a, b) => {
                            return a.concat(Array.isArray(b) ? flat(b, d - 1) : b);
                        }, []);
                    })(this.slice(), Math.max(Number(depth) || 0, 1));
                },
            });
        }

        if (!Array.prototype.at) {
            Object.defineProperty(Array.prototype, 'at', {
                value(index) {
                    let relIdx = ( /* @IIFE */ () => {
                        const num = Number(index);
                        return isNaN(num) ? 0 : isFinite(num) ? Math.trunc(num) : num;
                    })();

                    let idx = relIdx >= 0 ? relIdx : relIdx + this.length;

                    return idx >= 0 && idx < this.length ? this[idx] : void 0;
                },
            });
        }
    },
};