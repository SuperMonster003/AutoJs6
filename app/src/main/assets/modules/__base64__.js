/**
 * @param {org.autojs.autojs.runtime.ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Base64}
 */
module.exports = function (scriptRuntime, scope) {
    const Base64 = android.util.Base64;
    const JavaString = java.lang.String;
    const StandardCharsets = java.nio.charset.StandardCharsets;

    let _ = {
        Base64Ctor: ( /* @IIFE */ () => {
            /**
             * @implements Internal.Base64
             */
            const Base64Ctor = function () {
                // Empty interface body.
            };

            Base64Ctor.prototype = {
                constructor: Base64Ctor,
                /**
                 * @param str
                 * @param {string} encoding
                 * @return {string}
                 */
                encode(str, encoding) {
                    // noinspection JSValidateTypes
                    /**
                     * @type {java.lang.String}
                     */
                    let string = new JavaString(str);
                    return _.isValidEncoding(encoding)
                        ? Base64.encodeToString(string.getBytes(encoding), Base64.NO_WRAP)
                        : Base64.encodeToString(string.getBytes(), Base64.NO_WRAP);
                },
                decode(str, encoding) {
                    // noinspection JSValidateTypes
                    return _.isValidEncoding(encoding)
                        ? String(new JavaString(Base64.decode(str, Base64.NO_WRAP), encoding))
                        : String(new JavaString(Base64.decode(str, Base64.NO_WRAP)));
                },
            };

            return Base64Ctor;
        })(),
        isValidEncoding: (encode) => [
            StandardCharsets.US_ASCII,
            StandardCharsets.ISO_8859_1,
            StandardCharsets.UTF_8,
            StandardCharsets.UTF_16BE,
            StandardCharsets.UTF_16LE,
            StandardCharsets.UTF_16,
        ].map((javaCharset) => {
            return javaCharset.name().toLowerCase();
        }).includes(String(encode).toLowerCase()),
    };

    /**
     * @type {Internal.Base64}
     */
    const base64 = new _.Base64Ctor();

    return base64;
};