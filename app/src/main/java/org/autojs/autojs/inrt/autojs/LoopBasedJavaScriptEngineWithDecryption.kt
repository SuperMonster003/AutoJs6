package org.autojs.autojs.inrt.autojs

import android.content.Context
import org.autojs.autojs.engine.LoopBasedJavaScriptEngine
import org.autojs.autojs.engine.encryption.ScriptEncryption
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.script.EncryptedScriptFileHeader
import org.autojs.autojs.script.EncryptedScriptFileHeader.BLOCK_SIZE
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.script.ScriptSource
import org.autojs.autojs.script.StringScriptSource

class LoopBasedJavaScriptEngineWithDecryption(context: Context) : LoopBasedJavaScriptEngine(context) {

    override fun execute(source: ScriptSource, callback: ExecuteCallback?) {
        if (source is JavaScriptFileSource) {
            try {
                val file = source.file
                val bytes = PFiles.readBytes(file.path)
                if (EncryptedScriptFileHeader.isValidFile(bytes)) {
                    super.execute(StringScriptSource(file.name, String(ScriptEncryption.decrypt(bytes, BLOCK_SIZE))), callback)
                } else {
                    super.execute(source, callback)
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        } else {
            super.execute(source, callback)
        }
    }

}