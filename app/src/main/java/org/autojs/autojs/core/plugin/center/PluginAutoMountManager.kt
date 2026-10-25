package org.autojs.autojs.core.plugin.center

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.plugins.Plugins
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

/**
 * Created by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) on Mar 21, 2026.
 */
object PluginAutoMountManager {

    fun apply(scriptRuntime: ScriptRuntime) {
        val context = ScriptRuntime.applicationContext
        PluginMountStore.listEnabledConfigs().forEach { config ->
            val packageName = config.packageName
            val objectName = config.normalizedObjectName ?: PluginMountStore.defaultObjectName(packageName)
            val mountPoint = config.normalizedMountPoint

            if (!PluginEnableStore.isEnabled(context, packageName, defaultEnabled = true)) {
                return@forEach
            }
            if (!PluginTrustManager.isAuthorized(context, packageName)) {
                return@forEach
            }

            val exported = runCatching {
                Plugins.load(scriptRuntime, arrayOf(packageName))
            }.getOrNull() ?: return@forEach

            runCatching {
                mount(
                    root = scriptRuntime.topLevelScope,
                    mountPoint = mountPoint,
                    objectName = objectName,
                    value = exported,
                )
            }.onFailure {
                scriptRuntime.console.warn("Auto mount failed for $packageName: ${it.message}")
            }
        }
    }

    private fun mount(
        root: TopLevelScope,
        mountPoint: String?,
        objectName: String,
        value: Any?,
    ) {
        var parent: Scriptable = root.globalThis
        mountPoint
            ?.split('.')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.forEach { segment ->
                val next = when (val existing = ScriptableObject.getProperty(parent, segment)) {
                    ScriptableObject.NOT_FOUND,
                    Undefined.instance,
                    null,
                        -> RhinoUtils.newObject(root).also { ScriptableObject.putProperty(parent, segment, it) }
                    is Scriptable,
                        -> existing
                    else
                        -> RhinoUtils.newObject(root).also { ScriptableObject.putProperty(parent, segment, it) }
                }
                parent = next
            }
        ScriptableObject.putProperty(parent, objectName, value)
    }
}
