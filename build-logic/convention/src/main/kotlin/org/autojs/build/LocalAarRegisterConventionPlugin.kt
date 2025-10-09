@file:Suppress("unused")

package org.autojs.build

import org.gradle.api.Plugin
import org.gradle.api.Project

open class LocalAarExtension {
    val files = mutableListOf<String>()
    var configurationName: String? = null
}

class LocalAarRegisterConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val ext = project.extensions.create("localAars", LocalAarExtension::class.java)
        project.afterEvaluate {
            val configName = ext.configurationName ?: "default"
            configurations.maybeCreate(configName)
            ext.files.forEach { path ->
                val aar = file(path)
                require(aar.exists()) { "File not found: \"$path\"" }
                artifacts.add(configName, aar)
            }
        }
    }
}
