module.exports = function (runtime, global) {
    const Base64 = android.util.Base64;

    const $base64 = () => void 0;

    let _ = {
        charsetNames: [
            // charset names updated up to android sdk 27
            'us-ascii', 'iso-8859-1', 'utf-8', 'utf-16be', 'utf-16le', 'utf-16',
        ],
        isValidEncoding(encode) {
            return this.charsetNames.includes(encode);
        },
    };

    // noinspection JSValidateTypes
    $base64.encode = (str, encoding) => _.isValidEncoding(encoding)
        ? Base64.encodeToString(new java.lang.String(str).getBytes(encoding), Base64.NO_WRAP)
        : Base64.encodeToString(new java.lang.String(str).getBytes(), Base64.NO_WRAP);

    // noinspection JSValidateTypes
    $base64.decode = (str, encoding) => _.isValidEncoding(encoding)
        ? String(new java.lang.String(Base64.decode(str, Base64.NO_WRAP), encoding))
        : String(new java.lang.String(Base64.decode(str, Base64.NO_WRAP)));

    return $base64;
};