/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Files}
 */
module.exports = function (scriptRuntime, scope) {
    const RtFiles = org.autojs.autojs.runtime.api.Files;

    let _ = {
        Files: (/* @IIFE */ () => {
            /**
             * @extends Internal.Files
             */
            const Files = function () {
                /* Empty body. */
            };

            Files.prototype = {
                constructor: Files,
                join(parent, children) {
                    return RtFiles.join.apply(RtFiles, arguments);
                },
                toFile(path) {
                    return new java.io.File(this.path(path));
                },
            };

            Object.setPrototypeOf(Files.prototype, scriptRuntime.files);

            return Files;
        })(),
        scopeAugment() {
            Object.assign(scope, {
                /** @global */
                open(path, mode, encoding, bufferSize) {
                    return files.open.apply(files, arguments);
                },
            });
        },
    };

    /**
     * @type {Internal.Files}
     */
    const files = new _.Files();

    _.scopeAugment();

    return files;
};