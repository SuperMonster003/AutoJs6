package org.autojs.autojs.ui.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.autojs.autojs.apkbuilder.keystore.KeyStore
import org.autojs.autojs.apkbuilder.keystore.KeyStoreRepository
import java.io.File

class KeyStoreViewModel(context: Context) : ViewModel() {
    private val keyStoreRepository: KeyStoreRepository = KeyStoreRepository(context)

    private val _allKeyStores = MutableLiveData<List<KeyStore>>()
    val allKeyStores: LiveData<List<KeyStore>> get() = _allKeyStores

    private val _verifiedKeyStores = MutableLiveData<List<KeyStore>>()
    val verifiedKeyStores: LiveData<List<KeyStore>> get() = _verifiedKeyStores

    init {
        updateVerifiedKeyStores()
    }

    fun updateVerifiedKeyStores() {
        viewModelScope.launch {
            val keyStores = keyStoreRepository.getAllKeyStores()
            val validKeyStores = mutableListOf<KeyStore>()

            keyStores.forEach { keyStore ->
                val file = File(keyStore.absolutePath)
                if (file.exists()) {
                    validKeyStores.add(keyStore)
                } else {
                    keyStoreRepository.deleteKeyStores(keyStore)
                }
            }

            _verifiedKeyStores.value = validKeyStores
        }
    }

    fun updateAllKeyStoresFromFiles(files: Array<File>) {
        viewModelScope.launch {
            val updatedKeyStores = files.map { file ->
                keyStoreRepository.getKeyStoreAbsolutePath(file.absolutePath) ?: KeyStore(
                    absolutePath = file.absolutePath,
                    filename = file.name
                )
            }

            _allKeyStores.value = updatedKeyStores
        }
    }

    fun upsertKeyStore(keyStore: KeyStore) {
        viewModelScope.launch {
            keyStoreRepository.upsertKeyStores(keyStore)

            val currentKeyStores = _allKeyStores.value ?: emptyList()

            val updatedKeyStores =
                if (currentKeyStores.any { it.absolutePath == keyStore.absolutePath }) {
                    currentKeyStores.map {
                        if (keyStore.absolutePath == it.absolutePath) {
                            keyStore
                        } else {
                            it
                        }
                    }
                } else {
                    currentKeyStores + keyStore
                }

            _allKeyStores.value = updatedKeyStores
        }
    }

    fun deleteKeyStore(keyStore: KeyStore) {
        viewModelScope.launch {
            keyStoreRepository.deleteKeyStores(keyStore)

            val currentKeyStores = _allKeyStores.value ?: emptyList()
            val updatedKeyStores = currentKeyStores.filter { it != keyStore }
            _allKeyStores.value = updatedKeyStores
        }
    }

    fun deleteAllKeyStores() {
        viewModelScope.launch {
            keyStoreRepository.deleteAllKeyStores()
            _allKeyStores.value = emptyList()
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return KeyStoreViewModel(context) as T
        }
    }
}
