// Type definitions for AutoJs6 internal module engines
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 4.3.5
//
// Last modified: Oct 21, 2021

/// <reference path="../index.d.ts" />

/**
 * @Source %AutoJs6Assets%/modules/__engines__.js
 */

declare namespace Internal {

    class Engines {

        public constructor(engineService: org.autojs.autojs.engine.ScriptEngineService, scriptRuntime: ScriptRuntime);

        /**
         * @example
         * console.log(engines.myEngine().execArgv);
         *
         * @example Source code summary (zh-CN: 源代码摘要)
         * let rtEngines = __runtime__.engines;
         * engines.myEngine = function () {
         *     return rtEngines.myEngine();
         * };
         *
         * @example Source code summary (zh-CN: 源代码摘要)
         * private JavaScriptEngine mScriptEngine;
         * public JavaScriptEngine myEngine() {
         *     return mScriptEngine;
         * }
         * @see org.autojs.autojs.engine.RhinoJavaScriptEngine
         */
        public myEngine(): org.autojs.autojs.engine.RhinoJavaScriptEngine;

        /**
         * @example
         * console.log(engines.all().filter((e) => {
         *     return e.getTag('exclusive_task') && e.id < engines.myEngine().id;
         * }));
         *
         * @example Source code summary (zh-CN: 源代码摘要)
         * let rtEngines = __runtime__.engines;
         * engines.all = function () {
         *     return rtEngines.all();
         * };
         *
         * @example Source code summary (zh-CN: 源代码摘要)
         * private ScriptEngineService mEngineService;
         * private ScriptRuntime mScriptRuntime;
         * public Object all() {
         *     return mScriptRuntime.bridges.toArray(mEngineService.getEngines());
         * }
         * @see org.autojs.autojs.engine.ScriptEngineService
         * @see org.autojs.autojs.runtime.ScriptRuntime
         */
        public all(): org.autojs.autojs.engine.ScriptEngine<any>[];

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * let rtEngines = __runtime__.engines;
         * engines.stopAll = rtEngines.stopAll.bind(rtEngines);
         *
         * @example Source code summary (zh-CN: 源代码摘要)
         * private ScriptEngineService mEngineService;
         * public int stopAll() {
         *     return mEngineService.stopAll();
         * }
         * @see org.autojs.autojs.engine.ScriptEngineService
         */
        public stopAll(): number;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * let rtEngines = __runtime__.engines;
         * engines.stopAllAndToast = rtEngines.stopAllAndToast.bind(rtEngines);
         *
         * @example Source code summary (zh-CN: 源代码摘要)
         * private ScriptEngineService mEngineService;
         * public void stopAllAndToast() {
         *     mEngineService.stopAllAndToast();
         * }
         * @see org.autojs.autojs.engine.ScriptEngineService
         */
        public stopAllAndToast(): void;

        /**
         * @example without extended module
         * engines.execScript("hello", "!" + function () {
         *     let argv = engines.myEngine().execArgv;
         *     let argv_info = [];
         *     for (let i in argv.information) {
         *         if (argv.information.hasOwnProperty(i)) {
         *             argv_info.push(i + ": " + argv.information[i]);
         *         }
         *     }
         *     alert(argv.title + "\n\n" + argv_info.join('\n'));
         * }.toString() + "()", {
         *     arguments: {
         *         title: "information",
         *         information: {
         *             name: "003",
         *             age: 3,
         *             born: "1333/3/3",
         *         },
         *     },
         * });
         *
         * @example with extended module
         * enginesx.execScript("hello", "!" + function () {
         *     let argv = engines.myEngine().execArgv;
         *     let argv_info = [];
         *     for (let i in argv.information) {
         *         if (argv.information.hasOwnProperty(i)) {
         *             argv_info.push(i + ": " + argv.information[i]);
         *         }
         *     }
         *     alert(
         *         "continuation: " + engines.myEngine().hasFeature('continuation') + '\n\n' +
         *         argv.title + "\n\n" + argv_info.join('\n')
         *     );
         * }.toString() + "()", {
         *     scriptConfig: {
         *         useFeatures: ['continuation'],
         *     },
         *     arguments: {
         *         title : "information",
         *         information: {
         *             name: "003",
         *             age: 3,
         *             born: "1333/3/3",
         *         },
         *     },
         * });
         *
         * @example Source code summary (zh-CN: 源代码摘要)
         * let rtEngines = __runtime__.engines;
         * engines.execScript = function (name, script, config) {
         *     return rtEngines.execScript(name, script, fillConfig(config));
         * };
         * function fillConfig(c) {
         *     let config = new org.autojs.autojs.execution.ExecutionConfig();
         *     c = c || {};
         *     c.path = c.path || files.cwd();
         *     if (c.path) {
         *         config.workingDirectory = c.path;
         *     }
         *     config.delay = c.delay || 0;
         *     config.interval = c.interval || 0;
         *     config.loopTimes = (c.loopTimes === undefined) ? 1 : c.loopTimes;
         *     if (c.arguments) {
         *         let arguments = c.arguments;
         *         for (let key in arguments) {
         *             if (arguments.hasOwnProperty(key)) {
         *                 config.setArgument(key, arguments[key]);
         *             }
         *         }
         *     }
         *     return config;
         * }
         *
         * @example Source code summary (zh-CN: 源代码摘要)
         * private ScriptEngineService mEngineService;
         * public ScriptExecution execScript(String name, String script, ExecutionConfig config) {
         *     return mEngineService.execute(new StringScriptSource(name, script), config);
         * }
         * @see org.autojs.autojs.engine.ScriptEngineService
         * @see org.autojs.autojs.execution.ExecutionConfig
         * @see org.autojs.autojs.execution.ScriptExecution
         * @see org.autojs.autojs.script.StringScriptSource
         */
        public execScript(name: string, script: string, config: org.autojs.autojs.execution.ExecutionConfig): org.autojs.autojs.execution.ScriptExecution;

        /**
         * @example without extended module
         * engines.execScriptFile("./test.js", {
         *     arguments: {
         *         title: "information",
         *         information: {
         *             name: "003",
         *             age: 3,
         *             born: "1333/3/3",
         *         },
         *     },
         * });
         *
         * @example with extended module
         * enginesx.execScript("./test.js", {
         *     scriptConfig: {
         *         useFeatures: ['continuation'],
         *     },
         *     arguments: {
         *         title : "information",
         *         information: {
         *             name: "003",
         *             age: 3,
         *             born: "1333/3/3",
         *         },
         *     },
         * });
         *
         * @example Source code summary (zh-CN: 源代码摘要)
         * let rtEngines = __runtime__.engines;
         * engines.execScriptFile = function (path, config) {
         *     return rtEngines.execScriptFile(path, fillConfig(config));
         * };
         * function fillConfig(c) {
         *     let config = new org.autojs.autojs.execution.ExecutionConfig();
         *     c = c || {};
         *     c.path = c.path || files.cwd();
         *     if (c.path) {
         *         config.workingDirectory = c.path;
         *     }
         *     config.delay = c.delay || 0;
         *     config.interval = c.interval || 0;
         *     config.loopTimes = (c.loopTimes === undefined) ? 1 : c.loopTimes;
         *     if (c.arguments) {
         *         let arguments = c.arguments;
         *         for (let key in arguments) {
         *             if (arguments.hasOwnProperty(key)) {
         *                 config.setArgument(key, arguments[key]);
         *             }
         *         }
         *     }
         *     return config;
         * }
         *
         * @example Source code summary (zh-CN: 源代码摘要)
         * private ScriptEngineService mEngineService;
         * private ScriptRuntime mScriptRuntime;
         * public ScriptExecution execScriptFile(String path, ExecutionConfig config) {
         *     return mEngineService.execute(new JavaScriptFileSource(mScriptRuntime.files.path(path)), config);
         * }
         * @see org.autojs.autojs.engine.ScriptEngineService
         * @see org.autojs.autojs.execution.ExecutionConfig
         * @see org.autojs.autojs.execution.ScriptExecution
         * @see org.autojs.autojs.runtime.ScriptRuntime
         * @see org.autojs.autojs.script.JavaScriptFileSource
         */
        public execScriptFile(path: string, config: org.autojs.autojs.execution.ExecutionConfig): org.autojs.autojs.execution.ScriptExecution;

        /**
         * @example
         * engines.execAutoFile("./test.auto");
         *
         * @example Source code summary (zh-CN: 源代码摘要)
         * let rtEngines = __runtime__.engines;
         * engines.execAutoFile = function (path, config) {
         *     return rtEngines.execAutoFile(path, fillConfig(config));
         * };
         * function fillConfig(c) {
         *     let config = new org.autojs.autojs.execution.ExecutionConfig();
         *     c = c || {};
         *     c.path = c.path || files.cwd();
         *     if (c.path) {
         *         config.workingDirectory = c.path;
         *     }
         *     config.delay = c.delay || 0;
         *     config.interval = c.interval || 0;
         *     config.loopTimes = (c.loopTimes === undefined) ? 1 : c.loopTimes;
         *     if (c.arguments) {
         *         let arguments = c.arguments;
         *         for (let key in arguments) {
         *             if (arguments.hasOwnProperty(key)) {
         *                 config.setArgument(key, arguments[key]);
         *             }
         *         }
         *     }
         *     return config;
         * }
         *
         * @example Source code summary (zh-CN: 源代码摘要)
         * private ScriptEngineService mEngineService;
         * private ScriptRuntime mScriptRuntime;
         * public ScriptExecution execAutoFile(String path, ExecutionConfig config) {
         *     return mEngineService.execute(new AutoFileSource(mScriptRuntime.files.path(path)), config);
         * }
         * @see org.autojs.autojs.engine.ScriptEngineService
         * @see org.autojs.autojs.execution.ExecutionConfig
         * @see org.autojs.autojs.execution.ScriptExecution
         * @see org.autojs.autojs.runtime.ScriptRuntime
         * @see org.autojs.autojs.script.AutoFileSource
         */
        public execAutoFile(path: string, config: org.autojs.autojs.execution.ExecutionConfig): org.autojs.autojs.execution.ScriptExecution;

    }

    namespace Engines {

        interface ExecutionConfig extends org.autojs.autojs.execution.ExecutionConfig {

            /** @default files.cwd() */
            path?: string | string[];
            /** @default 0 */
            delay?: number;
            /** @default 0 */
            interval?: number;
            /** @default 1 */
            loopTimes?: number;
            arguments?: object;

        }

    }

}