package org.autojs.autojs.script

import org.autojs.autojs.engine.NodeProjectRunner
import java.io.File

object NodeProjectScriptSourceFactory {

    @JvmStatic
    fun create(path: String): JavaScriptFileSource = create(File(path))

    @JvmStatic
    fun create(file: File): JavaScriptFileSource = createInternal(file = file, name = null)

    @JvmStatic
    fun create(name: String, file: File): JavaScriptFileSource = createInternal(file = file, name = name)

    private fun createInternal(file: File, name: String?): JavaScriptFileSource {
        val normalizedFile = file.normalizedCanonicalFile()
        val projectRoot = JavaScriptFileSource(normalizedFile)
            .nodeProjectRoot
            ?.normalizedCanonicalFile()
            ?: return createFileSource(normalizedFile, name)
        val entryFile = runCatching {
            NodeProjectRunner
                .resolveProjectEntryFileIfNodeProject(projectRoot)
                ?.normalizedCanonicalFile()
        }.getOrNull() ?: return createFileSource(normalizedFile, name)
        if (!normalizedFile.isSameFile(entryFile)) {
            return createFileSource(normalizedFile, name)
        }
        return createProjectEntry(projectRoot, entryFile, name ?: normalizedFile.nameWithoutExtension)
    }

    @JvmStatic
    @JvmOverloads
    fun createProjectEntry(
        projectRoot: File,
        entryFile: File,
        name: String = entryFile.nameWithoutExtension,
    ): JavaScriptFileSource {
        val normalizedProjectRoot = projectRoot.normalizedCanonicalFile()
        val normalizedEntryFile = entryFile.normalizedCanonicalFile()
        val launchOptions = NodeProjectLaunchDescriptor.readOptionsOrDefault(normalizedProjectRoot)
        return createProjectLaunch(normalizedProjectRoot, normalizedEntryFile, name, launchOptions)
    }

    @JvmStatic
    @JvmOverloads
    fun createProjectLaunch(
        projectRoot: File,
        entryFile: File,
        name: String = entryFile.nameWithoutExtension,
        launchOptions: NodeProjectLaunchOptions = NodeProjectLaunchOptions(),
    ): JavaScriptFileSource {
        val normalizedProjectRoot = projectRoot.normalizedCanonicalFile()
        val normalizedEntryFile = entryFile.normalizedCanonicalFile()
        return NodeProjectJavaScriptFileSource(normalizedProjectRoot, normalizedEntryFile, name, launchOptions)
    }

    private fun createFileSource(file: File, name: String?): JavaScriptFileSource {
        return if (name == null) {
            JavaScriptFileSource(file)
        } else {
            JavaScriptFileSource(name, file)
        }
    }

    private fun File.normalizedCanonicalFile(): File {
        return runCatching { canonicalFile }.getOrElse { absoluteFile }
    }

    private fun File.isSameFile(other: File): Boolean {
        return normalizedCanonicalFile().path == other.normalizedCanonicalFile().path
    }
}
