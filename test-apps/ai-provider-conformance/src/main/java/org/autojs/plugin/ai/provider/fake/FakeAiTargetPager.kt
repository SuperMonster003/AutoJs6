package org.autojs.plugin.ai.provider.fake

import org.autojs.plugin.ai.provider.api.AiTargetListRequest
import org.autojs.plugin.ai.provider.api.AiTargetPage
import org.autojs.plugin.ai.provider.api.AiProviderVersionPolicy

object FakeAiTargetPager {
    private val TOKEN = Regex("^fake-targets-v2:([1-9][0-9]*):([1-9][0-9]*)$")

    fun page(request: AiTargetListRequest): AiTargetPage {
        AiProviderVersionPolicy.requireSelected(request.protocolVersion, FakeAiProviderFixture.protocolVersion)
        val offset = request.pageToken?.let { decodeToken(it, request.pageSize) } ?: 0
        val targets = FakeAiProviderFixture.publicTargets
        require(offset in 0 until targets.size) { "Target page token offset is out of range" }
        val end = minOf(offset + request.pageSize, targets.size)
        return AiTargetPage(
            catalogGeneration = FakeAiProviderFixture.CATALOG_GENERATION,
            targets = targets.subList(offset, end),
            nextPageToken = if (end < targets.size) encodeToken(end, request.pageSize) else null,
        )
    }

    private fun decodeToken(token: String, requestedPageSize: Int): Int {
        val match = TOKEN.matchEntire(token) ?: throw IllegalArgumentException("Invalid target page token")
        val offset = match.groupValues[1].toIntOrNull()
            ?: throw IllegalArgumentException("Invalid target page token offset")
        val pageSize = match.groupValues[2].toIntOrNull()
            ?: throw IllegalArgumentException("Invalid target page token page size")
        require(pageSize == requestedPageSize) { "Target page size changed while continuing a catalog" }
        return offset
    }

    private fun encodeToken(offset: Int, pageSize: Int): String =
        "${FakeAiProviderFixture.CATALOG_GENERATION}:$offset:$pageSize"
}
