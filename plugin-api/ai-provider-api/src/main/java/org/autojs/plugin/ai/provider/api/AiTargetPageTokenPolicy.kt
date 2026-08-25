package org.autojs.plugin.ai.provider.api

import org.autojs.plugin.ai.common.api.AiProtocolVersion
import org.autojs.plugin.ai.common.api.AiValidation

data class AiTargetTokenScope(
    val packageName: String,
    val signerSha256: String,
    val providerId: String,
    val protocolVersion: AiProtocolVersion,
    val catalogGeneration: String,
    val pageSize: Int,
) {
    init {
        require(PACKAGE_NAME.matches(packageName)) { "Invalid package name in target token scope" }
        require(AiValidation.SHA_256.matches(signerSha256)) { "Invalid signer digest in target token scope" }
        require(AiValidation.STABLE_ID.matches(providerId)) { "Invalid provider ID in target token scope" }
        AiValidation.requireUtf8(catalogGeneration, 1, 256, "Target catalog generation")
        require(pageSize in 1..AiProviderLimits.MAX_TARGETS_PER_PAGE) {
            "Invalid page size in target token scope"
        }
    }

    private companion object {
        val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    }
}

class AiTargetPageTokenLedger(
    private val maximumConsumedTokens: Int = AiProviderLimits.MAX_TARGET_PAGE_TOKEN_LEDGER_ENTRIES,
) {
    private val consumedTokens = linkedMapOf<String, AiTargetTokenScope>()

    init {
        require(maximumConsumedTokens in 1..AiProviderLimits.MAX_TARGET_PAGE_TOKEN_LEDGER_ENTRIES) {
            "Invalid target page-token ledger capacity"
        }
    }

    @Synchronized
    fun consume(token: String, scope: AiTargetTokenScope) {
        AiValidation.requireUtf8(token, 1, AiProviderLimits.MAX_TARGET_PAGE_TOKEN_BYTES, "Target page token")
        check(token !in consumedTokens) { "Target page token is repeated or replayed" }
        check(consumedTokens.size < maximumConsumedTokens) { "Target page-token ledger capacity is exhausted" }
        consumedTokens[token] = scope
    }
}

class AiTargetPaginationPolicy(
    private val scope: AiTargetTokenScope,
    private val ledger: AiTargetPageTokenLedger,
) {
    private val observedTokens = linkedSetOf<String>()
    private val observedTargetIds = linkedSetOf<String>()
    private var pages = 0
    private var finished = false

    @Synchronized
    fun acceptPage(requestToken: String?, page: AiTargetPage) {
        check(!finished) { "Target catalog is already complete" }
        check(pages < AiProviderLimits.MAX_TARGET_PAGES) { "Target page count exceeds the protocol limit" }
        require(page.catalogGeneration == scope.catalogGeneration) { "Target catalog generation changed" }
        require(page.targets.size <= scope.pageSize) { "Target page exceeds the requested page size" }
        val targetIds = page.targets.mapTo(linkedSetOf()) { it.targetId }
        check(targetIds.none { it in observedTargetIds }) { "A target ID is repeated across catalog pages" }
        if (pages == 0) {
            require(requestToken == null) { "The first target page must not use a token" }
        } else {
            val token = requireNotNull(requestToken) { "A continuation target page requires its issued token" }
            check(token in observedTokens) { "Target page token was not issued by this catalog" }
            ledger.consume(token, scope)
        }
        page.nextPageToken?.let { next ->
            check(next != requestToken) { "Target page token forms a direct cycle" }
            check(next !in observedTokens) { "Target page token forms a cycle" }
            observedTokens += next
        }
        if (page.nextPageToken == null) finished = true
        observedTargetIds += targetIds
        pages += 1
    }
}
