package org.autojs.autojs.ui.edit.editor.ace.font

import java.security.MessageDigest

internal object FontTestFixtures {
    fun catalogJson(
        catalogVersion: Long = 1,
        minHostVersionCode: Int = 5221,
        fontId: String = "test_font",
        bytes: ByteArray = validWoff2(),
        revokedSha256: Set<String> = emptySet(),
    ): String {
        val sha = sha256(bytes)
        val revocations = revokedSha256.joinToString(",") { "\"$it\"" }
        return """
            {
              "schemaVersion": 1,
              "catalogVersion": $catalogVersion,
              "minHostVersionCode": $minHostVersionCode,
              "releaseTag": "fonts-v$catalogVersion",
              "revokedSha256": [$revocations],
              "fonts": [{
                "id": "$fontId",
                "displayName": "Test Font",
                "family": "Test Font",
                "order": 10,
                "author": "Test Author",
                "license": {
                  "name": "SIL Open Font License 1.1",
                  "spdx": "OFL-1.1",
                  "files": [{
                    "fileName": "LICENSE.txt",
                    "size": 10,
                    "sha256": "${"1".repeat(64)}",
                    "url": "https://example.test/LICENSE.txt"
                  }]
                },
                "source": {
                  "repository": "https://example.test/font",
                  "upstreamVersion": null,
                  "autoJs6Snapshot": {
                    "versionCode": 5221,
                    "commit": "abc123",
                    "path": "fonts/Test.woff2"
                  }
                },
                "artifact": {
                  "version": $catalogVersion,
                  "fileName": "test-font-v$catalogVersion.woff2",
                  "format": "woff2",
                  "mimeType": "font/woff2",
                  "weight": 400,
                  "style": "normal",
                  "size": ${bytes.size},
                  "sha256": "$sha",
                  "url": "https://example.test/test-font.woff2",
                  "urls": ["https://example.test/test-font.woff2"]
                }
              }]
            }
        """.trimIndent()
    }

    fun remoteFont(
        bytes: ByteArray,
        id: String = "test_font",
        version: String = "1",
        url: String = "https://example.test/$id-$version.woff2",
        sha256: String = sha256(bytes),
    ): RemoteFont = RemoteFont(
        id = id,
        displayName = "Test Font",
        family = "Test Font",
        order = 10,
        author = "Test Author",
        license = FontLicense("OFL-1.1", "OFL-1.1", emptyList()),
        source = FontSource("https://example.test/font", null, null),
        artifact = FontArtifact(
            version = version,
            fileName = "$id-$version.woff2",
            format = "woff2",
            mimeType = "font/woff2",
            weight = 400,
            style = "normal",
            sizeBytes = bytes.size.toLong(),
            sha256 = sha256,
            urls = listOf(url),
            revoked = false,
        ),
    )

    fun validWoff2(size: Int = 96, fill: Byte = 7): ByteArray {
        require(size.toLong() >= FontFileVerifier.WOFF2_HEADER_SIZE)
        return ByteArray(size) { fill }.also { bytes ->
            bytes[0] = 'w'.code.toByte()
            bytes[1] = 'O'.code.toByte()
            bytes[2] = 'F'.code.toByte()
            bytes[3] = '2'.code.toByte()
            bytes[4] = 0
            bytes[5] = 1
            bytes[6] = 0
            bytes[7] = 0
            writeUInt32(bytes, 8, size.toLong())
            bytes[12] = 0
            bytes[13] = 1
            bytes[14] = 0
            bytes[15] = 0
        }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun writeUInt32(bytes: ByteArray, offset: Int, value: Long) {
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }
}
