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
            "IPythonExecutionCallback.aidl" to "cacd897f23bd468ed41558a7467d900defd2e5bac3e9ccc4d62c20f4f53b598a",
            "IPythonExecutionSession.aidl" to "a99156173a323e6aabc9d78148f4a90bb9010b4408acb3d9e0c8bfa0cc1b9823",
            "IPythonRuntimeProvider.aidl" to "fe8bcd7561e8aeded1bb729ac57f47e65eaa94e3c3d70ca8b94e3acc77ca3a83",
            "IPythonSessionOpenCallback.aidl" to "927b0af34edcf509491c209f9b581c0f740393158d6fab75f191acc8843741c9",
        )
    }
}
