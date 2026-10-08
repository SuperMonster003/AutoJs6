package org.autojs.autojs.core.plugin.compose

import android.content.pm.ApplicationInfo
import android.graphics.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame

/** Initializes the JNI bridge retained by graphics-path's consumer rules in the release APK. */
internal fun assertGraphicsPathNativeLibraryInitialization(loaded: ComposeUiPluginLoader.Loaded) {
    // Initializing this subclass also initializes PathIteratorImpl, whose static initializer loads
    // androidx.graphics.path. Its Java superclass name may be obfuscated by the plugin's R8 pass.
    val nativeBridge = Class.forName("androidx.graphics.path.PathIteratorPreApi34Impl", true, loaded.loader)
    assertSame("Native library initialization uses the installed plugin's class loader", loaded.loader, nativeBridge.classLoader)

    // A debug APK can additionally exercise the public utility when present. Release R8 may
    // remove that unused utility, so native-library initialization is the shared release assertion.
    if (loaded.pluginContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
    val utilities = try { loaded.loader.loadClass("androidx.graphics.path.PathUtilities") }
    catch (_: ClassNotFoundException) { return }
    val path = Path().apply { moveTo(0f, 0f); lineTo(20f, 20f); close() }
    val iterator = utilities.getMethod("iterator", Path::class.java).invoke(null, path) as Iterator<*>
    var segments = 0
    while (iterator.hasNext()) { iterator.next(); segments++ }
    assertEquals("The available debug path iterator returns the expected segments", 3, segments)
}
