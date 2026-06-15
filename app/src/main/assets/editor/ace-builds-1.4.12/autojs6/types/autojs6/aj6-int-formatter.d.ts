// Type definitions for AutoJs6 internal module formatter
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 4.8.4
//
// Last modified: Jun 14, 2026

/// <reference path="./index.d.ts" />

/**
 * @Source /src/main/java/org/autojs/autojs/runtime/api/augment/formatter/Formatter.kt
 * @Source /src/main/java/org/autojs/autojs/runtime/api/augment/formatter/Bytes.kt
 */

declare namespace Internal {

    interface Formatter {
        bytes: Formatter.Bytes;
    }

}

declare namespace Formatter {

    type BytesValue = number | string;

    type BytesUnit = Converter.BytesUnit;

    interface BytesOptions {
        fromUnit?: BytesUnit;
        toUnit?: BytesUnit;
        useIecIdentifier?: boolean;
        useSpace?: boolean;
        fractionDigits?: number;
        trimTrailingZero?: boolean;
        autoCarryThreshold?: number;
        strict?: boolean;
    }

    type BytesLooseOptions = Omit<BytesOptions, 'strict'> & {
        strict?: never;
    };

    type BytesStrictOptions = Omit<BytesOptions, 'strict' | 'useIecIdentifier'> & {
        strict?: never;
        useIecIdentifier?: never;
    };

    interface BytesCallable<Options extends BytesOptions = BytesOptions> {
        (source: BytesValue, options?: Options): string;
        (source: BytesValue, useIecIdentifier: boolean): string;
        (source: BytesValue, fractionDigits: number): string;
        (source: BytesValue, toUnit: BytesUnit, options?: Options): string;
        (source: BytesValue, toUnit: BytesUnit, useIecIdentifier: boolean): string;
        (source: BytesValue, toUnit: BytesUnit, fractionDigits: number): string;
        (source: BytesValue, fromUnit: BytesUnit, toUnit: BytesUnit, options?: Options): string;
        (source: BytesValue, fromUnit: BytesUnit, toUnit: BytesUnit, useIecIdentifier: boolean): string;
        (source: BytesValue, fromUnit: BytesUnit, toUnit: BytesUnit, fractionDigits: number): string;
    }

    interface Bytes extends BytesCallable {
        strict: BytesCallable<BytesStrictOptions>;
        loose: BytesCallable<BytesLooseOptions>;
        UNITS: string;
        AUTO: 'AUTO';
        IEC_DIV: number;
        SI_DIV: number;
    }

}
