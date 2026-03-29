package org.autojs.autojs.core.plugin.center

import androidx.core.content.edit
import org.autojs.autojs.core.pref.Pref

/**
 * Created by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) on Mar 21, 2026.
 */
object PluginMountStore {

    const val GLOBAL_MOUNT_POINT = "global"

    private const val KEY_PACKAGES = "key_\$_plugin_mount_packages"
    private const val KEY_ENABLED_PREFIX = "key_\$_plugin_mount_enabled_\$_"
    private const val KEY_OBJECT_NAME_PREFIX = "key_\$_plugin_mount_object_name_\$_"
    private const val KEY_MOUNT_POINT_PREFIX = "key_\$_plugin_mount_point_\$_"

    data class MountConfig(
        val packageName: String,
        val enabled: Boolean,
        val objectName: String?,
        val mountPoint: String?,
    ) {
        val normalizedMountPoint: String?
            get() = normalizeMountPoint(mountPoint)
        val normalizedObjectName: String?
            get() = objectName?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun isEnabled(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return Pref.getBoolean(enabledKey(packageName), false)
    }

    fun setEnabled(packageName: String, enabled: Boolean) {
        if (packageName.isBlank()) return
        touchPackage(packageName)
        Pref.putBoolean(enabledKey(packageName), enabled)
    }

    fun getObjectName(packageName: String): String? {
        if (packageName.isBlank()) return null
        return Pref.getStringOrNull(objectNameKey(packageName))
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun setObjectName(packageName: String, objectName: String?) {
        if (packageName.isBlank()) return
        touchPackage(packageName)
        val value = objectName?.trim().orEmpty()
        if (value.isBlank()) {
            Pref.remove(objectNameKey(packageName))
        } else {
            Pref.putString(objectNameKey(packageName), value)
        }
    }

    fun getMountPoint(packageName: String): String? {
        if (packageName.isBlank()) return null
        return normalizeMountPoint(Pref.getStringOrNull(mountPointKey(packageName)))
    }

    fun setMountPoint(packageName: String, mountPoint: String?) {
        if (packageName.isBlank()) return
        touchPackage(packageName)
        val normalized = normalizeMountPoint(mountPoint)
        if (normalized == null) {
            Pref.remove(mountPointKey(packageName))
        } else {
            Pref.putString(mountPointKey(packageName), normalized)
        }
    }

    fun getMountConfig(packageName: String): MountConfig {
        return MountConfig(
            packageName = packageName,
            enabled = isEnabled(packageName),
            objectName = getObjectName(packageName),
            mountPoint = getMountPoint(packageName),
        )
    }

    fun listEnabledConfigs(): List<MountConfig> {
        val packages = allTrackedPackages()
        return packages
            .map { pkg -> getMountConfig(pkg) }
            .filter { it.enabled }
            .filter { !it.packageName.isBlank() }
    }

    fun defaultObjectName(packageName: String): String {
        val raw = packageName.substringAfterLast('.', packageName).ifBlank { "plugin" }
        val chars = raw.map { c -> if (c.isLetterOrDigit() || c == '_' || c == '$') c else '_' }.toCharArray()
        if (chars.isEmpty()) return "plugin"
        if (!(chars[0].isLetter() || chars[0] == '_' || chars[0] == '$')) {
            return "_${String(chars)}"
        }
        return String(chars)
    }

    fun defaultMountPoint(): String? = null

    fun resolveObjectName(packageName: String): String {
        return getObjectName(packageName)?.takeIf { it.isNotBlank() }
            ?: defaultObjectName(packageName)
    }

    fun resolveMountPoint(packageName: String): String? {
        return normalizeMountPoint(getMountPoint(packageName)) ?: defaultMountPoint()
    }

    fun normalizeMountPoint(input: String?): String? {
        val raw = input?.trim().orEmpty()
        if (raw.isBlank()) return null
        if (raw.equals(GLOBAL_MOUNT_POINT, ignoreCase = true)) return null
        return raw
    }

    private fun touchPackage(packageName: String) {
        val set = allTrackedPackages().toMutableSet()
        if (set.add(packageName)) {
            Pref.get().edit { putStringSet(KEY_PACKAGES, set) }
        }
    }

    private fun allTrackedPackages(): Set<String> {
        return Pref.get().getStringSet(KEY_PACKAGES, emptySet())?.toSet().orEmpty()
    }

    private fun enabledKey(packageName: String) = "$KEY_ENABLED_PREFIX$packageName"

    private fun objectNameKey(packageName: String) = "$KEY_OBJECT_NAME_PREFIX$packageName"

    private fun mountPointKey(packageName: String) = "$KEY_MOUNT_POINT_PREFIX$packageName"
}
