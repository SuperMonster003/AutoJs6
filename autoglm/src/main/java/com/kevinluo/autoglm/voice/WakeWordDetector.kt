package com.kevinluo.autoglm.voice

import com.kevinluo.autoglm.util.Logger

/**
 * Wake word detector for voice activation.
 *
 * Detects wake words in recognized text using exact and fuzzy matching.
 *
 * @property wakeWords List of wake words to detect
 * @property sensitivity Detection sensitivity (0.0 to 1.0), higher values enable more fuzzy matching
 */
class WakeWordDetector(private val wakeWords: List<String>, private val sensitivity: Float = 0.5f) {
    companion object {
        private const val TAG = "WakeWordDetector"
    }

    /**
     * Detects if the text contains any wake word.
     *
     * @param text The recognized text to search
     * @return The detected wake word, or null if none found
     */
    fun detect(text: String): String? {
        if (text.isBlank() || wakeWords.isEmpty()) {
            return null
        }

        val normalizedText = normalizeText(text)

        // 精确匹配
        for (wakeWord in wakeWords) {
            val normalizedWakeWord = normalizeText(wakeWord)
            if (normalizedText.contains(normalizedWakeWord)) {
                Logger.d(TAG, "Exact match found: $wakeWord in '$text'")
                return wakeWord
            }
        }

        // 模糊匹配（基于灵敏度）
        if (sensitivity > 0.3f) {
            for (wakeWord in wakeWords) {
                if (fuzzyMatch(normalizedText, normalizeText(wakeWord))) {
                    Logger.d(TAG, "Fuzzy match found: $wakeWord in '$text'")
                    return wakeWord
                }
            }
        }

        return null
    }

    /**
     * Normalizes text for comparison.
     * Removes spaces, punctuation, and converts to lowercase.
     */
    private fun normalizeText(text: String): String = text
        .lowercase()
        .replace(Regex("[\\s\\p{Punct}]"), "")
        .trim()

    /**
     * Performs fuzzy matching using edit distance.
     */
    private fun fuzzyMatch(text: String, wakeWord: String): Boolean {
        if (wakeWord.isEmpty()) return false

        // 在文本中滑动窗口查找
        val windowSize = wakeWord.length + 2

        for (i in 0..maxOf(0, text.length - wakeWord.length + 2)) {
            val endIndex = minOf(i + windowSize, text.length)
            val window = text.substring(i, endIndex)

            val similarity = calculateSimilarity(window, wakeWord)
            val threshold = 0.5f + (sensitivity * 0.4f) // 灵敏度越高，阈值越高

            if (similarity >= threshold) {
                return true
            }
        }

        return false
    }

    /**
     * Calculates similarity between two strings using Levenshtein distance.
     */
    private fun calculateSimilarity(s1: String, s2: String): Float {
        if (s1.isEmpty() && s2.isEmpty()) return 1f
        if (s1.isEmpty() || s2.isEmpty()) return 0f

        val distance = levenshteinDistance(s1, s2)
        val maxLength = maxOf(s1.length, s2.length)

        return 1f - (distance.toFloat() / maxLength)
    }

    /**
     * Calculates Levenshtein edit distance between two strings.
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length

        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] =
                    minOf(
                        // 删除
                        dp[i - 1][j] + 1,
                        // 插入
                        dp[i][j - 1] + 1,
                        // 替换
                        dp[i - 1][j - 1] + cost,
                    )
            }
        }

        return dp[m][n]
    }

    /**
     * Checks if text starts with a wake word.
     *
     * @param text The text to check
     * @return Pair of (wake word, remaining text) if found, null otherwise
     */
    fun startsWithWakeWord(text: String): Pair<String, String>? {
        if (text.isBlank() || wakeWords.isEmpty()) {
            return null
        }

        val normalizedText = normalizeText(text)

        for (wakeWord in wakeWords) {
            val normalizedWakeWord = normalizeText(wakeWord)
            if (normalizedText.startsWith(normalizedWakeWord)) {
                // 返回唤醒词和剩余文本
                val remainingText =
                    text
                        .substring(
                            text.lowercase().indexOf(wakeWord.lowercase()) + wakeWord.length,
                        ).trim()
                return Pair(wakeWord, remainingText)
            }
        }

        return null
    }
}
