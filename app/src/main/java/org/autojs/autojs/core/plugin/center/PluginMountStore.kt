package org.autojs.autojs.core.plugin.center

import org.autojs.autojs.core.pref.keys.PluginCenterKeys

/**
 * Created by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) on Mar 21, 2026.
 *
 * Automatic mounts in the default preferences: the package list [PluginCenterKeys.MOUNT_PACKAGES] and the key families
 * [PluginCenterKeys.MOUNT_ENABLED], [PluginCenterKeys.MOUNT_OBJECT_NAME] and [PluginCenterKeys.MOUNT_POINT] per
 * package name (docs/settings/ROADMAP.md P3.1, names and value types unchanged).
 */
object PluginMountStore {

    const val GLOBAL_MOUNT_POINT = "global"

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
        return enabledKey(packageName).get()
    }

    fun setEnabled(packageName: String, enabled: Boolean) {
        if (packageName.isBlank()) return
        touchPackage(packageName)
        enabledKey(packageName).set(enabled)
    }

    fun getObjectName(packageName: String): String? {
        if (packageName.isBlank()) return null
        return objectNameKey(packageName).storedOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun setObjectName(packageName: String, objectName: String?) {
        if (packageName.isBlank()) return
        touchPackage(packageName)
        val value = objectName?.trim().orEmpty()
        if (value.isBlank()) {
            objectNameKey(packageName).remove()
        } else {
            objectNameKey(packageName).set(value)
        }
    }

    fun getMountPoint(packageName: String): String? {
        if (packageName.isBlank()) return null
        return normalizeMountPoint(mountPointKey(packageName).storedOrNull())
    }

    fun setMountPoint(packageName: String, mountPoint: String?) {
        if (packageName.isBlank()) return
        touchPackage(packageName)
        val normalized = normalizeMountPoint(mountPoint)
        if (normalized == null) {
            mountPointKey(packageName).remove()
        } else {
            mountPointKey(packageName).set(normalized)
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
            PluginCenterKeys.MOUNT_PACKAGES.set(set)
        }
    }

    private fun allTrackedPackages(): Set<String> {
        return PluginCenterKeys.MOUNT_PACKAGES.get().toSet()
    }

    private fun enabledKey(packageName: String) = PluginCenterKeys.MOUNT_ENABLED.key(packageName)

    private fun objectNameKey(packageName: String) = PluginCenterKeys.MOUNT_OBJECT_NAME.key(packageName)

    private fun mountPointKey(packageName: String) = PluginCenterKeys.MOUNT_POINT.key(packageName)
}
