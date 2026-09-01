package org.autojs.plugin.pinyin4j.api

object Pinyin4jReadingsContract {
    const val MAX_SOURCE_CODE_POINTS = 16 * 1024
    const val MAX_RESULT_JSON_BYTES = 512 * 1024
    const val MAX_CANDIDATES_PER_CODE_POINT = 64
    const val MAX_READING_CODE_POINTS = 64
}

object Pinyin4jRomanizationSystems {
    const val HANYU = "HANYU"
    const val TONGYONG = "TONGYONG"
    const val WADE_GILES = "WADE_GILES"
    const val MPS2 = "MPS2"
    const val YALE = "YALE"
    const val GWOYEU_ROMATZYH = "GWOYEU_ROMATZYH"

    val ALL = setOf(HANYU, TONGYONG, WADE_GILES, MPS2, YALE, GWOYEU_ROMATZYH)
}
