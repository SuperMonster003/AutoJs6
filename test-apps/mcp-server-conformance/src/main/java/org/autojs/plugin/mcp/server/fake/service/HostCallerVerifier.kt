package org.autojs.plugin.mcp.server.fake.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import org.autojs.plugin.mcp.server.fake.FakeMcpServerFixture

/** Only the installed AutoJs6 host, signed with the fixture's own test key, may call the fixture. */
internal class HostCallerVerifier(context: Context) {

    private val packageManager = context.applicationContext.packageManager
    private val fixturePackageName = context.applicationContext.packageName

    fun enforceAllowedCaller(): Int = Binder.getCallingUid().also(::enforceAllowedUid)

    fun enforceSessionOwner(expectedUid: Int) {
        val callingUid = Binder.getCallingUid()
        if (callingUid != expectedUid) {
            throw SecurityException("Fake MCP Server session UID does not match its owner")
        }
        enforceAllowedUid(callingUid)
    }

    private fun enforceAllowedUid(uid: Int) {
        val packages = packageManager.getPackagesForUid(uid)?.toSet().orEmpty()
        if (FakeMcpServerFixture.HOST_PACKAGE_NAME !in packages) {
            throw SecurityException("Fake MCP Server fixture only serves the AutoJs6 host")
        }
        val match = packageManager.checkSignatures(fixturePackageName, FakeMcpServerFixture.HOST_PACKAGE_NAME)
        if (match != PackageManager.SIGNATURE_MATCH) {
            throw SecurityException("Fake MCP Server fixture and the host must share the test signer")
        }
    }
}
