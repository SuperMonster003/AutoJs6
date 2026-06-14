// Type definitions for AutoJs6 internal module sysprops
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 4.8.4
//
// Last modified: Jun 14, 2026

/// <reference path="../index.d.ts" />

/**
 * @Source /src/main/java/org/autojs/autojs/runtime/api/augment/sysprops/Sysprops.kt
 */

declare namespace Internal {

    interface SysProps {
        (propName: string, defaultValue?: string): string | null;

        get(propName: string, defaultValue?: string): string | null;

        getInt(propName: string, defaultValue?: number): number | null;

        getBoolean(propName: string, defaultValue?: boolean): boolean | null;

        getAll(): SysProps.Properties;
        getAll(filter: SysProps.Filter | SysProps.FilterOptions): SysProps.Properties;
        getAll(keyFilter: SysProps.Filter, valueFilter: SysProps.Filter): SysProps.Properties;
    }

}

declare namespace SysProps {

    type Filter = string | RegExp | org.mozilla.javascript.regexp.NativeRegExp | null | undefined;

    interface FilterOptions {
        key?: Filter;
        keys?: Filter;
        value?: Filter;
        values?: Filter;
    }

    interface Properties {
        [key: string]: string;
    }

}
