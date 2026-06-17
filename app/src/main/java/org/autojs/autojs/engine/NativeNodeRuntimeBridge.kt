package org.autojs.autojs.engine

import android.os.Build
import android.util.Log
import org.autojs.autojs.runtime.api.Console
import java.nio.charset.StandardCharsets
import java.util.Locale

object NativeNodeRuntimeBridge {

    const val NODE_LIBRARY_NAME = "node"
    const val BRIDGE_LIBRARY_NAME = "autojs6-node"

    private val nodeLoadResult: LoadResult by lazy { loadLibrary(NODE_LIBRARY_NAME) }

    private val bridgeLoadResult: LoadResult by lazy {
        nodeLoadResult.throwIfFailed(NativeLibraryRole.NODE)
        loadLibrary(BRIDGE_LIBRARY_NAME)
    }

    val isAvailable: Boolean
        get() = isNodeLibraryAvailable && isBridgeLibraryAvailable

    val isNodeLibraryAvailable: Boolean
        get() = nodeLoadResult is LoadResult.Loaded

    val isBridgeLibraryAvailable: Boolean
        get() = bridgeLoadResult is LoadResult.Loaded

    val isEmbedderRuntimeSupported: Boolean
        get() = missingEmbedderSymbols().isEmpty()

    fun requireAvailable() {
        nodeLoadResult.throwIfFailed(NativeLibraryRole.NODE)
        bridgeLoadResult.throwIfFailed(NativeLibraryRole.BRIDGE)
    }

    fun missingEmbedderSymbols(): Array<String> {
        requireAvailable()
        return nativeMissingEmbedderSymbols()
    }

    fun runMain(
        argv: Array<String>,
        workingDirectory: String? = null,
        outputSink: OutputSink? = null,
    ): Int {
        requireAvailable()
        return nativeRunMain(argv, workingDirectory, outputSink)
    }

    fun consoleOutputSink(console: Console): OutputSink = ConsoleOutputSink(console)

    private external fun nativeRunMain(
        argv: Array<String>,
        workingDirectory: String?,
        outputSink: OutputSink?,
    ): Int

    private external fun nativeMissingEmbedderSymbols(): Array<String>

    interface OutputSink {
        fun onStdout(bytes: ByteArray)
        fun onStderr(bytes: ByteArray)
    }

    private class ConsoleOutputSink(private val console: Console) : OutputSink {

        override fun onStdout(bytes: ByteArray) {
            print(Log.INFO, bytes)
        }

        override fun onStderr(bytes: ByteArray) {
            print(Log.ERROR, bytes)
        }

        private fun print(level: Int, bytes: ByteArray) {
            console.println(level, bytes.toString(StandardCharsets.UTF_8).trimEnd('\r', '\n'))
        }
    }

    private fun loadLibrary(name: String): LoadResult = try {
        System.loadLibrary(name)
        LoadResult.Loaded
    } catch (e: UnsatisfiedLinkError) {
        LoadResult.Failed(e)
    } catch (e: SecurityException) {
        LoadResult.Failed(e)
    }

    private enum class NativeLibraryRole(val libraryName: String) {
        NODE(NODE_LIBRARY_NAME),
        BRIDGE(BRIDGE_LIBRARY_NAME),
        ;

        val fileName: String
            get() = "lib$libraryName.so"
    }

    private sealed interface LoadResult {
        data object Loaded : LoadResult
        data class Failed(val error: Throwable) : LoadResult

        fun throwIfFailed(role: NativeLibraryRole) {
            if (this is Failed) {
                throw NativeNodeRuntimeUnavailableException(buildLoadFailureMessage(role, error), error)
            }
        }
    }

    private fun buildLoadFailureMessage(role: NativeLibraryRole, error: Throwable): String {
        val rawMessage = error.message.orEmpty()
        return buildString {
            append("Native Node.js backend failed to load ${role.fileName}.")
            append('\n')
            append("Reason: ${classifyLoadFailure(role, rawMessage)}")
            if (rawMessage.isNotBlank()) {
                append('\n')
                append("Original linker error: $rawMessage")
            } else {
                append('\n')
                append("Original exception: ${error.javaClass.name}")
            }
            append('\n')
            append("Process ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
            System.getProperty("java.library.path")
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    append('\n')
                    append("java.library.path: $it")
                }
        }
    }

    private fun classifyLoadFailure(role: NativeLibraryRole, rawMessage: String): String {
        if (rawMessage.isBlank()) {
            return "Android linker did not provide a detail message; inspect the attached cause."
        }

        val lowerMessage = rawMessage.lowercase(Locale.ROOT)
        val missingLibrary = Regex("""library "([^"]+\.so)" not found""", RegexOption.IGNORE_CASE)
            .find(rawMessage)
            ?.groupValues
            ?.getOrNull(1)
        val missingSymbol = Regex("cannot locate symbol \"([^\"]+)\"", RegexOption.IGNORE_CASE)
            .find(rawMessage)
            ?.groupValues
            ?.getOrNull(1)

        return when {
            isAbiMismatch(lowerMessage) -> {
                "${role.fileName} or one of its dependencies does not match the current process ABI."
            }
            isMissingLibrary(rawMessage, role.fileName) || role.fileName.equals(missingLibrary, ignoreCase = true) -> {
                "${role.fileName} is missing from the APK for the current ABI."
            }
            isMissingLibrary(rawMessage, "libc++_shared.so") ||
                    "libc++_shared.so".equals(missingLibrary, ignoreCase = true) -> {
                "libc++_shared.so is missing from the APK for the current ABI."
            }
            missingSymbol != null && isLibcxxRuntimeSymbol(missingSymbol) -> {
                "libc++_shared.so does not export C++ runtime symbol $missingSymbol required by ${role.fileName}."
            }
            lowerMessage.contains("cannot locate symbol") -> {
                "${role.fileName} or one of its dependencies is missing symbol ${missingSymbol ?: "(unknown)"}."
            }
            missingLibrary != null -> {
                "${role.fileName} depends on $missingLibrary, but $missingLibrary is missing from the APK or system image."
            }
            else -> "${role.fileName} failed to load; see the original linker error."
        }
    }

    private fun isMissingLibrary(rawMessage: String, fileName: String): Boolean {
        val lowerMessage = rawMessage.lowercase(Locale.ROOT)
        val lowerFileName = fileName.lowercase(Locale.ROOT)
        return lowerMessage.contains("library \"$lowerFileName\" not found") ||
                lowerMessage.contains("couldn't find \"$lowerFileName\"") ||
                lowerMessage.contains("cannot locate \"$lowerFileName\"")
    }

    private fun isAbiMismatch(lowerMessage: String): Boolean =
        lowerMessage.contains("wrong elf class") ||
                lowerMessage.contains("unexpected e_machine") ||
                lowerMessage.contains("is 32-bit instead of 64-bit") ||
                lowerMessage.contains("is 64-bit instead of 32-bit") ||
                lowerMessage.contains("has text relocations") ||
                lowerMessage.contains("bad elf magic")

    private fun isLibcxxRuntimeSymbol(symbol: String): Boolean =
        symbol.contains("__ndk1") ||
                symbol.contains("NSt6__ndk1") ||
                symbol.contains("basic_ostringstream") ||
                symbol.contains("basic_stringstream") ||
                symbol.contains("basic_istringstream") ||
                symbol.contains("__cxa_demangle") ||
                symbol.contains("__cxa_pure_virtual") ||
                symbol.startsWith("_ZTVNSt6__ndk1") ||
                symbol.startsWith("_ZTINSt6__ndk1") ||
                symbol.startsWith("_ZTSNSt6__ndk1") ||
                symbol.startsWith("_ZTTNSt6__ndk1")

}
