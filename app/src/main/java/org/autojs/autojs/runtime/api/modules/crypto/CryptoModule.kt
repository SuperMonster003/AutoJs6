package org.autojs.autojs.runtime.api.modules.crypto

import org.autojs.autojs.runtime.api.modules.ScriptJavaClass
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptPrototype
import org.autojs.autojs.core.crypto.Crypto as CoreCrypto

/**
 * The `crypto` module on the new script module layer (roadmap P3.5): as the current `augment/cryptyo/Crypto`, the module
 * object has the Java classes `Key` and `KeyPair` and the Kotlin object `org.autojs.autojs.core.crypto.Crypto` as its
 * prototype, which provides the documented `digest`, `encrypt`, `decrypt` and `generateKeyPair` (Java methods, with the
 * overloads and conversions of that object). The obsolete JS bound the same four methods on the module object itself.
 *
 * zh-CN: 新脚本模块层上的 crypto 模块 (P3.5): 与现有 augment/cryptyo/Crypto 相同, 模块对象有 Java 类 Key 与 KeyPair, 原型为 Kotlin 对象
 * org.autojs.autojs.core.crypto.Crypto, 由它提供文档所述的 digest, encrypt, decrypt 与 generateKeyPair (Java 方法, 重载与转换照该对象). 旧 JS 把这四个方法绑定在
 * 模块对象自身.
 */
@ScriptModule(name = "crypto")
@ScriptJavaClass(name = "Key", type = CoreCrypto.Key::class)
@ScriptJavaClass(name = "KeyPair", type = CoreCrypto.KeyPair::class)
object CryptoModule {

    @ScriptPrototype
    val prototype: Any
        get() = CoreCrypto

}
