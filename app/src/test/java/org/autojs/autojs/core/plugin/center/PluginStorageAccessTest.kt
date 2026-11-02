package org.autojs.autojs.core.plugin.center

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What counts as a refused access to shared storage in the error output of a script (api-coverage Q228 (b)). */
class PluginStorageAccessTest {

    @Test fun errorsOfNodeAndBunCount() {
        assertTrue(PluginStorageAccess.isDenied("Error: EACCES: permission denied, open '/sdcard/aj6/a.txt'"))
        assertTrue(PluginStorageAccess.isDenied("error: EACCES: permission denied, open '/storage/emulated/0/a.txt'"))
        assertTrue(PluginStorageAccess.isDenied("""path: "/storage/emulated/0/a.txt", syscall: "open", errno: -13, code: "EACCES""""))
        assertTrue(PluginStorageAccess.isDenied("Error: EPERM: operation not permitted, open '/sdcard/a.txt'"))
        assertTrue(PluginStorageAccess.isDenied("EACCES: /mnt/sdcard/a.txt: this runtime plugin has no access to shared storage"))
    }

    @Test fun otherOutputDoesNot() {
        assertFalse(PluginStorageAccess.isDenied("Error: EACCES: permission denied, open '/data/data/other/a.txt'"))
        assertFalse(PluginStorageAccess.isDenied("Error: EACCES: permission denied, mkdir '/proc/1/storage/a'"))
        assertFalse(PluginStorageAccess.isDenied("Error: ENOENT: no such file or directory, open '/sdcard/a.txt'"))
        assertFalse(PluginStorageAccess.isDenied("wrote /sdcard/a.txt"))
        // The host process failed: a grant to the plugin would not help.
        assertFalse(PluginStorageAccess.isDenied("UncheckedIOException: java.io.FileNotFoundException: /sdcard/a.txt: open failed: EPERM (Operation not permitted)"))
        assertFalse(PluginStorageAccess.isDenied(""))
    }
}
