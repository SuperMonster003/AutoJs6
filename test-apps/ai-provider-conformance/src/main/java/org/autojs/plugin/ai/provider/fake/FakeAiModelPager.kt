package org.autojs.plugin.ai.provider.fake

import org.autojs.plugin.ai.provider.api.AiModelListRequest
import org.autojs.plugin.ai.provider.api.AiModelPage
import org.autojs.plugin.ai.provider.api.AiProviderVersionPolicy

object FakeAiModelPager {
    private val TOKEN = Regex("^fake-models-v1:([1-9][0-9]*):([1-9][0-9]*)$")

    fun page(request: AiModelListRequest): AiModelPage {
        AiProviderVersionPolicy.requireSelected(request.protocolVersion, FakeAiProviderFixture.protocolVersion)
        val offset = request.pageToken?.let { decodeToken(it, request.pageSize) } ?: 0
        val models = FakeAiProviderFixture.publicModels
        require(offset in 0 until models.size) { "Model page token offset is out of range" }
        val end = minOf(offset + request.pageSize, models.size)
        return AiModelPage(
            listingGeneration = FakeAiProviderFixture.LISTING_GENERATION,
            models = models.subList(offset, end),
            nextPageToken = if (end < models.size) encodeToken(end, request.pageSize) else null,
        )
    }

    private fun decodeToken(token: String, requestedPageSize: Int): Int {
        val match = TOKEN.matchEntire(token) ?: throw IllegalArgumentException("Invalid model page token")
        val offset = match.groupValues[1].toIntOrNull()
            ?: throw IllegalArgumentException("Invalid model page token offset")
        val pageSize = match.groupValues[2].toIntOrNull()
            ?: throw IllegalArgumentException("Invalid model page token page size")
        require(pageSize == requestedPageSize) { "Model page size changed while continuing a listing" }
        return offset
    }

    private fun encodeToken(offset: Int, pageSize: Int): String =
        "${FakeAiProviderFixture.LISTING_GENERATION}:$offset:$pageSize"
}
