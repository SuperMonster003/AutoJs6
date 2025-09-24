@file:Suppress("unused")

package org.autojs.build

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Convention plugin: Unified Java/Kotlin target version configuration for Android modules.
 * 
 * zh-CN: 约定插件: 为 Android 模块统一配置 Java/Kotlin 目标版本.
 * 
 * - `id`: "org.autojs.build.jvm-convention"
 * - `implementationClass`: "org.autojs.build.JvmConventionPlugin"
 * - `displayName`: "AutoJs6 JVM Convention Plugin"
 * - `description`: "Configures Java/Kotlin targets for Android modules using central Versions."
 * 
 * Apply this plugin to your Android module's `build.gradle.kts`:
 *
 * zh-CN: 在 Android 模块的 `build.gradle.kts` 中应用此插件:<br>
 *
 * ```kts
 * plugins {
 *     id("org.autojs.build.jvm-convention")
 * }
 * ```
 */
class JvmConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        Utils.configureJvmForAndroidModule(project)
    }
}