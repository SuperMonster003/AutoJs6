package org.autojs.autojs.ui.explorer

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.annotation.WorkerThread
import org.autojs.autojs6.R
import java.io.File
import kotlin.math.roundToInt

internal enum class ExplorerStorageKind { ROOT, PRIMARY, REMOVABLE }

internal data class ExplorerStorageUsage(
    val totalBytes: Long,
    val availableBytes: Long,
) {
    init {
        require(totalBytes > 0L && availableBytes in 0L..totalBytes)
    }

    val usedBytes: Long = totalBytes - availableBytes
    val usedPercent: Int = (usedBytes.toDouble() / totalBytes * 100.0).roundToInt().coerceIn(0, 100)

    companion object {
        /** Invalid/unavailable statistics are not a zero-capacity or completely full device. */
        internal fun fromBytes(totalBytes: Long, availableBytes: Long): ExplorerStorageUsage? {
            if (totalBytes <= 0L || availableBytes < 0L || availableBytes > totalBytes) return null
            return ExplorerStorageUsage(totalBytes, availableBytes)
        }
    }
}

internal data class ExplorerStorageEntry(
    val path: String,
    val title: String,
    val kind: ExplorerStorageKind,
    val usage: ExplorerStorageUsage?,
)

internal data class ExplorerStorageVolumeSnapshot(
    val path: String,
    val title: String,
    val primary: Boolean,
    val mounted: Boolean,
)

/** Disk metadata and mount discovery run on an IO thread, without requesting elevated access. */
internal object ExplorerStorageCatalog {

    @WorkerThread
    fun load(context: Context): List<ExplorerStorageEntry> {
        val primaryDirectory = Environment.getExternalStorageDirectory()
        val primaryTitle = context.getString(R.string.term_internal_strorage)
        val storageManager = context.getSystemService(StorageManager::class.java)
        val volumes = runCatching { storageManager?.storageVolumes.orEmpty() }.getOrDefault(emptyList())
        val externalDirectories = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            runCatching { context.getExternalFilesDirs(null).filterNotNull() }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val snapshots = volumes.mapNotNull { volume ->
            if (!isMounted(volume.state)) return@mapNotNull null
            val directory = resolveDirectory(volume, storageManager, externalDirectories, primaryDirectory, context.packageName)
                ?: return@mapNotNull null
            ExplorerStorageVolumeSnapshot(
                path = directory.path,
                title = if (volume.isPrimary) primaryTitle else {
                    runCatching { volume.getDescription(context) }.getOrNull()
                        ?.takeIf(String::isNotBlank)
                        ?: volume.uuid
                        ?: directory.name
                },
                primary = volume.isPrimary,
                mounted = true,
            )
        }.toMutableList()
        if (snapshots.none { it.primary } && runCatching {
                isMounted(Environment.getExternalStorageState(primaryDirectory))
            }.getOrDefault(false)
        ) {
            snapshots += ExplorerStorageVolumeSnapshot(primaryDirectory.path, primaryTitle, primary = true, mounted = true)
        }
        return buildEntries(context.getString(R.string.text_explorer_storage_root), snapshots, ::readUsage)
    }

    private fun resolveDirectory(
        volume: StorageVolume,
        manager: StorageManager?,
        externalDirectories: List<File>,
        primaryDirectory: File,
        packageName: String,
    ): File? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> volume.directory
        volume.isPrimary -> primaryDirectory
        else -> externalDirectories.firstNotNullOfOrNull { directory ->
            val candidate = runCatching { manager?.getStorageVolume(directory) }.getOrNull()
            if (candidate == null || candidate.isPrimary ||
                (candidate != volume && (volume.uuid == null || candidate.uuid != volume.uuid))
            ) {
                null
            } else {
                legacyVolumeRoot(directory.path, packageName)?.let(::File)
            }
        }
    }

    private fun isMounted(state: String) = state == Environment.MEDIA_MOUNTED || state == Environment.MEDIA_MOUNTED_READ_ONLY

    @WorkerThread
    private fun readUsage(path: String): ExplorerStorageUsage? = runCatching {
        val statistics = StatFs(path)
        ExplorerStorageUsage.fromBytes(statistics.totalBytes, statistics.availableBytes)
    }.getOrNull()

    internal fun buildEntries(
        rootTitle: String,
        volumes: List<ExplorerStorageVolumeSnapshot>,
        usageForPath: (String) -> ExplorerStorageUsage?,
    ): List<ExplorerStorageEntry> {
        val entries = linkedMapOf("/" to ExplorerStorageEntry("/", rootTitle, ExplorerStorageKind.ROOT, usageForPath("/")))
        volumes.asSequence()
            .filter { it.mounted }
            .sortedBy { !it.primary }
            .forEach { volume ->
                val path = normalizePath(volume.path)
                if (!path.startsWith('/') || path in entries) return@forEach
                entries[path] = ExplorerStorageEntry(
                    path,
                    volume.title,
                    if (volume.primary) ExplorerStorageKind.PRIMARY else ExplorerStorageKind.REMOVABLE,
                    usageForPath(path),
                )
            }
        return entries.values.toList()
    }

    /** Only strip the app-specific suffix from an observed external-files directory. */
    internal fun legacyVolumeRoot(externalFilesPath: String, packageName: String): String? {
        val normalized = normalizePath(externalFilesPath)
        val suffix = "/Android/data/$packageName/files"
        return normalized.takeIf { it.startsWith('/') && it.endsWith(suffix) }
            ?.removeSuffix(suffix)
            ?.takeIf { it.isNotEmpty() && it != "/" }
    }

    /** Classification is for permission guidance, not a substitute for trying the real read. */
    fun isAndroidRestrictedPath(path: String): Boolean = restrictedDirectory.matches(normalizePath(path))

    private val restrictedDirectory = Regex(
        "^(?:/sdcard|/mnt/sdcard|/storage/self/primary|/storage/emulated/[^/]+|" +
            "/storage/[^/]+|/mnt/media_rw/[^/]+|/data/media/[^/]+)/Android/(?:data|obb)(?:/.*)?$",
    )

    private fun normalizePath(path: String): String {
        if (!path.startsWith('/')) return path
        val segments = mutableListOf<String>()
        path.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
                else -> segments += segment
            }
        }
        return "/" + segments.joinToString("/")
    }
}
