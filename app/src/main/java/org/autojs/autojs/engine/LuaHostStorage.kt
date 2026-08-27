package org.autojs.autojs.engine

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.autojs.plugin.lua.runtime.api.LuaRuntimeContract
import org.autojs.plugin.lua.runtime.api.LuaValue
import org.autojs.plugin.lua.runtime.api.LuaValueCodec
import org.autojs.plugin.lua.runtime.api.LuaValueValidation
import java.io.IOException
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

internal object LuaStorageContract {
    const val CAPABILITY = "storage.kv.v1"
    const val MAX_KEYS_PER_PRINCIPAL = 256
    const val MAX_ENCODED_VALUE_BYTES = 252 * 1024
    const val MAX_PRINCIPAL_BYTES = 2 * 1024 * 1024
    const val MAX_OPERATIONS_PER_EXECUTION = 64
    const val MAX_MUTATIONS_PER_EXECUTION = 32
    const val MAX_READ_BYTES_PER_EXECUTION = 1024 * 1024
    const val MAX_WRITE_BYTES_PER_EXECUTION = 1024 * 1024

    // With a maximum-length call ID and storage key, a put request adds exactly 730 bytes around
    // the canonical stored LuaValue. Keep an explicit margin below TaggedWire's 256 KiB document
    // limit instead of relying on a failed Binder encoding as quota enforcement.
    const val MAX_PUT_ENVELOPE_BYTES = 730

    private val KEY_PATTERN = Regex("[A-Za-z_][A-Za-z0-9._-]{0,63}")

    fun requireValidKey(key: String) {
        require(KEY_PATTERN.matches(key)) { "Lua storage key is invalid" }
    }

    fun encodeStoredValue(value: LuaValue): ByteArray {
        require(value !== LuaValue.Nil) { "Lua storage does not accept a nil value" }
        requireTextOnlyValue(value)
        LuaValueValidation.validate(value)
        return LuaValueCodec.encode(value).also { encoded ->
            require(encoded.size <= MAX_ENCODED_VALUE_BYTES) {
                "Lua storage value exceeds its canonical encoded byte limit"
            }
        }
    }

    fun decodeStoredValue(encoded: ByteArray): LuaValue {
        require(encoded.size <= MAX_ENCODED_VALUE_BYTES) {
            "Stored Lua value exceeds its canonical encoded byte limit"
        }
        val value = LuaValueCodec.decode(encoded)
        require(value !== LuaValue.Nil) { "Stored Lua value must not be nil" }
        requireTextOnlyValue(value)
        val canonical = LuaValueCodec.encode(value)
        require(MessageDigest.isEqual(encoded, canonical)) { "Stored Lua value is not canonical" }
        return value
    }

    fun strictUtf8Size(value: String, label: String): Int = try {
        Charsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(value))
            .remaining()
    } catch (error: Exception) {
        throw IllegalArgumentException("$label is not valid UTF-8 text", error)
    }

    private fun requireTextOnlyValue(value: LuaValue) {
        when (value) {
            LuaValue.Nil -> Unit
            is LuaValue.BooleanValue,
            is LuaValue.Int64Value,
            is LuaValue.Float64Value,
            is LuaValue.StringValue,
            -> Unit
            is LuaValue.BytesValue -> throw IllegalArgumentException(
                "Lua storage V1 does not admit byte-string values",
            )
            is LuaValue.ArrayValue -> value.values.forEach(::requireTextOnlyValue)
            is LuaValue.MapValue -> value.values.values.forEach(::requireTextOnlyValue)
        }
    }
}

internal class LuaStorageQuotaExceededException(message: String) : IllegalStateException(message)

/** Durable Host-owned storage. Values are canonical LuaValue documents, never Java objects. */
internal interface LuaHostStorage {
    fun get(principal: LuaStoragePrincipal, key: String): ByteArray?
    fun put(principal: LuaStoragePrincipal, key: String, encodedValue: ByteArray)
    fun remove(principal: LuaStoragePrincipal, key: String): Boolean
    fun clear(principal: LuaStoragePrincipal): Int

    data object Unavailable : LuaHostStorage {
        override fun get(principal: LuaStoragePrincipal, key: String): ByteArray? = unavailable()
        override fun put(principal: LuaStoragePrincipal, key: String, encodedValue: ByteArray) = unavailable()
        override fun remove(principal: LuaStoragePrincipal, key: String): Boolean = unavailable()
        override fun clear(principal: LuaStoragePrincipal): Int = unavailable()

        private fun unavailable(): Nothing = error("Lua Host storage is unavailable")
    }
}

/**
 * Versioned app-private persistence with one linearization lock per principal.
 *
 * SharedPreferences commit is synchronous. Each mutation computes and validates the complete
 * projected principal quota while holding the same lock, then acknowledges only after commit.
 */
internal class SharedPreferencesLuaHostStorage(context: Context) : LuaHostStorage {
    private val applicationContext = context.applicationContext ?: context

    override fun get(principal: LuaStoragePrincipal, key: String): ByteArray? =
        synchronized(lockFor(principal)) {
            LuaStorageContract.requireValidKey(key)
            decodeEntry(preferences(principal), key)
        }

    override fun put(principal: LuaStoragePrincipal, key: String, encodedValue: ByteArray) {
        synchronized(lockFor(principal)) {
            LuaStorageContract.requireValidKey(key)
            require(encodedValue.size <= LuaStorageContract.MAX_ENCODED_VALUE_BYTES) {
                "Lua storage value exceeds its canonical encoded byte limit"
            }
            val prefs = preferences(principal)
            val entries = decodeAll(prefs)
            if (key !in entries && entries.size >= LuaStorageContract.MAX_KEYS_PER_PRINCIPAL) {
                throw LuaStorageQuotaExceededException("Lua storage key quota is exhausted")
            }
            val projected = entries.entries.sumOf { (storedKey, storedValue) ->
                if (storedKey == key) 0L else storedKey.toByteArray(Charsets.UTF_8).size.toLong() + storedValue.size
            } + key.toByteArray(Charsets.UTF_8).size.toLong() + encodedValue.size.toLong()
            if (projected > LuaStorageContract.MAX_PRINCIPAL_BYTES) {
                throw LuaStorageQuotaExceededException("Lua storage principal byte quota is exhausted")
            }
            val stored = Base64.encodeToString(encodedValue, Base64.NO_WRAP)
            if (!prefs.edit().putString(key, stored).commit()) {
                throw IOException("Lua storage commit failed")
            }
        }
    }

    override fun remove(principal: LuaStoragePrincipal, key: String): Boolean =
        synchronized(lockFor(principal)) {
            LuaStorageContract.requireValidKey(key)
            val prefs = preferences(principal)
            if (!prefs.contains(key)) return@synchronized false
            // Decode first so corruption never gets silently converted into a successful mutation.
            decodeEntry(prefs, key)
            if (!prefs.edit().remove(key).commit()) throw IOException("Lua storage remove commit failed")
            true
        }

    override fun clear(principal: LuaStoragePrincipal): Int = synchronized(lockFor(principal)) {
        val prefs = preferences(principal)
        val count = decodeAll(prefs).size
        if (count != 0 && !prefs.edit().clear().commit()) {
            throw IOException("Lua storage clear commit failed")
        }
        count
    }

    private fun preferences(principal: LuaStoragePrincipal): SharedPreferences =
        applicationContext.getSharedPreferences(PREFERENCES_PREFIX + principal.digest, Context.MODE_PRIVATE)

    private fun decodeAll(preferences: SharedPreferences): Map<String, ByteArray> =
        LinkedHashMap<String, ByteArray>().apply {
            preferences.all.toSortedMap().forEach { (key, _) ->
                LuaStorageContract.requireValidKey(key)
                put(key, checkNotNull(decodeEntry(preferences, key)))
            }
        }

    private fun decodeEntry(preferences: SharedPreferences, key: String): ByteArray? {
        val stored = preferences.all[key] ?: return null
        require(stored is String) { "Lua storage entry has an incompatible persistent type" }
        val decoded = try {
            Base64.decode(stored, Base64.NO_WRAP)
        } catch (error: IllegalArgumentException) {
            throw IllegalStateException("Lua storage entry is not valid Base64", error)
        }
        require(Base64.encodeToString(decoded, Base64.NO_WRAP) == stored) {
            "Lua storage entry is not canonical Base64"
        }
        require(decoded.size <= LuaStorageContract.MAX_ENCODED_VALUE_BYTES) {
            "Lua storage entry exceeds its encoded byte limit"
        }
        return decoded
    }

    private fun lockFor(principal: LuaStoragePrincipal): Any = LOCKS.computeIfAbsent(principal.digest) { Any() }

    private companion object {
        const val PREFERENCES_PREFIX = "lua-runtime-storage-v1-"
        val LOCKS = ConcurrentHashMap<String, Any>()
    }
}
