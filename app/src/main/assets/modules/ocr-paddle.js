// noinspection UnnecessaryLocalVariableJS,JSUnusedLocalSymbols

/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.OcrPaddle}
 */
module.exports = function (scriptRuntime, scope) {

    const rtOcrPaddle = scriptRuntime.ocrPaddle;

    let _ = {
        OcrPaddleCtor: (/* @IIFE */ () => {
            /**
             * @implements Internal.OcrPaddle
             */
            const OcrPaddleCtor = function () {
                const instanceFunc = function __() {
                    let argArray = Array.from(arguments);
                    if (arguments.length === 0 || !(arguments[0] instanceof ImageWrapper)) {
                        return __.apply(this, [ images.captureScreen() ].concat(argArray));
                    }
                    /** @type {Internal.Ocr.ModeName} */
                    argArray[ocr['_forcibleModeArgIndex']] = ocr['_modeName']['paddle'];
                    return ocr.recognizeText.apply(ocr, argArray);
                };
                return Object.assign(instanceFunc, OcrPaddleCtor.prototype);
            };

            OcrPaddleCtor.prototype = {
                constructor: OcrPaddleCtor,
                recognizeTextMethodCreator(options) {
                    return function (img) {
                        const { cpuThreadNum, useSlim } = options;

                        let niceUseSlim = useSlim === undefined ? true : Boolean(useSlim);
                        let niceCpuThreadNum = cpuThreadNum || 4;

                        return Array.from(rtOcrPaddle.recognizeText(
                            img,
                            niceCpuThreadNum,
                            niceUseSlim,
                        ));
                    };
                },
                detectMethodCreator(options) {
                    return function (img) {
                        const { cpuThreadNum, useSlim } = options;

                        let niceUseSlim = useSlim === undefined ? true : Boolean(useSlim);
                        let niceCpuThreadNum = cpuThreadNum || 4;

                        return Array.from(rtOcrPaddle.detect(
                            img,
                            niceCpuThreadNum,
                            niceUseSlim,
                        ).toArray());
                    };
                },
            };

            return OcrPaddleCtor;
        })(),
    };

    /**
     * @type {Internal.OcrPaddle}
     */
    const ocrPaddle = new _.OcrPaddleCtor();

    return ocrPaddle;
};