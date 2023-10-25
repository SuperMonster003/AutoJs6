// noinspection JSUnusedLocalSymbols,UnnecessaryLocalVariableJS

/* Overwritten protection. */

let { images, qrcode } = global;

/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.QrCode}
 */
module.exports = function (scriptRuntime, scope) {

    let _ = {
        QrCodeCtor: (/* @IIFE */ () => {
            const instanceFunc = function __(img, options) {
                if (typeof arguments[0] === 'string') {
                    let image = images.read(/* path = */ arguments[0]);
                    if (image === null) {
                        throw TypeError(`Invalid image of path "${arguments[0]}" for qrcode(img, options?)`);
                    }
                    let args = [ image.oneShot() ].concat(Array.from(arguments).slice(1));
                    return QrCodeCtor.prototype.recognizeText.apply(QrCodeCtor.prototype, args);
                }
                return QrCodeCtor.prototype.recognizeText.apply(QrCodeCtor.prototype, arguments);
            };

            /**
             * @implements Internal.QrCode
             */
            const QrCodeCtor = function () {
                let o = Object.assign(instanceFunc, QrCodeCtor.prototype);
                Object.defineProperties(instanceFunc, Object.getOwnPropertyDescriptors(QrCodeCtor.prototype));
                return o;
            };

            QrCodeCtor.prototype = {
                constructor: QrCodeCtor,
                recognizeTexts() {
                    let results = this.detectAll.apply(this, arguments);
                    return results.map(result => result.getRawValue()).filter(o => o !== null);
                },
                recognizeText() {
                    let results = this.recognizeTexts.apply(this, arguments);
                    if (_.shouldTakenAsAll(arguments)) {
                        return results;
                    }
                    return results.length > 0 ? results[0] : null;
                },
                /**
                 * @returns {Internal.Barcode.Result[]}
                 */
                detectAll() {
                    if (typeof arguments[0] === 'string') {

                        // @Signature
                        // detectAll(imgPath: string): QrCode.Result[];
                        // detectAll(imgPath: string, options: DetectOptionsWithoutIsAll): QrCode.Result[];

                        let img = images.read(/* path = */ arguments[0]);
                        if (img === null) {
                            throw TypeError(`Invalid image of path "${arguments[0]}" for qrcode.detectAll(img, options?)`);
                        }
                        // @Overload
                        // detectAll(img: ImageWrapper): QrCode.Result[];
                        // detectAll(img: ImageWrapper, options: DetectOptionsWithoutIsAll): QrCode.Result[];
                        return this.detectAll(img.oneShot(), arguments[1]);
                    }

                    if (!(arguments[0] instanceof ImageWrapper)) {

                        // @Signature detectAll(options?: DetectOptionsWithoutIsAll): QrCode.Result[];

                        // @Overload detectAll(img: ImageWrapper, options?: DetectOptionsWithoutIsAll): QrCode.Result[];
                        return this.detectAll.apply(this, [ images.captureScreen() ].concat(Array.from(arguments)));
                    }

                    // @Signature detectAll(img: ImageWrapper, options?: DetectOptionsWithoutIsAll): QrCode.Result[];

                    let img = arguments[0];
                    /** @type {Internal.QrCode.DetectOptionsWithoutIsAll} */
                    let opt = /* options */ arguments[1] || {};
                    let enableAllPotentialBarcodes = Boolean(opt.enableAllPotentialBarcodes);
                    let formats = [ com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE ];
                    // noinspection JSValidateTypes
                    return scriptRuntime.barcode.detect(img, formats, enableAllPotentialBarcodes);
                },
                detect() {
                    let results = this.detectAll.apply(this, arguments);
                    if (_.shouldTakenAsAll(arguments)) {
                        return results;
                    }
                    return results.length > 0 ? results[0] : null;
                },
            };

            return QrCodeCtor;
        })(),
        /**
         * @param {IArguments} args
         * @returns {boolean}
         */
        shouldTakenAsAll(args) {
            if (args.length === 0) {
                return false;
            }
            /**
             * @type {Internal.QrCode.DetectOptions | boolean | OmniRegion}
             */
            let arg = args[args.length - 1];
            return arg === true || isObject(arg) && Boolean(arg.isAll);
        },
    };

    /**
     * @type {Internal.QrCode}
     */
    const qrcode = new _.QrCodeCtor();

    return qrcode;
};