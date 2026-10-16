package org.autojs.autojs.core.ui.inflater

import android.view.View

/**
 * Created by Stardust on Nov 4, 2017.
 * Transformed by SuperMonster003 on May 21, 2023.
 */
object Exceptions {

    val NO_EXCEPTION = RuntimeException()

    fun unsupported(v: View, name: String?, value: String?) {
        throw UnsupportedAttributeException(
            "${v.javaClass.simpleName}: { name: $name, value: $value } is not supported"
        )
    }
}

/**
 * A registered attribute that its handler declares unsupported; the inflater then tries the attribute fallback
 * (host roadmap `docs/dev/android-xml-attributes-roadmap.md` A2).
 */
class UnsupportedAttributeException(message: String) : UnsupportedOperationException(message)