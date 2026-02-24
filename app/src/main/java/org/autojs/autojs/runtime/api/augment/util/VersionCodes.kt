package org.autojs.autojs.runtime.api.augment.util

import org.autojs.autojs.annotation.RhinoSingletonFunctionInterface
import org.autojs.autojs.annotation.RhinoStandardFunctionInterface
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component1
import org.autojs.autojs.rhino.extension.IterableExtensions.toNativeArray
import org.autojs.autojs.rhino.extension.NumberExtensions.jsString
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.newNativeArray
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeDate
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptRuntime.wrapNumber
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject.DONTENUM
import org.mozilla.javascript.ScriptableObject.PERMANENT
import org.mozilla.javascript.ScriptableObject.READONLY
import java.util.*

@Suppress("unused", "UNUSED_PARAMETER")
object VersionCodes : Augmentable() {

    override val selfAssignmentFunctions = listOf(
        "toString" to AS_LITERAL_TO_STRING,
        ::search.name,
        ::searchAll.name,
        ::summary.name,
    )

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun search(args: Array<out Any?>) = ensureArgumentsOnlyOne(args) {
        Searcher.search(it)
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun searchAll(args: Array<out Any?>) = ensureArgumentsOnlyOne(args) {
        Searcher.searchAll(it)
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun summary(args: Array<out Any?>): String = ensureArgumentsAtMost(args, 1) {
        val (arg) = it
        val isInDetail = if (arg.isJsNullish()) false else Context.toBoolean(arg)

        VersionCodesInfo.list.joinToString("\n") { info ->
            when {
                isInDetail -> arrayOf(
                    info.versionCode,
                    info.apiLevel,
                    info.releaseName,
                    info.platformVersion,
                    info.internalCodename,
                    info.releaseDate,
                    info.releaseTimestamp,
                ).joinToString(", ")
                else -> "${info.versionCode}: ${info.apiLevel} / ${info.releaseName} / ${info.platformVersion}"
            }
        }
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun toString(args: Array<out Any?>): String {
        return summary(args)
    }

    class Info(
        val versionCode: String,
        val releaseName: String,
        val internalCodename: String,
        val platformVersion: String,
        val apiLevel: String,
        val releaseDate: String,
    ) {
        private val releaseTimestampLong = parseTimestamp(releaseDate)
        val releaseTimestamp = releaseTimestampLong.jsString

        fun toNativeObject() = newNativeObject().also {
            it.defineProperty("versionCode", versionCode, READONLY or PERMANENT)
            it.defineProperty("apiLevel", apiLevel.toInt(), READONLY or PERMANENT)
            it.defineProperty("releaseName", releaseName, READONLY or PERMANENT)
            it.defineProperty("platformVersion", platformVersion, READONLY or PERMANENT)
            it.defineProperty("internalCodename", internalCodename, READONLY or PERMANENT)
            it.defineProperty("releaseDate", releaseDate, READONLY or PERMANENT)
            it.defineProperty("releaseTimestamp", releaseTimestampLong, READONLY or PERMANENT)
            it.defineFunctionProperties(arrayOf("valueOf"), javaClass, READONLY or DONTENUM or PERMANENT)
        }

        private fun parseTimestamp(s: String): Long {
            val replaceQuarter = s.replace(Regex("^Q([1-4]), (\\d+)$")) {
                val (_, quarter, year) = it.groupValues
                val month = when (quarter) {
                    "1" -> "Jan"
                    "2" -> "Apr"
                    "3" -> "Jul"
                    "4" -> "Oct"
                    else -> throw WrappedIllegalArgumentException("Invalid quarter: $quarter which must be within 1..4")
                }
                val day = 1
                "$month $day, $year"
            }
            val replaceFullDate = replaceQuarter.replace(Regex("^(\\w{3})\\w* (\\d\\d?), (\\d+)$")) {
                val (_, month, day, year) = it.groupValues
                "$year,${monthMap[month.lowercase().slice(0..2)]!!.minus(1)},$day"
            }
            val (year, month, day) = replaceFullDate.split(',').map { it.toIntOrNull() ?: return@parseTimestamp 0 }
            return Calendar.getInstance().apply {
                clear()
                set(year, month, day)
            }.timeInMillis
        }

        companion object : ArgumentGuards() {

            private val monthMap: Map<String, Int> = mapOf(
                "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
                "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
            )

            @JvmStatic
            @RhinoStandardFunctionInterface
            fun valueOf(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Any? {
                return thisObj.prop("apiLevel")
            }

        }

    }

    internal object Searcher {

        fun search(o: Any?): NativeObject? = when (o) {
            null -> null
            else -> VersionCodesInfo.list
                .find(getPredictor(normalizeSearcherSource(o)))
                ?.toNativeObject()
        }

        fun searchAll(o: Any?): NativeArray = when (o) {
            null -> newNativeArray()
            else -> VersionCodesInfo.list
                .filter(getPredictor(normalizeSearcherSource(o)))
                .distinct()
                .sortedByDescending { it.apiLevel.toInt() }
                .map { it.toNativeObject() }
                .toNativeArray()
        }

        private fun getPredictor(obj: String): (Info) -> Boolean = {
            it.apiLevel == obj ||
                    it.versionCode == obj ||

                    it.releaseName == obj ||
                    it.releaseName.split(Regex("\\s+")).contains(obj) ||

                    it.internalCodename == obj ||
                    it.internalCodename.split(Regex("\\s+")).contains(obj) ||

                    it.releaseTimestamp == obj ||
                    it.releaseDate == obj ||

                    it.platformVersion == obj ||
                    it.platformVersion.split(Regex("\\D+")).firstOrNull() == obj
        }

        private fun normalizeSearcherSource(o: Any) = when (o) {
            is NativeDate -> wrapNumber(o.date).toString()
            is Number -> o.jsString
            is String -> o
            else -> throw WrappedIllegalArgumentException("Invalid argument type: $o for versionCodes.search")
        }

    }

}
