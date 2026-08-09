package org.autojs.plugin.ai.text.fake.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import org.autojs.plugin.ai.text.fake.FakeAiTextFixture
import org.autojs.plugin.ai.text.fake.FakeHostIdentityEvidence
import org.autojs.plugin.ai.text.fake.FakeHostIdentityPolicy

internal class HostCallerVerifier(context: Context) {
    private val packageManager = context.applicationContext.packageManager
    private val providerPackageName = context.applicationContext.packageName

    fun enforceAllowedCaller(): Int = Binder.getCallingUid().also(::enforceAllowedUid)

    fun enforceSessionOwner(expectedUid: Int) {
        val callingUid = Binder.getCallingUid()
        if (callingUid != expectedUid) {
            throw SecurityException("Fake AI session UID does not match its owner")
        }
        enforceAllowedUid(callingUid)
    }

    @Suppress("DEPRECATION")
    private fun enforceAllowedUid(uid: Int) {
        val hostPackageName = FakeAiTextFixture.HOST_PACKAGE_NAME
        val installedHostUid = try {
            packageManager.getApplicationInfo(hostPackageName, 0).uid
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        FakeHostIdentityPolicy.requireAllowed(
            FakeHostIdentityEvidence(
                callingUid = uid,
                installedHostUid = installedHostUid,
                packagesForCallingUid = packageManager.getPackagesForUid(uid)?.toSet().orEmpty(),
                providerAndHostSignaturesMatch = packageManager.checkSignatures(
                    providerPackageName,
                    hostPackageName,
                ) == PackageManager.SIGNATURE_MATCH,
            ),
        )
    }
}
