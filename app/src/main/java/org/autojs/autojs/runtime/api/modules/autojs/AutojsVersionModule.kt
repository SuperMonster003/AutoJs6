package org.autojs.autojs.runtime.api.modules.autojs

import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptProperty
import org.autojs.autojs6.BuildConfig
import org.mozilla.javascript.Wrapper
import io.github.g00fy2.versioncompare.Version as InternalVersion
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * `autojs.version` on the new script module layer (roadmap P3.4): the version code, name and date of AutoJs6 and the
 * comparisons with another version (a `Version` object or its string form; other values are compared by their string, as
 * with the current module). `ignoreSuffix` is a boolean as documented (T4: the current module and the obsolete JS took its
 * truthiness).
 *
 * zh-CN: 新脚本模块层上的 autojs.version (P3.4): AutoJs6 的版本号, 版本名与日期, 以及与另一版本 (Version 对象或其字符串形式; 其他值按字符串比较, 同现有模块)
 * 的比较. ignoreSuffix 按文档为布尔值 (T4: 现有模块与旧 JS 取其真值).
 */
@ScriptModule(name = "version", parent = AutojsModule::class, dollarPrefix = false)
class AutojsVersionModule {

    @ScriptProperty
    val code: Int = BuildConfig.VERSION_CODE

    @ScriptProperty
    val name: String = BuildConfig.VERSION_NAME

    @ScriptProperty
    val date: String = BuildConfig.VERSION_DATE

    @ScriptFunction
    fun isHigherThan(otherVersion: Any): Boolean = when (val other = versionOf(otherVersion)) {
        is InternalVersion -> current.isHigherThan(other)
        else -> current.isHigherThan(other as String)
    }

    @ScriptFunction
    fun isLowerThan(otherVersion: Any): Boolean = when (val other = versionOf(otherVersion)) {
        is InternalVersion -> current.isLowerThan(other)
        else -> current.isLowerThan(other as String)
    }

    @ScriptFunction
    fun isEqual(otherVersion: Any): Boolean = when (val other = versionOf(otherVersion)) {
        is InternalVersion -> current.isEqual(other)
        else -> current.isEqual(other as String)
    }

    @ScriptFunction
    fun isAtLeast(otherVersion: Any, ignoreSuffix: Boolean? = null): Boolean = when (val other = versionOf(otherVersion)) {
        is InternalVersion -> if (ignoreSuffix == null) current.isAtLeast(other) else current.isAtLeast(other, ignoreSuffix)
        else -> if (ignoreSuffix == null) current.isAtLeast(other as String) else current.isAtLeast(other as String, ignoreSuffix)
    }

    private val current: InternalVersion
        get() = InternalVersion(BuildConfig.VERSION_NAME)

    /** A `Version` object as is, any other value as its script string. */
    private fun versionOf(value: Any): Any = when (val unwrapped = if (value is Wrapper) value.unwrap() else value) {
        is InternalVersion -> unwrapped
        else -> RhinoScriptRuntime.toString(value)
    }

}
