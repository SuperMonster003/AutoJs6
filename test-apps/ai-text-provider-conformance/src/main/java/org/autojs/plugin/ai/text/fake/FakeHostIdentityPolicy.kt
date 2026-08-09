package org.autojs.plugin.ai.text.fake

data class FakeHostIdentityEvidence(
    val callingUid: Int,
    val installedHostUid: Int?,
    val packagesForCallingUid: Set<String>,
    val providerAndHostSignaturesMatch: Boolean,
)

enum class FakeHostIdentityDecision {
    ALLOW,
    HOST_PACKAGE_NOT_OWNED,
    HOST_NOT_INSTALLED,
    HOST_UID_MISMATCH,
    SIGNATURE_MISMATCH,
}

object FakeHostIdentityPolicy {
    fun evaluate(evidence: FakeHostIdentityEvidence): FakeHostIdentityDecision = when {
        FakeAiTextFixture.HOST_PACKAGE_NAME !in evidence.packagesForCallingUid ->
            FakeHostIdentityDecision.HOST_PACKAGE_NOT_OWNED
        evidence.installedHostUid == null -> FakeHostIdentityDecision.HOST_NOT_INSTALLED
        evidence.callingUid != evidence.installedHostUid -> FakeHostIdentityDecision.HOST_UID_MISMATCH
        !evidence.providerAndHostSignaturesMatch -> FakeHostIdentityDecision.SIGNATURE_MISMATCH
        else -> FakeHostIdentityDecision.ALLOW
    }

    fun requireAllowed(evidence: FakeHostIdentityEvidence) {
        val decision = evaluate(evidence)
        if (decision != FakeHostIdentityDecision.ALLOW) {
            throw SecurityException("Fake AI provider caller rejected: $decision")
        }
    }
}
