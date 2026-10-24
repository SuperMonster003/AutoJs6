package org.autojs.autojs.core.plugin.scriptmodule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import org.autojs.autojs.core.plugin.center.PluginDefaultEnabledPolicy
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginStateChangeRegistry
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.StandardCharsets

/**
 * The Bundled Libraries plugin as a source of JavaScript modules (roadmap T11 / P4.3, design
 * `docs/augment/P4-3-bundled-libraries-design.md` (b)). Nothing here binds a service or starts the plugin process: the
 * state comes from the PackageManager (package, version, the enabled per-library aliases of roadmap Q100) and the Plugin
 * Center stores (authorization, the plugin switch); the catalog and the module files are read from the plugin APK
 * through a package context (roadmap Q101).
 *
 * The state is kept until a package broadcast for the plugin (added, removed, replaced, changed: the per-library
 * switches change component states) or a Plugin Center switch ([PluginStateChangeRegistry]) clears it, so a lookup is
 * normally a memory read; the next lookup after a change builds it again.
 *
 * zh-CN: 作为 JavaScript 模块源的 Bundled Libraries 插件 (ROADMAP T11 / P4.3, 设计 P4-3 (b)). 这里不绑定服务, 也不启动插件进程: 状态来自
 * PackageManager (包, 版本, Q100 的已启用的各库 alias) 与插件中心的存储 (授权, 插件开关); 清单与模块文件经 package context 从插件 APK 读取 (Q101).
 * 状态一直保留, 直到插件的包广播 (安装, 卸载, 替换, 变更: 单库开关改变组件状态) 或插件中心开关 ([PluginStateChangeRegistry]) 将其清除, 因此查询通常只读内存;
 * 变化后的下一次查询重新构建.
 */
internal object ScriptModulePlugins {

    const val PACKAGE_NAME = "io.github.supermonster003.autojs6.plugin.bundled.libraries"
    const val PLUGIN_TITLE = "Bundled Libraries"
    const val ENGINE = "script-modules"

    const val META_DATA_CATALOG = "org.autojs.plugin.scriptModules.CATALOG"
    const val ACTION_LIBRARY = "org.autojs.plugin.SCRIPT_MODULE_LIBRARY"
    const val META_DATA_LIBRARY = "org.autojs.plugin.scriptModules.LIBRARY"

    private const val TAG = "ScriptModulePlugins"

    private class Loaded(val state: ScriptModulePluginState, val packageContext: Context?)

    @Volatile
    private var loaded: Loaded? = null

    @Volatile
    private var listening = false

    private val lock = Any()

    private val pluginStateChangedListener: (String) -> Unit = { packageName ->
        if (packageName == PACKAGE_NAME) invalidate()
    }

    private val packageChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.data?.schemeSpecificPart == PACKAGE_NAME) invalidate()
        }
    }

    /** The plugin for the module provider of an engine. zh-CN: 供引擎的 module provider 使用的插件. */
    fun access(context: Context): ScriptModulePluginAccess {
        val appContext = context.applicationContext ?: context
        return object : ScriptModulePluginAccess {
            override fun state() = state(appContext)
            override fun open(plugin: ScriptModulePluginState.Installed, path: String) = open(appContext, plugin, path)
        }
    }

    /** The current state; built on first use and after each change. zh-CN: 当前状态; 首次使用及每次变化后构建. */
    fun state(context: Context): ScriptModulePluginState = load(context).state

    /**
     * The source of the module file at the normalized [path] of [plugin], or null when the plugin changed since
     * [plugin] was taken or the file is missing.
     */
    fun open(context: Context, plugin: ScriptModulePluginState.Installed, path: String): Reader? {
        val current = load(context)
        val state = current.state as? ScriptModulePluginState.Installed ?: return null
        if (state.versionCode != plugin.versionCode || state.lastUpdateTime != plugin.lastUpdateTime) return null
        val catalog = state.catalog ?: return null
        val assets = current.packageContext?.assets ?: return null
        return try {
            InputStreamReader(assets.open(catalog.assetPath(path)), StandardCharsets.UTF_8)
        } catch (_: FileNotFoundException) {
            null
        }
    }

    fun invalidate() {
        loaded = null
    }

    private fun load(context: Context): Loaded {
        loaded?.let { return it }
        val appContext = context.applicationContext ?: context
        synchronized(lock) {
            loaded?.let { return it }
            listen(appContext)
            return build(appContext).also { loaded = it }
        }
    }

    private fun build(context: Context): Loaded {
        val packageManager = context.packageManager
        val packageInfo = try {
            packageManager.getPackageInfo(PACKAGE_NAME, PackageManager.GET_META_DATA)
        } catch (_: PackageManager.NameNotFoundException) {
            return Loaded(ScriptModulePluginState.NotInstalled, null)
        }
        val applicationInfo: ApplicationInfo = packageInfo.applicationInfo ?: return Loaded(ScriptModulePluginState.NotInstalled, null)
        if (!applicationInfo.enabled) return Loaded(ScriptModulePluginState.NotInstalled, null)
        val packageContext = runCatching { context.createPackageContext(PACKAGE_NAME, 0) }
            .onFailure { Log.w(TAG, "Cannot create the package context of $PACKAGE_NAME", it) }
            .getOrNull()
        val catalog = packageContext?.let { readCatalog(it, applicationInfo) }
        val defaultEnabled = PluginDefaultEnabledPolicy.isEnabledByDefault(context, ENGINE, PACKAGE_NAME)
        val state = ScriptModulePluginState.Installed(
            versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
            lastUpdateTime = packageInfo.lastUpdateTime,
            authorized = PluginTrustManager.isAuthorized(context, PACKAGE_NAME),
            pluginEnabled = PluginEnableStore.isEnabled(context, PACKAGE_NAME, defaultEnabled),
            catalog = catalog,
            enabledLibraries = enabledLibraries(packageManager),
        )
        Log.i(TAG, "state: v${state.versionCode}, authorized ${state.authorized}, enabled ${state.pluginEnabled}, libraries ${catalog?.libraries?.size}, on ${state.enabledLibraries.size}")
        return Loaded(state, packageContext)
    }

    private fun readCatalog(packageContext: Context, applicationInfo: ApplicationInfo): ScriptModulePluginCatalog? {
        val path = applicationInfo.metaData?.getString(META_DATA_CATALOG) ?: return null
        return runCatching {
            packageContext.assets.open(path).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        }.map(ScriptModulePluginCatalog::parse).onFailure { Log.w(TAG, "Cannot read the catalog $path of $PACKAGE_NAME", it) }.getOrNull()
    }

    /** The libraries whose alias is enabled: a disabled component is not returned by the query. */
    private fun enabledLibraries(packageManager: PackageManager): Set<String> {
        val intent = Intent(ACTION_LIBRARY).setPackage(PACKAGE_NAME)
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.GET_META_DATA)
        }
        return activities.mapNotNullTo(LinkedHashSet()) { it.activityInfo?.metaData?.getString(META_DATA_LIBRARY) }
    }

    private fun listen(context: Context) {
        if (listening) return
        listening = true
        PluginStateChangeRegistry.addListener(pluginStateChangedListener)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
            addDataSchemeSpecificPart(PACKAGE_NAME, android.os.PatternMatcher.PATTERN_LITERAL)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(packageChangedReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(packageChangedReceiver, filter)
            }
        }.onFailure { Log.e(TAG, "Unable to register the package monitor of $PACKAGE_NAME", it) }
    }

}
