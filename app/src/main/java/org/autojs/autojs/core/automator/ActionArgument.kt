package org.autojs.autojs.core.automator

import android.os.Bundle

/**
 * Created by Stardust on 2017/3/9.
 */
abstract class ActionArgument private constructor(protected val key: String) {

    abstract fun putIn(bundle: Bundle)

    class IntActionArgument(name: String, private val value: Int) : ActionArgument(name) {

        override fun putIn(bundle: Bundle) = bundle.putInt(key, value)

    }

    class BooleanActionArgument(name: String, private val value: Boolean) : ActionArgument(name) {

        override fun putIn(bundle: Bundle) = bundle.putBoolean(key, value)

    }

    class CharSequenceActionArgument(name: String, private val value: CharSequence) : ActionArgument(name) {

        override fun putIn(bundle: Bundle) = bundle.putCharSequence(key, value)

    }

    class StringActionArgument(name: String, private val value: String) : ActionArgument(name) {

        override fun putIn(bundle: Bundle) = bundle.putString(key, value)

    }

    class FloatActionArgument(name: String, private val value: Float) : ActionArgument(name) {

        override fun putIn(bundle: Bundle) = bundle.putFloat(key, value)

    }

}
