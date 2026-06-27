package org.autojs.autojs.ui.edit.editor.service

import android.content.Context
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
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
                    val tmpFile = TmpScriptFiles.create(context, extensionWithDotProvider())
                    write(tmpFile, latestText)
                    onTmpFileCreated(tmpFile)
                    tmpFile
                }.subscribeOn(Schedulers.io())
            }
    }
}
