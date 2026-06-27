// Type definitions for AutoJs6 internal module base64
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 5.7.3
//
// Last modified: Jun 14, 2026

/// <reference path="./index.d.ts" />

/**
 * @Source /src/main/java/org/autojs/autojs/runtime/api/augment/base64/Base64.kt
 */

declare namespace Internal {

    interface Base64 {

        encode(o: Base64.Input, encoding?: Base64.Encoding): string;

        decode(o: Base64.Input, encoding?: Base64.Encoding): string;

    }

    declare namespace Base64 {

        type Input = string | number[] | {
            toString(): string;
        };

        type Encoding = StandardCharset | string | java.nio.charset.Charset;

    }

}
