// noinspection JSUnusedGlobalSymbols,NpmUsedModulesInstalled

// @Caution by SuperMonster003 on Apr 19, 2022.
//  ! Do not declare with const because variable has already
//  ! declared globally in RhinoJavaScriptEngine.kt
let global = this;

let _ = {
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
    define(property, descriptorValue) {
        Object.defineProperty(global, property, {
            value: descriptorValue,
            // Not necessary, just for emphasis.
            writable: false,
        });
    },
    bind(modules) {
        modules.forEach((module) => {
            if (Array.isArray(module)) {
                this.bind(module);
            } else if (module in this.moduleSpecials) {
                this.moduleSpecials[module].call();
            } else {
                global[module] = require(`__${module}__`)(runtime, global);
            }
        });
    },

};

let $ = {
    init() {
        runtime.init();

        // 设置 JavaScriptBridges 用于与 Java 层的交互和数据转换
        runtime.bridges.setBridges(require('__bridges__'));

        return this;
    },
    bind() {
        this.bindEpilogue();
        this.bindModules();
        this.bindPrologue();
    },
    bindEpilogue() {
        Object.assign(global, {
            Intent: android.content.Intent,
            Paint: android.graphics.Paint,
            KeyEvent: android.view.KeyEvent,
            Shell: com.stardust.autojs.core.util.Shell,
            Image: com.stardust.autojs.core.image.ImageWrapper,
            Canvas: com.stardust.autojs.core.graphics.ScriptCanvas,
            MutableOkHttp: com.stardust.autojs.core.http.MutableOkHttp,
            OkHttpClient: Packages.okhttp3.OkHttpClient,
            // 重定向 importClass 使其支持字符串参数
            importClass: ( /* @IIFE */ () => {
                let __importClass__ = importClass;
                return function (pack) {
                    __importClass__(typeof pack === 'string' ? Packages[pack] : pack);
                };
            })(),
            __asGlobal__(obj, functions) {
                functions.forEach((name) => {
                    let {objName, globalName} = ( /* @IIFE */ () => {
                        if (typeof name === 'string') {
                            let objName = globalName = name;
                            return {objName, globalName};
                        }
                        if (typeof name === 'object') {
                            let [objName] = Object.values(name);
                            let [globalName] = Object.keys(name);
                            return {objName, globalName};
                        }
                        throw TypeError(`Unknown type of name (${name}) in functions for __asGlobal__`);
                    })();
                    let f = obj[objName];
                    if (typeof f !== 'function') {
                        throw ReferenceError(`${objName} is not exist on object: ${obj}`);
                    }
                    global[globalName] = f.bind(obj);
                });
            },
            __exitIfError__(action) {
                try {
                    return action();
                } catch (err) {
                    if (err instanceof java.lang.Throwable) {
                        runtime.exit(err);
                    } else if (err instanceof Error) {
                        runtime.exit(new org.mozilla.javascript.EvaluatorException(`${err.name}: ${err.message}`, err.fileName, err.lineNumber));
                    } else {
                        runtime.exit();
                    }
                }
            },
        });
    },
    bindModules() {
        // @OrderMatters by SuperMonster003 on May 2, 2022.
        _.bind([
            /* First team */
            ['polyfill', 'json', 'globals', 'util'],

            /* ! timers < promise # setTimeout() */
            /* ! timers < automator # setTimeout() in result-adapter */
            /* ! timers < images # setTimeout() in result-adapter */
            ['timers', 'promise', 'automator'],

            /* ! threads < images # threads.atomic() */
            /* ! images < dialogs # colors */
            ['threads', 'images', 'dialogs'],

            /* ! console < tasks # console.error */
            ['console', 'tasks'],

            /* ! engines < continuation # engines.myEngine() */
            ['engines', 'continuation'],

            /* Safe to put last regardless of the order, no guarantee ;). */
            ['RootAutomator', 'floaty', 'ui'],
            ['storages', 'device', 'files', 'autojs', 'http'],
            ['recorder', 'base64', 'media', 'sensors', 'web'],
            ['selector', 'events', 'shell', 'plugins', 'app'],
        ]);
    },
    bindPrologue() {
        // 重定向 require 以支持相对路径和 npm 模块
        global.Module = require('jvm-npm');
        global.require = id => Module.require(id);
    },
};

$.init().bind();