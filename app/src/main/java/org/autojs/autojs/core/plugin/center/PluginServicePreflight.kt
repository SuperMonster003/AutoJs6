package org.autojs.autojs.core.plugin.center

import android.content.pm.ServiceInfo
import dalvik.system.DexFile
import java.util.zip.ZipFile

internal object PluginServicePreflight {

    // Enumerate class names without loading or executing code from the plugin in the host.
    // Include code splits and ignore resource-only APKs.
    // zh-CN: 只枚举类名, 不在宿主中加载或执行插件代码. 同时检查代码分包并跳过资源包.
    @Suppress("DEPRECATION")
    fun verify(service: ServiceInfo) {
        val application = service.applicationInfo
        val paths = listOf(application.sourceDir) + application.splitSourceDirs.orEmpty()
        for (path in paths.distinct()) {
            val hasDex = ZipFile(path).use { apk -> apk.getEntry("classes.dex") != null }
            if (!hasDex) continue
            val dex = DexFile(path)
            try {
                val names = dex.entries()
                while (names.hasMoreElements()) {
                    if (names.nextElement() == service.name) return
                }
            } finally {
                dex.close()
            }
        }
        throw ClassNotFoundException("Plugin service class missing from installed APK: ${service.packageName}/${service.name}")
    }
}
