let _ = {
    init(__runtime__, scope) {
        this.runtime = __runtime__;
        this.scope = scope;

        this.rtSensors = __runtime__.sensors;
        this.sensors = Object.create(this.rtSensors);
    },
    getModule() {
        return this.sensors;
    },
    selfAugment() {
        Object.assign(this.sensors, {
            // Empty augmentations so far
        });
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
 * @return {Internal.Sensors}
 */
module.exports = function (__runtime__, scope) {
    return $.getModule(__runtime__, scope);
};