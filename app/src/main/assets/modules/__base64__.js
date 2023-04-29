/**
 * @param {org.autojs.autojs.runtime.ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Base64}
 */
module.exports = function (scriptRuntime, scope) {
    const Base64 = android.util.Base64;
    let _ = {
        Base64Ctor: (/* @IIFE */ () => {
            /**
             * @implements Internal.Base64
             */
            const Base64Ctor = function () {
                // Empty interface body.
            };

            Base64Ctor.prototype = {
                constructor: Base64Ctor,
                encode(str, encoding) {
                    // noinspection JSValidateTypes
                    let string = new java.lang.String(str);
                    let niceEncoding = _.parseEncoding(encoding);
                    return niceEncoding !== undefined
                        ? Base64.encodeToString(string.getBytes(niceEncoding), Base64.NO_WRAP)
                        : Base64.encodeToString(string.getBytes(), Base64.NO_WRAP);
                },
                decode(str, encoding) {
                    let niceEncoding = _.parseEncoding(encoding);
                    return niceEncoding !== undefined
                        ? String(new java.lang.String(Base64.decode(str, Base64.NO_WRAP), niceEncoding))
                        : String(new java.lang.String(Base64.decode(str, Base64.NO_WRAP)));
                },
            };

            return Base64Ctor;
        })(),
        /**
         * Ignored (but not fuzzy) regex matching for non-word characters.
         */
        parseEncoding: (encoding) => {
            return [
                StandardCharsets.ISO_8859_1,
                StandardCharsets.US_ASCII,
                StandardCharsets.UTF_8,
                StandardCharsets.UTF_16,
                StandardCharsets.UTF_16BE,
                StandardCharsets.UTF_16LE,
            ].find((cs) => cs.name().toLowerCase().replace(/\W+/g, '')
                === String(encoding).trim().toLowerCase().replace(/\W+/g, ''));
        },
    };

    /**
     * @type {Internal.Base64}
     */
    const base64 = new _.Base64Ctor();

    return base64;
};