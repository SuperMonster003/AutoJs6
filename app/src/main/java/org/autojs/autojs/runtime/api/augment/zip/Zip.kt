package org.autojs.autojs.runtime.api.augment.zip

import okhttp3.internal.closeQuietly
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component1
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component2
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component3
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.StringUtils.toFile
import org.mozilla.javascript.NativeObject
import java.io.File
import java.time.LocalDateTime.now
import java.time.format.DateTimeFormatter.ofPattern
import java.util.UUID.randomUUID

class Zip(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), Invokable {

    override val key = super.key.lowercase()

    override val selfAssignmentFunctions = listOf(
        ::open.name,
        ::zipFile.name,
        ::zipDir.name,
        ::zipFiles.name,
        ::unzip.name,
    )

    override fun invoke(vararg args: Any?): ZipNativeObject = ensureArgumentsOnlyOne(args) { zipPath ->
        open(scriptRuntime, arrayOf(zipPath))
    }

    companion object : ArgumentGuards() {

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun open(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ZipNativeObject = ensureArgumentsLengthInRange(args, 1..2) { argList ->
            val (zipPath, options) = argList
            ZipNativeObject(scriptRuntime, ::open.name, zipPath, options)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun zipFile(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ZipNativeObject = ensureArgumentsLengthInRange(args, 2..3) { argList ->
            var (filePath, destZipPath, options) = argList
            val fileToZip = scriptRuntime.files.nonNullPath(coerceString(filePath)).toFile()
            if (argList.size == 2 && destZipPath is NativeObject) {
                options = destZipPath
                destZipPath = null
            }
            val pathString = when {
                !destZipPath.isJsNullish() -> coerceString(destZipPath)
                else -> "${fileToZip.nameWithoutExtension}.zip"
            }
            ZipNativeObject(scriptRuntime, ::zipFile.name, pathString, options).tryWithSelf {
                zipFile.addFile(fileToZip, zipParameters)
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun zipDir(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ZipNativeObject = ensureArgumentsLengthInRange(args, 2..3) { argList ->
            var (filePath, destZipPath, options) = argList
            val fileToZip = scriptRuntime.files.nonNullPath(coerceString(filePath)).toFile()
            if (argList.size == 2 && destZipPath is NativeObject) {
                options = destZipPath
                destZipPath = null
            }
            val pathString = when {
                !destZipPath.isJsNullish() -> coerceString(destZipPath)
                else -> "${fileToZip.nameWithoutExtension}.zip"
            }
            ZipNativeObject(scriptRuntime, ::zipDir.name, pathString, options).tryWithSelf {
                zipFile.createSplitZipFileFromFolder(fileToZip, zipParameters, false, -1)
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun zipFiles(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ZipNativeObject = ensureArgumentsLengthInRange(args, 2..3) { argList ->
            var (filePathList, destZipPath, options) = argList
            require(filePathList is Iterable<*>) {
                "Argument \"filePathList\" ${filePathList.jsBrief()} for zip.zipFiles must be an Iterable"
            }
            val fileList = mutableListOf<File>()
            filePathList.forEach { filePath ->
                val file = scriptRuntime.files.nonNullPath(coerceString(filePath)).toFile()
                require(file.exists()) {
                    "File ${file.absolutePath} does not exist"
                }
                fileList.add(file)
            }
            if (argList.size == 2 && destZipPath is NativeObject) {
                options = destZipPath
                destZipPath = null
            }
            val zipDestinationPath = when {
                !destZipPath.isJsNullish() -> coerceString(destZipPath)
                fileList.size == 1 -> "${fileList.first().nameWithoutExtension}.zip"
                else -> fileList.map {
                    if (it.isFile) it.parent else it.absolutePath
                }.toSet().singleOrNull()?.toFile()?.nameWithoutExtension?.let { "$it.zip" } ?: randomZipName()
            }
            ZipNativeObject(scriptRuntime, ::zipFiles.name, zipDestinationPath, options).tryWithSelf {
                zipFile.addFiles(fileList, zipParameters)
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun unzip(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ZipNativeObject = ensureArgumentsLengthInRange(args, 2..3) { argList ->
            var (zipPath, destPath, options) = argList
            if (argList.size == 2 && destPath is NativeObject) {
                options = destPath
                destPath = null
            }
            ZipNativeObject(scriptRuntime, ::unzip.name, zipPath, options).tryWithSelf {
                zipFile.extractAll(scriptRuntime.files.nonNullPath(coerceString(destPath, "")), unzipParameters)
            }
        }

        private fun randomZipName(): String {
            val ts = now().format(ofPattern("yyyyMMdd-HHmmss"))
            val rand = "${randomUUID()}".substring(0, 4).uppercase()
            return "${ts}-${rand}.zip"
        }

        private fun ZipNativeObject.tryWithSelf(func: ZipNativeObject.() -> Unit): ZipNativeObject {
            runCatching { func() }.getOrElse { t -> zipFile.closeQuietly(); throw t }
            return this
        }

    }

}
