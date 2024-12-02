package org.autojs.autojs.core.database

class Transaction(val database: Database) {

    fun end() {
        database.endTransaction()
    }

    fun succeed() {
        database.setTransactionSuccessful()
    }

}
