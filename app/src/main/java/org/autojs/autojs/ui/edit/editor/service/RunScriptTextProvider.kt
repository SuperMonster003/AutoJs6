package org.autojs.autojs.ui.edit.editor.service

import android.content.Context
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.core.plugin.jvm.JvmSourceR1JavaPolicy
import org.autojs.autojs.pio.PFiles.write
import org.autojs.autojs.storage.file.TmpScriptFiles
import java.io.File

class RunScriptTextProvider(
    private val context: Context,
    private val editorTextProvider: EditorTextProvider,
    private val onTmpFileCreated: (File) -> Unit,
    private val extensionWithDotProvider: () -> String = { ".js" },
) {

    fun saveToTmpFile(): Observable<File> {
        return editorTextProvider.getTextForSaving()
            .flatMap { latestText ->
                Observable.fromCallable {
                    val extensionWithDot = extensionWithDotProvider()
                    val tmpFile = if (extensionWithDot.equals(".java", ignoreCase = true)) {
                        // The Java pipeline requires the exact compilation unit name (Main.java);
                        // a unique parent directory keeps concurrent tmp saves from colliding.
                        // zh-CN: Java 管线要求精确编译单元名 (Main.java); 唯一父目录避免临时保存互相覆盖.
                        TmpScriptFiles.createWithExactName(
                            context,
                            "${JvmSourceR1JavaPolicy.ENTRY_SIMPLE_NAME}.java",
                        )
                    } else {
                        TmpScriptFiles.create(context, extensionWithDot)
                    }
                    write(tmpFile, latestText)
                    onTmpFileCreated(tmpFile)
                    tmpFile
                }.subscribeOn(Schedulers.io())
            }
    }
}
