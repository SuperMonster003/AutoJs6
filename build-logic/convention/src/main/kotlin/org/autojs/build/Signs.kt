package org.autojs.build

import org.gradle.api.Project
import java.io.File
import java.util.*

class Signs @JvmOverloads constructor(project: Project, filePath: String = "${project.rootDir}/sign.properties") {

    var isValid = false
        private set

    val properties = Properties().also { props ->
        File(filePath).takeIf { it.exists() }?.let { file ->
            file.inputStream().use { props.load(it) }
            isValid = props.isNotEmpty()
        }
    }

}