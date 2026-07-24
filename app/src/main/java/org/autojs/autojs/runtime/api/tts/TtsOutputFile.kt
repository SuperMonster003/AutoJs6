package org.autojs.autojs.runtime.api.tts

import android.system.ErrnoException
import android.system.Os
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps incomplete synthesis output away from the caller's destination.
 *
 * Android TTS writes to [stagingFile]. The destination is replaced only after the platform
 * reports onDone, so an error, stop, or runtime exit cannot silently truncate an existing file.
 */
internal class TtsOutputFile private constructor(
    val targetFile: File,
    val stagingFile: File,
    private val overwrite: Boolean,
    private val reservationKey: String,
    private val publisher: TtsFilePublisher,
) {

    private val finished = AtomicBoolean(false)

    val targetPath: String
        get() = targetFile.absolutePath

    @Synchronized
    fun commit(): Boolean {
        check(finished.compareAndSet(false, true)) {
            "TTS output transaction has already finished"
        }
        return try {
            require(stagingFile.isFile) {
                "TTS engine completed without creating an output file"
            }
            require(stagingFile.length() > 0L) {
                "TTS engine completed with an empty output file"
            }
            require(!targetFile.isDirectory) {
                "TTS output path points to a directory: ${targetFile.absolutePath}"
            }
            if (targetFile.exists() && !overwrite) {
                throw IOException("TTS output file already exists: ${targetFile.absolutePath}")
            }
            publisher.publish(stagingFile, targetFile, overwrite)
        } catch (error: Throwable) {
            if (stagingFile.exists() && !stagingFile.delete()) {
                error.addSuppressed(
                    IOException("Unable to remove incomplete TTS output"),
                )
            }
            throw error
        } finally {
            reservedTargets.remove(reservationKey)
        }
    }

    @Synchronized
    fun discard() {
        val firstFinish = finished.compareAndSet(false, true)
        if (!firstFinish && !stagingFile.exists()) return
        try {
            if (stagingFile.exists() && !stagingFile.delete()) {
                throw IOException(
                    "Unable to remove incomplete TTS output: ${stagingFile.absolutePath}",
                )
            }
        } finally {
            reservedTargets.remove(reservationKey)
        }
    }

    companion object {

        fun prepare(
            path: String,
            options: TtsFileOptions,
            publisher: TtsFilePublisher,
        ): TtsOutputFile {
            val target = File(path).canonicalFile
            val reservationKey = target.path
            require(reservedTargets.add(reservationKey)) {
                "Another TTS synthesis is already targeting ${target.absolutePath}"
            }
            try {
                require(target.name.isNotBlank()) { "TTS output path must name a file" }
                require(!target.isDirectory) {
                    "TTS output path points to a directory: ${target.absolutePath}"
                }
                if (target.exists() && !options.overwrite) {
                    throw IllegalArgumentException(
                        "TTS output file already exists: ${target.absolutePath}",
                    )
                }

                val parent = target.parentFile
                    ?: throw IllegalArgumentException(
                        "TTS output file must have a parent directory",
                    )
                if (!parent.exists()) {
                    require(options.createDirectories) {
                        "TTS output directory does not exist: ${parent.absolutePath}"
                    }
                    require(parent.mkdirs() || parent.isDirectory) {
                        "Unable to create TTS output directory: ${parent.absolutePath}"
                    }
                }
                require(parent.isDirectory) {
                    "TTS output parent is not a directory: ${parent.absolutePath}"
                }

                val suffix = target.extension
                    .takeIf(String::isNotBlank)
                    ?.take(32)
                    ?.let { ".$it" }
                    .orEmpty()
                var staging: File
                do {
                    staging = File(
                        parent,
                        ".autojs6-tts-${UUID.randomUUID()}$suffix",
                    )
                } while (staging.exists())

                return TtsOutputFile(
                    target,
                    staging,
                    options.overwrite,
                    reservationKey,
                    publisher,
                )
            } catch (error: Throwable) {
                reservedTargets.remove(reservationKey)
                throw error
            }
        }

        private val reservedTargets = ConcurrentHashMap.newKeySet<String>()
    }
}

/**
 * Publishes a completed staging file without exposing a partially replaced destination.
 *
 * Implementations must leave an existing [target] intact when publication fails.
 *
 * @return true when [staging] no longer needs cleanup. A false result still means publication
 * succeeded; the transaction owner must retain [staging] for a later cleanup attempt.
 */
internal fun interface TtsFilePublisher {

    @Throws(IOException::class)
    fun publish(staging: File, target: File, overwrite: Boolean): Boolean
}

/**
 * Both files are siblings, so POSIX rename is an atomic replacement on Android-supported
 * filesystems. No-replace publication uses an atomic hard link and explicitly fails on file
 * systems that cannot provide that guarantee.
 */
internal object AndroidAtomicTtsFilePublisher : TtsFilePublisher {

    override fun publish(
        staging: File,
        target: File,
        overwrite: Boolean,
    ): Boolean {
        if (!overwrite) {
            return publishWithoutOverwrite(staging, target)
        }
        try {
            Os.rename(staging.absolutePath, target.absolutePath)
            return true
        } catch (error: ErrnoException) {
            throw IOException("Unable to atomically publish the synthesized TTS file", error)
        }
    }

    /**
     * Os.link atomically installs the completed staging inode while refusing an existing name.
     * If the destination file system cannot provide that guarantee, publication fails without
     * creating or mutating the destination.
     */
    private fun publishWithoutOverwrite(staging: File, target: File): Boolean {
        try {
            Os.link(staging.absolutePath, target.absolutePath)
        } catch (error: ErrnoException) {
            throw IOException(
                if (target.exists()) {
                    "TTS output file already exists: ${target.absolutePath}"
                } else {
                    "The destination file system does not support atomic TTS publication " +
                        "without replacement; set overwrite to true to use atomic rename"
                },
                error,
            )
        }
        // The destination already points to the completed inode. Cleanup must never roll it back:
        // doing so after a path identity check would introduce another check/remove race.
        return runCatching {
            Os.remove(staging.absolutePath)
            true
        }.getOrElse {
            !staging.exists() || staging.delete()
        }
    }
}
