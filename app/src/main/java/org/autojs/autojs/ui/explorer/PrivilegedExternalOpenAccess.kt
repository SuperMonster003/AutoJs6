package org.autojs.autojs.ui.explorer

import android.content.Context
import org.autojs.autojs.model.explorer.PrivilegedAccessMode
import org.autojs.autojs.model.explorer.PrivilegedExternalFiles
import org.autojs.autojs.model.explorer.PrivilegedExternalFiles.FileDescription
import org.autojs.autojs.model.explorer.PrivilegedExternalFiles.PreparedOpen
import java.io.File

/** Separates external-open UI decisions from storage and Binder work. */
internal interface PrivilegedExternalOpenAccess {
    fun prepare(mode: PrivilegedAccessMode, path: String): PreparedOpen
    fun describe(mode: PrivilegedAccessMode, path: String): FileDescription
    fun release(prepared: PreparedOpen)
    fun copy(mode: PrivilegedAccessMode, path: String, onProgress: (Long, Long) -> Unit, cancelled: () -> Boolean, confirmed: FileDescription): File
    fun consumeFailure(): String?

    companion object {
        fun forContext(context: Context): PrivilegedExternalOpenAccess = object : PrivilegedExternalOpenAccess {
            private val applicationContext = context.applicationContext
            override fun prepare(mode: PrivilegedAccessMode, path: String) = PrivilegedExternalFiles.prepare(applicationContext, mode, path)
            override fun describe(mode: PrivilegedAccessMode, path: String) = PrivilegedExternalFiles.describe(applicationContext, mode, path)
            override fun release(prepared: PreparedOpen) = PrivilegedExternalFiles.release(prepared)
            override fun copy(mode: PrivilegedAccessMode, path: String, onProgress: (Long, Long) -> Unit, cancelled: () -> Boolean, confirmed: FileDescription) =
                PrivilegedExternalFiles.copyForCompatibility(applicationContext, mode, path, onProgress, cancelled, confirmed)
            override fun consumeFailure() = PrivilegedExternalFiles.consumeFailure(applicationContext)
        }
    }
}
