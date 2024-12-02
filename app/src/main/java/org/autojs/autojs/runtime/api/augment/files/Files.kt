package org.autojs.autojs.runtime.api.augment.files

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.extension.FlexibleArray
import org.autojs.autojs.pio.PFileInterface
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.exception.ShouldNeverHappenException
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.mozilla.javascript.Context
import java.io.File
import org.autojs.autojs.runtime.api.Files as ApiFiles

@Suppress("unused", "UNUSED_PARAMETER")
class Files(scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime) {

    override val selfAssignmentFunctions = listOf(
        ::open.name to AS_GLOBAL,
        ::join.name,
        ::toFile.name,
    )

    companion object : FlexibleArray() {

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun open(scriptRuntime: ScriptRuntime, args: Array<out Any?>): PFileInterface = ensureArgumentsLengthInRange(args, 1..4) {
            when (it.size) {
                1 -> scriptRuntime.files.open(Context.toString(it[0]))
                2 -> scriptRuntime.files.open(Context.toString(it[0]), Context.toString(it[1]))
                3 -> scriptRuntime.files.open(Context.toString(it[0]), Context.toString(it[1]), Context.toString(it[2]))
                4 -> scriptRuntime.files.open(Context.toString(it[0]), Context.toString(it[1]), Context.toString(it[2]), coerceIntNumber(it[3]))
                else -> throw ShouldNeverHappenException()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun join(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsAtLeast(args, 1) {
            ApiFiles.join(Context.toString(it[0]), *it.drop(1).map { arg -> Context.toString(arg) }.toTypedArray())
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun toFile(scriptRuntime: ScriptRuntime, args: Array<out Any?>): File = ensureArgumentsOnlyOne(args) {
            File(scriptRuntime.files.path(Context.toString(it)))
        }

    }

}