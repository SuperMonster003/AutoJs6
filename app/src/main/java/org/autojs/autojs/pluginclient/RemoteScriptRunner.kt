package org.autojs.autojs.pluginclient

import android.content.Context
import android.util.Log
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.model.script.Scripts
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.script.LuaScriptSource
import org.autojs.autojs.script.LuaStringSource
import org.autojs.autojs.script.ScriptSource
import org.autojs.autojs.script.StringScriptSource
import org.autojs.autojs.script.TypeScriptStringSource
import org.autojs.autojs.util.ViewUtils
import java.io.File

/**
 * Runs a single script sent by the VSCode extension ("run" / "rerun" commands), choosing the engine from
 * the file name instead of treating everything as Rhino JavaScript:
 *
 * - `.ts`, `.tsx`, `.mts`, `.cts` (incl. `.bun.ts`) compile through [TypeScriptStringSource]
 * - `.lua` runs through [LuaStringSource]
 * - `.js` and the engine-marked variants (`.node.js`, `.bun.js`, `.quickjs.js`, `.mjs`, `.cjs`) keep their
 *   marker in the source name so [StringScriptSource] resolves Node.js / Bun / QuickJS
 * - anything else (`.py`, `.auto`, `.java`, `.kt`) needs a real file: it is written to the cache and
 *   launched like a local file, so the regular file routing (and the Python preview gate) applies
 *
 * zh-CN: 运行 VSCode 插件发送的单个脚本 ("run" / "rerun" 命令), 按文件名选择引擎, 而非一律按 Rhino JavaScript 处理:
 * `.ts/.tsx/.mts/.cts` (含 `.bun.ts`) 经 [TypeScriptStringSource] 编译; `.lua` 经 [LuaStringSource] 运行;
 * `.js` 及带引擎标记的变体 (`.node.js`, `.bun.js`, `.quickjs.js`, `.mjs`, `.cjs`) 在源名称中保留标记以便
 * [StringScriptSource] 解析 Node.js / Bun / QuickJS; 其他 (`.py`, `.auto`, `.java`, `.kt`) 需要真实文件:
 * 写入缓存后按本地文件方式启动, 复用常规文件路由 (含 Python 预览门控).
 *
 * Created by SuperMonster003 on Sep 16, 2026.
 */
object RemoteScriptRunner {

    private const val TAG = "RemoteScriptRunner"

    /** Shown before the script name in the console and the task list. zh-CN: 控制台与任务列表中脚本名前的前缀. */
    const val PREFIX = "\$remote/"

    @JvmStatic
    fun run(context: Context, cacheDir: File, viewId: String, name: String?, script: String): ScriptExecution? {
        // The extension sends a plain file name; strip any directory part defensively.
        // zh-CN: 插件发送的是纯文件名; 保险起见去除目录部分.
        val fileName = name?.trim()?.let { File(it).name }.orEmpty()
        val baseName = if (fileName.isEmpty()) "[$viewId]" else PFiles.getNameWithoutExtension(fileName)
        return try {
            val source = createStringSource(fileName, baseName, script)
            if (source != null) {
                source.prefix = PREFIX
                Scripts.run(context, source)
            } else {
                val file = writeCacheFile(cacheDir, viewId, fileName, script)
                Log.i(TAG, "Running remote script from cache file: ${file.path}")
                Scripts.run(context, ScriptFile(file.path))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to run remote script \"$fileName\"", e)
            ViewUtils.showToast(context, e.message ?: e.javaClass.simpleName, true)
            null
        }
    }

    private fun createStringSource(fileName: String, baseName: String, script: String): ScriptSource? = when {
        fileName.isEmpty() -> StringScriptSource(baseName, script)
        JavaScriptSource.isCompilerBackedTypeScriptFileName(fileName) -> TypeScriptStringSource(fileName, script)
        LuaScriptSource.isLuaFileName(fileName) -> LuaStringSource(baseName, script)
        JavaScriptSource.isJavaScriptFileName(fileName) -> {
            val hasEngineMarker = JavaScriptSource.isQuickJsFileName(fileName) ||
                    JavaScriptSource.isBunFileName(fileName) ||
                    JavaScriptSource.isNodeJsFileName(fileName)
            if (hasEngineMarker) MarkedJavaScriptStringSource(fileName, script) else StringScriptSource(baseName, script)
        }
        else -> null
    }

    /**
     * A JavaScript entry whose source name keeps its full file name so that the engine marker is honoured, while
     * the displayed name stays `$remote/t.node.js` instead of `$remote/t.node.js.js`.
     * zh-CN: 源名称保留完整文件名以识别引擎标记的 JavaScript 脚本, 同时显示名保持为 `$remote/t.node.js` 而非 `$remote/t.node.js.js`.
     */
    private class MarkedJavaScriptStringSource(fileName: String, script: String) : StringScriptSource(fileName, script) {
        override fun toString(): String = originPath ?: originUri ?: (prefix.orEmpty() + name)
    }

    private fun writeCacheFile(cacheDir: File, viewId: String, fileName: String, script: String): File {
        val dirName = viewId.replace(Regex("[^A-Za-z0-9._-]"), "_").take(64).ifEmpty { "script" }
        val dir = File(cacheDir, dirName)
        if (!dir.isDirectory) {
            dir.deleteRecursively()
            check(dir.mkdirs()) { "Unable to create cache directory: ${dir.path}" }
        }
        return File(dir, fileName).also { it.writeText(script, Charsets.UTF_8) }
    }

}
