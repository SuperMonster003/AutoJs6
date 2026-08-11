package org.autojs.autojs.runtime

import java.io.File

/** Thin, testable argument boundary for the explicit ordered-classpath runtime entry. */
internal object ScriptRuntimeJarClasspathRoute {

    fun <T> dispatch(
        programPath: String,
        orderedClasspathPaths: Array<out String>,
        resolvePath: (String) -> String,
        dispatch: (programJar: File, orderedClasspath: List<File>) -> T,
    ): T {
        require(orderedClasspathPaths.isNotEmpty()) { "Ordered classpath must not be empty" }
        val programJar = File(resolvePath(programPath))
        val classpathJars = orderedClasspathPaths.map { path -> File(resolvePath(path)) }
        return dispatch(programJar, classpathJars)
    }
}
