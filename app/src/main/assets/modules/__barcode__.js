// noinspection JSUnusedLocalSymbols,UnnecessaryLocalVariableJS

/* Overwritten protection. */

let { images } = global;

/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Barcode}
 */
module.exports = function (scriptRuntime, scope) {

    let _ = {
        BarcodeCtor: (/* @IIFE */ () => {
            const instanceFunc = function __(img, options) {
                if (typeof arguments[0] === 'string') {
                    let image = images.read(/* path = */ arguments[0]);
                    if (image === null) {
                        throw TypeError(`Invalid image of path "${arguments[0]}" for barcode(img, options?)`);
                    }
                    let args = [ image.oneShot() ].concat(Array.from(arguments).slice(1));
                    return BarcodeCtor.prototype.recognizeText.apply(BarcodeCtor.prototype, args);
                }
                return BarcodeCtor.prototype.recognizeText.apply(BarcodeCtor.prototype, arguments);
            };

            /**
             * @implements Internal.Barcode
             */
            const BarcodeCtor = function () {
                let o = Object.assign(instanceFunc, BarcodeCtor.prototype);
                Object.defineProperties(instanceFunc, Object.getOwnPropertyDescriptors(BarcodeCtor.prototype));
                return o;
            };

            BarcodeCtor.prototype = {
                constructor: BarcodeCtor,
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
                        // detectAll(imgPath: string): Barcode.Result[];
                        // detectAll(imgPath: string, options: DetectOptionsWithoutIsAll): Barcode.Result[];

                        let img = images.read(/* path = */ arguments[0]);
                        if (img === null) {
                            throw TypeError(`Invalid image of path "${arguments[0]}" for barcode.detectAll(img, options?)`);
                        }
                        // @Overload
                        // detectAll(img: ImageWrapper): Barcode.Result[];
                        // detectAll(img: ImageWrapper, options: DetectOptionsWithoutIsAll): Barcode.Result[];
                        return this.detectAll(img.oneShot(), arguments[1]);
                    }

                    if (!(arguments[0] instanceof ImageWrapper)) {

                        // @Signature detectAll(options?: DetectOptionsWithoutIsAll): Barcode.Result[];

                        // @Overload detectAll(img: ImageWrapper, options?: DetectOptionsWithoutIsAll): Barcode.Result[];
                        return this.detectAll.apply(this, [ images.captureScreen() ].concat(Array.from(arguments)));
                    }

                    // @Signature detectAll(img: ImageWrapper, options?: DetectOptionsWithoutIsAll): Barcode.Result[];

                    let img = arguments[0];
                    /** @type {Internal.Barcode.DetectOptionsWithoutIsAll} */
                    let opt = /* options */ arguments[1] || {};
                    let formats = (() => {
                        if (isNullish(opt.format)) {
                            return [];
                        }
                        let results = Array.isArray(opt.format) ? opt.format : [ opt.format ];
                        let transformer = {
                            FORMAT_QRCODE: 'FORMAT_QR_CODE',
                        };
                        return results.map((result) => {
                            if (typeof result === 'number') {
                                return result;
                            }
                            if (typeof result === 'string') {
                                let tmp = result.toUpperCase().replace(/\W+/g, '_');
                                let prefix = 'FORMAT_';
                                if (!result.startsWith(prefix)) {
                                    tmp = prefix + tmp;
                                }
                                if (tmp in transformer) {
                                    tmp = transformer[tmp];
                                }
                                return com.google.mlkit.vision.barcode.common.Barcode[tmp];
                            }
                            throw TypeError(`Unknown format (value: ${result}, species: ${species(result)}) for barcode`);
                        });
                    })();
                    let enableAllPotentialBarcodes = Boolean(opt.enableAllPotentialBarcodes);
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

            return BarcodeCtor;
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
             * @type {Internal.Barcode.DetectOptions | boolean | OmniRegion}
             */
            let arg = args[args.length - 1];
            return arg === true || isObject(arg) && Boolean(arg.isAll);
        },
    };

    /**
     * @type {Internal.Barcode}
     */
    const barcode = new _.BarcodeCtor();

    return barcode;
};