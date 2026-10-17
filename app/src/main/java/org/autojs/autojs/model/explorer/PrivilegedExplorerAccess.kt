package org.autojs.autojs.model.explorer

import android.os.DeadObjectException
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.json.JSONObject
import java.util.concurrent.ExecutionException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Explicitly selected, read-only privileged access. Call listDirectory from a background thread. */
object PrivilegedExplorerAccess {
    // A stalled remote filesystem must not leave an unlimited number of blocked Binder callers.
    private val serviceExecutor = ThreadPoolExecutor(
        0, 2, 60L, TimeUnit.SECONDS, SynchronousQueue(),
        { action -> Thread(action, "ExplorerShizukuReader").apply { isDaemon = true } },
    )

    @JvmStatic
    fun listDirectory(path: String, mode: PrivilegedAccessMode): ExplorerPage {
        val checkedPath = PrivilegedExplorerProtocol.checkedPath(path)
        val listing = when (mode) {
            PrivilegedAccessMode.ROOT -> readRoot(checkedPath)
            PrivilegedAccessMode.SHIZUKU -> readShizukuWithTimeout(checkedPath)
        }
        val page = PrivilegedExplorerPage(
            PrivilegedExplorerEntry(listing.path.substringAfterLast('/').ifEmpty { "/" }, true, 0L, listing.modified),
            listing.path, null, mode,
        )
        listing.entries.forEach { entry ->
            val childPath = PrivilegedExplorerProtocol.childPath(listing.path, entry.name)
            page.addChild(if (entry.directory) {
                PrivilegedExplorerPage(entry, childPath, page, mode)
            } else {
                PrivilegedExplorerFileItem(entry, childPath, page, mode)
            })
        }
        return page
    }

    private fun readRoot(path: String): PrivilegedExplorerListing {
        val command = PrivilegedExplorerProtocol.withRootDeadline(PrivilegedExplorerProtocol.rootCommand(path))
        val result = PrivilegedExplorerProcess.run(listOf("su", "-c", command))
        if (result.exitCode != 0) {
            val reason = when (result.exitCode) {
                124, 137, 143 -> PrivilegedExplorerAccessException.Reason.TIMEOUT
                15 -> PrivilegedExplorerAccessException.Reason.TOO_LARGE
                11, 12 -> PrivilegedExplorerAccessException.Reason.PERMISSION_DENIED
                else -> PrivilegedExplorerAccessException.Reason.IO
            }
            val detail = if (result.exitCode == 15) "Directory contains more than ${PrivilegedExplorerProtocol.MAX_ENTRIES} entries" else result.stderr.trim().take(4096)
            throw PrivilegedExplorerAccessException(reason, "Root could not list $path (exit ${result.exitCode})${if (detail.isEmpty()) "" else ": $detail"}")
        }
        return PrivilegedExplorerProtocol.parseRootListing(path, result.stdout)
    }

    private fun readShizukuWithTimeout(path: String): PrivilegedExplorerListing {
        val future = try {
            serviceExecutor.submit<PrivilegedExplorerListing> { readShizuku(path) }
        } catch (error: RejectedExecutionException) {
            throw PrivilegedExplorerAccessException(PrivilegedExplorerAccessException.Reason.SERVICE_UNAVAILABLE, "Shizuku directory reader is busy; try again after the previous request finishes", error)
        }
        try {
            return future.get(PrivilegedExplorerProtocol.TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (error: TimeoutException) {
            throw PrivilegedExplorerAccessException(PrivilegedExplorerAccessException.Reason.TIMEOUT, "Shizuku directory access timed out", error)
        } catch (error: ExecutionException) {
            throw error.cause ?: error
        } finally {
            future.cancel(true)
        }
    }

    private fun readShizuku(path: String, allowReconnect: Boolean = true): PrivilegedExplorerListing {
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Directory request cancelled")
        if (!WrappedShizuku.hasPermission()) {
            throw PrivilegedExplorerAccessException(PrivilegedExplorerAccessException.Reason.PERMISSION_DENIED, "Grant AutoJs6 access in Shizuku before browsing this directory")
        }
        val service = WrappedShizuku.getServiceOrNull() ?: throw PrivilegedExplorerAccessException(
            PrivilegedExplorerAccessException.Reason.SERVICE_UNAVAILABLE, "Shizuku user service is unavailable",
        )
        val entries = ArrayList<PrivilegedExplorerEntry>()
        val names = HashSet<String>()
        var token = ""
        var offset = 0
        var total = -1
        var modified = 0L
        var receivedChars = 0
        try {
            do {
                if (Thread.currentThread().isInterrupted) throw InterruptedException("Directory request cancelled")
                val response = service.listExplorerDirectory(path, token, offset)
                    ?: throw invalidResponse("Missing directory response; restart the Shizuku user service after updating AutoJs6")
                receivedChars += response.length
                if (response.length > PrivilegedExplorerProtocol.MAX_PAGE_CHARS + 16 * 1024 || receivedChars > PrivilegedExplorerProtocol.MAX_OUTPUT_BYTES) {
                    throw PrivilegedExplorerAccessException(PrivilegedExplorerAccessException.Reason.TOO_LARGE, "Shizuku directory response exceeds the safe output limit")
                }
                val json = JSONObject(response)
                if (json.has("error")) {
                    val reason = runCatching { PrivilegedExplorerAccessException.Reason.valueOf(json.getString("error")) }
                        .getOrDefault(PrivilegedExplorerAccessException.Reason.IO)
                    throw PrivilegedExplorerAccessException(reason, json.optString("message", "Unable to read this directory"))
                }
                val receivedToken = json.getString("token")
                val receivedTotal = json.getInt("total")
                val receivedModified = json.getLong("modified")
                require(receivedToken.isNotEmpty() && receivedToken.length <= 64 && json.getString("path") == path && json.getInt("offset") == offset)
                require(receivedTotal in 0..PrivilegedExplorerProtocol.MAX_ENTRIES)
                if (token.isEmpty()) {
                    token = receivedToken
                    total = receivedTotal
                    modified = receivedModified
                } else {
                    require(receivedToken == token && receivedTotal == total && receivedModified == modified)
                }
                val page = json.getJSONArray("entries")
                require(page.length() <= PrivilegedExplorerProtocol.MAX_PAGE_ENTRIES)
                for (index in 0 until page.length()) {
                    val entry = page.getJSONObject(index)
                    val name = PrivilegedExplorerProtocol.checkedName(entry.getString("name"))
                    require(names.add(name)) { "Duplicate directory entry" }
                    val size = entry.getLong("size").also { require(it >= 0) }
                    entries += PrivilegedExplorerEntry(name, entry.getBoolean("directory"), size, entry.getLong("modified"))
                }
                val nextOffset = json.getInt("nextOffset")
                require(entries.size <= total)
                if (nextOffset == -1) {
                    require(entries.size == total) { "Incomplete directory response" }
                } else {
                    require(nextOffset == entries.size && nextOffset > offset && nextOffset < total) { "Invalid directory continuation" }
                }
                offset = nextOffset
            } while (offset != -1)
        } catch (error: DeadObjectException) {
            if (!allowReconnect) throw error
            if (WrappedShizuku.service?.asBinder() == service.asBinder()) WrappedShizuku.service = null
            return readShizuku(path, allowReconnect = false)
        } catch (error: IllegalArgumentException) {
            throw invalidResponse(error.message.orEmpty(), error)
        } finally {
            if (token.isNotEmpty()) runCatching { service.closeExplorerDirectory(token) }
        }
        return PrivilegedExplorerListing(path, modified, entries)
    }

    private fun invalidResponse(detail: String, cause: Throwable? = null) = PrivilegedExplorerAccessException(
        PrivilegedExplorerAccessException.Reason.INVALID_DATA, "Invalid Shizuku directory response: $detail", cause,
    )
}
