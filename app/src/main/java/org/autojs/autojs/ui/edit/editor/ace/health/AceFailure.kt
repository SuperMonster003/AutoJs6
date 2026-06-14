package org.autojs.autojs.ui.edit.editor.ace.health

import android.os.SystemClock

data class AceFailure(
    val type: AceFailureType,
    val message: String,
    val detail: String? = null,
    val state: AceHealthState? = null,
    val fatal: Boolean = true,
    val timestampUptimeMillis: Long = SystemClock.uptimeMillis(),
)
