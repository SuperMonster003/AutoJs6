package org.autojs.plugin.python.runtime.fake.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import org.autojs.plugin.python.runtime.fake.FakeHostIdentityEvidence
import org.autojs.plugin.python.runtime.fake.FakeHostIdentityPolicy
import org.autojs.plugin.python.runtime.fake.FakePythonFixture

internal class HostCallerVerifier(context: Context) {
    private val packageManager = context.applicationContext.packageManager
    private val providerPackageName = context.applicationContext.packageName

    fun enforceAllowedCaller(): Int = Binder.getCallingUid().also(::enforceAllowedUid)

    fun enforceSessionOwner(expectedUid: Int) {
        val callingUid = Binder.getCallingUid()
        if (callingUid != expectedUid) throw SecurityException("Python fake session owner changed")
        enforceAllowedUid(callingUid)
    }

    @Suppress("DEPRECATION")
    private fun enforceAllowedUid(uid: Int) {
        val host = FakePythonFixture.HOST_PACKAGE_NAME
        val installedUid = try {
            packageManager.getApplicationInfo(host, 0).uid
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        FakeHostIdentityPolicy.requireAllowed(
            FakeHostIdentityEvidence(
                callingUid = uid,
                installedHostUid = installedUid,
                packagesForCallingUid = packageManager.getPackagesForUid(uid)?.toSet().orEmpty(),
                providerAndHostSignaturesMatch =
                    packageManager.checkSignatures(providerPackageName, host) == PackageManager.SIGNATURE_MATCH,
            ),
        )
    }
}
