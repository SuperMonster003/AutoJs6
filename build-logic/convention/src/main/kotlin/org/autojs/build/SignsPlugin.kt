@file:Suppress("unused")

package org.autojs.build

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * A Gradle plugin that provides signing functionality.
 *
 * zh-CN: Gradle 签名功能插件.
 *
 * - `id`: "org.autojs.build.signs"
 * - `implementationClass`: "org.autojs.build.SignsPlugin"
 * - `displayName`: "AutoJs6 Signs Plugin"
 * - `description`: "Provides signing helpers."
 *
 * Apply this plugin to your Android module's `build.gradle.kts`:
 *
 * zh-CN: 在 Android 模块的 `build.gradle.kts` 中应用此插件:<br>
 *
 * ```kts
 * plugins {
 *     id("org.autojs.build.signs")
 * }
 *
 * android {
 *   signingConfigs {
 *     if (signs.isValid) {
 *       create(buildTypeRelease) {
 *         storeFile = signs.properties["storeFile"]?.let { file(it as String) }
 *         keyPassword = signs.properties["keyPassword"] as String
 *         keyAlias = signs.properties["keyAlias"] as String
 *         storePassword = signs.properties["storePassword"] as String
 *       }
 *     }
 *   }
 * }
 * ```
 */
class SignsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.add("signs", Utils.newSigns(project))
    }
}
