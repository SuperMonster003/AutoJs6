package org.autojs.autojs.core.pref.archive

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.io.IOException

/**
 * The keys of the app for sealed category files (docs/settings/ROADMAP.md D23 / T21).
 * zh-CN: 应用用于加密分类文件的密钥 (ROADMAP D23 / T21).
 */
object AndroidSettingsKeys {

    /**
     * The device key of this app on this device, from its `ANDROID_ID` (per signature and user since Android 8);
     * throws [IOException] when the system gives none.
     * zh-CN: 本应用在本机的设备密钥, 来自其 `ANDROID_ID` (Android 8 起按签名与用户区分); 系统未提供时抛 [IOException].
     */
    @JvmStatic
    @SuppressLint("HardwareIds")
    fun device(context: Context): SettingsDeviceKey {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        if (id.isNullOrBlank()) throw IOException("ANDROID_ID is unavailable")
        return SettingsDeviceKey.fromAndroidId(id)
    }
}
