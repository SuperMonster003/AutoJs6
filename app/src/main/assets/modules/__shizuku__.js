// noinspection UnnecessaryLocalVariableJS,JSUnusedLocalSymbols

/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Shizuku}
 */
module.exports = function (scriptRuntime, scope) {

    const rtShizuku = scriptRuntime.shizuku;

    let _ = {
        ShizukuCtor: (/* @IIFE */ () => {
            /**
             * @implements Internal.Shizuku
             */
            const ShizukuCtor = function () {
                /** @global */
                const shizuku = function (cmd) {
                    return rtShizuku.execCommand(cmd);
                };
                return Object.assign(shizuku, ShizukuCtor.prototype);
            };

            ShizukuCtor.prototype = {
                constructor: ShizukuCtor,
                ensureService() {
                    rtShizuku.ensureService();
                },
                execCommand() {
                    return rtShizuku.execCommand.apply(rtShizuku, arguments);
                },
            };

            return ShizukuCtor;
        })(),
    };

    /**
     * @type {Internal.Shizuku}
     */
    const shizuku = new _.ShizukuCtor();

    return shizuku;
};