package org.autojs.autojs.runtime.api.modules.crypto

import org.autojs.autojs.runtime.api.augment.cryptyo.Crypto
import org.autojs.autojs.runtime.api.augment.differential.DifferentialCases
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.differential.ObsoleteModuleHost
import org.autojs.autojs.runtime.api.augment.differential.SurfaceDiff
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport
import org.autojs.autojs.runtime.api.modules.ScriptModuleExpectations
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.ScriptableObject
import org.autojs.autojs.core.crypto.Crypto as CoreCrypto

/**
 * Obsolete `__crypto__.js` against `crypto` of the new script module layer (roadmap P3.5). The obsolete module bound
 * `digest` / `encrypt` / `decrypt` / `generateKeyPair` of the Kotlin object `core.crypto.Crypto` on a plain object; the
 * current module and the new layer reach the same methods through that object as the prototype, so the results are the
 * same Java calls (error types compared, D10). The surface is also compared with the current `augment/cryptyo/Crypto`.
 *
 * zh-CN: 旧 __crypto__.js 对新层 crypto (P3.5). 旧模块把 Kotlin 对象 core.crypto.Crypto 的 digest / encrypt / decrypt / generateKeyPair 绑定在普通对象上;
 * 现有模块与新层以该对象为原型访问同样的方法, 因此结果来自同样的 Java 调用 (比较错误类型, D10). 表面另与现有 augment/cryptyo/Crypto 比较.
 */
class CryptoModuleDifferentialTest {

    private lateinit var rhino: DifferentialRhino
    private lateinit var jsCrypto: Any

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        if (ScriptModuleExpectations.RECORDING) jsCrypto = ObsoleteModuleHost(rhino.context, rhino.jsScope).loadAndDefine("crypto")!!
        rhino.mountNewModules("crypto")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    /**
     * Every obsolete member is readable on the new module: `Key` / `KeyPair` as own properties, the four methods through
     * the Java prototype (static methods of the Kotlin object, which Rhino resolves on the object but does not list, so the
     * surface walk only sees them on the obsolete side).
     */
    @Test
    fun obsoleteMembersExistOnTheNewModule() {
        val report = SurfaceDiff.compare(ScriptModuleExpectations.obsoleteSurface("surface") { SurfaceDiff.surface(jsCrypto, rhino.jsScope.globalThis) }, SurfaceDiff.surface(rhino.evalKt("crypto"), rhino.ktScope.globalThis))
        assertTrue(SurfaceDiff.describe(report, "js", "kt"), report.typeMismatches.isEmpty())
        assertEquals(setOf("decrypt", "digest", "encrypt", "generateKeyPair"), report.onlyInFirst)
        report.onlyInFirst.forEach { name -> assertEquals(name, "function", rhino.evalKt("typeof crypto.$name")) }
    }

    @Test
    fun surfaceMatchesTheCurrentModule() {
        HostModuleTestSupport.assertSameSurface(rhino, "crypto") { Crypto.augmentWithRuntime(it.ktScope.globalThis as ScriptableObject, it.ktRuntime, CoreCrypto) }
    }

    @Test
    fun resultsAgree() {
        DifferentialCases.run(rhino, EXPRESSIONS, emptyList(), exportDir = "build/script-modules-device", exportAll = true)
    }

    companion object {

        private const val KEY = "new crypto.Key('0123456789abcdef')"

        private val EXPRESSIONS = listOf(
            "typeof crypto",
            "[typeof crypto.digest, typeof crypto.encrypt, typeof crypto.decrypt, typeof crypto.generateKeyPair]",
            "String(crypto.Key) === String(org.autojs.autojs.core.crypto.Crypto.Key)",
            "crypto.digest('abc')",
            "crypto.digest('abc', 'SHA-256')",
            "crypto.digest('abc', 'SHA-1', { output: 'base64' })",
            "crypto.digest('abc', { output: 'hex' })",
            "crypto.digest([97, 98, 99], 'MD5', { input: 'bytes' })",
            "crypto.digest('YWJj', 'MD5', { input: 'base64' })",
            "Array.from(crypto.digest('abc', 'MD5', { output: 'bytes' })).length",
            "crypto.digest('abc', 'NO-SUCH-ALGORITHM')",
            "crypto.digest()",
            "crypto.encrypt('hello', $KEY, 'AES/ECB/PKCS5Padding', { output: 'base64' })",
            "crypto.decrypt(crypto.encrypt('hello', $KEY, 'AES/ECB/PKCS5Padding', { output: 'base64' }), $KEY, 'AES/ECB/PKCS5Padding', { input: 'base64', output: 'string' })",
            "crypto.decrypt(crypto.encrypt('hello', $KEY, 'AES/CBC/PKCS5Padding', { iv: 'abcdefghijklmnop', output: 'hex' }), $KEY, 'AES/CBC/PKCS5Padding', { iv: 'abcdefghijklmnop', input: 'hex', output: 'string' })",
            "crypto.encrypt('hello', 1, 'AES/ECB/PKCS5Padding')",
            "crypto.encrypt('hello', $KEY, 'NO/SUCH/TRANSFORMATION')",
            "(function (pair) { return [String(pair.publicKey.keyPair), String(pair.privateKey.keyPair), pair.publicKey.data.length > 0]; })(crypto.generateKeyPair('RSA', 1024))",
            "crypto.generateKeyPair('NO-SUCH')",
        )

    }

}
