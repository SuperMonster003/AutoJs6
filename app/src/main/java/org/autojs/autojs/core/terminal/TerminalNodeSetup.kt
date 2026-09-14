package org.autojs.autojs.core.terminal

import android.content.Context
import org.autojs.autojs.core.terminal.NodeCliLocator.Resolution
import java.io.IOException

/**
 * Turns a [Resolution] into the extra environment of a new terminal session: when the plugin's
 * launcher is available, the npm / corepack archive is installed (once per plugin build), the
 * `$PREFIX/bin` links are rebuilt and the node variables are exported; otherwise the shell stays
 * plain and stale links are removed.
 *
 * zh-CN: 把 [Resolution] 变成新终端会话的附加环境: 启动器可用时安装 npm / corepack 资产 (每个插件构建一次),
 * 重建 `$PREFIX/bin` 链接并导出 node 变量; 否则保持纯 shell 并清理过期链接.
 */
object TerminalNodeSetup {

    data class Prepared(
        val resolution: Resolution,
        val environment: Map<String, String>,
        val installedNow: Boolean,
    )

    /**
     * True when [prepare] will have to extract the archive, so the UI can show progress.
     * zh-CN: [prepare] 需要解压资产时为 true, 便于界面显示进度.
     */
    @JvmStatic
    fun needsInstall(paths: TerminalPaths, resolution: Resolution): Boolean =
        resolution is Resolution.Available && !NodeCliInstaller.isInstalled(paths, resolution.launcher)

    /**
     * Runs on a worker thread; never throws. A failure while installing or linking is reported as
     * [Resolution.Unavailable.SetupFailed] so the terminal explains it instead of exposing a
     * half-working `npm`.
     * zh-CN: 在工作线程运行, 不抛异常. 安装或链接失败以 [Resolution.Unavailable.SetupFailed] 报告,
     * 让终端解释原因而不是暴露半可用的 `npm`.
     */
    @JvmStatic
    fun prepare(context: Context, paths: TerminalPaths, resolution: Resolution, options: TerminalNodeEnvironment.Options): Prepared {
        if (resolution !is Resolution.Available) {
            runCatching { NodeCliInstaller.unlinkCommands(paths, DEFAULT_COMMANDS) }
            return Prepared(resolution, emptyMap(), installedNow = false)
        }
        val launcher = resolution.launcher
        return try {
            val installedBefore = NodeCliInstaller.isInstalled(paths, launcher)
            NodeCliInstaller.ensureInstalled(context, paths, launcher)
            NodeCliInstaller.linkCommands(paths, launcher)
            Prepared(resolution, TerminalNodeEnvironment.build(paths, options), installedNow = !installedBefore)
        } catch (e: IOException) {
            runCatching { NodeCliInstaller.unlinkCommands(paths, launcher.descriptor.commands) }
            Prepared(Resolution.Unavailable.SetupFailed(launcher, e.message ?: e.javaClass.simpleName), emptyMap(), installedNow = false)
        } catch (e: RuntimeException) {
            runCatching { NodeCliInstaller.unlinkCommands(paths, launcher.descriptor.commands) }
            Prepared(Resolution.Unavailable.SetupFailed(launcher, e.toString()), emptyMap(), installedNow = false)
        }
    }

    /** Link names to clean up when no launcher is available. zh-CN: 无启动器时需要清理的链接名. */
    private val DEFAULT_COMMANDS = listOf("node", "npm", "npx", "corepack", "yarn", "yarnpkg", "pnpm", "pnpx")

}
