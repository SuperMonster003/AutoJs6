package com.kevinluo.autoglm.app

/**
 * Data class representing information about an installed application.
 *
 * @property displayName The user-visible name of the application
 * @property packageName The unique package identifier of the application
 */
data class AppInfo(val displayName: String, val packageName: String)
