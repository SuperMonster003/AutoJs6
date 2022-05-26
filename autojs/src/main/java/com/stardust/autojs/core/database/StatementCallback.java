package com.stardust.autojs.core.database;

public interface StatementCallback {

    void handleEvent(Transaction transaction, DatabaseResultSet resultSet);
}
