package org.autojs.autojs.script

import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs.util.FileUtils
import org.autojs.plugin.autojs4.runtime.api.AutoJs4RuntimeContract
import java.io.File

/**
 * A script that the user explicitly asked to run with the AutoJs4 Runtime plugin ("Run with
 * AutoJs4"). It carries the absolute file path, the AutoJs4 source type and, for project
 * folders, the project directory; the plugin reads the file itself (roadmap D4).
 *
 * zh-CN: 用户通过 "使用 AutoJs4 运行" 显式交给 AutoJs4 Runtime 插件执行的脚本. 持有绝对路径,
 * AutoJs4 源类型以及项目文件夹 (如有); 文件由插件自行读取 (roadmap D4).
 */
class AutoJs4ScriptSource private constructor(
    val file: File,
    val sourceType: String,
    val projectDirectory: File?,
) : ScriptSource(PFiles.getNameWithoutExtension(file.path), PFiles.getExtension(file.name).lowercase()) {

    override val engineName: String
        get() = ENGINE

    val workingDirectory: String
        get() = projectDirectory?.path ?: file.parent ?: file.path

    override fun toString(): String = file.path

    companion object {
        const val ENGINE = "autojs4"

        @JvmStatic
        fun isEligibleFile(file: File): Boolean = file.isFile && (
            JavaScriptSource.isJavaScriptFileName(file.name) || AutoFileSource.isAutoFileName(file.name)
            )

        @JvmStatic
        fun isEligibleProject(directory: File): Boolean = directory.isDirectory && ProjectConfig.isProject(directory)

        @JvmStatic
        fun forFile(file: File): AutoJs4ScriptSource {
            val normalized = file.absoluteFile
            val sourceType = if (AutoFileSource.isAutoFileName(normalized.name)) {
                AutoJs4RuntimeContract.SOURCE_TYPE_AUTO
            } else {
                AutoJs4RuntimeContract.SOURCE_TYPE_JAVASCRIPT
            }
            return AutoJs4ScriptSource(normalized, sourceType, projectDirectory = null)
        }

        /**
         * Resolves the `main` entry of `project.json` like AutoJs4's `ProjectLauncher` does.
         * Returns null when the project has no usable main script.
         */
        @JvmStatic
        fun forProject(directory: File): AutoJs4ScriptSource? {
            val config = ProjectConfig.fromProjectDir(directory.absolutePath) ?: return null
            return resolveProjectMain(directory, config.mainScriptFileName)
        }

        /** Pure part of [forProject]: maps the `main` entry of `project.json` to a source, or null when unusable. */
        @JvmStatic
        fun resolveProjectMain(directory: File, mainScriptFileName: String?): AutoJs4ScriptSource? {
            val normalized = directory.absoluteFile
            val mainName = mainScriptFileName?.takeIf { it.isNotBlank() } ?: return null
            val main = File(normalized, mainName)
            if (!main.isFile) return null
            return AutoJs4ScriptSource(main, AutoJs4RuntimeContract.SOURCE_TYPE_JAVASCRIPT, projectDirectory = normalized)
        }

        @JvmStatic
        fun isAutoJs4FileType(type: FileUtils.TYPE): Boolean =
            type == FileUtils.TYPE.JAVASCRIPT || type == FileUtils.TYPE.AUTO
    }
}
