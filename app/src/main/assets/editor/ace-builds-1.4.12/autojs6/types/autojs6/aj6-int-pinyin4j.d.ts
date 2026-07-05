// Type definitions for AutoJs6 internal module pinyin4j
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 4.8.4
//
// Last modified: Jun 14, 2026

/// <reference path="./index.d.ts" />

/**
 * @Source /src/main/java/org/autojs/autojs/runtime/api/augment/pinyin4j/Pinyin4j.kt
 */

declare namespace Internal {

    interface Pinyin4j {
        (source: string, options?: string | Pinyin4j.Options): string;

        of(source: string, options?: string | Pinyin4j.Options): string;

        "as"(source: string): string;
    }

}

declare namespace Pinyin4j {

    type CaseType = 'LOWERCASE' | 'LOW' | 'L' | '0' | 'UPPERCASE' | 'UP' | 'U' | '1'
        | string;

    type ToneType = 'WITH_TONE_NUMBER' | 'WITH_NUMBER' | 'NUMBER' | 'NUM'
        | 'WITHOUT_TONE' | 'NO_TONE' | 'NO' | 'FALSE' | '0'
        | 'WITH_TONE_MARK' | 'WITH_MARK' | 'MARK' | 'TRUE' | '1'
        | string;

    type VCharType = 'WITH_U_AND_COLON' | 'U_AND_COLON' | 'U_COLON' | 'U:'
        | 'WITH_V' | 'V'
        | 'WITH_U_UNICODE' | 'U_UNICODE' | 'UNICODE'
        | string;

    interface Options {
        separator?: string;
        sep?: string;
        case?: CaseType;
        caseType?: CaseType;
        tone?: ToneType;
        toneType?: ToneType;
        v?: VCharType;
        vChar?: VCharType;
        vCharType?: VCharType;
    }

}
