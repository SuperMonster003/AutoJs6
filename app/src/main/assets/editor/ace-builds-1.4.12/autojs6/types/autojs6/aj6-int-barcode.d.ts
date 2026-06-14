// Type definitions for AutoJs6 internal module barcode
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 5.1.3
//
// Last modified: Jun 14, 2026

/// <reference path="../index.d.ts" />

/**
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/barcode/Barcode.kt
 */

declare namespace Internal {

    import DetectOptions = Internal.Barcode.DetectOptions;
    import MLKitBarcode = com.google.mlkit.vision.barcode.common.Barcode;

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
             * com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE;
             * "qr-code";
             * "QR_CODE";
             * "qrcode"; // specially adaptation
             * "QRCODE"; // specially adaptation
             */
            format?: number | number[] | Format | Format[];
        }

        class Result extends org.autojs.autojs.runtime.api.WrappedBarcode {

            boundingBox: Android.Rect | null;
            calendarEvent: MLKitBarcode.CalendarEvent | null;
            contactInfo: MLKitBarcode.ContactInfo | null;
            cornerPoints: android.graphics.Point[] | null;
            displayValue: string | null;
            driverLicense: MLKitBarcode.DriverLicense | null;
            email: MLKitBarcode.Email | null;
            format: number;
            formatName: string;
            geoPoint: MLKitBarcode.GeoPoint | null;
            phone: MLKitBarcode.Phone | null;
            rawBytes: number[] | null;
            rawValue: string | null;
            sms: MLKitBarcode.Sms | null;
            type: number;
            typeName: string;
            url: MLKitBarcode.UrlBookmark | null;
            valueType: number;
            valueTypeName: string;
            wifi: MLKitBarcode.WiFi | null;

        }

    }

}
