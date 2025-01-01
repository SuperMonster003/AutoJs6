package org.autojs.autojs.apkbuilder.keystore

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KeyStoreRepository(context: Context) {

    private var dao: KeyStoreDao

    init {
        val keyStoreDatabase = KeyStoreDatabase.getDatabase(context)
        dao = keyStoreDatabase.keyStoreDao()
    }

    // 获取所有 KeyStore
    suspend fun getAllKeyStores(): List<KeyStore> {
        return withContext(Dispatchers.IO) {
            dao.getAll()
        }
    }

    // 插入或更新 KeyStore
    suspend fun upsertKeyStores(vararg keyStores: KeyStore) {
        withContext(Dispatchers.IO) {
            dao.upsert(*keyStores)
        }
    }

    // 根据绝对路径获取 KeyStore
    suspend fun getKeyStoreAbsolutePath(absolutePath: String): KeyStore? {
        return withContext(Dispatchers.IO) {
            dao.getByAbsolutePath(absolutePath)
        }
    }

    // 删除 KeyStore
    suspend fun deleteKeyStores(vararg keyStores: KeyStore) {
        withContext(Dispatchers.IO) {
            dao.delete(*keyStores)
        }
    }

    // 删除所有 KeyStore
    suspend fun deleteAllKeyStores() {
        withContext(Dispatchers.IO) {
            dao.deleteAll()
        }
    }
}
