// Type definitions for AutoJs6 internal module mediainfo
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 5.1.3
//
// Last modified: Jun 14, 2026

/// <reference path="../index.d.ts" />

/**
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/mediainfo/Mediainfo.kt
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/mediainfo/MediainfoNativeObject.kt
 * @Source %AutoJs6%/app/src/main/java/org/mediainfo/android/MediaInfo.kt
 */

declare namespace Internal {

    interface Mediainfo {
        (path: string): Mediainfo.Result;

        read(path: string): Mediainfo.Result;
    }

}

declare namespace Mediainfo {

    type StreamKind = 'general' | 'video' | 'audio' | 'text' | 'other' | 'image' | 'menu' | 'max';

    interface StreamAccessor {
        (parameter?: string): string;

        [property: string]: string | ((parameter?: string) => string);
    }

    interface Result {
        readonly path: string;
        readonly inform: string;

        readonly general: StreamAccessor;
        readonly video: StreamAccessor;
        readonly audio: StreamAccessor;
        readonly text: StreamAccessor;
        readonly other: StreamAccessor;
        readonly image: StreamAccessor;
        readonly menu: StreamAccessor;
        readonly max: StreamAccessor;

        toString(): string;

        [section: string]: any;
    }

}
