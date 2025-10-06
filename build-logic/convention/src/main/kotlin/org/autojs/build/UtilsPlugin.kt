@file:Suppress("unused")

package org.autojs.build

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * A Gradle plugin that provides basic utilities.
 *
 * zh-CN: Gradle 基础工具插件.
 *
 * - `id`: "org.autojs.build.utils"
 * - `implementationClass`: "org.autojs.build.UtilsPlugin"
 * - `displayName`: "AutoJs6 Build Utils Plugin"
 * - `description`: "Provides utilities for downloading, extracting archives, and version helpers."
 *
 * Apply this plugin to your Android module's `build.gradle.kts`:
 *
 * zh-CN: 在 Android 模块的 `build.gradle.kts` 中应用此插件:<br>
 *
 * ```kts
 * plugins {
 *     id("org.autojs.build.utils")
 * }
 *
 * utils.digestCRC32(file("some/file.zip"))
 * utils.getDateString("MMM d, yyyy", "GMT+08:00")
 * utils.hours2Millis(0.75)
 * utils.compareVersionStrings("6.3.2 beta", "6.3.2 alpha4) > 0
 *
 * utils.registerTemplateApkCopy(project)
 * ```
 */
class UtilsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // project.extensions.extraProperties.set("utils", Utils)
        project.extensions.add("utils", Utils)
    }
}
