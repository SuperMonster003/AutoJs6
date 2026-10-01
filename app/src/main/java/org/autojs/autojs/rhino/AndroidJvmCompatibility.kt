package org.autojs.autojs.rhino

import android.os.Build
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass

/** Compatibility needed before external JVM libraries initialize their Unsafe method handles. */
internal object AndroidJvmCompatibility {

    private val initialization by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                // Android hides getAndAddInt/getAndAddLong from apps targeting above Android 11.
                // Kotlin's embedded IntelliJ containers resolve them during class initialization.
                // Keep the exemption limited to Unsafe; other non-SDK APIs retain their policy.
                // zh-CN: Kotlin 编译器的容器在类初始化时解析 Unsafe 原子操作, 仅豁免该类.
                if (!HiddenApiBypass.addHiddenApiExemptions("Lsun/misc/Unsafe;")) {
                    Log.w(TAG, "Unable to enable Unsafe compatibility for dynamically loaded libraries")
                }
            } catch (failure: Exception) {
                Log.w(TAG, "Unable to enable Unsafe compatibility for dynamically loaded libraries", failure)
            } catch (failure: LinkageError) {
                Log.w(TAG, "Unsafe compatibility is unavailable on this Android runtime", failure)
            }
        }
    }

    fun ensureInitialized() {
        initialization
    }

    private const val TAG = "AndroidJvmCompatibility"
}
