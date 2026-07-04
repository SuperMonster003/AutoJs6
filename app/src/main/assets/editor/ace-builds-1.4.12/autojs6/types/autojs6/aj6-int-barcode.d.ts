// Type definitions for AutoJs6 internal module barcode
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 5.1.3
//
// Last modified: Jun 14, 2026

/// <reference path="./index.d.ts" />

/**
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/barcode/Barcode.kt
 */

declare namespace Internal {

    import DetectOptions = Internal.Barcode.DetectOptions;

    interface Barcode {

        (options?: DetectOptions): string | string[] | null;
        (isAll: true): string[];
        (isAll: false): string | null;
        (img: ImageWrapper | string, options?: DetectOptions): string | string[] | null;

        detect(options?: DetectOptions): Barcode.Result | Barcode.Result[] | null;
        detect(img: ImageWrapper | string, options?: DetectOptions): Barcode.Result | Barcode.Result[] | null;

        detectAll(options?: DetectOptions): Barcode.Result[];
        detectAll(img: ImageWrapper | string, options?: DetectOptions): Barcode.Result[];

        recognizeText(options?: DetectOptions): string | string[] | null;
        recognizeText(img: ImageWrapper | string, options?: DetectOptions): string | string[] | null;

        recognizeTexts(options?: DetectOptions): string[];
        recognizeTexts(img: ImageWrapper | string, options?: DetectOptions): string[];

    }

    namespace Barcode {

        type FormatUppercaseShort =
            'CODE_128' | 'CODE_39' | 'CODE_93' | 'CODABAR' | 'EAN_13' |
            'EAN_8' | 'ITF' | 'UPC_A' | 'UPC_E' | 'QR_CODE' |
            'PDF417' | 'AZTEC' | 'DATA_MATRIX' | 'QRCODE'

        type FormatLowercaseShort = 'code-128' | 'code-39' | 'code-93' | 'codabar' | 'ean-13' |
            'ean-8' | 'itf' | 'upc-a' | 'upc-e' | 'qr-code' |
            'pdf417' | 'aztec' | 'data-matrix' | 'qrcode';

        type FormatUppercaseLong = `FORMAT_${FormatUppercaseShort}`;

        type FormatLowercaseLong = `FORMAT_${FormatLowercaseShort}`;

        type Format = FormatUppercaseLong | FormatUppercaseShort | FormatLowercaseLong | FormatLowercaseShort;

        interface DetectOptions {
            /** @default false */
            isAll?: boolean;
            /** @default false */
            enableAllPotentialBarcodes?: boolean;
            /**
             * @default 0
             * @example
             * org.autojs.plugin.mlkit.barcode.api.BarcodeFormats.FORMAT_QR_CODE;
             * "qr-code";
             * "QR_CODE";
             * "qrcode"; // specially adaptation
             * "QRCODE"; // specially adaptation
             */
            format?: number | number[] | Format | Format[];
        }

        class Result extends org.autojs.autojs.runtime.api.WrappedBarcode {

            boundingBox: Android.Rect | null;
            calendarEvent: org.json.JSONObject | null;
            contactInfo: org.json.JSONObject | null;
            cornerPoints: android.graphics.Point[] | null;
            displayValue: string | null;
            driverLicense: org.json.JSONObject | null;
            email: org.json.JSONObject | null;
            format: number;
            formatName: string;
            geoPoint: org.json.JSONObject | null;
            phone: org.json.JSONObject | null;
            rawBytes: number[] | null;
            rawValue: string | null;
            sms: org.json.JSONObject | null;
            type: number;
            typeName: string;
            url: org.json.JSONObject | null;
            valueType: number;
            valueTypeName: string;
            wifi: org.json.JSONObject | null;

        }

    }

}
