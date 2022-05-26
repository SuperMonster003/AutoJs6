let _ = {
    init(__runtime__, scope) {
        this.runtime = __runtime__;
        this.scope = scope;

        this.rtMedia = __runtime__.media;
        this.media = Object.create(this.rtMedia);
    },
    getModule() {
        return this.media;
    },
    selfAugment() {
        Object.assign(this.media, {
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
 * @return {Internal.Media}
 */
module.exports = function (__runtime__, scope) {
    return $.getModule(__runtime__, scope);
};