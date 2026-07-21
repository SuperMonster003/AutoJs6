package org.autojs.autojs.storage.file

import android.content.Context
import org.autojs.autojs.pio.PFiles
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by SuperMonster003 on Feb 15, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 15, 2026.
 */
class StableDraftFileHelper(private val context: Context, private val keyPath: String? = null) {

    fun saveDraft(text: String, shouldWrite: () -> Boolean = { true }): File? {
        val target = stableDraftFileForKeyPathOrNull(keyPath) ?: return null
        return synchronized(lockFor(target)) {
            if (!shouldWrite()) return@synchronized null
            runCatching {
                // Atomic write: write to .part then rename.
                // zh-CN: 原子写入: 先写入 .part 再重命名.
                val part = File.createTempFile("${target.name}.", ".part", target.parentFile)

                PFiles.write(part, text)

                if (!part.renameTo(target)) {
                    // Fallback: try best-effort copy/replace.
                    // zh-CN: 后备方案: 尽力 copy/replace.
                    PFiles.write(target, text)
                    runCatching { part.delete() }
                }

                target
            }.onFailure { it.printStackTrace() }.getOrNull()
        }
    }

    fun deleteDraft() {
        stableDraftFileForKeyPathOrNull(keyPath)?.let { target ->
            synchronized(lockFor(target)) {
                // Best-effort cleanup.
                // zh-CN: 尽力清理.
                runCatching { target.delete() }
                target.parentFile?.listFiles { file ->
                    file.name.startsWith("${target.name}.") && file.name.endsWith(".part")
                }?.forEach { part -> runCatching { part.delete() } }
            }
        }
    }

    private fun stableDraftFileForKeyPathOrNull(keyPath: String?): File? {
        if (keyPath.isNullOrBlank()) return null

        // Stable name: sha256(path).js.
        // zh-CN: 稳定文件名: sha256(path).js.
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(keyPath.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(Locale.US, it) }

        val dir = File(context.cacheDir, "editor-drafts").apply { mkdirs() }
        return File(dir, "draft-$digest.js")
    }

    private fun lockFor(target: File): Any =
        draftLocks.computeIfAbsent(target.absolutePath) { Any() }

    companion object {
        private val draftLocks = ConcurrentHashMap<String, Any>()
    }
}
