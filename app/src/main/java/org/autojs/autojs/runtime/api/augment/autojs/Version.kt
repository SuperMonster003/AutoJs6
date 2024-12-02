package org.autojs.autojs.runtime.api.augment.autojs

import org.autojs.autojs.annotation.RhinoSingletonFunctionInterface
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs6.BuildConfig
import io.github.g00fy2.versioncompare.Version as InternalVersion

@Suppress("unused")
object Version : Augmentable() {

    private const val NAME = BuildConfig.VERSION_NAME

    override val selfAssignmentProperties = listOf(
        "code" to BuildConfig.VERSION_CODE,
        "name" to NAME,
        "date" to BuildConfig.VERSION_DATE,
    )

    override val selfAssignmentFunctions = listOf(
        ::isHigherThan.name,
        ::isLowerThan.name,
        ::isEqual.name,
        ::isAtLeast.name,
    )

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isHigherThan(args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
        when (it) {
            is InternalVersion -> InternalVersion(NAME).isHigherThan(it)
            else -> InternalVersion(NAME).isHigherThan(coerceString(it))
        }
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isLowerThan(args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
        when (it) {
            is InternalVersion -> InternalVersion(NAME).isLowerThan(it)
            else -> InternalVersion(NAME).isLowerThan(coerceString(it))
        }
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isEqual(args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
        when (it) {
            is InternalVersion -> InternalVersion(NAME).isEqual(it)
            else -> InternalVersion(NAME).isEqual(coerceString(it))
        }
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isAtLeast(args: Array<out Any?>): Boolean = ensureArgumentsLengthInRange(args, 1..2) {
        val (otherVersion, ignoreSuffix) = it
        when (otherVersion) {
            is InternalVersion -> when {
                ignoreSuffix.isJsNullish() -> InternalVersion(NAME).isAtLeast(otherVersion)
                else -> InternalVersion(NAME).isAtLeast(otherVersion, coerceBoolean(ignoreSuffix))
            }
            else -> when {
                ignoreSuffix.isJsNullish() -> InternalVersion(NAME).isAtLeast(coerceString(otherVersion))
                else -> InternalVersion(NAME).isAtLeast(coerceString(otherVersion), coerceBoolean(ignoreSuffix))
            }
        }
    }

}
