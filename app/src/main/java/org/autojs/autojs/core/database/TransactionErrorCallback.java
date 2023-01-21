package org.autojs.autojs.core.database;

import android.database.SQLException;

public interface TransactionErrorCallback {

    void handleEvent(SQLException e);

}
