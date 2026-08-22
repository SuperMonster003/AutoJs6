package org.autojs.plugin.python.runtime.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest

class PythonRuntimeAidlGoldenTest {
    @Test
    fun normalizedAidlSourcesMatchProtocolV1Sha256Baselines() {
        val sourceDirectory = locateAidlSourceDirectory()
        val actualFileNames = Files.list(sourceDirectory).use { paths ->
            paths
                .filter { path -> Files.isRegularFile(path) && path.fileName.toString().endsWith(".aidl") }
                .map { path -> path.fileName.toString() }
                .sorted()
                .toList()
        }
        assertEquals(GOLDEN_SHA256.keys.toList(), actualFileNames)

        GOLDEN_SHA256.forEach { (fileName, expectedSha256) ->
            val source = sourceDirectory.resolve(fileName).toFile().readText(StandardCharsets.UTF_8)
            val normalizedLf = source.replace("\r\n", "\n").replace('\r', '\n')
            assertEquals(
                "Normalized AIDL source drifted: $fileName",
                expectedSha256,
                normalizedLf.toByteArray(StandardCharsets.UTF_8).sha256(),
            )
        }
    }

    private fun locateAidlSourceDirectory(): Path {
        val candidates = listOf(
            Paths.get("src", "main", "aidl").resolve(AIDL_PACKAGE_PATH),
            Paths.get("plugin-api", "python-runtime-api", "src", "main", "aidl")
                .resolve(AIDL_PACKAGE_PATH),
        ).map(Path::toAbsolutePath).map(Path::normalize)
        val directory = candidates.firstOrNull(Files::isDirectory)
        assertTrue("Python Runtime AIDL source directory was not found in $candidates", directory != null)
        return checkNotNull(directory)
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private companion object {
        val AIDL_PACKAGE_PATH: Path = Paths.get("org", "autojs", "plugin", "python", "runtime", "api")

        val GOLDEN_SHA256 = linkedMapOf(
            "IPythonExecutionCallback.aidl" to "69e283c172fec2aa069fe640eb41f80d42f731eb31124735de7cc4873907431c",
            "IPythonExecutionSession.aidl" to "566aab5f7076df5075ae4403de2578315be2adf623e2051b059cc85c1887725a",
            "IPythonRuntimeProvider.aidl" to "fe8bcd7561e8aeded1bb729ac57f47e65eaa94e3c3d70ca8b94e3acc77ca3a83",
            "IPythonSessionOpenCallback.aidl" to "927b0af34edcf509491c209f9b581c0f740393158d6fab75f191acc8843741c9",
        )
    }
}
