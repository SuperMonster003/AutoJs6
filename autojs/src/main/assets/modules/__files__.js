let _ = {
    init(__runtime__, scope) {
        this.runtime = __runtime__;
        this.scope = scope;

        this.rtFiles = __runtime__.files;
        this.files = Object.create(this.rtFiles);
    },
    getModule() {
        return this.files;
    },
    selfAugment() {
        Object.assign(this.files, {
            join(base) {
                let paths = Array.from(arguments).slice(1);
                return _.rtFiles.join(base, paths);
            },
        });
    },
    scopeAugment() {
        Object.assign(this.scope, {
            open(path, mode, encoding, bufferSize) {
                switch (arguments.length) {
                    case 1:
                        return _.files.open(path);
                    case 2:
                        return _.files.open(path, mode);
                    case 3:
                        return _.files.open(path, mode, encoding);
                    case 4:
                        return _.files.open(path, mode, encoding, bufferSize);
                }
            },
        });
    },
};

let $ = {
    getModule(__runtime__, scope) {
        _.init(__runtime__, scope);

        _.selfAugment();
        _.scopeAugment();

        return _.getModule();
    },
};

/**
 * @param {com.stardust.autojs.runtime.ScriptRuntime} __runtime__
 * @param {org.mozilla.javascript.Scriptable} scope
 * @return {Internal.Files}
 */
module.exports = function (__runtime__, scope) {
    return $.getModule(__runtime__, scope);
};