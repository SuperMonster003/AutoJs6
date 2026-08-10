package org.autojs.plugin.lua.runtime.fake.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import org.autojs.plugin.lua.runtime.fake.FakeLuaRuntimeFixture

internal class HostCallerVerifier(context: Context) {
    private val packageManager = context.applicationContext.packageManager
    private val providerPackageName = context.applicationContext.packageName

    fun enforceAllowedCaller(): Int = Binder.getCallingUid().also(::enforceAllowedUid)

    fun enforceSessionOwner(expectedUid: Int) {
        val callingUid = Binder.getCallingUid()
        if (callingUid != expectedUid) {
            throw SecurityException("Lua runtime session UID does not match its owner")
        }
        enforceAllowedUid(callingUid)
    }

    @Suppress("DEPRECATION")
    private fun enforceAllowedUid(uid: Int) {
        val hostPackageName = FakeLuaRuntimeFixture.HOST_PACKAGE_NAME
        val packages = packageManager.getPackagesForUid(uid)?.toSet().orEmpty()
        if (hostPackageName !in packages) {
            throw SecurityException("Calling UID is not the allowed AutoJs6 host package")
        }
        val hostUid = try {
            packageManager.getApplicationInfo(hostPackageName, 0).uid
        } catch (error: PackageManager.NameNotFoundException) {
            throw SecurityException("Allowed AutoJs6 host package is not installed", error)
        }
        if (hostUid != uid) {
            throw SecurityException("Calling UID does not own the allowed AutoJs6 host package")
        }
        if (
            packageManager.checkSignatures(providerPackageName, hostPackageName) !=
            PackageManager.SIGNATURE_MATCH
        ) {
            throw SecurityException("Lua fixture and AutoJs6 host signatures do not match")
        }
    }
}
