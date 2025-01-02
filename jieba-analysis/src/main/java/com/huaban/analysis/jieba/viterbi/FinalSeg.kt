package com.huaban.analysis.jieba.viterbi

import android.content.Context
import com.huaban.analysis.jieba.CharacterUtil
import com.huaban.analysis.jieba.Log
import com.huaban.analysis.jieba.Node
import com.huaban.analysis.jieba.Pair
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.*

class FinalSeg private constructor() {
    private fun loadModel(context: Context) {
        val s = System.currentTimeMillis()
        val prevStatus = HashMap<Char, CharArray>().also { prevStatus = it }
        prevStatus['B'] = charArrayOf('E', 'S')
        prevStatus['M'] = charArrayOf('M', 'B')
        prevStatus['S'] = charArrayOf('S', 'E')
        prevStatus['E'] = charArrayOf('B', 'M')

        val start = HashMap<Char, Double>().also { start = it }
        start['B'] = -0.26268660809250016
        start['E'] = -3.14e+100
        start['M'] = -3.14e+100
        start['S'] = -1.4652633398537678

        val trans = HashMap<Char, Map<Char, Double>>().also { trans = it }
        val transB: MutableMap<Char, Double> = HashMap()
        transB['E'] = -0.510825623765990
        transB['M'] = -0.916290731874155
        trans['B'] = transB
        val transE: MutableMap<Char, Double> = HashMap()
        transE['B'] = -0.5897149736854513
        transE['S'] = -0.8085250474669937
        trans['E'] = transE
        val transM: MutableMap<Char, Double> = HashMap()
        transM['E'] = -0.33344856811948514
        transM['M'] = -1.2603623820268226
        trans['M'] = transM
        val transS: MutableMap<Char, Double> = HashMap()
        transS['B'] = -0.7211965654669841
        transS['S'] = -0.6658631448798212
        trans['S'] = transS

        // val `is` = javaClass.getResourceAsStream(PROB_EMIT)!!
        val `is` = context.assets.open(PROB_EMIT)
        try {
            val br = BufferedReader(InputStreamReader(`is`, Charset.forName("UTF-8")))
            val emit = HashMap<Char, Map<Char, Double>>().also { emit = it }
            var values: MutableMap<Char, Double>? = null
            while (br.ready()) {
                val line = br.readLine()
                val tokens = line.split("\t".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (tokens.size == 1) {
                    values = HashMap()
                    emit[tokens[0][0]] = values
                } else {
                    values!![tokens[0][0]] = tokens[1].toDouble()
                }
            }
        } catch (e: IOException) {
            Log.error(String.format(Locale.getDefault(), "%s: load model failure!", PROB_EMIT))
        } finally {
            try {
                `is`.close()
            } catch (e: IOException) {
                Log.error(String.format(Locale.getDefault(), "%s: close failure!", PROB_EMIT))
            }
        }
        Log.debug(
            String.format(
                Locale.getDefault(), "model load finished, time elapsed %d ms.",
                System.currentTimeMillis() - s
            )
        )
    }

    fun cut(sentence: String, tokens: MutableList<String>) {
        var chinese = StringBuilder()
        var other = StringBuilder()
        for (element in sentence) {
            if (CharacterUtil.isChineseLetter(element)) {
                if (other.isNotEmpty()) {
                    processOtherUnknownWords(other.toString(), tokens)
                    other = StringBuilder()
                }
                chinese.append(element)
            } else {
                if (chinese.isNotEmpty()) {
                    viterbi(chinese.toString(), tokens)
                    chinese = StringBuilder()
                }
                other.append(element)
            }
        }
        if (chinese.isNotEmpty()) viterbi(chinese.toString(), tokens)
        else {
            processOtherUnknownWords(other.toString(), tokens)
        }
    }

    private fun viterbi(sentence: String, tokens: MutableList<String>) {
        val v = Vector<MutableMap<Char, Double>>()
        var path: MutableMap<Char?, Node?> = HashMap()

        v.add(HashMap())
        for (state in states) {
            var emP = emit!![state]!![sentence[0]]
            if (null == emP) emP = MIN_FLOAT
            v[0][state] = start!![state]!! + emP
            path[state] = Node(state, null)
        }

        for (i in 1..<sentence.length) {
            val vv: MutableMap<Char, Double> = HashMap()
            v.add(vv)
            val newPath: MutableMap<Char?, Node?> = HashMap()
            for (y in states) {
                var emp = emit!![y]!![sentence[i]]
                if (emp == null) emp = MIN_FLOAT
                var candidate: Pair<Char>? = null
                for (y0 in prevStatus!![y]!!) {
                    var tranp = trans!![y0]!![y]
                    if (null == tranp) tranp = MIN_FLOAT
                    tranp += (emp + v[i - 1][y0]!!)
                    if (null == candidate) candidate = Pair(y0, tranp)
                    else if (candidate.freq <= tranp) {
                        candidate.freq = tranp
                        candidate.key = y0
                    }
                }
                vv[y] = candidate!!.freq
                newPath[y] = Node(y, path[candidate.key])
            }
            path = newPath
        }
        val probE = v[sentence.length - 1]['E']!!
        val probS = v[sentence.length - 1]['S']!!
        val posList = Vector<Char>(sentence.length)
        var win: Node?
        win = if (probE < probS) path['S']
        else path['E']

        while (win != null) {
            posList.add(win.value)
            win = win.parent
        }
        posList.reverse()

        var begin = 0
        var next = 0
        for (i in sentence.indices) {
            val pos = posList[i]
            when (pos) {
                'B' -> begin = i
                'E' -> {
                    tokens.add(sentence.substring(begin, i + 1))
                    next = i + 1
                }
                'S' -> {
                    tokens.add(sentence.substring(i, i + 1))
                    next = i + 1
                }
            }
        }
        if (next < sentence.length) tokens.add(sentence.substring(next))
    }

    private fun processOtherUnknownWords(other: String, tokens: MutableList<String>) {
        val mat = CharacterUtil.reSkip.matcher(other)
        var offset = 0
        while (mat.find()) {
            if (mat.start() > offset) {
                tokens.add(other.substring(offset, mat.start()))
            }
            tokens.add(mat.group())
            offset = mat.end()
        }
        if (offset < other.length) tokens.add(other.substring(offset))
    }

    companion object {
        private var singleInstance: FinalSeg? = null
        private const val PROB_EMIT = "prob_emit.txt"
        private val states = charArrayOf('B', 'M', 'E', 'S')
        private var emit: MutableMap<Char, Map<Char, Double>>? = null
        private var start: MutableMap<Char, Double>? = null
        private var trans: MutableMap<Char, Map<Char, Double>>? = null
        private var prevStatus: MutableMap<Char, CharArray>? = null
        private const val MIN_FLOAT = -3.14e100

        @Synchronized
        fun getInstance(context: Context): FinalSeg {
            if (singleInstance == null) {
                singleInstance = FinalSeg().apply { loadModel(context) }
            }
            return singleInstance!!
        }
    }
}
