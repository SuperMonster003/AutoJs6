package org.autojs.autojs.inrt.autojs

import android.content.Context
import org.autojs.autojs.engine.LoopBasedJavaScriptEngine
import org.autojs.autojs.engine.encryption.ScriptEncryption
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.script.EncryptedScriptFileHeader
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.script.ScriptSource
import org.autojs.autojs.script.StringScriptSource
import java.io.File
import java.security.GeneralSecurityException

class XJavaScriptEngine(context: Context) : LoopBasedJavaScriptEngine(context) {


    override fun execute(source: ScriptSource, callback: ExecuteCallback?) {
        if (source is JavaScriptFileSource) {
            try {
                if (execute(source.file)) {
                    return
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                return
            }
        }
        super.execute(source, callback)
    }

    private fun execute(file: File): Boolean {
        val bytes = PFiles.readBytes(file.path)
        if (!EncryptedScriptFileHeader.isValidFile(bytes)) {
            return false
        }
        try {
            super.execute(StringScriptSource(file.name, String(ScriptEncryption.decrypt(bytes, EncryptedScriptFileHeader.BLOCK_SIZE))))
        } catch (e: GeneralSecurityException) {
            e.printStackTrace()
        }
        return true
    }

}