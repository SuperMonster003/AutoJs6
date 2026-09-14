package org.autojs.autojs.core.terminal

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Materializes the plugin's npm / corepack archive under `$PREFIX/lib/autojs6-node-cli` and keeps
 * the `$PREFIX/bin` command links pointing at the plugin's launcher.
 *
 * Installation is transactional: the archive is extracted and verified inside a sibling temp
 * directory, a stamp records which plugin build it came from, and only then is the directory
 * swapped into place. A plugin update (new version code, new archive digest) triggers a reinstall;
 * links are rebuilt on every session start because the native library directory changes on update.
 *
 * zh-CN: 把插件的 npm / corepack 资产落到 `$PREFIX/lib/autojs6-node-cli`, 并让 `$PREFIX/bin` 的命令链接
 * 指向插件的启动器. 安装是事务性的: 先在同级临时目录解压并校验, 用 stamp 记录来源插件构建, 再整体换入.
 * 插件更新 (版本号或资产摘要变化) 会触发重装; 链接在每次会话启动时重建, 因为更新会改变 native 库目录.
 */
object NodeCliInstaller {

    const val STAMP_FILE_NAME = ".autojs6-node-cli.json"
    private const val TEMP_SUFFIX = ".tmp"
    private const val OLD_SUFFIX = ".old"

    /** Scripts the launcher resolves; their absence means the install is unusable. zh-CN: 启动器依赖的脚本, 缺失即视为安装不可用. */
    val REQUIRED_SCRIPTS = listOf("npm/bin/npm-cli.js", "npm/bin/npx-cli.js", "corepack/dist/corepack.js")

    private val gson = Gson()

    /**
     * Identity of an installed archive. zh-CN: 已安装资产的身份.
     */
    data class Stamp(
        val archiveSha256: String,
        val pluginPackage: String,
        val pluginVersionCode: Long,
        val npmVersion: String?,
        val corepackVersion: String?,
        val entries: Int,
        val bytes: Long,
    )

    class InstallException(message: String, cause: Throwable? = null) : IOException(message, cause)

    @JvmStatic
    fun stampFor(launcher: NodeCliLocator.Launcher, entries: Int, bytes: Long) = Stamp(
        archiveSha256 = launcher.descriptor.archiveSha256,
        pluginPackage = launcher.packageName,
        pluginVersionCode = launcher.versionCode,
        npmVersion = launcher.descriptor.npmVersion,
        corepackVersion = launcher.descriptor.corepackVersion,
        entries = entries,
        bytes = bytes,
    )

    @JvmStatic
    fun readStamp(paths: TerminalPaths): Stamp? {
        val file = File(paths.nodeCliRoot, STAMP_FILE_NAME)
        if (!file.isFile) return null
        return runCatching { gson.fromJson(file.readText(), Stamp::class.java) }
            .getOrNull()
            ?.takeIf { it.archiveSha256.isNotEmpty() && it.pluginPackage.isNotEmpty() }
    }

    /**
     * True when the installed archive came from exactly this plugin build and the launcher scripts exist.
     * zh-CN: 已安装资产恰好来自当前插件构建且启动器脚本齐全时为 true.
     */
    @JvmStatic
    fun isInstalled(paths: TerminalPaths, launcher: NodeCliLocator.Launcher): Boolean {
        val stamp = readStamp(paths) ?: return false
        if (stamp.archiveSha256 != launcher.descriptor.archiveSha256) return false
        if (stamp.pluginPackage != launcher.packageName || stamp.pluginVersionCode != launcher.versionCode) return false
        return REQUIRED_SCRIPTS.all { File(paths.nodeCliRoot, it).isFile }
    }

    /**
     * Opens the archive from the plugin package and installs it unless the current install already matches.
     * zh-CN: 从插件包打开资产并安装, 除非当前安装已匹配.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun ensureInstalled(context: Context, paths: TerminalPaths, launcher: NodeCliLocator.Launcher): Stamp {
        readStamp(paths)?.takeIf { isInstalled(paths, launcher) }?.let { return it }
        return install(paths, launcher) { openArchive(context, launcher) }
    }

    @Throws(IOException::class)
    private fun openArchive(context: Context, launcher: NodeCliLocator.Launcher): InputStream {
        val resources = try {
            context.packageManager.getResourcesForApplication(launcher.packageName)
        } catch (e: Exception) {
            throw InstallException("plugin resources unavailable: ${e.message}", e)
        }
        return resources.assets.open(launcher.descriptor.archivePath)
    }

    /**
     * Extracts [open]'s stream into a temp directory next to the target, verifies it and swaps it in.
     * On any failure the temp directory is removed and the previous install (if any) stays untouched.
     * zh-CN: 把 [open] 的流解压到目标旁的临时目录, 校验后换入; 失败时删除临时目录, 原安装保持不变.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun install(paths: TerminalPaths, launcher: NodeCliLocator.Launcher, open: () -> InputStream): Stamp {
        val target = paths.nodeCliRoot
        val temp = File(target.path + TEMP_SUFFIX)
        val old = File(target.path + OLD_SUFFIX)
        temp.deleteRecursively()
        old.deleteRecursively()
        paths.lib.mkdirs()
        val descriptor = launcher.descriptor
        val (maxEntries, maxBytes) = NodeCliArchive.limitsFor(descriptor.archiveEntryCount, descriptor.archiveBytes)
        val result = try {
            open().use { input ->
                NodeCliArchive.extract(
                    input,
                    NodeCliArchive.Request(
                        expectedSha256 = descriptor.archiveSha256,
                        root = descriptor.archiveRoot,
                        destination = temp,
                        maxEntries = maxEntries,
                        maxBytes = maxBytes,
                    ),
                )
            }
        } catch (e: IOException) {
            temp.deleteRecursively()
            throw e
        }
        val missing = REQUIRED_SCRIPTS.filterNot { File(temp, it).isFile }
        if (missing.isNotEmpty()) {
            temp.deleteRecursively()
            throw InstallException("archive lacks ${missing.joinToString()}")
        }
        val stamp = stampFor(launcher, result.entries, result.bytes)
        File(temp, STAMP_FILE_NAME).writeText(gson.toJson(stamp))
        if (target.exists() && !target.renameTo(old)) {
            temp.deleteRecursively()
            throw InstallException("cannot move the previous install aside")
        }
        if (!temp.renameTo(target)) {
            old.takeIf { it.exists() }?.renameTo(target)
            temp.deleteRecursively()
            throw InstallException("cannot move the new install into place")
        }
        old.deleteRecursively()
        return stamp
    }

    /**
     * Rebuilds `$PREFIX/bin/<command>` links to the launcher. Stale links that point at an older
     * launcher location are replaced; unrelated files in `bin` are left alone.
     * zh-CN: 重建 `$PREFIX/bin/<command>` 到启动器的链接; 指向旧位置的过期链接被替换, `bin` 中无关文件不动.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun linkCommands(paths: TerminalPaths, launcher: NodeCliLocator.Launcher): List<File> {
        paths.bin.mkdirs()
        val targetPath = launcher.executable.path
        val created = ArrayList<File>()
        for (command in launcher.descriptor.commands) {
            val link = File(paths.bin, command)
            val current = runCatching { Os.readlink(link.path) }.getOrNull()
            if (current == targetPath) {
                created += link
                continue
            }
            if (current != null || link.exists()) {
                if (!link.delete()) throw InstallException("cannot replace ${link.path}")
            }
            try {
                Os.symlink(targetPath, link.path)
            } catch (e: ErrnoException) {
                throw InstallException("cannot link ${link.path}: ${e.message}", e)
            }
            created += link
        }
        return created
    }

    /**
     * Removes the command links so a shell-only terminal does not expose a launcher that no longer exists.
     * zh-CN: 删除命令链接, 使仅 shell 的终端不再暴露已不存在的启动器.
     */
    @JvmStatic
    fun unlinkCommands(paths: TerminalPaths, commands: Collection<String>) {
        commands.forEach { command ->
            val link = File(paths.bin, command)
            if (runCatching { Os.readlink(link.path) }.isSuccess) {
                link.delete()
            }
        }
    }

    @Suppress("unused")
    private fun Stamp.isValidJson() = runCatching { gson.toJson(this) }.isSuccess.also { if (!it) throw JsonSyntaxException("stamp") }

}
