package org.autojs.autojs.engine

import android.os.ParcelFileDescriptor
import org.autojs.autojs.script.LuaFileSource
import org.autojs.autojs.script.LuaScriptSource
import org.autojs.plugin.lua.runtime.api.LuaContractException
import org.autojs.plugin.lua.runtime.api.LuaContractViolation
import org.autojs.plugin.lua.runtime.api.LuaRuntimeContract
import org.autojs.plugin.lua.runtime.api.LuaSha256
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/** Host-private immutable source copy. Only its read-only descriptor crosses Binder. */
internal class LuaSourceSnapshot private constructor(
    val logicalName: String,
    val lengthBytes: Long,
    val sha256: LuaSha256,
    val storagePrincipal: LuaStoragePrincipal?,
    internal val snapshotFile: File,
) : Closeable {

    private val closed = AtomicBoolean(false)

    fun openReadOnlyDescriptor(): ParcelFileDescriptor {
        check(!closed.get()) { "Lua source snapshot is closed" }
        return ParcelFileDescriptor.open(snapshotFile, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        snapshotFile.delete()
    }

    companion object {
        private const val DIRECTORY_NAME = "lua-source-snapshots"
        private const val BUFFER_BYTES = 16 * 1024

        fun create(cacheDirectory: File, source: LuaScriptSource): LuaSourceSnapshot {
            val directory = File(cacheDirectory, DIRECTORY_NAME)
            if ((!directory.exists() && !directory.mkdirs()) || !directory.isDirectory) {
                invalid("Cannot create the Lua source snapshot directory")
            }
            val snapshot = File.createTempFile("source-", ".lua", directory)
            return try {
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                sourceInput(source).use { input ->
                    FileOutputStream(snapshot).use { output ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            total += count
                            if (total > LuaRuntimeContract.MAX_SOURCE_BYTES) {
                                invalid("Lua source exceeds the host protocol limit")
                            }
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                }
                LuaSourceSnapshot(
                    logicalName = source.fullName,
                    lengthBytes = total,
                    sha256 = LuaSha256.fromBytes(digest.digest()),
                    storagePrincipal = LuaStoragePrincipal.fromSource(source),
                    snapshotFile = snapshot,
                )
            } catch (error: Throwable) {
                snapshot.delete()
                throw error
            }
        }

        private fun sourceInput(source: LuaScriptSource) = when (source) {
            is LuaFileSource -> source.file.inputStream()
            else -> ByteArrayInputStream(source.script.toByteArray(Charsets.UTF_8))
        }

        private fun invalid(message: String): Nothing =
            throw LuaContractException(LuaContractViolation.INVALID_VALUE, message)
    }
}
