package org.autojs.autojs.core.plugin.nativealign

import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import jackpal.androidterm.TermExec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NativePageAlignmentDeviceTest {
    @Test fun hostLibrariesLoadAndTerminalPtyExecutesCommand() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pageSize = NativePageSize.current()
        Log.i("NativePageAlignmentTest", "package=${context.packageName} pageSize=$pageSize")
        for (name in listOf("libc++_shared.so", "libjackpal-androidterm5.so", "libjackpal-termexec2.so")) {
            val path = File(context.applicationInfo.nativeLibraryDir, name)
            assertTrue(path.toString(), path.isFile)
            assertTrue(NativeLibraryAlignment.requireCompatible(path, pageSize) >= pageSize)
        }
        System.loadLibrary("c++_shared")
        val execClass = Class.forName("jackpal.androidterm.Exec")
        val windowSize = execClass.getDeclaredMethod("setPtyWindowSizeInternal", Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        val utf8 = execClass.getDeclaredMethod("setPtyUTF8ModeInternal", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
        windowSize.isAccessible = true
        utf8.isAccessible = true
        ParcelFileDescriptor.open(File("/dev/ptmx"), ParcelFileDescriptor.MODE_READ_WRITE).use { terminal ->
            windowSize.invoke(null, terminal.fd, 24, 80, 0, 0)
            utf8.invoke(null, terminal.fd, true)
            // TermExec removes the executable from argv; the explicit sh is argv[0].
            val pid = TermExec("/system/bin/sh", "sh", "-c", "exit 37").start(terminal)
            assertTrue(pid > 0)
            assertEquals(37, TermExec.waitFor(pid))
            Log.i("NativePageAlignmentTest", "PTY shell exit=37; native JNI passed")
        }
    }
}
