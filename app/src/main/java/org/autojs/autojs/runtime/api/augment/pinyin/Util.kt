package org.autojs.autojs.runtime.api.augment.pinyin

import org.autojs.autojs.util.RhinoUtils.coerceString

/**
 * 工具类 Util, 用于实现拼音转换时辅助功能.
 */
@Suppress("SpellCheckingInspection")
object Util {

    /**
     * 合并二维字符串数组为一维字符串数组.
     *
     * @param arr 二维数组, 如 [["zhāo", "cháo"], ["yáng"], ["dōng"], ["shēng"]]
     * @return 合并后的字符串数组, 一维结果, 如 ["zhāoyáng", "cháoyáng", "dōng", "shēng"]
     */
    fun combo(arr: List<List<String>>): List<String> = when {
        arr.size <= 1 -> arr.firstOrNull() ?: emptyList()
        else -> {
            var result = combo2array(arr[0], arr[1])
            for (i in 2 until arr.size) {
                result = combo2array(result, arr[i])
            }
            result
        }
    }

    /**
     * 组合两个拼音数组为一维字符串数组.
     *
     * @param a1 第一个拼音数组, 如 ["zhāo", "cháo"]
     * @param a2 第二个拼音数组, 如 ["yáng"]
     * @return 组合后的一维字符串数组, 如 ["zhāoyáng", "cháoyáng"]
     */
    private fun combo2array(a1: List<String>, a2: List<String>): List<String> {
        return when {
            a1.isEmpty() -> a2
            a2.isEmpty() -> a1
            else -> a1.flatMap { item1 -> a2.map { item2 -> item1 + item2 } }
        }
    }

    /**
     * 合并多个拼音数组, 形成一个新的二维数组.
     * 每两个拼音元素形成一个元组数组.
     *
     * @param arr 二维数组（每组拼音数组）
     * @return 合并后的二维拼音数组, 如 [[hai, qian], [huan, qian]]
     */
    fun compact(arr: List<List<String>>): List<List<String>> = when {
        arr.isEmpty() -> emptyList()
        arr.size == 1 -> arr.map { listOf(it).flatten() }
        else -> {
            var result = compact2array(arr[0], arr[1])
            for (i in 2 until arr.size) {
                result = compact2array(result, arr[i])
            }
            result
        }
    }
    /**
     * 组合两个拼音数组, 形成一个新的二维数组.
     *
     * @param a1 第一个拼音数组 eg: ["hai", "huan"] / [["hai"], ["huan"]]
     * @param a2 第二个拼音数组 eg: ["qian"]
     * @return 组合后的二维数组, 如 [["hai", "qian"], ["huan", "qian"]]
     */
    private fun compact2array(a1: List<Any>, a2: List<String>): List<List<String>> = when {
        a1.isEmpty() -> listOf(a2)
        a2.isEmpty() -> a1.map { item1 ->
            when (item1) {
                is List<*> -> item1.map { coerceString(it) }
                else -> listOf(item1.toString())
            }
        }
        else -> {
            val result = mutableListOf<List<String>>()
            a1.forEach { item1 ->
                val list1 = when (item1) {
                    is List<*> -> item1.map { coerceString(it) }
                    else -> listOf(coerceString(item1))
                }
                a2.mapTo(result) { list1 + it }
            }
            result
        }
    }

}