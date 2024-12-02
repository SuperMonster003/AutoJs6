package org.autojs.autojs.runtime.api.augment.autojs

import org.autojs.autojs.rhino.ProxyObject
import org.autojs.autojs.rhino.ProxyObject.Companion.PROXY_GETTER_KEY
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import java.util.function.Supplier
import android.Manifest
import org.autojs.autojs6.BuildConfig
import android.content.pm.PackageManager
import org.autojs.autojs.util.RootUtils
import org.autojs.autojs.util.RootUtils.RootMode
import android.provider.Settings
import android.provider.Settings.System
import org.autojs.autojs.annotation.RhinoSingletonFunctionInterface
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.FlexibleArray
import org.autojs.autojs.extension.ScriptableExtensions.prop
import org.autojs.autojs.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.runtime.api.ScreenMetrics
import org.autojs.autojs.runtime.api.augment.util.Util.checkStringArgument
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.autojs.autojs6.R
import org.mozilla.javascript.Context
import org.mozilla.javascript.Undefined
import java.util.*
import android.content.Context as AndroidContext

class Autojs(private val scriptRuntime: ScriptRuntime) : Augmentable() {

    private val mCachedR = mutableMapOf<String, NativeObject>()

    private val mResourceGetter = Supplier<Any?> {
        val scope = scriptRuntime.topLevelScope
        val getter = newBaseFunction(PROXY_GETTER_KEY, { argList ->
            ensureArgumentsOnlyOne(argList) { type ->
                val niceType = coerceString(type).replace("strings", "string", true)
                when {
                    mCachedR.contains(niceType) -> mCachedR[niceType]
                    else -> getProxyObjectInstance(scope, newBaseFunction(PROXY_GETTER_KEY, { argList ->
                        val name = coerceString(argList[0])
                        val ctx: AndroidContext = scope.prop("activity") as? AndroidContext ?: globalContext
                        ctx.resources.getIdentifier(name, niceType, ctx.packageName)
                    }, NOT_CONSTRUCTABLE)).also { mCachedR[niceType] = it }
                }
            }
        }, NOT_CONSTRUCTABLE)
        getProxyObjectInstance(scope, getter)
    }

    override val selfAssignmentProperties = listOf(
        "versionCode" to BuildConfig.VERSION_CODE,
        "versionName" to BuildConfig.VERSION_NAME,
        "versionDate" to BuildConfig.VERSION_DATE,
        "name" to globalContext.getString(R.string.app_name),
        "packageName" to globalContext.packageName,
    )

    override val selfAssignmentFunctions = listOf(
        ::isScreenPortrait.name,
        ::isScreenLandscape.name,
        ::isRootAvailable.name,
        ::getRootMode.name,
        ::setRootMode.name,
        ::canModifySystemSettings.name,
        ::canWriteSecureSettings.name,
        ::canDisplayOverOtherApps.name,
        ::getLanguage.name,
        ::getLanguageTag.name,
    )

    override val selfAssignmentGetters = listOf<Pair<String, Supplier<Any?>>>(
        "R" to mResourceGetter,
        "rotation" to Supplier { ScreenMetrics.rotation },
        "orientation" to Supplier { ScreenMetrics.orientation },
        "themeColor" to Supplier { ThemeColorManager.currentThemeColor },
    )

    override val globalAssignmentGetters = listOf(
        "R" to mResourceGetter,
    )

    private fun getProxyObjectInstance(scope: Scriptable, getter: BaseFunction): ProxyObject {
        return ProxyObject(scope, newNativeObject().also { o ->
            o.defineProp(PROXY_GETTER_KEY, getter)
        })
    }

    companion object : FlexibleArray() {

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun isScreenPortrait(args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            ScreenMetrics.isScreenPortrait
        }

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun isScreenLandscape(args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            ScreenMetrics.isScreenLandscape
        }

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun isRootAvailable(args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            RootUtils.isRootAvailable()
        }

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun getRootMode(args: Array<out Any?>): RootMode = ensureArgumentsIsEmpty(args) {
            RootUtils.getRootMode()
        }

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun setRootMode(args: Array<out Any?>): Undefined = ensureArgumentsLengthInRange(args, 1..2) {
            val (modeArg, isWriteIntoPrefArg) = it

            val isWriteIntoPref = when {
                isWriteIntoPrefArg.isJsNullish() -> false
                isWriteIntoPrefArg is Boolean -> isWriteIntoPrefArg
                isWriteIntoPrefArg is String -> checkStringArgument(arrayOf(isWriteIntoPrefArg, "write_into_pref"))
                else -> false
            }

            fun setForceRoot() = RootUtils.setRootMode(RootMode.FORCE_ROOT, isWriteIntoPref)
            fun setForceNonRoot() = RootUtils.setRootMode(RootMode.FORCE_NON_ROOT, isWriteIntoPref)
            fun setAutoDetect() = RootUtils.setRootMode(RootMode.AUTO_DETECT, isWriteIntoPref)

            when (modeArg) {
                is Number -> when (coerceIntNumber(modeArg)) {
                    1 -> setForceRoot()
                    0 -> setForceNonRoot()
                    -1 -> setAutoDetect()
                }
                is Boolean -> when (modeArg) {
                    true -> setForceRoot()
                    false -> setForceNonRoot()
                }
                is String -> when {
                    checkStringArgument(arrayOf(modeArg, "root")) -> setForceRoot()
                    checkStringArgument(arrayOf(modeArg, "non-root")) -> setForceNonRoot()
                    checkStringArgument(arrayOf(modeArg, "auto")) -> setAutoDetect()
                    else -> throw WrappedIllegalArgumentException(
                        "Unknown mode (${modeArg}) for setRootMode()"
                    )
                }
                else -> when (Context.toBoolean(modeArg)) {
                    true -> throw WrappedIllegalArgumentException(
                        "Unknown mode (${modeArg}) for setRootMode(). Did you mean to use true or 1 or 'root' to forcibly set root mode?"
                    )
                    else -> throw WrappedIllegalArgumentException(
                        "Unknown mode (${modeArg}) for setRootMode(). Did you mean to use false or 0 or 'non-root' to forcibly set non-root mode?`"
                    )
                }
            }
            UNDEFINED
        }

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun canModifySystemSettings(args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            System.canWrite(globalContext)
        }

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun canWriteSecureSettings(args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            globalContext.checkCallingOrSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
        }

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun canDisplayOverOtherApps(args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            Settings.canDrawOverlays(globalContext)
        }

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun getLanguage(args: Array<out Any?>): Locale = ensureArgumentsIsEmpty(args) {
            org.autojs.autojs.core.pref.Language.getPrefLanguage().locale
        }

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun getLanguageTag(args: Array<out Any?>): String = ensureArgumentsIsEmpty(args) {
            getLanguage(arrayOf()).toLanguageTag()
        }

    }

}
