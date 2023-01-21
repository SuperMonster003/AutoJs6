package org.autojs.autojs.util

import android.os.Environment
import java.io.File

object EnvironmentUtils {

    val externalStorageDirectory: File
        get() = Environment.getExternalStorageDirectory()

    @JvmStatic
    val externalStoragePath: String
        get() = externalStorageDirectory.path

}