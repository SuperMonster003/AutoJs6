// noinspection JSUnusedGlobalSymbols

/* Overwritten protection. */

let { i18n } = global;

/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {() => Internal.Selector}
 */
module.exports = function (scriptRuntime, scope) {
    let _ = {
        Selector: (() => {
            /**
             * @extends Internal.Selector
             */
            const Selector = function () {
                /**
                 * @global
                 */
                const selector = function () {
                    return scriptRuntime.selector();
                };
                return Object.assign(selector, Selector.prototype);
            };

            Selector.prototype = {
                constructor: Selector,
            };

            return Selector;
        })(),
        javaObjectInstance: new java.lang.Object(),
        isInJavaObject: key => key in _.javaObjectInstance,
        isInScope: key => key in scope,
        scopeAugment() {
            Object.assign(scope, {
                pickup(root, selector, compass, resultType, callback) {
                    switch (arguments.length) {
                        case 5:
                            // @Signature pickup<R>(root: UiObject, selector: Pickup.Selector, compass: Selector.Compass, resultType: Pickup.ResultType, callback: (o: any) => R): R;
                            return UiSelector.pickup(root, selector, compass, resultType, callback);
                        case 4:
                            if (arguments[0] instanceof UiObject) {
                                if (typeof arguments[3] === 'function') {
                                    if (UiObject.isCompass(arguments[2])) {
                                        // @Overload pickup<R>(root: UiObject, selector: Pickup.Selector, compass: Selector.Compass, callback: (o: any) => R): R;
                                        return this.pickup(/* root = */ arguments[0], /* selector = */ arguments[1], /* compass = */ arguments[2], UiObject.RESULT_TYPE_WIDGET, /* callback = */ arguments[3]);
                                    }
                                    // @Overload pickup<R>(root: UiObject, selector: Pickup.Selector, resultType: Pickup.ResultType, callback: (o: any) => R): R;
                                    return this.pickup(/* root = */ arguments[0], /* selector = */ arguments[1], UiObject.COMPASS_PASS_ON, /* resultType = */ arguments[2], /* callback = */ arguments[3]);
                                }
                                // @Overload pickup(root: UiObject, selector: Pickup.Selector, compass: Selector.Compass, resultType: Pickup.ResultType): any;
                                return this.pickup(/* root = */ arguments[0], /* selector = */ arguments[1], /* compass = */ arguments[2], /* resultType = */ arguments[3], /* callback = */ null);
                            }
                            // @Overload pickup<R>(selector: Pickup.Selector, compass: Selector.Compass, resultType: Pickup.ResultType, callback: (o: any) => R): R;
                            return this.pickup(/* root = */ null, /* selector = */ arguments[0], /* compass = */ arguments[1], /* resultType = */ arguments[2], /* callback = */ arguments[3]);
                        case 3:
                            if (arguments[0] instanceof UiObject) {
                                if (typeof arguments[2] === 'function') {
                                    // @Overload pickup<R>(root: UiObject, selector: Pickup.Selector, callback: (o: any) => R): R;
                                    return this.pickup(/* root = */ arguments[0], /* selector = */ arguments[1], UiObject.COMPASS_PASS_ON, UiObject.RESULT_TYPE_WIDGET, /* callback = */ arguments[2]);
                                }
                                if (UiObject.isCompass(arguments[2])) {
                                    // @Overload pickup(root: UiObject, selector: Pickup.Selector, compass: Selector.Compass): any;
                                    return this.pickup(/* root = */ arguments[0], /* selector = */ arguments[1], /* compass = */ arguments[2], UiObject.RESULT_TYPE_WIDGET, /* callback = */ null);
                                }
                                // @Overload pickup(root: UiObject, selector: Pickup.Selector, resultType: Pickup.ResultType): any;
                                return this.pickup(/* root = */ arguments[0], /* selector = */ arguments[1], UiObject.COMPASS_PASS_ON, /* resultType = */ arguments[2], /* callback = */ null);
                            }
                            if (typeof arguments[2] === 'function') {
                                if (UiObject.isCompass(arguments[1])) {
                                    // @Overload pickup<R>(selector: Pickup.Selector, compass: Selector.Compass, callback: (o: any) => R): R;
                                    return this.pickup(/* root = */ null, /* selector = */ arguments[0], /* compass = */ arguments[1], UiObject.RESULT_TYPE_WIDGET, /* callback = */ arguments[2]);
                                }
                                // @Overload pickup<R>(selector: Pickup.Selector, resultType: Pickup.ResultType, callback: (o: any) => R): R;
                                return this.pickup(/* root = */ null, /* selector = */ arguments[0], UiObject.COMPASS_PASS_ON, /* resultType = */ arguments[1], /* callback = */ arguments[2]);
                            }
                            // @Overload pickup(selector: Pickup.Selector, compass: Selector.Compass, resultType: Pickup.ResultType): any;
                            return this.pickup(/* root = */ null, /* selector = */ arguments[0], /* compass = */ arguments[1], /* resultType = */ arguments[2], /* callback = */ null);
                        case 2:
                            if (arguments[0] instanceof UiObject) {
                                // @Overload pickup(root: UiObject, selector: Pickup.Selector): any;
                                return this.pickup(/* root = */ arguments[0], /* selector = */ arguments[1], UiObject.COMPASS_PASS_ON, UiObject.RESULT_TYPE_WIDGET, /* callback = */ null);
                            }
                            if (typeof arguments[1] === 'function') {
                                // @Overload pickup<R>(selector: Pickup.Selector, callback: (o: any) => R): R;
                                return this.pickup(/* root = */ null, /* selector = */ arguments[0], UiObject.COMPASS_PASS_ON, UiObject.RESULT_TYPE_WIDGET, /* callback = */ arguments[1]);
                            }
                            if (UiObject.isCompass(arguments[1])) {
                                // @Overload pickup(selector: Pickup.Selector, compass: Selector.Compass): any;
                                return this.pickup(/* root = */ null, /* selector = */ arguments[0], /* compass = */ arguments[1], UiObject.RESULT_TYPE_WIDGET, /* callback = */ null);
                            }
                            // @Overload pickup(selector: Pickup.Selector, resultType: Pickup.ResultType): any;
                            return this.pickup(/* root = */ null, /* selector = */ arguments[0], UiObject.COMPASS_PASS_ON, /* resultType = */ arguments[1], /* callback = */ null);
                        case 1:
                            // @Overload pickup(selector: Pickup.Selector): any;
                            return this.pickup(/* root = */ null, /* selector = */ arguments[0], UiObject.COMPASS_PASS_ON, UiObject.RESULT_TYPE_WIDGET, /* callback = */ null);
                        case 0:
                            // @Signature pickup(): UiObject;
                            return findOnce();
                        default:
                            throw Error(i18n('error-invalid-arguments-with-name-and-args', 'pickup', Array.from(arguments).join()));
                    }
                },
                detect(w, compass, resultType, callback) {
                    switch (arguments.length) {
                        case 4:
                            // @Signature detect<R>(w: UiObject, compass: Detect.Compass, resultType: Detect.ResultType, callback: ((o: any) => R)): R;
                            return UiObject.detect(w, compass, resultType, callback);
                        case 3:
                            if (typeof arguments[2] === 'function') {
                                if (UiObject.isCompass(arguments[1])) {
                                    // @Overload detect<R>(w: UiObject, compass: Detect.Compass, callback: ((w: UiObject) => R)): R;
                                    return this.detect(w, /* compass = */ arguments[1], UiObject.RESULT_TYPE_WIDGET, /* callback = */ arguments[2]);
                                }
                                // @Overload detect<R>(w: UiObject, resultType: Detect.ResultType, callback: ((o: any) => R)): R;
                                return this.detect(w, UiObject.COMPASS_PASS_ON, /* resultType = */ arguments[1], /* callback = */ arguments[2]);
                            }
                            // @Overload detect(w: UiObject, compass: Detect.Compass, resultType: Detect.ResultType): any;
                            return this.detect(w, /* compass = */ arguments[1], /* resultType = */ arguments[2], /* callback = */ null);
                        case 2:
                            if (typeof arguments[1] === 'function') {
                                // @Overload detect<T extends UiObject, R>(w: T, callback: ((w: T) => R)): R;
                                return this.detect(w, UiObject.COMPASS_PASS_ON, UiObject.RESULT_TYPE_WIDGET, /* callback = */ arguments[1]);
                            }
                            if (UiObject.isCompass(arguments[1])) {
                                // @Overload detect(w: UiObject, compass: Detect.Compass): any;
                                return this.detect(w, /* compass = */ arguments[1], UiObject.RESULT_TYPE_WIDGET, /* callback = */ null);
                            }
                            // @Overload detect(w: UiObject, resultType: Detect.ResultType): any;
                            return this.detect(w, UiObject.COMPASS_PASS_ON, /* resultType = */ arguments[1], /* callback = */ null);
                        default:
                            throw Error(i18n('error-invalid-arguments-with-name-and-args', 'detect', Array.from(arguments).join()));
                    }
                },
                existsAll() {
                    return Array.from(arguments).every(sel => this.pickup(sel, '?'));
                },
                existsOne() {
                    return Array.from(arguments).some(sel => this.pickup(sel, '?'));
                },
            });

            for (let method in scriptRuntime.selector()) {
                if (_.isInJavaObject(method) || _.isInScope(method)) {
                    // @Caution by SuperMonster003 as of Oct 23, 2022.
                    //  ! The following methods have been assigned by 'automator' module,
                    //  ! which not belonging to UiSelector:
                    //  ! [ click / longClick / scrollDown / scrollUp / setText ].
                    continue;
                }
                // @Caution by SuperMonster003 as of Apr 26, 2022.
                //  ! Make param "method" scoped to this IIFE immediately.
                //  ! Unwrapping this IIFE will cause TypeError.
                //  ! Reappearance: `let f = idMatches; f(/.+/).findOnce();`
                //  ! TypeError: Cannot find function findOnce in object true.
                // @ScopeBinding
                scope[method] = (/* @IIFE */ (method) => {
                    return function () {
                        let s = selector();
                        try {
                            return s[method].apply(s, arguments);
                        } catch (e) {
                            scriptRuntime.console.warn(`${e.message}\n${e.stack}`);
                            scriptRuntime.console.warn(`method: ${method}, arguments: ${Array.from(arguments).join()}`);
                            throw e;
                        }
                    };
                })(method);
            }
        },
    };

    /**
     * @type {() => Internal.Selector}
     */
    const selector = new _.Selector();

    _.scopeAugment();

    return selector;
};