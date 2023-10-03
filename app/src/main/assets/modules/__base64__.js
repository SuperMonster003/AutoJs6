/**
 * @param {ScriptRuntime} scriptRuntime
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
                /* Empty body. */
            };

            Base64Ctor.prototype = {
                constructor: Base64Ctor,
                encode(o, encoding) {
                    let niceEncoding = _.parseEncoding(encoding);
                    return Base64.encodeToString(_.toBytes(o, niceEncoding), Base64.NO_WRAP);
                },
                decode(o, encoding) {
                    let niceEncoding = _.parseEncoding(encoding);
                    let decoded = Base64.decode(_.toBytes(o, niceEncoding), Base64.NO_WRAP);
                    // noinspection JSValidateTypes
                    return typeof niceEncoding !== 'undefined'
                        ? String(new java.lang.String(decoded, niceEncoding))
                        : String(new java.lang.String(decoded));
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
        toBytes(o, encoding) {
            if (typeof o === 'string') {
                // noinspection JSValidateTypes
                /** @type { java.lang.String } */
                let string = new java.lang.String(o);
                return typeof encoding !== 'undefined'
                    ? string.getBytes(encoding)
                    : string.getBytes();
            }
            if (util.getClassName(o) === '[B') {
                return o;
            }
            if (Array.isArray(o)) {
                return ArrayUtils.jsBytesToByteArray(o);
            }
            if (!isNullish(o)) {
                if (typeof o.toString === 'function') {
                    return this.toBytes(o.toString());
                }
            }
            throw Error(`Can't convert o (${o}) to bytes`);
        },
    };

    // noinspection UnnecessaryLocalVariableJS
    /**
     * @type {Internal.Base64}
     */
    const base64 = new _.Base64Ctor();

    return base64;
};