package com.huaban.analysis.jieba

import android.content.Context
import com.huaban.analysis.jieba.viterbi.FinalSeg
import java.util.*

@Suppress("LocalVariableName")
class JiebaSegmenter(context: Context) {

    val dictionary by lazy { WordDictionary.getInstance(context) }

    private val finalSeg by lazy { FinalSeg.instance }

    enum class SegMode { INDEX, SEARCH }

    private fun createDAG(sentence: String): Map<Int, MutableList<Int>> {
        val dag = HashMap<Int, MutableList<Int>>()
        val trie = dictionary.trie
        val chars: CharArray = sentence.toCharArray()
        val N = chars.size
        var i = 0
        var j = 0
        while (i < N) {
            val hit = trie.match(chars, i, j - i + 1)
            if (hit.isPrefix || hit.isMatch) {
                if (hit.isMatch) {
                    if (!dag.containsKey(i)) {
                        val value: MutableList<Int> = ArrayList()
                        dag[i] = value
                        value.add(j)
                    } else dag[i]!!.add(j)
                }
                j += 1
                if (j >= N) {
                    i += 1
                    j = i
                }
            } else {
                i += 1
                j = i
            }
        }
        i = 0
        while (i < N) {
            if (!dag.containsKey(i)) {
                val value: MutableList<Int> = ArrayList()
                value.add(i)
                dag[i] = value
            }
            ++i
        }
        return dag
    }

    private fun calc(sentence: String, dag: Map<Int, MutableList<Int>>): Map<Int, Pair<Int>?> {
        val N = sentence.length
        val route = HashMap<Int, Pair<Int>?>()
        route[N] = Pair(0, 0.0)
        for (i in N - 1 downTo -1 + 1) {
            var candidate: Pair<Int>? = null
            for (x in dag[i]!!) {
                val freq = dictionary.getFreq(sentence.substring(i, x + 1)) + route[x + 1]!!.freq
                if (null == candidate) {
                    candidate = Pair(x, freq)
                } else if (candidate.freq < freq) {
                    candidate.freq = freq
                    candidate.key = x
                }
            }
            route[i] = candidate
        }
        return route
    }

    fun process(paragraph: String, mode: SegMode): List<SegToken> {
        val tokens: MutableList<SegToken> = ArrayList()
        var sb = StringBuilder()
        var offset = 0
        for (i in paragraph.indices) {
            val ch = CharacterUtil.regularize(paragraph[i])
            when {
                CharacterUtil.ccFind(ch) -> sb.append(ch)
                else -> {
                    if (sb.isNotEmpty()) {
                        // process
                        when (mode) {
                            SegMode.SEARCH -> {
                                for (word in sentenceProcess(sb.toString())) {
                                    tokens.add(SegToken(word, offset, word.length.let { offset += it; offset }))
                                }
                            }
                            else -> {
                                for (token in sentenceProcess(sb.toString())) {
                                    if (token.length > 2) {
                                        var gram2: String?
                                        var j = 0
                                        while (j < token.length - 1) {
                                            gram2 = token.substring(j, j + 2)
                                            if (dictionary.containsWord(gram2)) tokens.add(SegToken(gram2, offset + j, offset + j + 2))
                                            ++j
                                        }
                                    }
                                    if (token.length > 3) {
                                        var gram3: String?
                                        var j = 0
                                        while (j < token.length - 2) {
                                            gram3 = token.substring(j, j + 3)
                                            if (dictionary.containsWord(gram3)) tokens.add(SegToken(gram3, offset + j, offset + j + 3))
                                            ++j
                                        }
                                    }
                                    tokens.add(SegToken(token, offset, token.length.let { offset += it; offset }))
                                }
                            }
                        }
                        sb = StringBuilder()
                        offset = i
                    }
                    if (dictionary.containsWord(paragraph.substring(i, i + 1))) tokens.add(SegToken(paragraph.substring(i, i + 1), offset, ++offset))
                    else tokens.add(SegToken(paragraph.substring(i, i + 1), offset, ++offset))
                }
            }
        }
        if (sb.isNotEmpty()) when (mode) {
            SegMode.SEARCH -> {
                sentenceProcess(sb.toString()).mapTo(tokens) { token -> SegToken(token, offset, token.length.let { offset += it; offset }) }
            }
            else -> sentenceProcess(sb.toString()).forEach { token ->
                if (token.length > 2) {
                    var gram2: String?
                    var j = 0
                    while (j < token.length - 1) {
                        gram2 = token.substring(j, j + 2)
                        if (dictionary.containsWord(gram2)) tokens.add(SegToken(gram2, offset + j, offset + j + 2))
                        ++j
                    }
                }
                if (token.length > 3) {
                    var gram3: String?
                    var j = 0
                    while (j < token.length - 2) {
                        gram3 = token.substring(j, j + 3)
                        if (dictionary.containsWord(gram3)) tokens.add(SegToken(gram3, offset + j, offset + j + 3))
                        ++j
                    }
                }
                tokens.add(SegToken(token, offset, token.length.let { offset += it; offset }))
            }
        }

        return tokens
    }

    private fun sentenceProcess(sentence: String): List<String> {
        val tokens: MutableList<String> = ArrayList()
        val N = sentence.length
        val dag = createDAG(sentence)
        val route = calc(sentence, dag)

        var x = 0
        var y: Int
        var buf: String
        var sb = StringBuilder()
        while (x < N) {
            y = route[x]!!.key + 1
            val lWord: String = sentence.substring(x, y)
            when {
                y - x == 1 -> sb.append(lWord)
                else -> {
                    if (sb.isNotEmpty()) {
                        buf = sb.toString()
                        sb = StringBuilder()
                        when (buf.length) {
                            1 -> tokens.add(buf)
                            else -> when {
                                dictionary.containsWord(buf) -> tokens.add(buf)
                                else -> finalSeg.cut(buf, tokens)
                            }
                        }
                    }
                    tokens.add(lWord)
                }
            }
            x = y
        }
        buf = sb.toString()
        if (buf.isNotEmpty()) {
            when (buf.length) {
                1 -> tokens.add(buf)
                else -> when {
                    dictionary.containsWord(buf) -> tokens.add(buf)
                    else -> finalSeg.cut(buf, tokens)
                }
            }
        }
        return tokens
    }

    fun cutSmall(hans: String, limit: Int): List<String> = when {
        hans.isEmpty() || limit <= 0 -> emptyList()
        else -> process(hans, SegMode.SEARCH)
            .map { token -> token.word }
            .flatMap { word ->
                when {
                    word.length > limit -> word.chunked(limit)
                    else -> listOf(word)
                }
            }
    }

}
