@file:Suppress("unused")

package org.autojs.build

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * A Gradle plugin that provides version management functionality.
 *
 * zh-CN: Gradle 版本管理插件.
 *
 * - `id`: "org.autojs.build.versions"
 * - `implementationClass`: "org.autojs.build.VersionsPlugin"
 * - `displayName`: "AutoJs6 Versions Plugin"
 * - `description`: "Provides version helpers."
 *
 * Apply this plugin to your Android module's `build.gradle.kts`:
 *
 * zh-CN: 在 Android 模块的 `build.gradle.kts` 中应用此插件:<br>
 *
 * ```kts
 * plugins {
 *     id("org.autojs.build.versions")
 * }
 *
 * versions.appVersionName
 * versions.appVersionCode
 * versions.sdkVersionCompile
 * versions.sdkVersionMin
 * versions.sdkVersionTarget
 * versions.sdkVersionTargetInrt
 * versions.vscodeExtRequiredVersion
 * ```
 */
class VersionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val versions = Utils.newVersions(project)
        project.extensions.add("versions", versions)
        versions.sdkTargetOverride?.let { override ->
            val guard = project.tasks.register("rejectNonDebugSdkTargetOverride", SdkTargetOverrideGuard::class.java) {
                targetSdk.set(override)
            }
            // Attach to resolved tasks, including dependencies of abbreviated or aggregate requests.
            // zh-CN: 约束实际任务, 同时覆盖缩写和聚合任务引入的依赖; 不依赖 taskGraph 回调.
            project.tasks.configureEach {
                if (
                    name.contains("release", ignoreCase = true) ||
                    name.equals("generateRuntimeKit", ignoreCase = true) ||
                    name.equals("assembleApkBuilderTemplateDistribution", ignoreCase = true)
                ) {
                    dependsOn(guard)
                }
            }
        }
    }
}
