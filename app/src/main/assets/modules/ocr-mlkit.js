// noinspection UnnecessaryLocalVariableJS,JSUnusedLocalSymbols

/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.OcrMLKit}
 */
module.exports = function (scriptRuntime, scope) {

    const rtOcrMLKit = scriptRuntime.ocrMLKit;

    let _ = {
        OcrMLKitCtor: (/* @IIFE */ () => {
            /**
             * @implements Internal.OcrMLKit
             */
            const OcrMLKitCtor = function () {
                return Object.assign(function () {
                    let argArray = Array.from(arguments);
                    /** @type {Internal.Ocr.ModeName} */
                    argArray[ocr['_forcibleModeArgIndex']] = ocr['_modeName']['mlkit'];
                    return ocr.recognizeText.apply(ocr, argArray);
                }, OcrMLKitCtor.prototype);
            };

            OcrMLKitCtor.prototype = {
                constructor: OcrMLKitCtor,
                recognizeTextMethodCreator(options) {
                    return function (img) {
                        return Array.from(rtOcrMLKit.recognizeText(img));
                    };
                },
                detectMethodCreator(options) {
                    return function (img) {
                        return Array.from(rtOcrMLKit.detect(img).toArray());
                    };
                },
            };

            return OcrMLKitCtor;
        })(),
    };

    /**
     * @type {Internal.OcrMLKit}
     */
    const ocrMLKit = new _.OcrMLKitCtor();

    return ocrMLKit;
};