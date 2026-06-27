// Type definitions for AutoJs6 internal module qrcode
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 5.1.3
//
// Last modified: Jun 14, 2026

/// <reference path="./index.d.ts" />

/**
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/barcode/QrCode.kt
 */

declare namespace Internal {

    import DetectOptions = Internal.QrCode.DetectOptions;

    interface QrCode {

        (options?: DetectOptions): string | string[] | null;
        (isAll: true): string[];
        (isAll: false): string | null;
        (img: ImageWrapper | string, options?: DetectOptions): string | string[] | null;

        detect(options?: DetectOptions): QrCode.Result | QrCode.Result[] | null;
        detect(img: ImageWrapper | string, options?: DetectOptions): QrCode.Result | QrCode.Result[] | null;

        detectAll(options?: DetectOptions): QrCode.Result[];
        detectAll(img: ImageWrapper | string, options?: DetectOptions): QrCode.Result[];

        recognizeText(options?: DetectOptions): string | string[] | null;
        recognizeText(img: ImageWrapper | string, options?: DetectOptions): string | string[] | null;

        recognizeTexts(options?: DetectOptions): string[];
        recognizeTexts(img: ImageWrapper | string, options?: DetectOptions): string[];

    }

    namespace QrCode {

        interface DetectOptions {
            /** @default false */
            isAll?: boolean;
            /** @default false */
            enableAllPotentialQrCodes?: boolean;
            /** Compatibility alias accepted by the shared barcode parser. */
            enableAllPotentialBarcodes?: boolean;
        }

        class Result extends Internal.Barcode.Result {
            /* Empty body. */
        }

    }

}
