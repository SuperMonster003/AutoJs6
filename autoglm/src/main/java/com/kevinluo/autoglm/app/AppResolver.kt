package com.kevinluo.autoglm.app

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.kevinluo.autoglm.util.Logger

/**
 * Resolves application names to package names using fuzzy matching.
 *
 * This class queries the PackageManager for installed launchable apps
 * and provides fuzzy matching to find apps by their display names.
 * It supports exact package name lookup, contains matching, starts-with matching,
 * and Levenshtein distance-based fuzzy matching.
 *
 * Usage example:
 * ```kotlin
 * val resolver = AppResolver(context.packageManager)
 * val packageName = resolver.resolvePackageName("WeChat")
 * ```
 *
 *
 * @property packageManager The Android PackageManager for querying installed apps
 */
class AppResolver(private val packageManager: PackageManager) {
    /**
     * Resolves an app name to its package name using fuzzy matching.
     *
     * The resolution process follows this order:
     * 1. If the input looks like a package name (contains dots), verify it exists
     * 2. Otherwise, perform fuzzy search across all installed launchable apps
     * 3. Return the best match above the similarity threshold
     *
     * @param appName The display name or partial name of the app to find.
     *                Can also be a full package name.
     * @return The package name of the best matching app, or null if no match found
     *
     */
    fun resolvePackageName(appName: String): String? {
        Logger.i(TAG, "resolvePackageName called with: '$appName'")

        if (appName.isBlank()) {
            Logger.i(TAG, "appName is blank, returning null")
            return null
        }

        val normalizedQuery = appName.lowercase().trim()
        Logger.i(TAG, "Normalized query: '$normalizedQuery'")

        // Check if appName looks like a package name (contains dots)
        if (appName.contains('.')) {
            Logger.i(TAG, "appName contains '.', checking as package name")
            try {
                packageManager.getPackageInfo(appName, 0)
                Logger.i(TAG, "Found as package name: $appName")
                return appName
            } catch (_: PackageManager.NameNotFoundException) {
                Logger.i(TAG, "Not a valid package name, continuing with fuzzy search")
            }
        }

        // Fuzzy search installed apps
        val apps = getAllLaunchableApps()
        Logger.i(TAG, "Found ${apps.size} launchable apps")

        // Log first 10 apps for debugging
        apps.take(10).forEach { app ->
            Logger.d(TAG, "  App: '${app.displayName}' -> ${app.packageName}")
        }

        // Find the best match based on similarity score
        var bestMatch: AppInfo? = null
        var bestScore = 0.0

        for (app in apps) {
            val score = calculateSimilarity(normalizedQuery, app.displayName.lowercase())
            if (score > 0.1) {
                Logger.d(TAG, "  Similarity '${app.displayName}': $score")
            }
            if (score > bestScore && score >= MIN_SIMILARITY_THRESHOLD) {
                bestScore = score
                bestMatch = app
            }
        }

        if (bestMatch != null) {
            Logger.i(TAG, "Best match: '${bestMatch.displayName}' (${bestMatch.packageName}) with score $bestScore")
        } else {
            Logger.w(TAG, "No match found for '$appName'")
        }

        return bestMatch?.packageName
    }

    /**
     * Returns all installed launchable applications.
     *
     * Queries the PackageManager for all apps that have a launcher activity
     * (i.e., apps that appear in the device's app drawer).
     *
     * @return List of all launchable apps with their display names and package names,
     *         deduplicated by package name
     *
     */
    fun getAllLaunchableApps(): List<AppInfo> {
        val intent =
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

        val resolveInfoList: List<ResolveInfo> = packageManager.queryIntentActivities(intent, 0)

        return resolveInfoList
            .mapNotNull { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                val displayName = resolveInfo.loadLabel(packageManager)?.toString() ?: return@mapNotNull null
                val packageName = activityInfo.packageName ?: return@mapNotNull null

                AppInfo(
                    displayName = displayName,
                    packageName = packageName,
                )
            }.distinctBy { it.packageName }
    }

    /**
     * Searches for apps matching the given query.
     *
     * Performs a fuzzy search across all installed launchable apps and returns
     * matches sorted by similarity score (best matches first).
     *
     * @param query The search query string
     * @return List of matching apps sorted by relevance (highest similarity first),
     *         empty list if query is blank or no matches found
     */
    fun searchApps(query: String): List<AppInfo> {
        if (query.isBlank()) {
            return emptyList()
        }

        val normalizedQuery = query.lowercase().trim()
        val apps = getAllLaunchableApps()

        return apps
            .map { app -> app to calculateSimilarity(normalizedQuery, app.displayName.lowercase()) }
            .filter { (_, score) -> score >= MIN_SIMILARITY_THRESHOLD }
            .sortedByDescending { (_, score) -> score }
            .map { (app, _) -> app }
    }

    /**
     * Calculates the similarity between two strings.
     *
     * Uses a combination of matching strategies with different priority levels:
     * 1. Exact match: returns 1.0
     * 2. Contains match: returns 0.8-0.95 based on coverage
     * 3. Starts with match: returns 0.75-0.9 based on coverage
     * 4. Query starts with target: returns 0.7-0.85 based on coverage
     * 5. Levenshtein distance: returns 0.0-0.7 based on edit distance
     *
     * @param query The search query (should be normalized to lowercase)
     * @param target The target string to compare against (should be normalized to lowercase)
     * @return Similarity score between 0.0 (no match) and 1.0 (exact match)
     */
    internal fun calculateSimilarity(query: String, target: String): Double {
        // Exact match
        if (query == target) {
            return 1.0
        }

        // Target contains query exactly
        if (target.contains(query)) {
            // Score based on how much of the target is covered by the query
            val coverageScore = query.length.toDouble() / target.length
            return 0.8 + (coverageScore * 0.15) // Range: 0.8 to 0.95
        }

        // Target starts with query
        if (target.startsWith(query)) {
            val coverageScore = query.length.toDouble() / target.length
            return 0.75 + (coverageScore * 0.15) // Range: 0.75 to 0.9
        }

        // Query starts with target (e.g., query="wechat app" target="wechat")
        if (query.startsWith(target)) {
            val coverageScore = target.length.toDouble() / query.length
            return 0.7 + (coverageScore * 0.15) // Range: 0.7 to 0.85
        }

        // Fuzzy matching using Levenshtein distance
        val distance = levenshteinDistance(query, target)
        val maxLength = maxOf(query.length, target.length)

        if (maxLength == 0) {
            return 0.0
        }

        // Convert distance to similarity score
        val similarity = 1.0 - (distance.toDouble() / maxLength)

        // Scale down fuzzy matches to be below exact/contains matches
        return similarity * 0.7
    }

    /**
     * Calculates the Levenshtein distance between two strings.
     *
     * The Levenshtein distance is the minimum number of single-character edits
     * (insertions, deletions, or substitutions) required to change one string
     * into the other. Uses dynamic programming for O(m*n) time complexity.
     *
     * @param s1 First string to compare
     * @param s2 Second string to compare
     * @return The Levenshtein distance (minimum edit operations needed)
     */
    internal fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length

        // Handle edge cases
        if (m == 0) return n
        if (n == 0) return m

        // Create distance matrix
        val dp = Array(m + 1) { IntArray(n + 1) }

        // Initialize first column
        for (i in 0..m) {
            dp[i][0] = i
        }

        // Initialize first row
        for (j in 0..n) {
            dp[0][j] = j
        }

        // Fill in the rest of the matrix
        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] =
                    minOf(
                        // Deletion
                        dp[i - 1][j] + 1,
                        // Insertion
                        dp[i][j - 1] + 1,
                        // Substitution
                        dp[i - 1][j - 1] + cost,
                    )
            }
        }

        return dp[m][n]
    }

    companion object {
        private const val TAG = "AppResolver"

        /**
         * Minimum similarity score (0.0 to 1.0) required for a match.
         * Apps with similarity below this threshold are not considered matches.
         */
        const val MIN_SIMILARITY_THRESHOLD = 0.3
    }
}
