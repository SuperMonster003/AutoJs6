// noinspection JSUnusedGlobalSymbols,NpmUsedModulesInstalled,JSUnusedLocalSymbols

'use strict';

// @Caution by SuperMonster003 on Apr 19, 2022.
//  ! Do not declare with const because variable has already
//  ! declared globally in RhinoJavaScriptEngine.kt.
let global = this;

/* Global classes. */

let Paint = android.graphics.Paint;
let App = org.autojs.autojs.util.App;
let RootMode = org.autojs.autojs.util.RootUtils.RootMode;
let Pref = org.autojs.autojs.pref.Pref;
let Shell = org.autojs.autojs.runtime.api.Shell;
let Intent = global.Intent = android.content.Intent;
let Toast = android.widget.Toast;
let KeyEvent = android.view.KeyEvent;
let Version = Packages.io.github.g00fy2.versioncompare.Version;
let Image = org.autojs.autojs.core.image.ImageWrapper;
let ColorTable = org.autojs.autojs.core.image.ColorTable;
let Canvas = org.autojs.autojs.core.graphics.ScriptCanvas;
let UiObject = org.autojs.autojs.core.automator.UiObject;
let UiObjectCollection = org.autojs.autojs.core.automator.UiObjectCollection;
let ImageWrapper = org.autojs.autojs.core.image.ImageWrapper;
let UiSelector = org.autojs.autojs.core.accessibility.UiSelector;
let VolatileBox = org.autojs.concurrent.VolatileBox;
let MutableOkHttp = org.autojs.autojs.core.http.MutableOkHttp;
let OkHttpClient = Packages.okhttp3.OkHttpClient;
let ScriptInterruptedException = org.autojs.autojs.runtime.exception.ScriptInterruptedException;
let ReentrantLock = java.util.concurrent.locks.ReentrantLock;
let ScreenMetrics = org.autojs.autojs.runtime.api.ScreenMetrics;

/* Global assignment. */

Object.assign(this, {
    io: Packages.io,
    de: Packages.de,
    ezy: Packages.ezy,
    kotlin: Packages.kotlin,
    okhttp3: Packages.okhttp3,
    androidx: Packages.androidx,
    isNullish(o) {
        // nullish coalescing operator: ??
        return o === null || o === undefined;
    },
    isObjectSpecies(o) {
        return species(o) === 'Object';
    },
    isJavaClass(o) {
        return species(o) === 'JavaClass';
    },
    isJavaPackage(o) {
        return species(o) === 'JavaPackage';
    },
    isJavaObject(o) {
        if (o !== null && typeof o === 'object') {
            if (typeof o.getClass === 'function') {
                try {
                    return o.getClass() instanceof java.lang.Class;
                } catch (_) {
                    // Ignored.
                }
            }
        }
        return false;
    },
    isInteger(o) {
        return Number.isInteger(o);
    },
    isPrimitive(o) {
        // @Comment by SuperMonster003 on Apr 21, 2022.

        // return this.isNull(arg)
        //     || this.isBoolean(arg)
        //     || this.isNumber(arg)
        //     || this.isString(arg)
        //     || this.isSymbol(arg)
        //     || this.isUndefined(arg)
        //     || this.isBigInt(arg);

        return o !== Object(o);
    },
    isReference(o) {
        return o === Object(o);
    },
    isEmptyObject(obj) {
        // noinspection LoopStatementThatDoesntLoopJS
        for (let name in obj) {
            return false;
        }
        return true;
    },
    species(o) {
        return Object.prototype.toString.call(o).slice('[Object\x20'.length, ']'.length * -1);
    },
    /**
     * @param {{getClass(): java.lang.Class<?>}} o
     * @return {*}
     */
    unwrapJavaObject(o) {

        /*
           @Hint by SuperMonster003 on Apr 21, 2022.
            ! Code below also works.
            ! However, IDE like WebStorm may not show the syntax highlighting
            ! for methods ("TypeScript Declarations" is needed)
        */
        // switch (o.getClass()) {
        //     case java.lang.Boolean:
        //         return o.booleanValue();
        //     ...
        // }

        if (o instanceof java.lang.Boolean) {
            return o.booleanValue();
        }

        if (o instanceof java.lang.Double) {
            return o.doubleValue();
        }

        if (o instanceof java.lang.Float) {
            return o.floatValue();
        }

        if (o instanceof java.lang.Integer) {
            return o.intValue();
        }

        if (o instanceof java.lang.Long) {
            return o.longValue();
        }

        if (o instanceof java.lang.Short) {
            return o.shortValue();
        }

        if (o instanceof java.lang.Character) {
            return o.charValue();
        }

        if (o instanceof java.lang.String) {
            return String(o);
        }

        return o;
    },
});

( /* @ModuleIIFE */ () => {

    let _ = {
        Throwable: java.lang.Throwable,
        EvaluatorException: org.mozilla.javascript.EvaluatorException,
        moduleSpecials: {
            polyfill() {
                require('polyfill').fill();
            },
            json() {
                // @Commented by SuperMonster003 on May 24, 2022.
                //  ! Use internal Rhino JSON for better performance and compatibility.
                // _.define('JSON', require('json2'));
            },
            promise() {
                /**
                 * @Hint by SuperMonster003 on Apr 17, 2022.
                 * Try using internal Rhino Promise instead.
                 * Legacy Promise may be ignored by AutoJs6 engine when Promise was placed at the end.
                 *
                 * @example Code for reappearance
                 * let test = () => 'hello';
                 * Promise.resolve().then(test).then(res => log(res));
                 */
                /**
                 * @Hint by SuperMonster003 on May 24, 2022.
                 * Use updated external Promise from Rhino instead of internal Rhino Promise.
                 * Rhino Promise is not compatible with AutoJs6 continuation and causes a suspension.
                 * Also, updated Promise solved the problem mentioned on Apr 17, 2022.
                 *
                 * @example Code for reappearance
                 * AutoJs6/示例代码/协程/协程HelloWorld.js
                 */
                /**
                 * Substitution of Promise.
                 */
                _.define('Promise', require('promise'));
            },
        },
        cacheR: {},
        define(property, descriptorValue) {
            Object.defineProperty(global, property, { value: descriptorValue, enumerable: true });
        },
        bind(modules) {
            modules.forEach((module) => {
                if (Array.isArray(module)) {
                    this.bind(module);
                } else if (module in this.moduleSpecials) {
                    this.moduleSpecials[module].call();
                } else {
                    // Object.defineProperty(global, module, { value: require(`__${module}__`)(runtime, global), enumerable: true });
                    global[module] = require(`__${module}__`)(runtime, global);
                    try {
                        if (typeof global[module] === 'object') {
                            if (!(Object.hasOwn(global[module], 'toString'))) {
                                global[module].toString = () => module;
                            }
                        }
                    } catch (e) {
                        // Ignored.
                    }
                }
            });
        },
        getProxyObjectInstance(getter, setter) {
            let object = new org.autojs.autojs.rhino.ProxyObject();
            object.__proxy__ = Object.assign({}, getter ? { get: getter } : {}, setter ? { set: setter } : {});
            return object;
        },
    };

    let $ = {
        init() {
            runtime.init();

            // 设置 JavaScriptBridges 用于与 Java 层的交互和数据转换.
            runtime.bridges.setBridges(require('__bridges__'));

            return this;
        },
        bind() {
            this.bindEpilogue();
            this.bindModules();
            this.bindPrologue();
        },
        bindEpilogue() {
            const __importClass__ = importClass;
            const __importPackage__ = importPackage;

            Object.assign(global, {
                // 重定向 importClass 使其支持字符串参数.
                /**
                 * @global
                 */
                importClass() {
                    Array.from(arguments).forEach(clazz => {
                        __importClass__(typeof clazz === 'string' ? Packages[clazz] : clazz);
                    });
                },
                // 重定向 importPackage 使其支持字符串参数.
                /**
                 * @global
                 */
                importPackage() {
                    Array.from(arguments).forEach(pkg => {
                        __importPackage__(typeof pkg === 'string' ? Packages[pkg] : pkg);
                    });
                },
                /**
                 * @global
                 */
                __asGlobal__(obj, functions, scope) {
                    if (typeof scope !== 'object') {
                        // @Overload __asGlobal__(obj: object, functions: (string | { [prop: string]: string })[]): void
                        return this.__asGlobal__(obj, functions, global);
                    }
                    functions.forEach((name) => {
                        let { objKey, scopeKey } = ( /* @IIFE */ () => {
                            if (typeof name === 'string') {
                                let objKey = scopeKey = name;
                                return { objKey, scopeKey };
                            }
                            if (typeof name === 'object') {
                                let [ objKey ] = Object.values(name);
                                let [ scopeKey ] = Object.keys(name);
                                return { objKey, scopeKey };
                            }
                            throw TypeError(`Unknown type of name (${name}) in functions for __asGlobal__`);
                        })();
                        let f = obj[objKey];
                        if (typeof f !== 'function') {
                            throw ReferenceError(`${objKey} doesn't exist on object: ${obj}`);
                        }
                        scope[scopeKey] = f.bind(obj);
                    });
                },
                /**
                 * @global
                 */
                __exitIfError__(action) {
                    try {
                        return action();
                    } catch (err) {
                        if (err instanceof _.Throwable) {
                            runtime.exit(err);
                        } else if (err instanceof Error) {
                            runtime.exit(new _.EvaluatorException(`${err.name}: ${err.message}`, err.fileName, err.lineNumber));
                        } else {
                            runtime.exit();
                        }
                    }
                },
            });

            Object.defineProperties(global, {
                R: {
                    get() {
                        return new _.getProxyObjectInstance(function (type) {
                            return _.cacheR[type] = _.cacheR[type] || new _.getProxyObjectInstance(function (name) {
                                let ctx = typeof activity !== 'undefined' ? activity : context;
                                if (type === 'strings') {
                                    type = 'string';
                                }
                                return ctx.resources.getIdentifier(name, type, ctx.packageName);
                            });
                        });
                    },
                    enumerable: true,
                },
            });
        },
        bindModules() {
            // @OrderMatters by SuperMonster003 on May 2, 2022.
            _.bind([
                /* First team */
                [ 'polyfill', 'json', 'util' ],

                /* ! autojs < app */
                /* ! shell < app */
                /* ! files < app */
                [ 'autojs', 'shell', 'files', 'app' ],

                /* ! timers < promise # setTimeout() */
                /* ! timers < automator # setTimeout() in result-adapter */
                /* ! timers < images # setTimeout() in result-adapter */
                [ 'timers', 'promise', 'automator' ],

                /* ! threads < images # threads.atomic() */
                /* ! ui < images */
                /* ! ui < dialogs # ui.run() */
                /* ! images < dialogs # colors */
                /* ! engines < continuation # engines.myEngine() */
                /* ! continuation < http */
                /* ! ui < http */
                /* ! ui < tasks */
                /* ! threads < tasks */
                /* ! files < tasks */
                /* ! files < ui */
                /* ! files < images */
                [ 'threads', 'ui', 'tasks' ],
                [ 'engines', 'continuation', 'http' ],
                [ 'images', 'dialogs' ],

                /* ! files < i18n */
                /* ! i18n < selector */
                [ 'i18n', 'selector' ],

                /* ! autojs < RootAutomator */
                [ 'RootAutomator' ],

                /* ! files < console */
                [ 'console' ],

                /* ! plugins < %extensions% */
                /* ! Arrayx < Mathx */
                /* ! Numberx < Mathx */
                [ 'plugins', 'Arrayx', 'Numberx', 'Mathx' ],

                /* Safe to put last regardless of the order, no guarantee ;). */
                [ 'floaty', 'storages', 'device', 'recorder' ],
                [ 'media', 'sensors', 'web', 'events', 'base64' ],

                /* Last but not the least */
                [ 'globals' ],
            ]);
        },
        bindPrologue() {
            // 重定向 require 以支持相对路径和 npm 模块.
            global.Module = require('jvm-npm');
            global.require = id => Module.require(id);

            global.i18n.loadAll();
            global.i18n.setLocale('default');

            // @Comment by SuperMonster003 on Oct 6, 2022.
            //  ! The so-called "protect", has a strong possibility making users annoyed. :(
            //  ! Maybe a better way will come out someday.
            // global.$selfProtect();

            global.$appropriateProtect();

            if (Pref.isCompatibilityWithClassesForVer4xEnabled()) {
                require('redirect').perform();
            }

            if (Pref.isExtendingJsBuildInObjectsEnabled()) {
                plugins.extendAll();
            }
        },
    };

    $.init().bind();

})();