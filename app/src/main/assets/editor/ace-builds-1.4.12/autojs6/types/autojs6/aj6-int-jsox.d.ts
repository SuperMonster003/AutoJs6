// Type definitions for AutoJs6 internal module jsox
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 4.8.4
//
// Last modified: Jun 14, 2026

/// <reference path="../index.d.ts" />

/**
 * @Source /src/main/java/org/autojs/autojs/runtime/api/augment/jsox/Jsox.kt
 */

declare namespace Internal {

    interface Jsox {
        (...modules: Jsox.ModuleArg[]): void;

        extend(...modules: Jsox.ModuleArg[]): void;

        extendAll(): void;
    }

}

declare namespace Jsox {

    type ModuleName = 'Arrayx' | 'Array' | 'Mathx' | 'Math' | 'Numberx' | 'Number' | string;
    type ModuleArg = ModuleName | ModuleArg[];

}
