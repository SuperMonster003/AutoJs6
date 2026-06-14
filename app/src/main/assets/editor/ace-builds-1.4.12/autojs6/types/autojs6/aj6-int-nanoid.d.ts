// Type definitions for AutoJs6 internal module nanoid
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 4.8.4
//
// Last modified: Jun 14, 2026

/// <reference path="../index.d.ts" />

/**
 * @Source /src/main/java/org/autojs/autojs/runtime/api/augment/nanoid/NanoID.kt
 */

declare namespace Internal {

    interface NanoID {
        (size?: number): string;
    }

}
