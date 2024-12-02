package org.autojs.autojs.runtime.api.augment.util

import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.runtime.api.Device
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.mozilla.javascript.Context
import kotlin.math.roundToInt

class MorseCodeParser(source: Any?, timeSpan: Any?) {

    @JvmField
    var code: String = ""

    @JvmField
    var pattern: Iterable<Long> = mutableListOf()

    private val mWords: List<String>
    private val mTimeSpan: Int
    private val mCharsPattern: HashMap<String, MutableList<Int>> = hashMapOf()

    init {
        val niceSource = source?.let { Context.toString(it) } ?: ""
        val niceTimeSpan = (if (timeSpan.isJsNullish()) DEFAULT_TIME_SPAN else Context.toNumber(timeSpan).roundToInt())

        mWords = niceSource.split(Regex("\\s+"))
        mTimeSpan = maxOf(niceTimeSpan, DEFAULT_TIME_SPAN)

        CHARS_MAP.forEach { (char, code) ->
            val res = mutableListOf<Int>()
            code.forEachIndexed { i, s ->
                when (s) {
                    '·' -> res.add(DIT.length * mTimeSpan)
                    '-' -> res.add(DAH.length * mTimeSpan)
                    else -> throw IllegalArgumentException("Invalid internal morse code: $code")
                }
                if (i != code.lastIndex) {
                    res.add(INTRA_CHARACTER_GAP.length * mTimeSpan)
                }
            }
            mCharsPattern[char] = res
        }

        mWords.forEachIndexed { i, word: String ->
            word.forEachIndexed { j, letter: Char ->
                appendVibrationForLetter(letter)
                if (j != word.lastIndex) {
                    appendGapForLetters()
                }
            }
            if (i != mWords.lastIndex) {
                appendGapForWords()
            }
        }
    }

    @JvmName("intervalVibrate")
    fun vibrate(delay: Double): Any {
        pattern.toList().takeIf { it.isNotEmpty() }?.let {
            Device.doVibrate((listOf(parseVibrationDelay(delay)) + it).toLongArray())
        }
        return UNDEFINED
    }

    private fun parseVibrationDelay(delay: Double): Long = when {
        delay.isNaN() -> 0L
        else -> maxOf(0L, delay.toLong())
    }

    private fun appendVibrationForLetter(letter: Char) {
        val upperCaseLetter = letter.uppercaseChar().toString()
        if (upperCaseLetter !in mCharsPattern) {
            throw Error("Letter $upperCaseLetter is not in the internal chars pattern dictionary")
        }
        pattern += mCharsPattern[upperCaseLetter]!!.map { it.toLong() }
        code += CHARS_MAP[upperCaseLetter]
    }

    private fun appendGapForLetters() {
        pattern += SHORT_GAP.length * mTimeSpan.toLong()
        code += "\u0020".repeat(SHORT_GAP.length)
    }

    private fun appendGapForWords() {
        pattern += MEDIUM_GAP.length * mTimeSpan.toLong()
        code += "\u0020".repeat(MEDIUM_GAP.length)
    }

    companion object {

        private const val DEFAULT_TIME_SPAN = 100

        /* a.k.a short mark or dot */
        private const val DIT = "1"

        /* a.k.a longer mark or dash */
        private const val DAH = "111"

        /* between the dots and dashes within a character */
        private const val INTRA_CHARACTER_GAP = "0"

        /* between letters */
        private const val SHORT_GAP = "000"

        /* between words */
        private const val MEDIUM_GAP = "0000000"

        private val CHARS_MAP = mapOf(
            "A" to "·-", "B" to "-···", "C" to "-·-·", "D" to "-··", "E" to "·", "F" to "··-·", "G" to "--·",
            "H" to "····", "I" to "··", "J" to "·---", "K" to "-·-", "L" to "·-··", "M" to "--", "N" to "-·",
            "O" to "---", "P" to "·--·", "Q" to "--·-", "R" to "·-·", "S" to "···", "T" to "-",
            "U" to "··-", "V" to "···-", "W" to "·--", "X" to "-··-", "Y" to "-·--", "Z" to "--··",
            "1" to "·----", "2" to "··---", "3" to "···--", "4" to "····-", "5" to "·····",
            "6" to "-····", "7" to "--···", "8" to "---··", "9" to "----·", "0" to "-----",
            "." to "·-·-·-", ":" to "---···", "," to "--··--", ";" to "-·-·-·", "?" to "··--··",
            "=" to "-···-", "'" to "·----·", "/" to "-··-·", "!" to "-·-·--", "-" to "-····-",
            "_" to "··--·-", "\"" to "·-··-·", "(" to "-·--·", ")" to "-·--·-",
            "$" to "···-··-", "&" to "·-···", "@" to "·--·-·", "+" to "·-·-·",
        )

        @JvmStatic
        internal fun buildParser(vararg args: Any?): MorseCodeParser {
            val (source, timeSpan) = when (args.size) {
                0 -> arrayOf("", null)
                1 -> arrayOf(args[0], null)
                else -> args
            }
            return MorseCodeParser(source, timeSpan)
        }

    }

}