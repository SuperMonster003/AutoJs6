package org.autojs.autojs.runtime.api.augment.cryptyo

import org.autojs.autojs.runtime.api.augment.Augmentable
import kotlin.reflect.KClass
import org.autojs.autojs.core.crypto.Crypto as CoreCrypto

object Crypto : Augmentable() {

    override val selfAssignmentJavaClasses = listOf<Pair<String, KClass<*>>>(
        "Key" to CoreCrypto.Key::class,
        "KeyPair" to CoreCrypto.KeyPair::class,
    )

}