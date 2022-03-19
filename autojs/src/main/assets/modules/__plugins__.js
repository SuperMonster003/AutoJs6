module.exports = function (runtime, scope) {
    const plugins = () => void 0;

    plugins.load = function (packageName) {
        let plugin = runtime.plugins.load(packageName);
        let index = require(plugin.getMainScriptPath());
        return index(plugin.unwrap());
    };

    return plugins;
};