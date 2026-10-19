package org.autojs.autojs.runtime.api.shell

import android.util.Log
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.getCurrentTopCallScopeOrDefault
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Wrapper

/**
 * Glue between the script-facing shell APIs and [ShellLauncher]: reads the options object of `new Shell(options)` and
 * `shell.spawn(command, options)`, and opens a [PipeShell] that ends with the script which created it.
 * zh-CN: 面向脚本的 shell API 与 [ShellLauncher] 之间的衔接: 读取 `new Shell(options)` 与 `shell.spawn(command, options)` 的选项对象, 并打开随创建它的
 * 脚本一同结束的 [PipeShell].
 */
internal object ShellPipes {

    private const val TAG = "ShellPipes"

    private val OPTION_KEYS = setOf("cmd", "root", "adb", "pty", "env", "cwd")

    /**
     * Options `{ cmd?, root?, adb?, pty?, env?, cwd? }`; keys absent from [options] keep the values of [defaults]. A
     * relative `cwd` is resolved against the script's working directory.
     * zh-CN: 选项 `{ cmd?, root?, adb?, pty?, env?, cwd? }`; [options] 中缺少的键沿用 [defaults] 的值. 相对的 `cwd` 按脚本工作目录解析.
     */
    @JvmStatic
    @JvmOverloads
    fun optionsOf(options: Map<*, *>, runtime: ScriptRuntime? = currentRuntime(), defaults: ShellLauncher.Options = ShellLauncher.Options()): ShellLauncher.Options {
        options.keys.map { coerceString(it) }.firstOrNull { it !in OPTION_KEYS }?.let { unknown ->
            throw IllegalArgumentException("Unknown shell option \"$unknown\", expected one of ${OPTION_KEYS.joinToString(", ")}")
        }
        val env = options["env"].let { value ->
            when {
                value.isJsNullish() -> defaults.env
                value is Map<*, *> -> value.entries.associate { (k, v) -> coerceString(k) to coerceString(v, "") }
                else -> throw IllegalArgumentException("Shell option \"env\" must be an object instead of ${value.jsBrief()}")
            }
        }
        val cwd = options["cwd"].takeUnless { it.isJsNullish() }?.let { value ->
            coerceString(value).let { runtime?.files?.path(it) ?: it }
        } ?: defaults.cwd
        return ShellLauncher.Options(
            root = options["root"].takeUnless { it.isJsNullish() }?.let { coerceBoolean(it) } ?: defaults.root,
            adb = options["adb"].takeUnless { it.isJsNullish() }?.let { coerceBoolean(it) } ?: defaults.adb,
            pty = options["pty"].takeUnless { it.isJsNullish() }?.let { coerceBoolean(it) } ?: defaults.pty,
            env = env,
            cwd = cwd,
            cmd = options["cmd"].takeUnless { it.isJsNullish() }?.let { coerceString(it) } ?: defaults.cmd,
        )
    }

    @JvmStatic
    fun optionsOfCommand(cmd: String) = ShellLauncher.Options(cmd = cmd)

    /** Starts the process; the shell closes with the script that created it, if any. zh-CN: 启动进程; shell 随创建它的脚本 (如有) 一同关闭. */
    @JvmStatic
    fun open(options: ShellLauncher.Options): PipeShell {
        val runtime = currentRuntime()
        val process = ShellLauncher.startShell(options)
        val reportError: (Throwable) -> Unit = { t ->
            Log.w(TAG, "Shell callback failed", t)
            runtime?.console?.error(t)
        }
        return PipeShell(process, options, reportError) { runtime?.closeableManager?.remove(it) }.also { shell ->
            runtime?.closeableManager?.add(shell)
        }
    }

    /** The runtime of the script running on this thread, read from its `runtime` global. zh-CN: 当前线程上运行的脚本的运行时, 取自其 `runtime` 全局变量. */
    fun currentRuntime(): ScriptRuntime? = try {
        Context.getCurrentContext()?.let { cx ->
            when (val value = ScriptableObject.getProperty(cx.getCurrentTopCallScopeOrDefault(), "runtime")) {
                is ScriptRuntime -> value
                is Wrapper -> value.unwrap() as? ScriptRuntime
                else -> null
            }
        }
    } catch (e: Exception) {
        Log.d(TAG, "No script runtime on this thread: ${e.message}")
        null
    }

}
