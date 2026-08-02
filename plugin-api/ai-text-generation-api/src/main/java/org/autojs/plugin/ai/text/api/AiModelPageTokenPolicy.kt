package org.autojs.plugin.ai.text.api

import org.autojs.plugin.ai.common.api.AiProtocolVersion
import org.autojs.plugin.ai.common.api.AiValidation

data class AiModelTokenScope(
    val packageName: String,
    val signerSha256: String,
    val providerId: String,
    val protocolVersion: AiProtocolVersion,
    val listingGeneration: String,
    val pageSize: Int,
) {
    init {
        require(PACKAGE_NAME.matches(packageName)) { "Invalid package name in model token scope" }
        require(AiValidation.SHA_256.matches(signerSha256)) { "Invalid signer digest in model token scope" }
        require(AiValidation.STABLE_ID.matches(providerId)) { "Invalid provider ID in model token scope" }
        AiValidation.requireUtf8(listingGeneration, 1, 256, "Model listing generation")
        require(pageSize in 1..AiTextLimits.MAX_MODELS_PER_PAGE) {
            "Invalid page size in model token scope"
        }
    }

    private companion object {
        val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    }
}

class AiModelPageTokenLedger(
    private val maximumConsumedTokens: Int = AiTextLimits.MAX_MODEL_PAGE_TOKEN_LEDGER_ENTRIES,
) {
    private val consumedTokens = linkedMapOf<String, AiModelTokenScope>()

    init {
        require(maximumConsumedTokens in 1..AiTextLimits.MAX_MODEL_PAGE_TOKEN_LEDGER_ENTRIES) {
            "Invalid model page-token ledger capacity"
        }
    }

    @Synchronized
    fun consume(token: String, scope: AiModelTokenScope) {
        AiValidation.requireUtf8(token, 1, AiTextLimits.MAX_MODEL_PAGE_TOKEN_BYTES, "Model page token")
        check(token !in consumedTokens) { "Model page token is repeated or replayed" }
        check(consumedTokens.size < maximumConsumedTokens) { "Model page-token ledger capacity is exhausted" }
        consumedTokens[token] = scope
    }
}

class AiModelPaginationPolicy(
    private val scope: AiModelTokenScope,
    private val ledger: AiModelPageTokenLedger,
) {
    private val observedTokens = linkedSetOf<String>()
    private val observedModelIds = linkedSetOf<String>()
    private var pages = 0
    private var finished = false

    @Synchronized
    fun acceptPage(requestToken: String?, page: AiModelPage) {
        check(!finished) { "Model listing is already complete" }
        check(pages < AiTextLimits.MAX_MODEL_PAGES) { "Model page count exceeds the protocol limit" }
        require(page.listingGeneration == scope.listingGeneration) { "Model listing generation changed" }
        require(page.models.size <= scope.pageSize) { "Model page exceeds the requested page size" }
        val modelIds = page.models.mapTo(linkedSetOf()) { it.modelId }
        check(modelIds.none { it in observedModelIds }) { "A model ID is repeated across listing pages" }
        if (pages == 0) {
            require(requestToken == null) { "The first model page must not use a token" }
        } else {
            val token = requireNotNull(requestToken) { "A continuation page requires its issued token" }
            check(token in observedTokens) { "Model page token was not issued by this listing" }
            ledger.consume(token, scope)
        }
        page.nextPageToken?.let { next ->
            check(next != requestToken) { "Model page token forms a direct cycle" }
            check(next !in observedTokens) { "Model page token forms a cycle" }
            observedTokens += next
        }
        if (page.nextPageToken == null) finished = true
        observedModelIds += modelIds
        pages += 1
    }
}
