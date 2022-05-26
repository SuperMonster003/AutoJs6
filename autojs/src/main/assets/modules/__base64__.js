/* Here, importClass() is not recommended for intelligent code completion in IDE like WebStorm. */
/* The same is true of destructuring assignment syntax (like `let {Uri} = android.net`). */

const Base64 = android.util.Base64;
const javaString = java.lang.String;
const StandardCharsets = java.nio.charset.StandardCharsets;

let _ = {
    charsetNames: [
        StandardCharsets.US_ASCII,
        StandardCharsets.ISO_8859_1,
        StandardCharsets.UTF_8,
        StandardCharsets.UTF_16BE,
        StandardCharsets.UTF_16LE,
        StandardCharsets.UTF_16,
    ].map(javaCharset => javaCharset.name().toLowerCase()),
    init(__runtime__, scope) {
        this.runtime = __runtime__;
        this.scope = scope;
        this.base64 = () => {
            // Empty module body
        };
    },
    selfAugment() {
        Object.assign(this.base64, {
            encode(str, encoding) {
                // noinspection JSValidateTypes
                return _.isValidEncoding(encoding)
                    ? Base64.encodeToString(new javaString(str).getBytes(encoding), Base64.NO_WRAP)
                    : Base64.encodeToString(new javaString(str).getBytes(), Base64.NO_WRAP);
            },
            decode(str, encoding) {
                // noinspection JSValidateTypes
                return _.isValidEncoding(encoding)
                    ? String(new javaString(Base64.decode(str, Base64.NO_WRAP), encoding))
                    : String(new javaString(Base64.decode(str, Base64.NO_WRAP)));
            },
        });
    },
    isValidEncoding(encode) {
        return this.charsetNames.includes(String(encode).toLowerCase());
    },
    getModule() {
        return this.base64;
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
 * @return {Internal.Base64}
 */
module.exports = function (__runtime__, scope) {
    return $.getModule(__runtime__, scope);
};