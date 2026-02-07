package org.autojs.autojs.ui.settings

data class ReleaseHistoryItem(
    val version: String,
    val date: String,
    val lines: List<String>,
    var expanded: Boolean = false,
)