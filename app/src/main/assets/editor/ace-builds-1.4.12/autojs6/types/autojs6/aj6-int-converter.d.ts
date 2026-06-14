// Type definitions for AutoJs6 internal module converter
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 4.8.4
//
// Last modified: Jun 14, 2026

/// <reference path="../index.d.ts" />

/**
 * @Source /src/main/java/org/autojs/autojs/runtime/api/augment/converter/Converter.kt
 * @Source /src/main/java/org/autojs/autojs/runtime/api/augment/converter/Bytes.kt
 */

declare namespace Internal {

    interface Converter {
        bytes: Converter.Bytes;
    }

}

declare namespace Converter {

    type BytesValue = number | string;

    type BytesResult = number | bigint;

    type BytesUnit = 'B'
        | 'KB' | 'MB' | 'GB' | 'TB' | 'PB' | 'EB' | 'ZB' | 'YB' | 'RB' | 'QB'
        | 'KiB' | 'MiB' | 'GiB' | 'TiB' | 'PiB' | 'EiB' | 'ZiB' | 'YiB' | 'RiB' | 'QiB'
        | 'AUTO'
        | string;

    interface BytesOptions {
        fromUnit?: BytesUnit;
        toUnit?: BytesUnit;
        fractionDigits?: number;
        autoCarryThreshold?: number;
        strict?: boolean;
    }

    type BytesModeOptions = Omit<BytesOptions, 'strict'> & {
        strict?: never;
    };

    interface BytesCallable<Options extends BytesOptions = BytesOptions> {
        (source: BytesValue, options?: Options): BytesResult;
        (source: BytesValue, useIecIdentifier: boolean): BytesResult;
        (source: BytesValue, fractionDigits: number): BytesResult;
        (source: BytesValue, toUnit: BytesUnit, options?: Options): BytesResult;
        (source: BytesValue, toUnit: BytesUnit, useIecIdentifier: boolean): BytesResult;
        (source: BytesValue, toUnit: BytesUnit, fractionDigits: number): BytesResult;
        (source: BytesValue, fromUnit: BytesUnit, toUnit: BytesUnit, options?: Options): BytesResult;
        (source: BytesValue, fromUnit: BytesUnit, toUnit: BytesUnit, useIecIdentifier: boolean): BytesResult;
        (source: BytesValue, fromUnit: BytesUnit, toUnit: BytesUnit, fractionDigits: number): BytesResult;
    }

    interface Bytes extends BytesCallable {
        strict: BytesCallable<BytesModeOptions>;
        loose: BytesCallable<BytesModeOptions>;
        UNITS: string;
        AUTO: 'AUTO';
        IEC_DIV: number;
        SI_DIV: number;
    }

}
