package org.autojs.autojs.network.github

/**
 * Chooses the release asset that best matches the device, given an ABI list ordered from the most
 * preferred entry (index 0) to the least preferred one (typically `"universal"` last).
 *
 * An asset matches an entry when the entry appears in the asset name as a whole word
 * (`autojs6-v6.7.0-arm64-v8a-62db1ff8.apk` matches `arm64-v8a`; `x86_64` does not match `x86`
 * because `_` is a word character). Among all matching assets the one with the lowest ABI index
 * wins; ties keep the earlier asset.
 *
 * zh-CN: 按设备 ABI 优先级列表 (索引 0 最优先, 末尾通常是 `"universal"`) 选择最匹配的 release 资产.
 * 资产名中以整词形式包含列表项即视为匹配 (`x86_64` 不匹配 `x86`, 因为 `_` 是单词字符); 多个匹配时取 ABI 索引最小者,
 * 索引相同时保留靠前的资产.
 */
object GitHubReleaseAssetPicker {

    data class Picked(val asset: GitHubReleaseAsset, val abi: String)

    @JvmStatic
    fun pick(assets: List<GitHubReleaseAsset>, abiPriority: List<String>): Picked? {
        var best: Picked? = null
        var bestIndex = Int.MAX_VALUE
        for (asset in assets) {
            for ((index, abi) in abiPriority.withIndex()) {
                if (index >= bestIndex) break
                if (matches(asset.name, abi)) {
                    best = Picked(asset, abi)
                    bestIndex = index
                    if (index == 0) return best
                    break
                }
            }
        }
        return best
    }

    private fun matches(assetName: String, abi: String): Boolean {
        if (abi.isBlank()) return false
        return Regex(".*\\b${Regex.escape(abi)}\\b.*").matches(assetName)
    }
}
