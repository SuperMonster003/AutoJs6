package org.autojs.autojs.core.plugin.scriptmodule

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import org.autojs.autojs.core.plugin.scriptmodule.ScriptModulePluginState.Reason
import org.autojs.autojs.core.plugin.scriptmodule.ScriptModuleUsageScanner.Usage
import org.autojs.autojs6.R
import java.io.File
import java.util.zip.ZipFile

/**
 * The libraries of the Bundled Libraries plugin a packaged app takes along (roadmap P7.1, design
 * `docs/augment/P7-1-packaged-app-libraries-design.md`): the libraries the scripts use, or all of them when "Bundled
 * Libraries" is selected, with the libraries those require. A library the plugin may provide now
 * ([ScriptModulePluginState.unavailableReason]) is copied from the installed plugin APK to [ASSET_ROOT] of the app,
 * where the Rhino `require` looks first; the others are reported with the reason and the build goes on (roadmap Q186).
 *
 * zh-CN: 打包应用带上的 Bundled Libraries 插件中的库 (ROADMAP P7.1, 设计 P7-1): 脚本用到的库 (勾选 "Bundled Libraries" 时为全部), 以及这些库 require 的库.
 * 插件当前可以提供的库 ([ScriptModulePluginState.unavailableReason]) 从已安装的插件 APK 复制到应用的 [ASSET_ROOT], Rhino `require` 先在那里查找;
 * 其余的库连同原因报告出来, 构建照常进行 (Q186).
 */
internal object PackagedScriptModules {

    /** Where the packaged app's Rhino `require` reads asset modules first (`RhinoJavaScriptEngine.MODULES_ROOT_PATH`). */
    const val ASSET_ROOT = "assets/modules"

    private const val TAG = "PackagedScriptModules"

    data class Plan(
        /** Libraries to copy, in catalog order. zh-CN: 要复制的库, 按清单顺序. */
        val copied: List<String>,
        /** The files of [copied] by module path (relative to the module root). zh-CN: [copied] 的文件, 键为模块路径. */
        val files: Map<String, ByteArray>,
        /** Libraries in use that cannot be copied, with the reason. zh-CN: 用到但不能复制的库及原因. */
        val missing: Map<String, Reason>,
    ) {
        companion object {
            val EMPTY = Plan(emptyList(), emptyMap(), emptyMap())
        }
    }

    /** The module files of an installed plugin: paths under its module root and their bytes. */
    interface Files {
        val paths: Collection<String>
        fun read(path: String): ByteArray?
    }

    /**
     * The plan for [usage] (or every library when [includeAll]) under [state]; [files] are those of the installed
     * plugin, null when its APK cannot be read.
     */
    fun plan(state: ScriptModulePluginState, usage: Usage, includeAll: Boolean, files: Files?): Plan {
        val catalog = (state as? ScriptModulePluginState.Installed)?.catalog
        val order = catalog?.libraries?.map { it.id } ?: ScriptModulePluginState.KNOWN_MODULES.values.distinct()
        val wanted = LinkedHashSet(if (includeAll) order else libraries(usage, catalog))
        val pending = ArrayDeque(wanted)
        val copied = LinkedHashMap<String, List<String>>()
        val missing = LinkedHashMap<String, Reason>()
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            val reason = state.unavailableReason(id)
            val library = catalog?.libraries?.firstOrNull { it.id == id }
            if (reason != null || library == null || files == null) {
                missing[id] = reason ?: Reason.INCOMPATIBLE
                continue
            }
            val paths = files.paths.filter { isSafePath(it) && library.owns(it) }.sorted()
            copied[id] = paths
            // The libraries a copied library requires (the Node shims `fs` and `nodejs`), never its globals.
            // zh-CN: 已复制的库 require 的库 (Node 垫片 fs 与 nodejs), 不计其全局名.
            paths.filter { it.endsWith(".js", ignoreCase = true) }.forEach { path ->
                val text = files.read(path)?.toString(Charsets.ISO_8859_1) ?: return@forEach
                libraries(ScriptModuleUsageScanner.scan(text, globals = false), catalog).forEach { if (wanted.add(it)) pending += it }
            }
        }
        val sorted = (order + wanted).distinct()
        val copiedIds = sorted.filter { it in copied }
        val contents = LinkedHashMap<String, ByteArray>()
        copiedIds.forEach { id ->
            copied.getValue(id).forEach { path -> files?.read(path)?.let { contents[path] = it } }
        }
        return Plan(copiedIds, contents, sorted.filter { it in missing }.associateWith { missing.getValue(it) })
    }

    /**
     * The libraries [usage] names: the owners of its module ids (a `node:` prefix dropped) in [catalog], or among the
     * first release's names without one, and its globals.
     */
    fun libraries(usage: Usage, catalog: ScriptModulePluginCatalog?): List<String> {
        val ids = LinkedHashSet<String>()
        usage.moduleIds.forEach { raw ->
            val id = raw.removePrefix("node:")
            if (!ScriptModulePluginCatalog.isNamedId(id)) return@forEach
            val path = ScriptModulePluginCatalog.normalizeId(id)
            val owner = catalog?.ownerOf(path)?.id ?: ScriptModulePluginState.KNOWN_MODULES[path]
                ?: path.substringBefore('/', "").takeIf { catalog == null && it.isNotEmpty() }?.let { ScriptModulePluginState.KNOWN_MODULES["$it.js"] }
            owner?.let(ids::add)
        }
        ids += usage.globals
        return ids.toList()
    }

    /** The module files of the plugin APK [zip] under [moduleRoot]. */
    fun files(zip: ZipFile, moduleRoot: String): Files {
        val prefix = "assets/$moduleRoot/"
        val paths = zip.entries().asSequence().filter { !it.isDirectory && it.name.startsWith(prefix) }.map { it.name.substring(prefix.length) }.toList()
        return object : Files {
            override val paths = paths
            override fun read(path: String) = zip.getEntry(prefix + path)?.let { entry -> zip.getInputStream(entry).use { it.readBytes() } }
        }
    }

    /**
     * The plan on this device for the scripts of [source] (its [excludedDirs] skipped), or every library when
     * [includeAll]: the plugin state of the host and the files of the installed plugin APK.
     */
    fun plan(context: Context, source: File, excludedDirs: List<File>, includeAll: Boolean, checkCancelled: () -> Unit = {}): Plan {
        val started = SystemClock.elapsedRealtime()
        val usage = if (includeAll) Usage.EMPTY else ScriptModuleUsageScanner.scan(source, excludedDirs, checkCancelled)
        val scanned = SystemClock.elapsedRealtime()
        val state = ScriptModulePlugins.state(context)
        val catalog = (state as? ScriptModulePluginState.Installed)?.catalog
        if (!includeAll && libraries(usage, catalog).isEmpty()) {
            Log.i(TAG, "scan ${scanned - started} ms: no library in use")
            return Plan.EMPTY
        }
        val apk = (state as? ScriptModulePluginState.Installed)?.takeIf { catalog != null }?.let { apkOf(context, it) }
        val zip = apk?.let { runCatching { ZipFile(it) }.onFailure { error -> Log.w(TAG, "Cannot open $it", error) }.getOrNull() }
        return zip.use { plan(state, usage, includeAll, it?.let { opened -> files(opened, catalog!!.moduleRoot) }) }.also { plan ->
            Log.i(TAG, "scan ${scanned - started} ms, plan ${SystemClock.elapsedRealtime() - scanned} ms: copied ${plan.copied} (${plan.files.size} files), missing ${plan.missing}")
        }
    }

    /** One line per result for the build success dialog: the copied libraries, then the missing ones by reason. */
    fun notices(context: Context, plan: Plan): List<String> = buildList {
        val title = ScriptModulePlugins.PLUGIN_TITLE
        if (plan.copied.isNotEmpty()) add(context.getString(R.string.text_apk_build_script_modules_packaged, title, plan.copied.joinToString(", ")))
        Reason.entries.forEach { reason ->
            val ids = plan.missing.filterValues { it == reason }.keys
            if (ids.isEmpty()) return@forEach
            val resource = when (reason) {
                Reason.NOT_INSTALLED -> R.string.warning_apk_build_script_modules_not_installed
                Reason.UNAUTHORIZED -> R.string.warning_apk_build_script_modules_not_authorized
                Reason.PLUGIN_DISABLED -> R.string.warning_apk_build_script_modules_plugin_disabled
                Reason.LIBRARY_DISABLED -> R.string.warning_apk_build_script_modules_library_disabled
                Reason.INCOMPATIBLE -> R.string.warning_apk_build_script_modules_incompatible
            }
            add(context.getString(resource, title, ids.joinToString(", ")))
        }
    }

    /** The APK of the plugin [state] describes, or null when the plugin changed since. */
    private fun apkOf(context: Context, state: ScriptModulePluginState.Installed): File? {
        val info = try {
            context.packageManager.getPackageInfo(ScriptModulePlugins.PACKAGE_NAME, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        if (PackageInfoCompat.getLongVersionCode(info) != state.versionCode || info.lastUpdateTime != state.lastUpdateTime) return null
        return info.applicationInfo?.sourceDir?.let(::File)?.takeIf { it.isFile }
    }

    private fun isSafePath(path: String) = path.isNotEmpty() && path.split('/').none { it.isEmpty() || it == "." || it == ".." }

}
