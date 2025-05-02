package org.autojs.autojs.ui.settings

data class VersionHistoryItem(
    val version: String,
    val date: String,
    val lines: List<String>,
    var expanded: Boolean = false,
)