package org.autojs.autojs.model.explorer

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

/** Lives in the Shizuku user process. Each response stays well below Binder's transaction limit. */
internal class PrivilegedExplorerDirectoryService {
    private data class Snapshot(
        val path: String,
        val modified: Long,
        val entries: List<String>,
        var touched: Long = System.nanoTime(),
    )

    private val snapshots = LinkedHashMap<String, Snapshot>()

    @Synchronized
    fun listDirectory(path: String, snapshotToken: String, offset: Int): String = try {
        val checkedPath = PrivilegedExplorerProtocol.checkedPath(path)
        val now = System.nanoTime()
        snapshots.entries.removeAll { now - it.value.touched > 120_000_000_000L }
        val token: String
        val snapshot: Snapshot
        if (snapshotToken.isEmpty()) {
            require(offset == 0) { "A directory listing must start at offset zero" }
            snapshot = capture(checkedPath)
            token = UUID.randomUUID().toString()
            while (snapshots.size >= 2) snapshots.remove(snapshots.keys.first())
            snapshots[token] = snapshot
        } else {
            token = snapshotToken
            snapshot = snapshots[token] ?: throw IOException("Directory listing expired; refresh and try again")
            require(snapshot.path == checkedPath) { "Directory listing path changed" }
        }
        require(offset in 0..snapshot.entries.size) { "Invalid directory listing offset" }
        snapshot.touched = now
        val entries = JSONArray()
        var nextOffset = offset
        var charCount = 0
        while (nextOffset < snapshot.entries.size && entries.length() < PrivilegedExplorerProtocol.MAX_PAGE_ENTRIES) {
            val serialized = snapshot.entries[nextOffset]
            if (charCount + serialized.length > PrivilegedExplorerProtocol.MAX_PAGE_CHARS) break
            entries.put(JSONObject(serialized))
            charCount += serialized.length
            nextOffset++
        }
        check(nextOffset == snapshot.entries.size || nextOffset > offset) { "Directory entry exceeds the transport limit" }
        JSONObject()
            .put("token", token)
            .put("path", snapshot.path)
            .put("modified", snapshot.modified)
            .put("total", snapshot.entries.size)
            .put("offset", offset)
            .put("nextOffset", if (nextOffset == snapshot.entries.size) -1 else nextOffset)
            .put("entries", entries)
            .toString()
    } catch (error: Exception) {
        val reason = when (error) {
            is PrivilegedExplorerAccessException -> error.reason
            is SecurityException -> PrivilegedExplorerAccessException.Reason.PERMISSION_DENIED
            is ErrnoException -> if (error.errno == OsConstants.EACCES || error.errno == OsConstants.EPERM) {
                PrivilegedExplorerAccessException.Reason.PERMISSION_DENIED
            } else PrivilegedExplorerAccessException.Reason.IO
            is IllegalArgumentException -> PrivilegedExplorerAccessException.Reason.INVALID_DATA
            else -> PrivilegedExplorerAccessException.Reason.IO
        }
        JSONObject().put("error", reason.name).put("message", error.message.orEmpty().take(4096)).toString()
    }

    @Synchronized
    fun closeDirectory(snapshotToken: String) {
        snapshots.remove(snapshotToken)
    }

    private fun capture(path: String): Snapshot {
        val stat = Os.stat(path)
        require(OsConstants.S_ISDIR(stat.st_mode)) { "The requested path is not a directory" }
        val names = File(path).list() ?: throw PrivilegedExplorerAccessException(
            PrivilegedExplorerAccessException.Reason.PERMISSION_DENIED,
            "Shizuku cannot list this directory: $path",
        )
        if (names.size > PrivilegedExplorerProtocol.MAX_ENTRIES) {
            throw PrivilegedExplorerAccessException(PrivilegedExplorerAccessException.Reason.TOO_LARGE, "Directory contains more than ${PrivilegedExplorerProtocol.MAX_ENTRIES} entries")
        }
        var chars = 0
        val entries = names.map { name ->
            val childPath = PrivilegedExplorerProtocol.childPath(path, name)
            // Following a symlink allows entering linked directories. A dangling or inaccessible
            // target is represented using the link's own metadata instead.
            val child = try { Os.stat(childPath) } catch (_: ErrnoException) { Os.lstat(childPath) }
            JSONObject()
                .put("name", name)
                .put("directory", OsConstants.S_ISDIR(child.st_mode))
                .put("size", child.st_size.coerceAtLeast(0L))
                .put("modified", Math.multiplyExact(child.st_mtime, 1000L))
                .toString().also {
                    chars += it.length
                    if (it.length > PrivilegedExplorerProtocol.MAX_PAGE_CHARS || chars > PrivilegedExplorerProtocol.MAX_OUTPUT_BYTES / 2) {
                        throw PrivilegedExplorerAccessException(PrivilegedExplorerAccessException.Reason.TOO_LARGE, "Directory metadata exceeds the safe output limit")
                    }
                }
        }
        return Snapshot(path, Math.multiplyExact(stat.st_mtime, 1000L), entries)
    }
}
