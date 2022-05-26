let _ = {
    init(__runtime__, scope) {
        this.runtime = __runtime__;
        this.scope = scope;
        this.plugins = () => {
            // Empty module body
        };
    },
    selfAugment() {
        Object.assign(this.plugins, {
            load(packageName) {
                let plugin = _.runtime.plugins.load(packageName);
                let moduleExportedFunc = require(plugin.getMainScriptPath());
                return moduleExportedFunc(plugin.unwrap());
            },
        });
    },
    getModule() {
        return this.plugins;
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
 * @return {Internal.Plugins}
 */
module.exports = function (__runtime__, scope) {
    return $.getModule(__runtime__, scope);
};