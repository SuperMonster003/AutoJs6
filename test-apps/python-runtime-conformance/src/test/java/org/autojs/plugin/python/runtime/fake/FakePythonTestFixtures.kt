package org.autojs.plugin.python.runtime.fake

import org.autojs.plugin.python.runtime.api.PythonExecutionRequest
import org.autojs.plugin.python.runtime.api.PythonPayloadKind
import org.autojs.plugin.python.runtime.api.PythonPayloadReference
import org.autojs.plugin.python.runtime.api.PythonRequestId
import org.autojs.plugin.python.runtime.api.PythonSha256
import java.util.UUID

internal object FakePythonTestFixtures {
    val requestId: PythonRequestId = PythonRequestId.fromUuid(
        UUID.fromString("12345678-1234-5678-9abc-def012345678"),
    )

    fun request(
        source: ByteArray = "print('ok')\n".toByteArray(),
        stdin: ByteArray? = null,
        arguments: List<String> = emptyList(),
    ): PythonExecutionRequest = PythonExecutionRequest(
        requestId = requestId,
        protocolVersion = FakePythonFixture.protocolVersion,
        entryPoint = "main.py",
        source = PythonPayloadReference(
            kind = PythonPayloadKind.SOURCE,
            descriptorIndex = 0,
            declaredLengthBytes = source.size.toLong(),
            sha256 = PythonSha256.digest(source),
        ),
        stdin = stdin?.let {
            PythonPayloadReference(
                kind = PythonPayloadKind.STDIN,
                descriptorIndex = 1,
                declaredLengthBytes = it.size.toLong(),
                sha256 = PythonSha256.digest(it),
            )
        },
        arguments = arguments,
        timeoutMillis = 1_000L,
        maxOutputBytes = 1_024L,
        maxOutputChunkBytes = 128,
        maxOutputChunks = 128L,
    )
}
