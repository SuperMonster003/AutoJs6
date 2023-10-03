/* Overwritten protection. */

let { util, plugins } = global;

/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Arrayx}
 */
module.exports = (scriptRuntime, scope) => {
    let _ = {
        ArrayxCtor: (/* @IIFE */ () => {
            /**
             * @implements Internal.Arrayx
             */
            const ArrayxCtor = function () {
                return Object.assign(Array.bind(Array), ArrayxCtor.prototype);
            };

            ArrayxCtor.prototype = {
                constructor: ArrayxCtor,
                // assureArray(o) {
                //     if (Array.isArray(o)) {
                //         return o;
                //     }
                //     if (species.isObject(o) && typeof o.length === 'number' && o.length >= 0) {
                //         return Array.from(o);
                //     }
                //     return [ o ];
                // },
                ensureArray() {
                    Array.from(arguments).forEach(o => util.ensureArrayType(o));
                },
                distinct(arr) {
                    Arrayx.ensureArray(arr);
                    return Array.from(new Set(arr));
                },
                distinctBy(arr, selector) {
                    Arrayx.ensureArray(arr);
                    let res = [];
                    let cache = [];
                    arr.forEach(e => {
                        let selected = selector(e);
                        if (!cache.includes(selected)) {
                            cache.push(selected);
                            res.push(e);
                        }
                    });
                    return res;
                },
                /**
                 * TODO by SuperMonster003 on Jan 21, 2023.
                 *  ! Better performance.
                 */
                union(arr, others) {
                    Arrayx.ensureArray(arr);
                    let res = Array.from(arguments).slice(1).reduce((a, b) => {
                        return Array.isArray(b) ? a.concat(b) : a.concat([ b ]);
                    }, arr);
                    return Arrayx.distinct(res);
                },
                /**
                 * TODO by SuperMonster003 on Jan 21, 2023.
                 *  ! Better performance.
                 */
                intersect(arr, others) {
                    Arrayx.ensureArray(arr);
                    if (arr.length === 0 || arguments.length <= 1) {
                        return [];
                    }
                    return Arrayx.distinct(arr).filter((o) => {
                        return Array.from(arguments).slice(1).every((e) => {
                            return Array.isArray(e) ? Arrayx.distinct(e).includes(o) : e === o;
                        });
                    });
                },
                /**
                 * TODO by SuperMonster003 on Jan 21, 2023.
                 *  ! Necessary or not ?
                 */
                different() {
                    throw Error('TODO');
                },
                sortBy(arr, selector) {
                    Arrayx.ensureArray(arr);
                    if (arr.length < 2) {
                        return arr;
                    }
                    return arr.sort((a, b) => {
                        let sA = selector(a);
                        let sB = selector(b);
                        return sA === sB ? 0 : sA > sB ? 1 : -1;
                    });
                },
                sortDescending(arr) {
                    Arrayx.ensureArray(arr);
                    if (arr.length < 2) {
                        return arr;
                    }
                    return arr.sort(_.bySimpleCompare).reverse();
                },
                sortByDescending(arr, selector) {
                    Arrayx.ensureArray(arr);
                    if (arr.length < 2) {
                        return arr;
                    }
                    return arr.sort((a, b) => {
                        let sA = selector(a);
                        let sB = selector(b);
                        return sA === sB ? 0 : sA > sB ? -1 : 1;
                    });
                },
                sorted(arr) {
                    Arrayx.ensureArray(arr);
                    return arr.slice().sort(_.bySimpleCompare);
                },
                sortedBy(arr, selector) {
                    Arrayx.ensureArray(arr);
                    let copy = arr.slice();
                    if (copy.length < 2) {
                        return copy;
                    }
                    return this.sortBy(copy, selector);
                },
                sortedDescending(arr) {
                    Arrayx.ensureArray(arr);
                    let copy = arr.slice();
                    if (copy.length < 2) {
                        return copy;
                    }
                    return this.sortDescending(copy);
                },
                sortedByDescending(arr, selector) {
                    Arrayx.ensureArray(arr);
                    let copy = arr.slice();
                    if (copy.length < 2) {
                        return copy;
                    }
                    return this.sortByDescending(arr, selector);
                },
                shuffle(arr) {
                    return arr.sort(() => Math.random() >= 0.5 ? 1 : -1);
                },
            };

            return ArrayxCtor;
        })(),
        bySimpleCompare: (a, b) => a === b ? 0 : a > b ? 1 : -1,
        registerPluginModule() {
            plugins.extend.registerModule({
                Arrayx: {
                    protoKeys: {
                        intersect: 0,
                        union: 0,
                        distinct: 0,
                        distinctBy: 0,
                        sortBy: 0,
                        sortDescending: 0,
                        sortByDescending: 0,
                        sorted: 0,
                        sortedBy: 0,
                        sortedDescending: 0,
                        sortedByDescending: 0,
                        shuffle: 0,
                    },
                    extendJsBuildInObjects() {
                        let that = this;
                        Object.keys(_.ArrayxCtor.prototype).forEach((key) => {
                            if (!(key in that.protoKeys)) {
                                Array[key] = Arrayx[key];
                                return;
                            }
                            if (typeof that.protoKeys[key] !== 'number') {
                                Array.prototype[key] = Arrayx[key];
                                return;
                            }
                            Array.prototype[key] = function () {
                                const args = Array.from(arguments);
                                args.splice(that.protoKeys[key], 0, this.valueOf());
                                return Arrayx[key].apply(Arrayx, args);
                            };
                        });
                    },
                },
            });
        },
    };

    /**
     * @type {Internal.Arrayx}
     */
    const Arrayx = new _.ArrayxCtor();

    _.registerPluginModule();

    return Arrayx;
};