// Type definitions for AutoJs6 internal module sqlite
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 5.1.3
//
// Last modified: Jun 14, 2026

/// <reference path="./index.d.ts" />

/**
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/sqlite/SQLite.kt
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/SQLite.kt
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/core/database/Database.java
 */

declare namespace Internal {

    import DatabaseCallback = org.autojs.autojs.core.database.Database.DatabaseCallback;

    interface SQLite {

        (databaseFilePath: string, options?: SQLiteOpenOptions | null, callback?: DatabaseCallback | JavaScriptDatabaseCallback | null): WrappedDatabase;

        open(databaseFilePath: string, options?: SQLiteOpenOptions | null, callback?: DatabaseCallback | JavaScriptDatabaseCallback | null): WrappedDatabase;

    }

    interface SQLiteOpenOptions {
        /** @default 1 */
        version?: number;
        /** @default false */
        readOnly?: boolean;
    }

    interface JavaScriptDatabaseCallback {
        onCorruption?(db: android.database.sqlite.SQLiteDatabase): void;
        onCreate?(database: WrappedDatabase): void;
        onOpen?(database: WrappedDatabase): void;
        onUpgrade?(database: WrappedDatabase, oldVersion: number, newVersion: number): void;
    }

    interface WrappedDatabase extends org.autojs.autojs.core.database.Database {
        attachedDbs: Array<[string, string]>;
        maximumSize: number;
        pageSize: number;
        path: string;
        typeAdapter: org.autojs.autojs.core.database.Database.TypeAdapter;
        version: number;

        query(table: string, columns: string[], selection?: string | null, selectionArgs?: string[] | null, groupBy?: string | null, having?: string | null, orderBy?: string | null, limit?: string | null): WrappedCursor;
        rawQuery(sql: string, selectionArgs?: string[] | null): WrappedCursor;
    }

    interface WrappedSQLiteDatabase extends android.database.sqlite.SQLiteDatabase {
        attachedDbs: Array<[string, string]>;
        lastChangedRowCount: number;
        lastInsertRowId: number;
        maximumSize: number;
        pageSize: number;
        path: string;
        syncedTables: java.util.Map<string, string>;
        totalChangedRowCount: number;
        version: number;
    }

    interface WrappedCursor extends android.database.Cursor {
        get(index: number): any;
        getByColumn(column: string): any;
        all(close?: boolean): NativeObject[];
        pick(): NativeObject;
        next(): NativeObject | null;
        single(): NativeObject | null;
    }

}
