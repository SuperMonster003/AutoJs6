package org.autojs.autojs.runtime.api.augment.nanoid

import org.autojs.autojs.annotation.RhinoSingletonFunctionInterface
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import java.security.SecureRandom

object NanoID : Augmentable(), Invokable {

    override val key = super.key.lowercase()

    @RhinoSingletonFunctionInterface
    override fun invoke(args: Array<out Any?>): String = ensureArgumentsAtMost(args, 1) { argList ->
        val (o) = argList
        val id = coerceIntNumber(o, 21)

        // Alphabet used for nanoid generation
        @Suppress("SpellCheckingInspection")
        val urlAlphabet = "useandom-26T198340PX75pxJACKVERYMINDBUSHWOLF_GQZbfghjklqvwyzrict"

        // Nanoid function using the custom getRandomValues
        val pool = getRandomValues(id)
        (0 until id).map { urlAlphabet[pool[it].toInt() and 63] }.joinToString("")
    }

    private fun getRandomValues(size: Int) = ByteArray(size).apply { SecureRandom().nextBytes(this) }

}