/*  Copyright (c) MediaArea.net SARL. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license that can
 *  be found in the License.html file in the root of the source tree.
 */
package org.mediainfo.android

/**
 * Give information about a lot of media format.
 *
 * Modified by SuperMonster003 as of Nov 26, 2024.
 * Transformed by SuperMonster003 on Nov 26, 2024.
 *
 * @see <a href="https://github.com/olegazyx/MediaInfoLib-android">olegazyx/MediaInfoLib-android</a>
 */
@Suppress("unused")
class MediaInfo {

    private var mIsCanceled: Int = 0

    init {
        System.loadLibrary("mediainfo")
    }

    /**
     * Get a piece of information about a file. (parameter is an integer)
     *
     * @param streamKind Kind of Stream
     * @param streamNum Stream number in Kind of stream
     * @param parameter Parameter you are looking for in the stream (codec, width, bitrate, ...), in integer format
     * @return a string about information you search, an empty string if there is a problem.
     */
    fun get(filename: String, streamKind: StreamKind, streamNum: Int, parameter: Int): String {
        return getById(filename, streamKind.ordinal, streamNum, parameter /* InfoKind.TEXT */)
    }

    /**
     * Get a piece of information about a file. (parameter is an integer)
     *
     * @param streamKind Kind of Stream
     * @param streamNum Stream number in Kind of stream
     * @param parameter Parameter you are looking for in the stream (codec, width, bitrate, ...), in integer format
     * @param infoKind Kind of information you want about the parameter (the text, the measure, the help, ...)
     * @return a string about information you search, an empty string if there is a problem.
     */
    fun get(filename: String, streamKind: StreamKind, streamNum: Int, parameter: Int, infoKind: InfoKind): String {
        return getByIdDetail(filename, streamKind.ordinal, streamNum, parameter, infoKind.ordinal)
    }

    /**
     * Get a piece of information about a file. (parameter is a string)
     *
     * @param streamKind Kind of Stream (general, video, audio, ...)
     * @param streamNum Stream number in Kind of stream
     * @param parameter Parameter you are looking for in the stream (codec, width, bitrate, ...), in string format ("Codec", "Width", ...)
     * @return a string about information you search, an empty string if there is a problem
     */
    fun get(filename: String, streamKind: StreamKind, streamNum: Int, parameter: String): String {
        return getByName(filename, streamKind.ordinal, streamNum, parameter /* InfoKind.TEXT, InfoKind.NAME */)
    }

    /**
     * Get a piece of information about a file. (parameter is a string)
     *
     * @param streamKind Kind of Stream (general, video, audio, ...)
     * @param streamNum Stream number in Kind of stream
     * @param parameter Parameter you are looking for in the stream (codec, width, bitrate, ...), in string format ("Codec", "Width", ...)
     * @param infoKind Kind of information you want about the parameter (the text, the measure, the help, ...)
     * @return a string about information you search, an empty string if there is a problem.
     */
    fun get(filename: String, streamKind: StreamKind, streamNum: Int, parameter: String, infoKind: InfoKind): String {
        return getByNameDetail(filename, streamKind.ordinal, streamNum, parameter, infoKind.ordinal, InfoKind.NAME.ordinal)
    }

    /**
     * Get a piece of information about a file. (parameter is a string)
     *
     * @param streamKind Kind of Stream (general, video, audio, ...)
     * @param streamNum Stream number in Kind of stream
     * @param parameter Parameter you are looking for in the stream (codec, width, bitrate, ...), in string format ("Codec", "Width", ...)
     * @param infoKind Kind of information you want about the parameter (the text, the measure, the help, ...)
     * @param searchKind Where to look for the parameter
     * @return a string about information you search, an empty string if there is a problem.
     */
    fun get(filename: String, streamKind: StreamKind, streamNum: Int, parameter: String, infoKind: InfoKind, searchKind: InfoKind): String {
        return getByNameDetail(filename, streamKind.ordinal, streamNum, parameter, infoKind.ordinal, searchKind.ordinal)
    }

    /**
     * Count of streams of a stream kin (StreamNumber not filled), or count of piece of information in this stream.
     *
     * @param streamKind Kind of Stream (general, video, audio, ...)
     * @return number of streams of the given stream kind
     */
    fun countGet(filename: String, streamKind: StreamKind): Int {
        return countGet(filename, streamKind.ordinal, -1)
    }

    /**
     * Count of streams of a stream kin (StreamNumber not filled), or count of piece of information in this stream.
     *
     * @param streamKind Kind of Stream (general, video, audio, ...)
     * @param streamNumber Stream number in Kind of stream
     * @return number of streams of the given stream kind
     */
    fun countGet(filename: String, streamKind: StreamKind, streamNumber: Int): Int {
        return countGet(filename, streamKind.ordinal, streamNumber)
    }

    fun getMI(filename: String): String {
        return getMediaInfo(filename)
    }

    fun getMIOption(param: String): String {
        return getMediaInfoOption(param)
    }

    fun getIsCanceled() = mIsCanceled

    fun getMediaInfoTrimmed(filename: String): String {
        var result = getMediaInfo(filename)

        replaceMap.forEach { map ->
            val (old, new) = map
            result = result.replace(old, new + " ".repeat(maxOf(0, old.length - new.length)))
        }

        var maxWhitespaceToTrim = 0
        result.split("\n").forEach { s ->
            countSpacesBeforeColon(s).let { if (maxWhitespaceToTrim == 0 || it in 1..maxWhitespaceToTrim) maxWhitespaceToTrim = it }
        }
        return result.split("\n")
            .joinToString("\n") {
                trimSpacesBeforeColon(it, maxOf(0, maxWhitespaceToTrim - 1))
                    .replace(Regex("^[A-Z][a-z]+$"), "# $0")
            }
    }

    private fun countSpacesBeforeColon(input: String): Int {
        val index = input.indexOf(':')
        if (index == -1) return 0 // 如果没有冒号, 则返回0

        var count = 0
        for (i in index - 1 downTo 0) {
            if (input[i] == ' ') {
                count++
            } else {
                break
            }
        }
        return count
    }

    private fun trimSpacesBeforeColon(input: String, n: Int): String {
        if (n == 0) return input
        @Suppress("RegExpSimplifiable")
        val regex = Regex("\\s{1,$n}+:")
        return regex.replace(input) { matchResult ->
            matchResult.value.replaceFirst("\\s+".toRegex(), "")
        }
    }

    external fun getMediaInfo(filename: String): String

    external fun getMediaInfoOption(param: String): String

    private external fun getById(filename: String, streamKind: Int, streamNum: Int, parameter: Int): String

    private external fun getByIdDetail(filename: String, streamKind: Int, streamNum: Int, parameter: Int, kindOfInfo: Int): String

    private external fun getByName(filename: String, streamKind: Int, streamNum: Int, parameter: String): String

    private external fun getByNameDetail(filename: String, streamKind: Int, streamNum: Int, parameter: String, kindOfInfo: Int, kindOfSearch: Int): String

    private external fun countGet(filename: String, streamKind: Int, streamNum: Int): Int

    // @Remark
    //  ! Don't change it carelessly. This order is from MediaInfo_Const.h
    enum class StreamKind {
        GENERAL,
        VIDEO,
        AUDIO,
        TEXT,
        OTHER,
        IMAGE,
        MENU,
        MAX,
    }

    // @Remark
    //  ! Don't change it carelessly. This order is from MediaInfo_Const.h
    enum class InfoKind {
        /** Unique name of parameter */
        NAME,

        /** Value of parameter */
        TEXT,

        /** Unique name of measure unit of parameter */
        MEASURE,

        /** See InfoOptionKind */
        OPTIONS,

        /** Translated name of parameter */
        NAME_TEXT,

        /** Translated name of measure unit */
        MEASURE_TEXT,

        /** More information about the parameter */
        INFO,

        /** How this parameter is supported, could be N(No), B(Beta), R(Read only), W(Read/Write) */
        HOWTO,

        /** Domain of this piece of information */
        DOMAIN,

        MAX,
    }

    companion object {

        private val replaceMap = arrayOf(
            "first frame" to "1st frame",
            "GOP, Open/Closed of 1st frame" to "GOP, O/C of 1st frame",
        )

    }

}
