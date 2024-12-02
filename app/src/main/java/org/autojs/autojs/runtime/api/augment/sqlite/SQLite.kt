package org.autojs.autojs.runtime.api.augment.sqlite

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.database.Database
import org.autojs.autojs.core.database.Database.DatabaseCallback
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.AnyExtensions.jsBrief
import org.autojs.autojs.extension.AnyExtensions.jsSanitize
import org.autojs.autojs.extension.AnyExtensions.jsTryToJava
import org.autojs.autojs.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.SQLite.Companion.DEFAULT_READ_ONLY
import org.autojs.autojs.runtime.api.SQLite.Companion.DEFAULT_VERSION
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.NativeObject

class SQLite(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), Invokable {

    override val key = super.key.lowercase()

    override val selfAssignmentFunctions = listOf(
        ::open.name,
    )

    override fun invoke(vararg args: Any?): Database = ensureArgumentsLengthInRange(args, 1..3) { argList ->
        open(scriptRuntime, argList)
    }

    companion object {

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun open(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Database = ensureArgumentsLengthInRange(args, 1..3) { argList ->
            val (name, options, callback) = argList

            val niceName = coerceString(name, "")
            require(niceName.isNotEmpty()) { "Argument name of sqlite.open() must not be empty" }

            val niceOptions = if (options.isJsNullish()) newNativeObject() else options
            require(niceOptions is NativeObject) { "Argument options ${options.jsBrief()} for sqlite.open() must be a JavaScript Object" }

            val niceCallback = callback.jsSanitize()?.jsTryToJava<DatabaseCallback>()
            require(niceCallback is DatabaseCallback?) { "Argument callback ${callback.jsBrief()} for sqlite.open() must be a DatabaseCallback" }

            scriptRuntime.sqlite.open(
                name = niceName,
                version = niceOptions.inquire("version", ::coerceIntNumber, DEFAULT_VERSION),
                readOnly = niceOptions.inquire("readOnly", ::coerceBoolean, DEFAULT_READ_ONLY),
                callback = niceCallback,
            )
        }

    }

}
