package org.autojs.autojs.core.database;

import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;

public final class DatabaseErrorHandlerWrapper implements DatabaseErrorHandler {

    private Database.DatabaseCallback mCallback;

    public DatabaseErrorHandlerWrapper(Database.DatabaseCallback callback) {
        mCallback = callback;
    }

    @Override
    public void onCorruption(SQLiteDatabase sqLiteDatabase) {
        mCallback.onCorruption(sqLiteDatabase);
    }

}