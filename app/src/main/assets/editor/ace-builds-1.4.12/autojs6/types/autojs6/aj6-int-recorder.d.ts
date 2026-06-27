// Type definitions for AutoJs6 internal module recorder
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 5.1.3
//
// Last modified: Jun 14, 2026

/// <reference path="./index.d.ts" />

/**
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/recorder/Recorder.kt
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/Recorder.kt
 */

declare namespace Internal {

    interface Recorder {
        /**
         * Records or loads a time gap by key, or measures a function call.
         */
        (key?: string | null, timestamp?: number | null): number;
        (key: Function, thisObj?: object | null): number;

        /**
         * Saves a timestamp for a key, or pushes an anonymous timestamp when key is nullish.
         */
        save(key?: string | null, ts?: number | null): number;

        /**
         * Loads elapsed time from a key, or pops the latest anonymous timestamp when key is nullish.
         */
        load(key?: string | null, ts?: number | null): number;

        isLessThan(key?: string | null, compare?: any): boolean;

        isGreaterThan(key?: string | null, compare?: any): boolean;

        has(key?: string | null): boolean;

        remove(key?: string | null): boolean;

        clear(): void;
    }

}

declare namespace Recorder {

    type Key = string | Function;

    type Keycode = 'home' | 'back' | 'recents' | 'power_dialog' | 'notifications' | 'quick_settings' | 'split_screen';

}
