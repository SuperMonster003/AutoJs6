package org.autojs.autojs.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ScriptLaunchSourceFactoryJvmTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun fileLaunchRoutesJavaAndKotlinToJvmFileSource() {
        listOf("Main.java", "Main.JAVA", "Main.kt", "Main.KT").forEachIndexed { index, fileName ->
            val file = File(temporaryFolder.newFolder("source-$index"), fileName).apply {
                check(createNewFile())
            }
            val launch = ScriptLaunchSourceFactory.createFileLaunch(
                file = file,
                defaultWorkingDirectory = "fallback",
            )

            assertTrue(fileName, launch.source is JvmFileSource)
            val source = launch.source as JvmFileSource
            assertEquals(file, source.file)
            assertEquals(file.extension, source.extension)
            assertEquals("jvm-source", launch.engineName)
            assertEquals(requireNotNull(file.parentFile).canonicalPath, launch.workingDirectory)
        }
    }

    @Test
    fun pathLaunchRoutesJavaAndKotlinAndHonorsWorkingDirectoryOverride() {
        listOf("Main.JaVa", "Main.Kt").forEach { fileName ->
            val file = temporaryFolder.newFile(fileName)
            val launch = ScriptLaunchSourceFactory.createPathLaunch(
                path = file.path,
                workingDirectory = "explicit-working-directory",
                defaultWorkingDirectory = "fallback",
            )

            assertTrue(fileName, launch.source is JvmFileSource)
            assertEquals(file.extension, launch.source.extension)
            assertEquals("explicit-working-directory", launch.workingDirectory)
        }
    }

    @Test
    fun preExecuteScriptIsRejectedForJvmSources() {
        listOf("Main.java", "Main.KT").forEach { fileName ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                ScriptLaunchSourceFactory.createPathLaunch(
                    path = File(temporaryFolder.root, fileName).path,
                    preExecuteScript = "bootstrap();",
                    defaultWorkingDirectory = "fallback",
                )
            }
            assertTrue(error.message.orEmpty().contains("preExecuteScript"))
        }
    }

    @Test
    fun aliasedJvmPathsCanonicalizeWithoutChangingRouting() {
        // Roadmap v2 M1: aliased prefixes (/sdcard, "child/..") resolve to canonical form;
        // only a routing-class change after canonicalization is rejected.
        val source = temporaryFolder.newFile("Aliased.java")
        File(source.parentFile, "child").mkdir()
        val aliased = File(source.parentFile, "child/../${source.name}")

        val fileLaunch = ScriptLaunchSourceFactory.createFileLaunch(
            file = aliased,
            defaultWorkingDirectory = "fallback",
        )
        assertTrue(fileLaunch.source is JvmFileSource)
        assertEquals(source.canonicalFile, (fileLaunch.source as JvmFileSource).file)

        val pathLaunch = ScriptLaunchSourceFactory.createPathLaunch(
            path = aliased.path,
            defaultWorkingDirectory = "fallback",
        )
        assertTrue(pathLaunch.source is JvmFileSource)
    }

    @Test
    fun canonicalJvmBasenameCannotHideBehindAnotherRequestedType() {
        listOf("Main.java", "Main.kt", "Main.kts", "Main.class", "Main.jar", "Main.dex").forEach { canonicalName ->
            assertTrue(
                canonicalName,
                JvmSourceKind.hasCanonicalJvmRoutingMismatch("alias.js", canonicalName),
            )
        }
        assertTrue(JvmSourceKind.hasCanonicalJvmRoutingMismatch("Alias.kt", "Main.java"))
        assertEquals(false, JvmSourceKind.hasCanonicalJvmRoutingMismatch("Alias.JAVA", "Main.java"))
        assertEquals(false, JvmSourceKind.hasCanonicalJvmRoutingMismatch("alias.js", "main.js"))
    }

    @Test
    fun unresolvedCanonicalRoutingTargetFailsClosedBeforeEngineSelection() {
        val source = object : File(temporaryFolder.root, "main.js") {
            override fun getCanonicalFile(): File = throw java.io.IOException("controlled canonicalization failure")
        }

        assertThrows(UnsafeJvmSourcePathException::class.java) {
            ScriptLaunchSourceFactory.createFileLaunch(
                file = source,
                defaultWorkingDirectory = "fallback",
            )
        }
    }

    @Test
    fun kotlinScriptsAndJvmArtifactsNeverFallBackToOtherEngines() {
        listOf("Main.kts", "Main.KTS", "Main.class", "Main.JAR", "Main.dex", "Main.DEX")
            .forEach { fileName ->
                val file = File(temporaryFolder.root, fileName)
                assertThrows(fileName, UnsupportedJvmSourceException::class.java) {
                    ScriptLaunchSourceFactory.createFileLaunch(
                        file = file,
                        defaultWorkingDirectory = "fallback",
                    )
                }
                assertThrows(fileName, UnsupportedJvmSourceException::class.java) {
                    ScriptLaunchSourceFactory.createPathLaunch(
                        path = file.path,
                        defaultWorkingDirectory = "fallback",
                    )
                }
            }
    }

    @Test
    fun javaScriptAndAutoFileRoutingDoNotRegress() {
        val javaScript = temporaryFolder.newFile("main.js")
        val autoFile = temporaryFolder.newFile("main.auto")

        assertTrue(
            ScriptLaunchSourceFactory.createFileLaunch(
                file = javaScript,
                defaultWorkingDirectory = "fallback",
            ).source is JavaScriptFileSource,
        )
        assertTrue(
            ScriptLaunchSourceFactory.createPathLaunch(
                path = javaScript.path,
                defaultWorkingDirectory = "fallback",
            ).source is JavaScriptFileSource,
        )
        assertTrue(
            ScriptLaunchSourceFactory.createFileLaunch(
                file = autoFile,
                defaultWorkingDirectory = "fallback",
            ).source is AutoFileSource,
        )
    }
}
