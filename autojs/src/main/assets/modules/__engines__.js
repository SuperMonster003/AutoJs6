/* Here, importClass() is not recommended for intelligent code completion in IDE like WebStorm. */
/* The same is true of destructuring assignment syntax (like `let {Uri} = android.net`). */

const ExecutionConfig = com.stardust.autojs.execution.ExecutionConfig;

let _ = {
    init(__runtime__, scope) {
        this.runtime = __runtime__;
        this.scope = scope;

        this.rtEngines = __runtime__.engines;
        this.engines = {};
    },
    getModule() {
        return this.engines;
    },
    selfAugment() {
        Object.assign(this.engines, {
            all: this.rtEngines.all.bind(this.rtEngines),
            myEngine: this.rtEngines.myEngine.bind(this.rtEngines),
            stopAll: this.rtEngines.stopAll.bind(this.rtEngines),
            stopAllAndToast: this.rtEngines.stopAllAndToast.bind(this.rtEngines),
            execScript(name, script, config) {
                return _.rtEngines.execScript(name, script, _.fillConfig(config));
            },
            execScriptFile(path, config) {
                return _.rtEngines.execScriptFile(path, _.fillConfig(config));
            },
            execAutoFile(path, config) {
                return _.rtEngines.execAutoFile(path, _.fillConfig(config));
            },
        });

        this.engines.myEngine().setExecArgv( /* @IIFE */ ((e) => {
            let execArgv = {};
            let iterator = e.getTag(ExecutionConfig.CREATOR.getTag()).arguments.entrySet().iterator();
            while (iterator.hasNext()) {
                let entry = iterator.next();
                execArgv[entry.getKey()] = entry.getValue();
            }
            return execArgv;
        })(this.engines.myEngine()));
    },
    /**
     * @param {Engines.ExecutionConfig} config
     * @return {com.stardust.autojs.execution.ExecutionConfig}
     */
    fillConfig(config) {
        let executionConfig = new ExecutionConfig();
        let c = config || {};

        executionConfig.setWorkingDirectory(c.path || files.cwd());
        executionConfig.setDelay(c.delay || 0);
        executionConfig.setInterval(c.interval || 0);
        executionConfig.setLoopTimes(typeof c.loopTimes === 'number' ? c.loopTimes : 1);

        Object.entries(c.arguments || []).forEach((value) => {
            let [k, v] = value;
            executionConfig.setArgument(k, v);
        });

        return executionConfig;
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
 * @return {Internal.Engines}
 */
module.exports = function (__runtime__, scope) {
    return $.getModule(__runtime__, scope);
};