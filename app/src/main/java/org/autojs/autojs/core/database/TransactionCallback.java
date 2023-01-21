package org.autojs.autojs.core.database;

public interface TransactionCallback {

    void handleEvent(Transaction transaction);

}
