// noinspection JSUnusedLocalSymbols

/* Overwritten protection. */

let { images } = global;

/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Ocr}
 */
module.exports = function (scriptRuntime, scope) {

    let _ = {
        OcrCtor: (/* @IIFE */ () => {

            /**
             * @type {{[modeName in Internal.Ocr.ModeName]: Internal.Ocr.ModeName}}
             */
            const modeName = {
                mlkit: 'mlkit',
                paddle: 'paddle',
                unknown: 'unknown',
            };

            /**
             * @type {Internal.Ocr.ModeName}
             */
            let _mode = modeName.unknown;

            const defaultPrivateDescriptor = {
                configurable: false,
                writable: false,
                enumerable: false,
            };

            const defaultProtectedDescriptor = {
                configurable: false,
                writable: false,
                enumerable: true, /* Emphasis. */
            };

            const instanceFunc = function (img, options) {
                if (typeof arguments[0] === 'string') {
                    let img = images.read(/* path = */ arguments[0]);
                    if (img === null) {
                        throw TypeError(`Invalid image of path "${arguments[0]}" for ocr(img, options?)`);
                    }
                    return OcrCtor.prototype.recognizeText(img.oneShot(), options);
                }
                return OcrCtor.prototype.recognizeText.apply(OcrCtor.prototype, arguments);
            };

            /**
             * @implements Internal.Ocr
             */
            const OcrCtor = function () {
                let o = Object.assign(instanceFunc, OcrCtor.prototype);
                Object.defineProperties(instanceFunc, Object.getOwnPropertyDescriptors(OcrCtor.prototype));
                return o;
            };

            OcrCtor.prototype = {
                constructor: OcrCtor,
                _modeName: modeName,
                _forcibleModeArgIndex: 10,
                mlkit: require('ocr-mlkit')(scriptRuntime, scope),
                paddle: require('ocr-paddle')(scriptRuntime, scope),
                get mode() {
                    return _mode;
                },
                set mode(mode) {
                    this.tap(mode);
                },
                tap(mode) {
                    if (mode === this.mlkit || String(mode).toLowerCase() === modeName.mlkit) {
                        _mode = modeName.mlkit;
                    } else if (mode === this.paddle || String(mode).toLowerCase() === modeName.paddle) {
                        _mode = modeName.paddle;
                    } else {
                        _mode = modeName.unknown;
                        throw TypeError(`Unknown mode (value: ${mode}, species: ${species(mode)}) for ocr.tap(mode)`);
                    }
                },
                recognizeText() {
                    if (typeof arguments[0] === 'string') {

                        // @Signature
                        // recognizeText(imgPath: string, options?: DetectOptionsMLKit | DetectOptionsPaddle): string[];
                        // recognizeText(imgPath: string, region: Images.Options.Region): string[];

                        let img = images.read(/* path = */ arguments[0]);
                        if (img === null) {
                            throw TypeError(`Invalid image of path "${arguments[0]}" for ocr.recognizeText(img, options?)`);
                        }
                        // @Overload
                        // recognizeText(img: ImageWrapper, options?: DetectOptionsMLKit | DetectOptionsPaddle): string[];
                        // recognizeText(img: ImageWrapper, region: Images.Options.Region): string[];
                        return this.recognizeText(img.oneShot(), arguments[1]);
                    }
                    if (_.shouldTakenAsRegion(arguments[1])) {

                        // @Signature recognizeText(img: ImageWrapper, region: Images.Options.Region): string[];

                        // @Overload recognizeText(img: ImageWrapper, options: DetectOptionsMLKit | DetectOptionsPaddle): string[];
                        return this.recognizeText(img, { region: arguments[1] });
                    }

                    // @Signature recognizeText(img: ImageWrapper, options?: DetectOptionsMLKit | DetectOptionsPaddle): string[];

                    /** @type {Internal.Ocr.DetectOptions} */
                    let opt = /* options */ arguments[1] || {};
                    let region = opt.region;
                    if (region === null) {
                        return [];
                    }

                    /** @type Internal.Ocr.RecognizeTextMethod */
                    let recognizeTextMethod;

                    switch (arguments[OcrCtor.prototype._forcibleModeArgIndex] || _mode) {
                        case modeName.mlkit:
                            recognizeTextMethod = this.mlkit.recognizeTextMethodCreator(opt);
                            break;
                        case modeName.paddle:
                            recognizeTextMethod = this.paddle.recognizeTextMethodCreator(opt);
                            break;
                        default:
                            throw TypeError(`Can't call ocr.recognizeText with an unknown mode (value: ${_mode}, species: ${species(_mode)})`);
                    }

                    let img = arguments[0];
                    if (region === undefined) {
                        return recognizeTextMethod(img);
                    }
                    let results = recognizeTextMethod(
                        images.clip(img, region).oneShot(),
                    );
                    img.shoot();
                    return results;
                },
                detect() {
                    if (typeof arguments[0] === 'string') {

                        // @Signature
                        // detect(imgPath: string, options?: DetectOptionsMLKit | DetectOptionsPaddle): org.autojs.autojs.runtime.api.OcrResult[];
                        // detect(imgPath: string, region: Images.Options.Region): org.autojs.autojs.runtime.api.OcrResult[];

                        let img = images.read(/* path = */ arguments[0]);
                        if (img === null) {
                            throw TypeError(`Invalid image of path "${arguments[0]}" for ocr.detect(img, options?)`);
                        }
                        // @Overload
                        // detect(img: ImageWrapper, options?: DetectOptionsMLKit | DetectOptionsPaddle): org.autojs.autojs.runtime.api.OcrResult[];
                        // detect(img: ImageWrapper, region: Images.Options.Region): org.autojs.autojs.runtime.api.OcrResult[];
                        return this.detect(img.oneShot(), arguments[1]);
                    }
                    if (_.shouldTakenAsRegion(arguments[1])) {

                        // @Signature detect(img: ImageWrapper, region: Images.Options.Region): org.autojs.autojs.runtime.api.OcrResult[];

                        // @Overload detect(img: ImageWrapper, options: DetectOptionsMLKit | DetectOptionsPaddle): org.autojs.autojs.runtime.api.OcrResult[];
                        return this.detect(img, { region: arguments[1] });
                    }

                    // @Signature detect(img: ImageWrapper, options?: DetectOptionsMLKit | DetectOptionsPaddle): org.autojs.autojs.runtime.api.OcrResult[];

                    let opt = /* options */ arguments[1] || {};
                    let region = opt.region;
                    if (region === null) {
                        return [];
                    }

                    /** type {Internal.Ocr.DetectMethod} */
                    let detectMethod;

                    switch (_mode) {
                        case modeName.mlkit:
                            detectMethod = this.mlkit.detectMethodCreator(opt);
                            break;
                        case modeName.paddle:
                            detectMethod = this.paddle.detectMethodCreator(opt);
                            break;
                        default:
                            throw TypeError(`Can't call ocr.detect with an unknown mode (value: ${_mode}, species: ${species(_mode)})`);
                    }

                    let img = arguments[0];
                    if (region === undefined) {
                        return detectMethod(img);
                    }
                    let results = detectMethod(images.clip(img, region).oneShot());
                    results.forEach((result) => {
                        let rect = images.buildRegion(img, region);
                        result.bounds.offset(rect.x, rect.y);
                    });
                    img.shoot();
                    return results;
                },
                summary() {
                    return [
                        `[ OCR summary ]`,
                        `Current mode: ${this.mode}`,
                        `Available modes: [ ${Object.values(modeName).filter(n => n !== modeName.unknown).join(', ')} ]`,
                    ].join('\n');
                },
                toString() {
                    return this.summary();
                },
            };

            /* Set prototype descriptors. */

            Object.defineProperties(OcrCtor.prototype, {
                _modeName: defaultPrivateDescriptor,
                _forcibleModeArgIndex: defaultPrivateDescriptor,
                mode: { configurable: false },
                constructor: defaultProtectedDescriptor,
                mlkit: defaultProtectedDescriptor,
                paddle: defaultProtectedDescriptor,
                tap: defaultProtectedDescriptor,
                summary: defaultProtectedDescriptor,
                toString: defaultProtectedDescriptor,
            });

            return OcrCtor;
        })(),
        shouldTakenAsRegion(o) {
            return o instanceof org.opencv.core.Rect
                || o instanceof android.graphics.Rect
                || Array.isArray(o);
        },
    };

    /**
     * @type {Internal.Ocr}
     */
    const ocr = new _.OcrCtor();

    /* Init OCR mode with MLKit. */
    ocr.mode = 'mlkit';

    return ocr;
};