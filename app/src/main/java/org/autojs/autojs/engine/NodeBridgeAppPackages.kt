package org.autojs.autojs.engine

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import org.autojs.autojs.util.App as PresetApp

/**
 * Read-only package queries of the Node / Bun app module that follow the Rhino app module (api-coverage P3.1a):
 * target resolution as `app.getPackageName`, the other users of the dual-app helpers (D-4, Q89) and JSON snapshots of
 * `app.getInstalledPackages` / `getApkInfo` / `getAppByAlias` (Q93).
 * zh-CN: Node / Bun app 模块中与 Rhino app 模块对应的只读包查询 (api-coverage P3.1a): 按 `app.getPackageName` 解析目标, 双开函数的其他用户 (D-4, Q89),
 * 以及 `app.getInstalledPackages` / `getApkInfo` / `getAppByAlias` 的 JSON 快照 (Q93).
 */
internal object NodeBridgeAppPackages {

    /**
     * Preset alias, installed package name, then application label (the order of Rhino `app.getPackageName`), with `via`
     * telling which matched and the launch intent of the package; null when none matches.
     */
    fun resolvePackage(context: Context, target: String): JSONObject? {
        val raw = target.trim()
        if (raw.isEmpty()) return null
        val manager = context.packageManager
        val (packageName, via) = PresetApp.getAppByAlias(raw)?.packageName?.let { it to "alias" }
            ?: raw.takeIf { runCatching { manager.getApplicationInfo(it, 0) }.isSuccess }?.let { it to "package" }
            ?: manager.getInstalledApplications(0).firstOrNull { raw.contentEquals(manager.getApplicationLabel(it)) }?.let { it.packageName to "label" }
            ?: return null
        val launch = manager.getLaunchIntentForPackage(packageName)
        return JSONObject().put("packageName", packageName).put("via", via)
            .put("launchIntent", launch?.let(::launchDescriptor) ?: JSONObject.NULL)
    }

    /** The current user id and the ids of its other profiles (dual apps, work profile), as Rhino lists them. */
    fun userProfiles(context: Context): JSONObject {
        val identifier = UserHandle::class.java.getDeclaredMethod("getIdentifier")
        val current = identifier.invoke(Process.myUserHandle()) as Int
        val manager = context.getSystemService(UserManager::class.java)
        val others = manager.userProfiles.map { identifier.invoke(it) as Int }.filter { it != current }
        return JSONObject().put("current", current).put("others", JSONArray(others))
    }

    fun installedPackages(context: Context, options: JSONObject?): JSONArray {
        val manager = context.packageManager
        val flags = flagsOf(options)
        @Suppress("DEPRECATION")
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else manager.getInstalledPackages(flags)
        return JSONArray(packages.map { snapshot(manager, it) })
    }

    fun apkInfo(context: Context, file: File, options: JSONObject?): JSONObject? {
        val manager = context.packageManager
        val flags = flagsOf(options)
        @Suppress("DEPRECATION")
        val info = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.getPackageArchiveInfo(file.path, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else manager.getPackageArchiveInfo(file.path, flags)) ?: return null
        // The label of an archive is read from its own resources.
        // zh-CN: 安装包的应用名从其自身资源读取.
        info.applicationInfo?.apply { sourceDir = file.path; publicSourceDir = file.path }
        return snapshot(manager, info)
    }

    fun presetApp(alias: String): JSONObject? = PresetApp.getAppByAlias(alias.trim())?.let {
        JSONObject().put("alias", it.alias).put("packageName", it.packageName).put("appName", it.getAppName())
            .put("appNameZh", it.getAppNameZh()).put("appNameEn", it.getAppNameEn())
    }

    /** `{ get?: string[], match?: string[] }` as Rhino reads it: `get: ["activities"]` is `PackageManager.GET_ACTIVITIES`; GET_META_DATA by default. */
    private fun flagsOf(options: JSONObject?): Int {
        var flags = PackageManager.GET_META_DATA
        if (options == null) return flags
        options.keys().forEach { require(it == "get" || it == "match") { "Unknown package manager option \"$it\", expected get or match" } }
        for (type in listOf("get", "match")) {
            val names = options.opt(type)?.takeUnless { it == JSONObject.NULL } ?: continue
            require(names is JSONArray) { "Package manager option \"$type\" must be an array" }
            for (i in 0 until names.length()) {
                val field = "${type}_${names.getString(i)}".uppercase()
                flags = flags or (runCatching { PackageManager::class.java.getField(field).getInt(null) }.getOrNull()
                    ?: throw IllegalArgumentException("Unknown PackageManager flag \"$field\""))
            }
        }
        return flags
    }

    @Suppress("DEPRECATION")
    private fun snapshot(manager: PackageManager, info: PackageInfo): JSONObject {
        val application = info.applicationInfo
        fun names(items: Array<out android.content.pm.ComponentInfo>?) = items?.let { list -> JSONArray(list.map { it.name }) } ?: JSONObject.NULL
        return JSONObject().put("packageName", info.packageName)
            .put("appName", application?.let { runCatching { manager.getApplicationLabel(it).toString() }.getOrNull() }.orEmpty())
            .put("versionName", info.versionName ?: "")
            .put("versionCode", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong())
            .put("system", (application?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0)
            .put("enabled", application?.enabled ?: false)
            .put("firstInstallTime", info.firstInstallTime).put("lastUpdateTime", info.lastUpdateTime)
            .put("uid", application?.uid ?: -1)
            .put("targetSdkVersion", application?.targetSdkVersion ?: 0)
            .put("minSdkVersion", application?.minSdkVersion ?: 0)
            .put("sourceDir", application?.sourceDir ?: JSONObject.NULL)
            .put("requestedPermissions", info.requestedPermissions?.let { JSONArray(it.toList()) } ?: JSONObject.NULL)
            .put("activities", names(info.activities)).put("services", names(info.services))
            .put("receivers", names(info.receivers)).put("providers", names(info.providers))
    }

    private fun launchDescriptor(intent: Intent): JSONObject = JSONObject().apply {
        intent.action?.let { put("action", it) }
        intent.categories?.takeIf { it.isNotEmpty() }?.let { put("category", JSONArray(it.sorted())) }
        intent.component?.let { put("packageName", it.packageName).put("className", it.className) }
        put("flags", intent.flags or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
