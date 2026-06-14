// Type definitions for AutoJs6 internal module ocr
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 4.8.4
//
// Last modified: Jun 14, 2026

/// <reference path="../index.d.ts" />

/**
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/ocr/Ocr.kt
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/ocr/OcrMLKit.kt
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/ocr/OcrPaddle.kt
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/ocr/OcrRapid.kt
 */

declare namespace Internal {

    import ModeName = Internal.Ocr.ModeName;
    import DetectOptionsMLKit = Internal.Ocr.DetectOptionsMLKit;
    import DetectOptionsPaddle = Internal.Ocr.DetectOptionsPaddle;
    import DetectOptionsRapid = Internal.Ocr.DetectOptionsRapid;
    import DetectOptionsAny = Internal.Ocr.DetectOptionsAny;
    import Mode = Internal.Ocr.Mode;

    interface OcrMLKit extends OcrDetector {

        recognizeText(options?: DetectOptionsMLKit): string[];
        recognizeText(region: OmniRegion): string[];
        recognizeText(img: ImageWrapper | string, options?: DetectOptionsMLKit): string[];
        recognizeText(img: ImageWrapper | string, region: OmniRegion): string[];

        detect(options?: DetectOptionsMLKit): org.autojs.autojs.runtime.api.OcrResult[];
        detect(region: OmniRegion): org.autojs.autojs.runtime.api.OcrResult[];
        detect(img: ImageWrapper | string, options?: DetectOptionsMLKit): org.autojs.autojs.runtime.api.OcrResult[];
        detect(img: ImageWrapper | string, region: OmniRegion): org.autojs.autojs.runtime.api.OcrResult[];

    }

    interface OcrPaddle extends OcrDetector {

        recognizeText(options?: DetectOptionsPaddle): string[];
        recognizeText(region: OmniRegion): string[];
        recognizeText(img: ImageWrapper | string, options?: DetectOptionsPaddle): string[];
        recognizeText(img: ImageWrapper | string, region: OmniRegion): string[];

        detect(options?: DetectOptionsPaddle): org.autojs.autojs.runtime.api.OcrResult[];
        detect(region: OmniRegion): org.autojs.autojs.runtime.api.OcrResult[];
        detect(img: ImageWrapper | string, options?: DetectOptionsPaddle): org.autojs.autojs.runtime.api.OcrResult[];
        detect(img: ImageWrapper | string, region: OmniRegion): org.autojs.autojs.runtime.api.OcrResult[];

    }

    interface OcrRapid extends OcrDetector {

        recognizeText(options?: DetectOptionsRapid): string[];
        recognizeText(region: OmniRegion): string[];
        recognizeText(img: ImageWrapper | string, options?: DetectOptionsRapid): string[];
        recognizeText(img: ImageWrapper | string, region: OmniRegion): string[];

        detect(options?: DetectOptionsRapid): org.autojs.autojs.runtime.api.OcrResult[];
        detect(region: OmniRegion): org.autojs.autojs.runtime.api.OcrResult[];
        detect(img: ImageWrapper | string, options?: DetectOptionsRapid): org.autojs.autojs.runtime.api.OcrResult[];
        detect(img: ImageWrapper | string, region: OmniRegion): org.autojs.autojs.runtime.api.OcrResult[];

    }

    interface Ocr extends OcrDetector {

        paddle: OcrPaddle;
        mlkit: OcrMLKit;
        rapid: OcrRapid;

        (options?: DetectOptionsAny): string[];
        (region: OmniRegion): string[];
        (img: ImageWrapper | string, options?: DetectOptionsAny): string[];
        (img: ImageWrapper | string, region: OmniRegion): string[];

        recognizeText(options?: DetectOptionsAny): string[];
        recognizeText(region: OmniRegion): string[];
        recognizeText(img: ImageWrapper | string, options?: DetectOptionsAny): string[];
        recognizeText(img: ImageWrapper | string, region: OmniRegion): string[];

        detect(options?: DetectOptionsAny): org.autojs.autojs.runtime.api.OcrResult[];
        detect(region: OmniRegion): org.autojs.autojs.runtime.api.OcrResult[];
        detect(img: ImageWrapper | string, options?: DetectOptionsAny): org.autojs.autojs.runtime.api.OcrResult[];
        detect(img: ImageWrapper | string, region: OmniRegion): org.autojs.autojs.runtime.api.OcrResult[];

        get mode(): ModeName;
        set mode(mode: Mode);

        tap(mode: Mode): void;

        summary(): string;

    }

    interface OcrDetector {

        recognizeText(options?: Ocr.DetectOptions): string[];
        recognizeText(region: OmniRegion): string[];
        recognizeText(img: ImageWrapper | string, options?: Ocr.DetectOptions): string[];
        recognizeText(img: ImageWrapper | string, region: OmniRegion): string[];

        detect(options?: Ocr.DetectOptions): org.autojs.autojs.runtime.api.OcrResult[];
        detect(region: OmniRegion): org.autojs.autojs.runtime.api.OcrResult[];
        detect(img: ImageWrapper | string, options?: Ocr.DetectOptions): org.autojs.autojs.runtime.api.OcrResult[];
        detect(img: ImageWrapper | string, region: OmniRegion): org.autojs.autojs.runtime.api.OcrResult[];

    }

    namespace Ocr {

        type Mode = OcrPaddle | OcrMLKit | OcrRapid | 'paddle' | 'mlkit' | 'rapid' | string;
        type ModeName = 'unknown' | 'mlkit' | 'paddle' | 'rapid' | string;
        type DetectOptionsAny = DetectOptionsMLKit | DetectOptionsPaddle | DetectOptionsRapid;

        type RecognizeTextMethod = (img: ImageWrapper) => string[];
        type DetectMethod = (img: ImageWrapper) => org.autojs.autojs.runtime.api.OcrResult[];

        interface DetectOptions {
            region?: OmniRegion;
            mode?: ModeName;
        }

        interface DetectOptionsMLKit extends DetectOptions {
            /* Empty body. */
        }

        interface DetectOptionsRapid extends DetectOptions {
            /* Reserved body. */
        }

        interface DetectOptionsPaddle extends DetectOptions {

            /**
             * 是否使用轻量化模型.
             * @default true
             */
            useSlim?: boolean;

            /**
             * CPU 线程数.
             * @default 4
             */
            cpuThreadNum?: number;

            /**
             * Whether to enable OpenCL in Paddle OCR.
             * @default false
             */
            useOpenCL?: boolean;

            /**
             * Detection long side size. A non-positive value keeps the engine default.
             * @default 0
             */
            detLongSize?: number;

            /**
             * Detection score threshold. A negative value keeps the engine default.
             * @default -1
             */
            scoreThreshold?: number;

            /**
             * Merge adjacent recognition boxes on the same visual line.
             * Ignored when word segmentation is enabled.
             * @default false
             */
            mergeLine?: boolean;

            /** @default false */
            splitWords?: boolean;

            /** @default false */
            useWordSegmentation?: boolean;

            /**
             * Pass the raw bitmap to the official plugin when supported.
             * @default true
             */
            useRaw?: boolean;

            /** Compatibility alias for useRaw. */
            raw?: boolean;

            /**
             * Encoded image quality passed through plugin extras when positive.
             * @default -1
             */
            imageQuality?: number;

            /**
             * Encoded image format passed through plugin extras when non-empty.
             * @default ''
             */
            imageFormat?: Images.Format | string;

        }

    }

}
