package org.autojs.autojs.apkbuilder.keystore

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert


@Dao
interface KeyStoreDao {

    @Query("SELECT * FROM keystore WHERE absolutePath = :absolutePath LIMIT 1")
    suspend fun getByAbsolutePath(absolutePath: String): KeyStore?

    @Upsert
    suspend fun upsert(vararg keyStores: KeyStore)

    @Query("SELECT * FROM keystore")
    suspend fun getAll(): List<KeyStore>

    @Delete
    suspend fun delete(vararg keyStores: KeyStore)

    @Query("DELETE FROM keystore WHERE absolutePath = :absolutePath")
    suspend fun deleteByAbsolutePath(absolutePath: String): Int

    @Query("DELETE FROM keystore")
    suspend fun deleteAll()
}
