package org.autojs.autojs.rhino

import android.os.Build
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The converters of [ClassFileDexer] on a device. API 24 / 25 are the API levels this is about: D8 runs there on the
 * `java.nio.file` of `desugar_jdk_libs_nio`, and legacy dx stays as its fallback and as the first converter of generated
 * classes. The jars are the fixtures of `assets/rhino/class-syntax-cases.js` (tools/rhino/build_class_fixtures.py).
 *
 * zh-CN: 设备上的 [ClassFileDexer] 转换器. 重点是 API 24 / 25: D8 在那里依靠 `desugar_jdk_libs_nio` 的 `java.nio.file` 运行, 旧版 dx
 * 保留为其回退及生成类的首选转换器. 所用 jar 是 `assets/rhino/class-syntax-cases.js` 的夹具 (tools/rhino/build_class_fixtures.py).
 */
class ClassFileDexerDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val root = File(instrumentation.targetContext.cacheDir, "class-file-dexer-${System.nanoTime()}").apply { mkdirs() }

    @After
    fun deleteFiles() {
        root.deleteRecursively()
    }

    @Test
    fun loadJarRunsJava8Classes() {
        val jar = fixture("FIXTURE_JAR_JAVA8")
        val loader = AndroidClassLoader(instrumentation.targetContext.classLoader, File(root, "loader"))
        repeat(2) { // The second load comes from the cache of the conversion.
            val type = loader.loadJar(jar).loadClass(JAVA8_CLASS)
            val instance = type.getConstructor(String::class.java).newInstance("java8")
            assertEquals("[hello java8]", type.getMethod("describe").invoke(instance))
        }
    }

    @Test
    fun convertToDexJarConvertsBothClassFileVersions() {
        for (name in listOf("FIXTURE_JAR", "FIXTURE_JAR_JAVA8")) {
            val output = File(root, "$name-out.jar")
            ClassFileDexer.convertToDexJar(fixture(name), output, root)
            assertTrue(name, "classes.dex" in entries(output))
        }
    }

    @Test
    fun generatedClassesOfBothClassFileVersionsConvert() {
        for (name in listOf("FIXTURE_JAR", "FIXTURE_JAR_JAVA8")) {
            val output = File(root, "$name-generated.jar")
            ClassFileDexer.convertGeneratedToDexJar(fixture(name), output, root)
            assertTrue(name, "classes.dex" in entries(output))
        }
    }

    @Test
    fun defineClassAcceptsJava8Classes() {
        val classes = entries(fixture("FIXTURE_JAR_JAVA8"))
        val bytes = classes.getValue("${NAMED_INTERFACE.replace('.', '/')}.class")
        val loader = AndroidClassLoader(instrumentation.targetContext.classLoader, File(root, "loader"))
        val type = loader.defineClass(NAMED_INTERFACE, bytes)
        assertEquals("[text]", type.getMethod("wrap", String::class.java).invoke(null, "text"))
    }

    @Test
    fun orderedClasspathCompilesWithD8() {
        val loader = AndroidClassLoader(instrumentation.targetContext.classLoader, File(root, "loader"))
        val dex = loader.loadJarWithExperimentalCompilerClasspath(fixture("FIXTURE_JAR_JAVA8"), listOf(fixture("FIXTURE_JAR")))
        val type = dex.loadClass(JAVA8_CLASS)
        val instance = type.getConstructor(String::class.java).newInstance("classpath")
        assertEquals("[hello classpath]", type.getMethod("describe").invoke(instance))
    }

    @Test
    fun brokenJarFailsWithoutEndingTheProcess() {
        val jar = File(root, "broken.jar")
        ZipOutputStream(jar.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("org/autojs/Broken.class"))
            out.write(ByteArray(64) { 0x42 })
            out.closeEntry()
        }
        val loader = AndroidClassLoader(instrumentation.targetContext.classLoader, File(root, "loader"))
        val failure = runCatching { loader.loadJar(jar) }.exceptionOrNull()
        assertTrue("the broken jar loaded", failure != null)
        assertFalse("legacy dx hid the failure of D8", failure!!.message.orEmpty().contains("Legacy dx failed"))
    }

    @Test
    fun legacyDxRefusesJava8ClassesOnTheApiLevelsThatKeepIt() {
        assumeTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
        val output = File(root, "legacy.jar")
        ClassFileDexer.convertWithLegacyDx(fixture("FIXTURE_JAR"), output)
        assertTrue("classes.dex" in entries(output))
        val failure = runCatching { ClassFileDexer.convertWithLegacyDx(fixture("FIXTURE_JAR_JAVA8"), output) }.exceptionOrNull()
        assertTrue(failure.toString(), failure is java.io.IOException)
    }

    private fun fixture(name: String): File {
        val cases = instrumentation.context.assets.open(CASES).bufferedReader().use { it.readText() }
        val base64 = checkNotNull(Regex("var $name = '([^']+)'").find(cases)) { "no $name in $CASES" }.groupValues[1]
        return File(root, "$name-${System.nanoTime()}.jar").apply { writeBytes(Base64.decode(base64, Base64.DEFAULT)) }
    }

    private fun entries(jar: File): Map<String, ByteArray> = buildMap {
        ZipInputStream(ByteArrayInputStream(jar.readBytes())).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry -> put(entry.name, zip.readBytes()) }
        }
    }

    private companion object {
        const val CASES = "rhino/class-syntax-cases.js"
        const val JAVA8_CLASS = "org.autojs.rhino.fixture.RhinoClassJava8Fixture"
        const val NAMED_INTERFACE = "org.autojs.rhino.fixture.RhinoClassJava8FixtureNamed"
    }
}
