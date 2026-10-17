package org.autojs.autojs.model.explorer

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException

/** Fixed-size request headers and bounded responses, with no path or content interpreted as shell. */
internal object PrivilegedReadProtocol {
    const val MAGIC = 0x414a3652 // AJ6R
    const val VERSION = 1
    const val MAX_CHUNK = 256 * 1024
    const val MAX_JSON = 16 * 1024
    const val OPEN_TIMEOUT_MS = 30_000L
    const val READ_TIMEOUT_MS = 30_000L
    const val MAX_LIFETIME_MS = 12 * 60 * 60_000L
    const val READ = 1
    const val CHECK = 2
    const val CLOSE = 3

    data class Identity(
        val dev: Long, val ino: Long, val mode: Int, val size: Long, val modified: Long,
        val changed: Long, val uid: Int, val gid: Int, val links: Long,
    ) {
        fun json() = JSONObject().put("dev", dev).put("ino", ino).put("mode", mode).put("size", size)
            .put("modified", modified).put("changed", changed).put("uid", uid).put("gid", gid).put("links", links)

        fun check(stat: StructStat) {
            if (this != from(stat)) throw failure("SOURCE_CHANGED", "The opened file changed; reopen it before continuing")
        }

        companion object {
            fun from(stat: StructStat): Identity {
                if (!OsConstants.S_ISREG(stat.st_mode) || stat.st_size < 0) throw failure("UNSUPPORTED_TYPE", "Only regular files support random access")
                return Identity(stat.st_dev, stat.st_ino, stat.st_mode, stat.st_size, Math.multiplyExact(stat.st_mtime, 1000L),
                    Math.multiplyExact(stat.st_ctime, 1000L), stat.st_uid, stat.st_gid, stat.st_nlink)
            }

            fun from(json: JSONObject) = Identity(json.getLong("dev"), json.getLong("ino"), json.getInt("mode"),
                json.getLong("size"), json.getLong("modified"), json.getLong("changed"), json.getInt("uid"),
                json.getInt("gid"), json.getLong("links")).also {
                if (!OsConstants.S_ISREG(it.mode) || it.size < 0) throw failure("UNSUPPORTED_TYPE", "Invalid random-access file identity")
            }
        }
    }

    fun requireExpected(expected: JSONObject?, actual: Identity) {
        if (expected == null) return
        val current = actual.json()
        val required = listOf("dev", "ino", "mode", "size", "modified", "changed")
        if (required.any { !expected.has(it) }) throw failure("INVALID_REQUEST", "Incomplete expected file identity")
        for (name in required + listOf("uid", "gid", "links").filter(expected::has)) {
            if (expected.getLong(name) != current.getLong(name)) throw failure("SOURCE_CHANGED", "The selected file changed before opening")
        }
    }

    fun read(descriptor: ParcelFileDescriptor, before: Identity, offset: Long, buffer: ByteArray, count: Int): Int {
        if (offset < 0 || count !in 0..MAX_CHUNK || count > buffer.size) throw failure("INVALID_REQUEST", "Invalid random-access read range")
        before.check(Os.fstat(descriptor.fileDescriptor))
        val wanted = if (offset >= before.size) 0 else minOf(count.toLong(), before.size - offset).toInt()
        val read = if (wanted == 0) 0 else Os.pread(descriptor.fileDescriptor, buffer, 0, wanted, offset)
        if (read < 0 || read > wanted) throw IOException("Invalid pread result")
        before.check(Os.fstat(descriptor.fileDescriptor))
        return read
    }

    fun readJson(input: DataInputStream): JSONObject {
        val count = input.readInt()
        if (count !in 1..MAX_JSON) throw failure("ACCESS_UNAVAILABLE", "Invalid random-access response size")
        val bytes = ByteArray(count)
        input.readFully(bytes)
        return JSONObject(bytes.toString(Charsets.UTF_8))
    }

    fun writeJson(output: DataOutputStream, json: JSONObject) {
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size in 1..MAX_JSON)
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    fun error(error: Throwable): JSONObject = JSONObject().put("ok", false)
        .put("code", code(error)).put("message", (error.message ?: error.javaClass.simpleName).take(2048))

    fun checkSuccess(json: JSONObject) {
        if (!json.optBoolean("ok")) throw failure(json.optString("code", "ACCESS_UNAVAILABLE"), json.optString("message", "Privileged file access failed"))
    }

    fun failure(code: String, message: String, cause: Throwable? = null) = PrivilegedFileOperationException(code, message, cause = cause)

    fun code(error: Throwable): String = when (error) {
        is PrivilegedFileOperationException -> error.code
        is PrivilegedFileEngine.Failure -> error.code
        is SecurityException -> "ACCESS_UNAVAILABLE"
        is ErrnoException -> when (error.errno) {
            OsConstants.EACCES, OsConstants.EPERM -> "PERMISSION_DENIED"
            OsConstants.ENOENT -> "NOT_FOUND"
            OsConstants.ELOOP -> "SOURCE_CHANGED"
            else -> "ACCESS_UNAVAILABLE"
        }
        is IllegalArgumentException, is org.json.JSONException -> "INVALID_REQUEST"
        is InterruptedException -> "CANCELLED"
        is EOFException -> "ACCESS_UNAVAILABLE"
        else -> "ACCESS_UNAVAILABLE"
    }
}
