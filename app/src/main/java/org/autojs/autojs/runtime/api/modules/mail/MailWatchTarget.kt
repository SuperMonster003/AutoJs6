package org.autojs.autojs.runtime.api.modules.mail

import org.autojs.autojs.core.plugin.mail.MailError

/** Event/state target shared by Rhino emitters and portable runtime clients. */
internal interface MailWatchTarget {
    val currentMode: String?
    val isClosed: Boolean
    fun bindStop(action: () -> Unit): MailWatchTarget
    fun generation(generation: Long): MailWatchTarget
    fun initialMode(mode: String?): MailWatchTarget
    fun message(plain: Any?, decorate: (Any?) -> Any? = { it }): Boolean
    fun mode(mode: String): Boolean
    fun resync(reason: String): Boolean
    fun error(error: MailError, convert: (MailError) -> Any? = { it }): Boolean
    fun close(reason: String): Boolean
    fun fail(error: MailError, reason: String, convert: (MailError) -> Any? = { it }): Boolean
    fun cancelSilently(): Boolean
}
