package org.autojs.plugin.ondeviceai.fake

import org.autojs.plugin.ondeviceai.api.AiModelListRequest
import org.autojs.plugin.ondeviceai.api.AiModelPage
import org.autojs.plugin.ondeviceai.api.OnDeviceAiVersionPolicy

object FakeAiModelPager {
    private val TOKEN = Regex("^fake-models-v1:([1-9][0-9]*):([1-9][0-9]*)$")

    fun page(request: AiModelListRequest): AiModelPage {
        OnDeviceAiVersionPolicy.requireSelected(request.protocolVersion, FakeOnDeviceAiFixture.protocolVersion)
        val offset = request.pageToken?.let { decodeToken(it, request.pageSize) } ?: 0
        val models = FakeOnDeviceAiFixture.publicModels
        require(offset in 0 until models.size) { "Model page token offset is out of range" }
        val end = minOf(offset + request.pageSize, models.size)
        return AiModelPage(
            listingGeneration = FakeOnDeviceAiFixture.LISTING_GENERATION,
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
        "${FakeOnDeviceAiFixture.LISTING_GENERATION}:$offset:$pageSize"
}
