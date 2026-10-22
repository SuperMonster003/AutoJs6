package org.autojs.autojs.runtime.api.modules.zip

import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.zip.Zip
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport.DeviceCases
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mozilla.javascript.ScriptableObject
import java.io.File

/**
 * Roadmap P3.5: `zip` on the new script module layer. There is no obsolete JS (6.7.0), so the module is compared with
 * the current `augment/zip/Zip` in the same JVM (surface; the results of the same expressions, Zip4j included, in a
 * working directory of their own), the argument errors are checked with the new types (D10), and every case goes to
 * `build/script-modules-device/ZipModuleTest.json` for the device. A stub `files.cwd()` gives the JVM side the working
 * directory the device script has.
 *
 * zh-CN: P3.5: 新层的 zip. 没有旧 JS (6.7.0), 因此在同一 JVM 中与现有 augment/zip/Zip 比较 (表面; 同一批表达式的结果, 含 Zip4j, 各用独立的工作目录),
 * 以新的错误类型检查参数错误 (D10), 全部用例写入 build/script-modules-device/ZipModuleTest.json 供真机复核. JVM 一侧以 stub 的 files.cwd() 提供与设备脚本
 * 相同含义的工作目录.
 */
class ZipModuleTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        prepare(rhino, temp.newFolder("new"))
        rhino.mountNewModules("zip")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    private fun prepare(side: DifferentialRhino, cwd: File) {
        HostModuleTestSupport.useWorkingDirectory(side.ktRuntime, cwd)
        side.evalKt("globalThis.files = { cwd: function () { return ${HostModuleTestSupport.json(cwd.path)}; } };")
    }

    private fun mountCurrent(side: DifferentialRhino) {
        Zip(side.ktRuntime).augment(side.ktScope.globalThis as ScriptableObject)
    }

    @Test
    fun surfaceMatchesTheCurrentModule() {
        HostModuleTestSupport.assertSameSurface(rhino, "zip", { prepare(it, temp.newFolder()) }, ::mountCurrent)
        HostModuleTestSupport.assertSameSurface(rhino, "zip.open('handle.zip')", { prepare(it, temp.newFolder()) }, ::mountCurrent)
    }

    /** The same expressions on the current module: equal results, except the argument errors with their new types. */
    @Test
    fun resultsMatchTheCurrentModule() {
        // The current module cannot touch files on the JVM (its paths go through StringUtils, which needs Android), so
        // only the cases without files are compared here; the device checks the rest against the same values.
        val compared = HostModuleTestSupport.assertSameResults(rhino, RESULTS.map { it.first }, KNOWN, { prepare(it, temp.newFolder()) }, ::mountCurrent)
        assertTrue("compared $compared", compared >= 7)
        HostModuleTestSupport.check(rhino, RESULTS)
        DEVICE.add(RESULTS)
    }

    @Test
    fun argumentErrors() {
        HostModuleTestSupport.check(rhino, ERRORS)
        DEVICE.add(ERRORS)
    }

    companion object {

        private val DEVICE = DeviceCases("ZipModuleTest", "new script module layer (P3.5)")

        /** A file `aj6-p35-zip/<name>` under the script's working directory with [text], created through java.io. */
        private fun source(name: String, text: String) =
            "(function () { var d = new java.io.File(files.cwd(), 'aj6-p35-zip'); d.mkdirs(); var f = new java.io.File(d, '$name'); " +
                "var w = new java.io.FileWriter(f); w.write('$text'); w.close(); return f; })()"

        /** Runs [body] with the handle `h` and closes its Zip4j file afterwards. */
        private fun withHandle(call: String, body: String) =
            "(function () { var h = $call; try { return $body; } finally { h.getZipFile().close(); } })()"

        private const val EXISTS = "new java.io.File(files.cwd(), 'aj6-p35-zip/out/a.txt').exists()"

        private val RESULTS = listOf(
            "typeof zip" to "\"function\"",
            "[typeof zip.open, typeof zip.zipFile, typeof zip.zipDir, typeof zip.zipFiles, typeof zip.unzip]" to "[\"function\",\"function\",\"function\",\"function\",\"function\"]",
            withHandle("zip('handle.zip')", "[h.name, h.isValidZipFile(), String(h).split('\\n')[0]]") to "[\"open\",false,\"Zip {\"]",
            withHandle("zip.open('handle.zip', { compressionLevel: 'MAXIMUM' })", "[h.name, String(h.zipParameters.getCompressionLevel())]") to "[\"open\",\"MAXIMUM\"]",
            withHandle("zip.open('handle.zip', null)", "String(h.zipParameters.getEncryptionMethod())") to "\"AES\"",
            "(${source("a.txt", "hello zip")}, " + withHandle(
                "zip.zipFile('aj6-p35-zip/a.txt', 'aj6-p35-zip/a.zip')",
                "[h.name, h.isValidZipFile(), h.getFileHeaders().length, String(h.getFileHeaders()[0].getFileName()), h.isEncrypted()]",
            ) + ")" to "[\"zipFile\",true,1,\"a.txt\",false]",
            "(${source("a.txt", "hello zip")}, " + withHandle(
                "zip.zipFile('aj6-p35-zip/a.txt', { password: 'secret' })",
                "[h.isEncrypted(), String(new java.io.File(h.path).getName())]",
            ) + ")" to "[true,\"a.zip\"]",
            "(${source("a.txt", "hello zip")}, " + withHandle(
                "zip.zipFile('aj6-p35-zip/a.txt', undefined, { fileComment: 'c' })",
                "String(new java.io.File(h.path).getName())",
            ) + ")" to "\"a.zip\"",
            "(${source("a.txt", "hello zip")}, ${source("b.txt", "hello b")}, " + withHandle(
                "zip.zipFiles(['aj6-p35-zip/a.txt', 'aj6-p35-zip/b.txt'], null)",
                "[h.name, h.getFileHeaders().length, String(new java.io.File(h.path).getName())]",
            ) + ")" to "[\"zipFiles\",2,\"aj6-p35-zip.zip\"]",
            "(${source("a.txt", "hello zip")}, " + withHandle(
                "zip.zipFiles(java.util.Arrays.asList('aj6-p35-zip/a.txt'), 'aj6-p35-zip/list.zip')",
                "h.getFileHeaders().length",
            ) + ")" to "1",
            "(${source("a.txt", "hello zip")}, new java.io.File(files.cwd(), 'dir.zip').delete(), " + withHandle(
                "zip.zipDir('aj6-p35-zip', 'dir.zip', { compressionLevel: 9 })",
                "[h.name, h.getFileHeaders().length > 0]",
            ) + ")" to "[\"zipDir\",true]",
            "(${source("a.txt", "hello zip")}, zip.zipFile('aj6-p35-zip/a.txt', 'aj6-p35-zip/u.zip').getZipFile().close(), " + withHandle(
                "zip.unzip('aj6-p35-zip/u.zip', 'aj6-p35-zip/out')",
                "[h.name, $EXISTS]",
            ) + ")" to "[\"unzip\",true]",
            "(${source("a.txt", "hello zip")}, zip.zipFile('aj6-p35-zip/a.txt', 'aj6-p35-zip/p.zip', { password: 'secret' }).getZipFile().close(), " + withHandle(
                "zip.unzip('aj6-p35-zip/p.zip', 'aj6-p35-zip/out', { password: 'secret' })",
                EXISTS,
            ) + ")" to "true",
            "zip.zipFile('aj6-p35-zip/no-such-file.txt', null)" to "throws JavaException ZipException",
            "zip.zipFiles(['aj6-p35-zip/no-such-file.txt'], null)" to "throws JavaException IllegalArgumentException",
            "zip.open('handle.zip', { aesKeyStrength: 'nope' })" to "throws JavaException IllegalArgumentException",
            "zip.unzip('aj6-p35-zip/missing.zip', 'aj6-p35-zip/out')" to "throws JavaException ZipException",
        )

        private const val TYPE = "throws TypeError"
        private const val RANGE = "throws RangeError"

        /** The argument errors whose current type differs (WrappedIllegalArgumentException, IllegalArgumentException). */
        private val ERRORS = listOf(
            "zip()" to TYPE,
            "zip(null)" to TYPE,
            "zip('')" to RANGE,
            "zip('  ')" to RANGE,
            "zip('a.zip', {})" to TYPE,
            "zip.open()" to TYPE,
            "zip.open(undefined)" to TYPE,
            "zip.open('a.zip', 5)" to TYPE,
            "zip.open('a.zip', [])" to TYPE,
            "zip.open('a.zip', function () {})" to TYPE,
            "zip.open('a.zip', {}, 1)" to TYPE,
            "zip.zipFile('aj6-p35-zip/a.txt')" to TYPE,
            "zip.zipFile('aj6-p35-zip/a.txt', [])" to TYPE,
            "zip.zipFile('aj6-p35-zip/a.txt', 'a.zip', 5)" to TYPE,
            "zip.zipFile('aj6-p35-zip/a.txt', 'a.zip', {}, 1)" to TYPE,
            "zip.zipFile('', null)" to RANGE,
            "zip.zipFile(null, null)" to TYPE,
            "zip.zipDir('aj6-p35-zip')" to TYPE,
            "zip.zipDir(' ', null)" to RANGE,
            "zip.zipFiles('aj6-p35-zip/a.txt', null)" to TYPE,
            "zip.zipFiles({}, null)" to TYPE,
            "zip.zipFiles([null], null)" to TYPE,
            "zip.zipFiles([''], null)" to RANGE,
            "zip.zipFiles(['aj6-p35-zip/a.txt'])" to TYPE,
            "zip.unzip('a.zip')" to TYPE,
            "zip.unzip(null, null)" to TYPE,
            "zip.unzip('a.zip', null, 5)" to TYPE,
            "zip.unzip('a.zip', [], {})" to TYPE,
        )

        /** Results of [RESULTS] that differ from the current module, with the reason. */
        private val KNOWN = emptyMap<String, Pair<String, String>>()

    }

}
