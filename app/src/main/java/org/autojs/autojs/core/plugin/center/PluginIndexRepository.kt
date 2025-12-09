package org.autojs.autojs.core.plugin.center

import android.content.Context

/**
 * 官方插件索引仓库 (M1: 内置静态; M2 再接入网络/ETag/缓存).
 */
class PluginIndexRepository {

    suspend fun fetchOfficialIndex(context: Context): List<PluginIndexEntry> {
        // TODO M1 先预置 1 条官方样例 "Paddle OCR (PP-OCRv5)", 便于与本地已安装合并显示.
        return listOf(
            PluginIndexEntry(
                packageName = "io.github.supermonster003.autojs6.plugin.paddleocr.v5",
                title = "Paddle OCR (PP-OCRv5)",
                description = "百度飞桨光学字符识别插件",
                author = "SuperMonster003",
                collaborators = emptyList(),
                versionName = "0.1.5",
                versionCode = 15L,
                versionDate = "2025-11-25",
                // TODO M1 若索引的下载地址/哈希/尺寸未知, 则先设置为 null.
                apkUrl = null,
                apkSha256 = null,
                apkSizeBytes = null,
                // TODO M1 暂不拉网图标, 使用应用图标或默认图标.
                iconUrl = null,
                tags = listOf("official", "ocr", "paddle", "v5"),
                engine = "paddle-ocr",
                variant = "v5",
                engineId = "paddle-ocr-pp-ocrv5",
            ),
        )
    }
}
