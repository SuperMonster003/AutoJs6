/* Overwritten protection. */

let { files } = global;

/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Engines}
 */
module.exports = function (scriptRuntime, scope) {
    const ExecutionConfig = org.autojs.autojs.execution.ExecutionConfig;

    const rtEngines = scriptRuntime.engines;

    let _ = {
        Engines: (/* @IIFE */ () => {
            /**
             * @extends Internal.Engines
             */
            const Engines = function () {
                /* Empty body. */
            };

            Engines.prototype = {
                constructor: Engines,
                all() {
                    return rtEngines.all();
                },
                myEngine() {
                    return rtEngines.myEngine();
                },
                stopAll() {
                    return rtEngines.stopAll();
                },
                stopAllAndToast() {
                    rtEngines.stopAllAndToast();
                },
                execScript(name, script, config) {
                    return rtEngines.execScript(name, script, _.fillConfig(config));
                },
                execScriptFile(path, config) {
                    return rtEngines.execScriptFile(path, _.fillConfig(config));
                },
                execAutoFile(path, config) {
                    return rtEngines.execAutoFile(path, _.fillConfig(config));
                },
            };

            return Engines;
        })(),
        /**
         * @param {Internal.Engines.ExecutionConfig | org.autojs.autojs.execution.ExecutionConfig} config
         * @return {org.autojs.autojs.execution.ExecutionConfig}
         */
        fillConfig(config) {
            let executionConfig = new ExecutionConfig();
            let c = config || {};

            executionConfig.setWorkingDirectory(c.path || files.cwd());
            executionConfig.setDelay(c.delay || 0);
            executionConfig.setInterval(c.interval || 0);
            executionConfig.setLoopTimes(typeof c.loopTimes === 'number' ? c.loopTimes : 1);

            Object.entries(c.arguments || []).forEach((value) => {
                let [ k, v ] = value;
                executionConfig.setArgument(k, v);
            });

            return executionConfig;
        },
        setEngineExecArgv() {
            engines.myEngine().setExecArgv(/* @IIFE */ ((e) => {
                let execArgv = {};
                let iterator = e.getTag(ExecutionConfig.tag).arguments.entrySet().iterator();
                while (iterator.hasNext()) {
                    let entry = iterator.next();
                    execArgv[entry.getKey()] = entry.getValue();
                }
                return execArgv;
            })(engines.myEngine()));
        },
    };

    /**
     * @type {Internal.Engines}
     */
    let engines = new _.Engines();

    _.setEngineExecArgv();

    return engines;
};