// Type definitions for AutoJs6 internal module toast
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 5.1.3
//
// Last modified: Jun 14, 2026

/// <reference path="../index.d.ts" />

/**
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/toast/Toast.kt
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/toast/ToastParser.kt
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/ScriptToast.kt
 */

declare namespace Internal {

    interface Toast {

        (text?: any, isLong?: Toast.IsLong, isForcible?: Toast.IsForcible): void;

        dismissAll(): void;

    }

}

declare namespace Toast {

    type IsLong = 'long' | 'l' | 'short' | 's' | 'forcible' | 'f' | boolean | number;

    type IsForcible = 'forcible' | 'f' | 'long' | 'l' | 'short' | 's' | boolean | number;

}
