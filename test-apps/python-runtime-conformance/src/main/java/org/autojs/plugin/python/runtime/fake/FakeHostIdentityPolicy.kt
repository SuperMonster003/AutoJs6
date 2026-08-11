package org.autojs.plugin.python.runtime.fake

data class FakeHostIdentityEvidence(
    val callingUid: Int,
    val installedHostUid: Int?,
    val packagesForCallingUid: Set<String>,
    val providerAndHostSignaturesMatch: Boolean,
)

object FakeHostIdentityPolicy {
    fun requireAllowed(evidence: FakeHostIdentityEvidence) {
        if (
            evidence.installedHostUid == null ||
            evidence.callingUid != evidence.installedHostUid ||
            FakePythonFixture.HOST_PACKAGE_NAME !in evidence.packagesForCallingUid ||
            !evidence.providerAndHostSignaturesMatch
        ) {
            throw SecurityException("Caller is not the allowed same-signer host")
        }
    }
}
