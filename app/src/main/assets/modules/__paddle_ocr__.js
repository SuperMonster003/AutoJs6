// noinspection UnnecessaryLocalVariableJS,JSUnusedLocalSymbols

/* Overwritten protection. */

let { images } = global;

/**
 * @param {org.autojs.autojs.runtime.ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Ocr}
 */
module.exports = function (scriptRuntime, scope) {

    const rtOcr = scriptRuntime.paddleOCR;

    let _ = {
        OcrCtor: (/* @IIFE */ () => {
            /**
             * @implements Internal.Ocr
             */
            const OcrCtor = function () {

                /** @global */
                const ocr = function (img, options) {
                    if (typeof arguments[0] === 'string') {
                        let img = images.read(/* path = */ arguments[0]);
                        if (img === null) {
                            throw TypeError(`Invalid image of path "${arguments[0]}" for ocr(img, options?)`);
                        }
                        return OcrCtor.prototype.recognizeText(img.oneShot(), options);
                    }
                    return OcrCtor.prototype.recognizeText.apply(OcrCtor.prototype, arguments);
                };

                return Object.assign(ocr, OcrCtor.prototype);
            };

            OcrCtor.prototype = {
                constructor: OcrCtor,
                recognizeText(img, options) {
                    if (typeof arguments[0] === 'string') {
                        let img = images.read(/* path = */ arguments[0]);
                        if (img === null) {
                            throw TypeError(`Invalid image of path "${arguments[0]}" for ocr.recognizeText(img, options?)`);
                        }
                        return this.recognizeText(img.oneShot(), options);
                    }
                    if (_.shouldTakenAsRegion(arguments[1])) {
                        return this.recognizeText(img, { region: arguments[1] });
                    }
                    let opt = options || {};
                    let region = opt.region;
                    if (region === null) {
                        return [];
                    }
                    let cpuThreadNum = opt.cpuThreadNum || 4
                    let useSlim = opt.useSlim
                    if (useSlim === undefined) {
                       // 默认使用轻量化模型
                       useSlim = true
                    }
                    if (region === undefined) {
                        return Array.from(rtOcr.recognizeText(img, cpuThreadNum, useSlim));
                    }
                    let results = Array.from(rtOcr.recognizeText(images.clip(img, region).oneShot()));
                    img.shoot();
                    return results;
                },
                detect(img, options) {
                    if (typeof arguments[0] === 'string') {
                        let img = images.read(/* path = */ arguments[0]);
                        if (img === null) {
                            throw TypeError(`Invalid image of path "${arguments[0]}" for ocr.detect(img, options?)`);
                        }
                        return this.detect(img.oneShot(), options);
                    }
                    if (_.shouldTakenAsRegion(arguments[1])) {
                        return this.detect(img, { region: arguments[1] });
                    }
                    let opt = options || {};
                    let region = opt.region;
                    if (region === null) {
                        return [];
                    }
                    let cpuThreadNum = opt.cpuThreadNum || 4
                    let useSlim = opt.useSlim
                    if (useSlim === undefined) {
                       // 默认使用轻量化模型
                       useSlim = true
                    }
                    /**
                     * @type {org.autojs.autojs.runtime.api.OcrResult[]}
                     */
                    let resultList = rtOcr.detect(region !== undefined ? images.clip(img, region).oneShot() : img, cpuThreadNum, useSlim).toArray();
                    if (!isNullish(region)) {
                        resultList.forEach((result) => {
                            let rect = images.buildRegion(img, region);
                            result.bounds.offset(rect.x, rect.y);
                        });
                    }
                    return Array.from(resultList);
                },
            };

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

    return ocr;
};