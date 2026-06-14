import java.io.File
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile

private val nativeNodeReportVariant = "appDebug"
private val nativeNodeReportVariantTaskSuffix = "AppDebug"
private val nativeNodeReportNdkVersion = "26.1.10909125"
private val nativeNodeReportRequiredLibcxxAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
private val nativeNodeReportBridgeLibraryName = "libautojs6-node.so"
private val nativeNodeReportNodeLibraryName = "libnode.so"
private val nativeNodeReportNodeLibcxxSymbols = listOf(
    "_ZTVNSt6__ndk118basic_stringstreamIcNS_11char_traitsIcEENS_9allocatorIcEEEE",
    "_ZTVNSt6__ndk119basic_istringstreamIcNS_11char_traitsIcEENS_9allocatorIcEEEE",
    "_ZTVNSt6__ndk119basic_ostringstreamIcNS_11char_traitsIcEENS_9allocatorIcEEEE",
)

private data class NativeNodeReportTools(
    val ndkDir: File,
    val readelf: File,
    val nm: File,
)

private data class NativeNodeSoInfo(
    val source: String,
    val abi: String,
    val file: File,
    val needed: List<String>,
    val undefinedSymbols: List<String>,
)

private data class NativeNodeApkLibcxxInfo(
    val apkName: String,
    val abi: String,
    val entryName: String,
    val extractedFile: File,
    val hash: String,
    val length: Long,
)

private data class NativeNodeLibcxxCandidate(
    val source: String,
    val abi: String,
    val path: String,
    val hash: String,
    val length: Long,
)

private data class NativeNodeSymbolSectionEvidence(
    val debugInfo: Boolean,
    val symtab: Boolean,
    val dynsym: Boolean,
) {
    val symbolFileAvailable: Boolean
        get() = debugInfo || symtab

    override fun toString(): String =
        "debug_info=$debugInfo, symtab=$symtab, dynsym=$dynsym"
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                break
            }
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun ByteArray.sha256(): String =
    MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

private fun runNativeNodeReportCommand(vararg command: String): List<String> {
    val process = ProcessBuilder(command.toList())
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    return buildList {
        if (exitCode != 0) {
            add("[command failed: exit=$exitCode, command=${command.joinToString(" ")}]")
        }
        addAll(output.lineSequence().filter { it.isNotBlank() })
    }
}

private fun findNativeNodeReportSdkDir(rootDir: File): File? {
    val localProperties = rootDir.resolve("local.properties")
    if (localProperties.isFile) {
        val properties = Properties()
        localProperties.inputStream().use { properties.load(it) }
        properties.getProperty("sdk.dir")?.takeIf { it.isNotBlank() }?.let {
            return File(it)
        }
    }
    return listOf("ANDROID_HOME", "ANDROID_SDK_ROOT")
        .asSequence()
        .mapNotNull { System.getenv(it)?.takeIf(String::isNotBlank) }
        .map(::File)
        .firstOrNull(File::isDirectory)
}

private fun nativeNodeReportHostTag(): String {
    val osName = System.getProperty("os.name").lowercase()
    return when {
        osName.contains("win") -> "windows"
        osName.contains("mac") || osName.contains("darwin") -> "darwin"
        else -> "linux"
    }
}

private fun findNativeNodeReportTools(rootDir: File): NativeNodeReportTools? {
    val sdkDir = findNativeNodeReportSdkDir(rootDir) ?: return null
    val ndkDir = sdkDir.resolve("ndk/$nativeNodeReportNdkVersion").takeIf(File::isDirectory) ?: return null
    val hostTag = nativeNodeReportHostTag()
    val prebuiltDir = ndkDir.resolve("toolchains/llvm/prebuilt")
        .listFiles()
        ?.filter(File::isDirectory)
        ?.firstOrNull { it.name.startsWith(hostTag) }
        ?: return null
    val exe = if (hostTag == "windows") ".exe" else ""
    val readelf = prebuiltDir.resolve("bin/llvm-readelf$exe")
    val nm = prebuiltDir.resolve("bin/llvm-nm$exe")
    if (!readelf.isFile || !nm.isFile) {
        return null
    }
    return NativeNodeReportTools(ndkDir, readelf, nm)
}

private fun nativeNodeReportNdkLibcxx(tools: NativeNodeReportTools, abi: String): File? {
    val triple = when (abi) {
        "arm64-v8a" -> "aarch64-linux-android"
        "armeabi-v7a" -> "arm-linux-androideabi"
        "x86" -> "i686-linux-android"
        "x86_64" -> "x86_64-linux-android"
        else -> return null
    }
    return tools.ndkDir
        .resolve("toolchains/llvm/prebuilt")
        .listFiles()
        ?.filter(File::isDirectory)
        ?.firstOrNull { it.name.startsWith(nativeNodeReportHostTag()) }
        ?.resolve("sysroot/usr/lib/$triple/libc++_shared.so")
        ?.takeIf(File::isFile)
}

private fun parseNativeNodeReportNeeded(tools: NativeNodeReportTools, so: File): List<String> {
    val neededPattern = Regex("""Shared library: \[(.+)]""")
    return runNativeNodeReportCommand(tools.readelf.absolutePath, "-d", "--wide", so.absolutePath)
        .mapNotNull { neededPattern.find(it)?.groupValues?.get(1) }
}

private fun parseNativeNodeReportUndefinedSymbols(tools: NativeNodeReportTools, so: File): List<String> =
    runNativeNodeReportCommand(tools.nm.absolutePath, "-D", "--undefined-only", so.absolutePath)
        .map { it.trim() }
        .filterNot { it.startsWith("[command failed:") }
        .mapNotNull { line ->
            val parts = line.split(Regex("""\s+""")).filter(String::isNotBlank)
            parts.lastOrNull()?.takeUnless { it == "U" || it == "w" || it == "v" }
        }
        .map { it.substringBefore("@") }
        .distinct()
        .sorted()

private fun parseNativeNodeReportDefinedSymbols(tools: NativeNodeReportTools, so: File): Set<String> =
    runNativeNodeReportCommand(tools.nm.absolutePath, "-D", "--defined-only", so.absolutePath)
        .map { it.trim() }
        .filterNot { it.startsWith("[command failed:") }
        .mapNotNull { line ->
            val parts = line.split(Regex("""\s+""")).filter(String::isNotBlank)
            parts.lastOrNull()
        }
        .map { it.substringBefore("@") }
        .toSet()

private fun isNativeNodeReportCppRuntimeSymbol(symbol: String): Boolean =
    symbol.contains("NSt6__ndk1") ||
            symbol.contains("__cxa") ||
            symbol.startsWith("_ZTV") ||
            symbol.startsWith("_ZTI") ||
            symbol.startsWith("_ZTS") ||
            symbol.startsWith("_ZTT")

private fun shouldNativeNodeReportResolveFromLibcxx(symbol: String): Boolean =
    symbol.contains("NSt6__ndk1") ||
            symbol.startsWith("_ZTVNSt6__ndk1") ||
            symbol.startsWith("_ZTINSt6__ndk1") ||
            symbol.startsWith("_ZTSNSt6__ndk1") ||
            symbol.startsWith("_ZTTNSt6__ndk1") ||
            symbol == "__cxa_demangle" ||
            symbol == "__cxa_pure_virtual"

private fun scanNativeNodeReportRoot(
    tools: NativeNodeReportTools,
    source: String,
    root: File,
): List<NativeNodeSoInfo> {
    if (!root.isDirectory) {
        return emptyList()
    }
    return root.listFiles()
        ?.filter(File::isDirectory)
        ?.sortedBy(File::getName)
        ?.flatMap { abiDir ->
            abiDir.listFiles { file -> file.isFile && file.extension == "so" }
                ?.sortedBy(File::getName)
                ?.map { so ->
                    NativeNodeSoInfo(
                        source = source,
                        abi = abiDir.name,
                        file = so,
                        needed = parseNativeNodeReportNeeded(tools, so),
                        undefinedSymbols = parseNativeNodeReportUndefinedSymbols(tools, so),
                    )
                }
                .orEmpty()
        }
        .orEmpty()
}

private fun scanNativeNodeReportCxxOutputs(
    tools: NativeNodeReportTools,
    source: String,
    root: File,
): List<NativeNodeSoInfo> {
    if (!root.isDirectory) {
        return emptyList()
    }
    return root.walkTopDown()
        .filter { it.isFile && it.extension == "so" }
        .mapNotNull { so ->
            val abi = nativeNodeReportAbiFromPath(so.absolutePath).takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            NativeNodeSoInfo(
                source = source,
                abi = abi,
                file = so,
                needed = parseNativeNodeReportNeeded(tools, so),
                undefinedSymbols = parseNativeNodeReportUndefinedSymbols(tools, so),
            )
        }
        .toList()
}

private fun latestNativeNodeReportBridgeInfoByAbi(infos: List<NativeNodeSoInfo>): Map<String, NativeNodeSoInfo> =
    infos
        .filter { it.file.name == nativeNodeReportBridgeLibraryName && it.abi.isNotBlank() }
        .groupBy { it.abi }
        .mapValues { (_, abiInfos) -> abiInfos.maxBy { it.file.lastModified() } }

private fun StringBuilder.appendNativeNodeReportBridgeDependencyLine(
    label: String,
    info: NativeNodeSoInfo?,
) {
    if (info == null) {
        appendLine("    $label: missing")
        return
    }
    val linksLibnode = nativeNodeReportNodeLibraryName in info.needed
    appendLine("    $label: ${info.file.absolutePath}")
    appendLine("      DT_NEEDED includes $nativeNodeReportNodeLibraryName: $linksLibnode")
    appendLine("      DT_NEEDED: ${if (info.needed.isEmpty()) "none" else info.needed.joinToString()}")
}

private fun formatNativeNodeReportSymbolSample(symbols: List<String>): String =
    when {
        symbols.isEmpty() -> "none"
        symbols.size <= 12 -> symbols.joinToString()
        else -> symbols.take(12).joinToString() + " ... (+${symbols.size - 12} more)"
    }

private fun nativeNodeReportAbiFromPath(path: String): String =
    path.split('/', '\\')
        .lastOrNull { it in setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86", "armeabi") }
        .orEmpty()

private fun nativeNodeReportSanitizeFileName(value: String): String =
    value.replace(Regex("""[^A-Za-z0-9._+-]"""), "_")

private fun parseNativeNodeReportBuildId(tools: NativeNodeReportTools, so: File): String? {
    if (!so.isFile) {
        return null
    }
    val buildIdPattern = Regex("""Build ID:\s*([0-9A-Fa-f]+)""")
    return runNativeNodeReportCommand(tools.readelf.absolutePath, "-n", "--wide", so.absolutePath)
        .firstNotNullOfOrNull { line -> buildIdPattern.find(line)?.groupValues?.get(1) }
}

private fun parseNativeNodeReportSymbolEvidence(
    tools: NativeNodeReportTools,
    so: File,
): NativeNodeSymbolSectionEvidence? {
    if (!so.isFile) {
        return null
    }
    val sections = runNativeNodeReportCommand(tools.readelf.absolutePath, "-S", "--wide", so.absolutePath)
        .filterNot { it.startsWith("[command failed:") }
    if (sections.isEmpty()) {
        return null
    }
    return NativeNodeSymbolSectionEvidence(
        debugInfo = sections.any { it.contains(".debug_info") || it.contains(".debug_line") },
        symtab = sections.any { it.contains(".symtab") },
        dynsym = sections.any { it.contains(".dynsym") },
    )
}

private fun latestNativeNodeReportLibraryFile(root: File, abi: String, libraryName: String): File? {
    if (!root.isDirectory) {
        return null
    }
    return root.walkTopDown()
        .filter { it.isFile && it.name == libraryName && nativeNodeReportAbiFromPath(it.absolutePath) == abi }
        .maxByOrNull { it.lastModified() }
}

private fun StringBuilder.appendNativeNodeSymbolArchiveEntry(
    tools: NativeNodeReportTools,
    label: String,
    file: File?,
) {
    val existingFile = file?.takeIf(File::isFile)
    appendLine("    $label:")
    appendLine("      path: ${existingFile?.absolutePath ?: "missing"}")
    appendLine("      size: ${existingFile?.length()?.toString() ?: "missing"}")
    appendLine("      sha256: ${existingFile?.sha256() ?: "missing"}")
    appendLine("      build_id: ${existingFile?.let { parseNativeNodeReportBuildId(tools, it) } ?: "missing"}")
    val symbolEvidence = existingFile?.let { parseNativeNodeReportSymbolEvidence(tools, it) }
    appendLine("      symbol_evidence: ${symbolEvidence ?: "missing"}")
    appendLine(
        "      symbol_file: " + when {
            existingFile == null -> "missing"
            symbolEvidence?.symbolFileAvailable == true -> existingFile.absolutePath
            else -> "missing (no .debug_* or .symtab section found)"
        }
    )
}

private fun extractNativeNodeReportApkLibcxx(
    apkFiles: List<File>,
    outputDir: File,
): List<NativeNodeApkLibcxxInfo> {
    outputDir.deleteRecursively()
    outputDir.mkdirs()
    val entryPattern = Regex("""^lib/([^/]+)/libc\+\+_shared\.so$""")
    return apkFiles.flatMap { apk ->
        ZipFile(apk).use { zip ->
            zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .mapNotNull { entry ->
                    val match = entryPattern.matchEntire(entry.name) ?: return@mapNotNull null
                    val abi = match.groupValues[1]
                    val extractedFile = outputDir.resolve(
                        nativeNodeReportSanitizeFileName("${apk.name}__${entry.name.replace('/', '_')}")
                    )
                    zip.getInputStream(entry).use { input ->
                        extractedFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    NativeNodeApkLibcxxInfo(
                        apkName = apk.name,
                        abi = abi,
                        entryName = entry.name,
                        extractedFile = extractedFile,
                        hash = extractedFile.sha256(),
                        length = extractedFile.length(),
                    )
                }
                .toList()
        }
    }
}

private fun scanNativeNodeReportLibcxxFiles(
    source: String,
    root: File,
): List<NativeNodeLibcxxCandidate> {
    if (!root.isDirectory) {
        return emptyList()
    }
    return root.walkTopDown()
        .filter { it.isFile && it.name == "libc++_shared.so" }
        .map { file ->
            NativeNodeLibcxxCandidate(
                source = source,
                abi = nativeNodeReportAbiFromPath(file.absolutePath),
                path = file.absolutePath,
                hash = file.sha256(),
                length = file.length(),
            )
        }
        .toList()
}

private fun scanNativeNodeReportAarLibcxxEntries(
    source: String,
    root: File,
    skippedDirNames: Set<String> = emptySet(),
): List<NativeNodeLibcxxCandidate> {
    if (!root.isDirectory) {
        return emptyList()
    }
    val entryPattern = Regex("""(^|/)libc\+\+_shared\.so$""")
    return root.walkTopDown()
        .onEnter { it.name !in skippedDirNames }
        .filter { it.isFile && it.extension.equals("aar", ignoreCase = true) }
        .flatMap { aar ->
            runCatching {
                ZipFile(aar).use { zip ->
                    zip.entries().asSequence()
                        .filterNot { it.isDirectory }
                        .filter { entryPattern.containsMatchIn(it.name) }
                        .map { entry ->
                            val bytes = zip.getInputStream(entry).use { it.readBytes() }
                            NativeNodeLibcxxCandidate(
                                source = source,
                                abi = nativeNodeReportAbiFromPath(entry.name),
                                path = "${aar.absolutePath}!/${entry.name}",
                                hash = bytes.sha256(),
                                length = entry.size,
                            )
                        }
                        .toList()
                }
            }.getOrDefault(emptyList()).asSequence()
        }
        .toList()
}

val nativeNodeDependencyReportAppDebug = tasks.register("nativeNodeDependencyReportAppDebug") {
    group = "verification"
    description = "Writes the appDebug native dependency report for Node.js/libc++ diagnostics."

    dependsOn("merge${nativeNodeReportVariantTaskSuffix}NativeLibs")
    outputs.file(layout.buildDirectory.file("reports/native-node/native-deps-$nativeNodeReportVariant.txt"))
    outputs.upToDateWhen { false }

    doLast {
        val reportFile = layout.buildDirectory.file("reports/native-node/native-deps-$nativeNodeReportVariant.txt").get().asFile
        reportFile.parentFile.mkdirs()

        val tools = findNativeNodeReportTools(rootProject.projectDir)
        if (tools == null) {
            reportFile.writeText(
                """
                Native Node dependency report: $nativeNodeReportVariant
                NDK $nativeNodeReportNdkVersion tools were not found.
                Checked sdk.dir from local.properties and ANDROID_HOME/ANDROID_SDK_ROOT.
                """.trimIndent()
            )
            logger.warn("Native Node dependency report skipped tool checks because NDK $nativeNodeReportNdkVersion tools were not found.")
            return@doLast
        }

        val sourceJniLibsRoot = projectDir.resolve("src/main/jniLibs")
        val mergedNativeLibsRoot = layout.buildDirectory
            .dir("intermediates/merged_native_libs/$nativeNodeReportVariant/merge${nativeNodeReportVariantTaskSuffix}NativeLibs/out/lib")
            .get()
            .asFile
        val cxxNativeLibsRoot = layout.buildDirectory
            .dir("intermediates/cxx")
            .get()
            .asFile
        val strippedNativeLibsRoot = layout.buildDirectory
            .dir("intermediates/stripped_native_libs/$nativeNodeReportVariant/strip${nativeNodeReportVariantTaskSuffix}DebugSymbols/out/lib")
            .get()
            .asFile
        val apkDir = layout.buildDirectory.dir("outputs/apk/app/debug").get().asFile
        val apkFiles = apkDir.listFiles { file -> file.isFile && file.extension == "apk" }
            ?.sortedBy(File::getName)
            .orEmpty()
        val apkExtractDir = layout.buildDirectory
            .dir("tmp/native-node-apk-libcxx")
            .get()
            .asFile
        val apkLibcxxInfos = extractNativeNodeReportApkLibcxx(apkFiles, apkExtractDir)

        val sourceInfos = scanNativeNodeReportRoot(tools, "src/main/jniLibs", sourceJniLibsRoot)
        val cxxInfos = scanNativeNodeReportCxxOutputs(tools, "intermediates/cxx latest", cxxNativeLibsRoot)
        val latestCxxBridgeInfosByAbi = latestNativeNodeReportBridgeInfoByAbi(cxxInfos)
        val mergedInfos = scanNativeNodeReportRoot(tools, "merged_native_libs/$nativeNodeReportVariant", mergedNativeLibsRoot)
        val mergedBridgeInfosByAbi = mergedInfos
            .filter { it.file.name == nativeNodeReportBridgeLibraryName }
            .associateBy { it.abi }
        val mergedLibcxxDefinedSymbolsByAbi = nativeNodeReportRequiredLibcxxAbis.associateWith { abi ->
            mergedNativeLibsRoot.resolve("$abi/libc++_shared.so")
                .takeIf(File::isFile)
                ?.let { parseNativeNodeReportDefinedSymbols(tools, it) }
                .orEmpty()
        }
        val libcxxCandidateRoots = listOf(
            "app/build/intermediates" to layout.buildDirectory.dir("intermediates").get().asFile,
            "Gradle cache files" to gradle.gradleUserHomeDir.resolve("caches"),
        )
        val libcxxCandidates = libcxxCandidateRoots.flatMap { (source, root) ->
            scanNativeNodeReportLibcxxFiles(source, root)
        } + scanNativeNodeReportAarLibcxxEntries(
            "project AAR entries",
            rootProject.projectDir,
            skippedDirNames = setOf(".bak", ".git", ".gradle"),
        ) +
                scanNativeNodeReportAarLibcxxEntries("Gradle cache AAR entries", gradle.gradleUserHomeDir.resolve("caches"))

        val report = buildString {
            appendLine("Native Node dependency report: $nativeNodeReportVariant")
            appendLine("Generated at: ${java.time.ZonedDateTime.now()}")
            appendLine("NDK reference: ${tools.ndkDir.absolutePath}")
            appendLine("Tools:")
            appendLine("  readelf: ${tools.readelf.absolutePath}")
            appendLine("  nm: ${tools.nm.absolutePath}")
            appendLine()

            appendLine("Scan roots:")
            appendLine("  src/main/jniLibs: ${sourceJniLibsRoot.absolutePath}")
            appendLine("  CMake outputs: ${cxxNativeLibsRoot.absolutePath}")
            appendLine("  merged native libs: ${mergedNativeLibsRoot.absolutePath}")
            appendLine("  stripped native libs: ${strippedNativeLibsRoot.absolutePath}")
            appendLine("  APK output dir: ${apkDir.absolutePath}")
            appendLine("  APK libc++ extraction dir: ${apkExtractDir.absolutePath}")
            appendLine()

            appendLine("libc++_shared.so provenance and packaging checks:")
            nativeNodeReportRequiredLibcxxAbis.forEach { abi ->
                val ndkLibcxx = nativeNodeReportNdkLibcxx(tools, abi)
                val sourceLibcxx = sourceJniLibsRoot.resolve("$abi/libc++_shared.so")
                val mergedLibcxx = mergedNativeLibsRoot.resolve("$abi/libc++_shared.so")
                val strippedLibcxx = strippedNativeLibsRoot.resolve("$abi/libc++_shared.so")
                val ndkSha = ndkLibcxx?.takeIf(File::isFile)?.sha256()
                val sourceSha = sourceLibcxx.takeIf(File::isFile)?.sha256()
                val mergedSha = mergedLibcxx.takeIf(File::isFile)?.sha256()
                val strippedSha = strippedLibcxx.takeIf(File::isFile)?.sha256()
                val mergedDefined = mergedLibcxxDefinedSymbolsByAbi.getValue(abi)
                val apkInfos = apkLibcxxInfos.filter { it.abi == abi }

                appendLine("  ABI: $abi")
                appendLine("    NDK libc++: ${ndkLibcxx?.absolutePath ?: "missing"}")
                appendLine("    src/main/jniLibs hash: ${sourceSha ?: "missing"}")
                appendLine("    merged hash: ${mergedSha ?: "missing"}")
                appendLine("    stripped hash: ${strippedSha ?: "missing"}")
                appendLine("    src matches NDK $nativeNodeReportNdkVersion: ${sourceSha != null && sourceSha == ndkSha}")
                appendLine("    merged matches NDK $nativeNodeReportNdkVersion: ${mergedSha != null && mergedSha == ndkSha}")
                appendLine("    merged matches src/main/jniLibs: ${mergedSha != null && mergedSha == sourceSha}")
                appendLine("    stripped matches merged: ${strippedSha != null && strippedSha == mergedSha}")
                nativeNodeReportNodeLibcxxSymbols.forEach { symbol ->
                    appendLine("    exports $symbol: ${symbol in mergedDefined}")
                }
                if (apkInfos.isEmpty()) {
                    appendLine("    APK entries: no app/debug APK entry found for this ABI")
                } else {
                    apkInfos.forEach { apkInfo ->
                        val apkDefined = parseNativeNodeReportDefinedSymbols(tools, apkInfo.extractedFile)
                        appendLine("    APK ${apkInfo.apkName} ${apkInfo.entryName} extracted: ${apkInfo.extractedFile.absolutePath}")
                        appendLine("    APK ${apkInfo.apkName} extracted hash: ${apkInfo.hash}")
                        appendLine("    APK ${apkInfo.apkName} extracted length: ${apkInfo.length}")
                        appendLine("    APK ${apkInfo.apkName} matches src/main/jniLibs: ${apkInfo.hash == sourceSha}")
                        appendLine("    APK ${apkInfo.apkName} matches merged: ${apkInfo.hash == mergedSha}")
                        appendLine("    APK ${apkInfo.apkName} matches stripped: ${apkInfo.hash == strippedSha}")
                        appendLine("    APK ${apkInfo.apkName} matches NDK $nativeNodeReportNdkVersion: ${apkInfo.hash == ndkSha}")
                        nativeNodeReportNodeLibcxxSymbols.forEach { symbol ->
                            appendLine("    APK ${apkInfo.apkName} exports $symbol: ${symbol in apkDefined}")
                        }
                    }
                }
            }
            appendLine()

            appendLine("libc++_shared.so candidate sources:")
            if (libcxxCandidates.isEmpty()) {
                appendLine("  none found")
            } else {
                libcxxCandidates
                    .distinctBy { listOf(it.source, it.path, it.hash) }
                    .sortedWith(compareBy<NativeNodeLibcxxCandidate> { it.source }.thenBy { it.abi }.thenBy { it.path })
                    .forEach { candidate ->
                        appendLine("  [${candidate.source}] ABI=${candidate.abi.ifBlank { "unknown" }} length=${candidate.length} hash=${candidate.hash}")
                        appendLine("    ${candidate.path}")
                    }
            }
            appendLine()

            appendLine("Packaging options relevant to libc++_shared.so:")
            appendLine("  app/build.gradle.kts configures resources.pickFirsts for lib/*/libc++_shared.so.")
            appendLine("  appDebug packaging uses strip${nativeNodeReportVariantTaskSuffix}DebugSymbols output; APK hashes are expected to match stripped_native_libs, not merged_native_libs, when stripping changes the file.")
            appendLine("  jniLibs.useLegacyPackaging = true.")
            appendLine()

            appendLine("$nativeNodeReportBridgeLibraryName libnode linkage summary:")
            val bridgeInfosForSummary = nativeNodeReportRequiredLibcxxAbis.flatMap { abi ->
                listOfNotNull(latestCxxBridgeInfosByAbi[abi], mergedBridgeInfosByAbi[abi])
            }
            val anyBridgeLinksLibnode = bridgeInfosForSummary.any { nativeNodeReportNodeLibraryName in it.needed }
            nativeNodeReportRequiredLibcxxAbis.forEach { abi ->
                appendLine("  ABI: $abi")
                appendNativeNodeReportBridgeDependencyLine("latest CMake output", latestCxxBridgeInfosByAbi[abi])
                appendNativeNodeReportBridgeDependencyLine("merged native libs", mergedBridgeInfosByAbi[abi])
            }
            appendLine("  Level 1 implication:")
            if (anyBridgeLinksLibnode) {
                appendLine("    WARNING: level=1 loading $nativeNodeReportBridgeLibraryName may also load $nativeNodeReportNodeLibraryName implicitly.")
                appendLine("    A crash at level=1 would be a combined bridge/libnode load failure, not a pure JNI bridge result.")
            } else {
                appendLine("    OK: inspected $nativeNodeReportBridgeLibraryName outputs do not DT_NEEDED $nativeNodeReportNodeLibraryName.")
                appendLine("    level=1 loading $nativeNodeReportBridgeLibraryName is not expected to load $nativeNodeReportNodeLibraryName implicitly.")
            }
            appendLine()

            appendLine("Native libraries:")
            (sourceInfos + latestCxxBridgeInfosByAbi.values + mergedInfos)
                .sortedWith(compareBy<NativeNodeSoInfo> { it.source }.thenBy { it.abi }.thenBy { it.file.name })
                .forEach { info ->
                    val cppUndefined = info.undefinedSymbols.filter(::isNativeNodeReportCppRuntimeSymbol)
                    appendLine("  [${info.source}] ${info.abi}/${info.file.name}")
                    appendLine("    DT_NEEDED: ${if (info.needed.isEmpty()) "none" else info.needed.joinToString()}")
                    appendLine("    depends on libc++_shared.so: ${"libc++_shared.so" in info.needed}")
                    appendLine("    C++ runtime-like undefined symbols: ${cppUndefined.isNotEmpty()} (count=${cppUndefined.size})")
                    appendLine("    C++ runtime-like undefined sample: ${formatNativeNodeReportSymbolSample(cppUndefined)}")
                }
            appendLine()

            appendLine("Focused native libraries libc++ satisfaction:")
            val focusedNamePatterns = listOf(
                "opencv",
                "mediainfo",
                "rapidocr",
                "ocr",
                "mlkit",
                "barhopper",
                "pngquant",
                "png16",
                "png.so",
            )
            mergedInfos
                .filter { info -> focusedNamePatterns.any { info.file.name.contains(it, ignoreCase = true) } }
                .sortedWith(compareBy<NativeNodeSoInfo> { it.abi }.thenBy { it.file.name })
                .forEach { info ->
                    val currentLibcxxSymbols = mergedLibcxxDefinedSymbolsByAbi[info.abi].orEmpty()
                    val libcxxUndefined = info.undefinedSymbols.filter(::shouldNativeNodeReportResolveFromLibcxx)
                    val missingFromCurrentLibcxx = libcxxUndefined.filterNot { it in currentLibcxxSymbols }
                    appendLine("  ${info.abi}/${info.file.name}")
                    appendLine("    depends on libc++_shared.so: ${"libc++_shared.so" in info.needed}")
                    appendLine("    libc++-resolved undefined symbols: ${libcxxUndefined.size}")
                    appendLine("    missing from current merged libc++_shared.so: ${formatNativeNodeReportSymbolSample(missingFromCurrentLibcxx)}")
                }
        }

        reportFile.writeText(report)
        logger.lifecycle("Native Node dependency report written to ${reportFile.absolutePath}")
    }
}

val nativeNodeSymbolArchiveReport = tasks.register("nativeNodeSymbolArchiveReport") {
    group = "verification"
    description = "Writes the appDebug native symbol archive report for Embedded Node crash diagnostics."

    dependsOn(
        "externalNativeBuild${nativeNodeReportVariantTaskSuffix}",
        "merge${nativeNodeReportVariantTaskSuffix}NativeLibs",
    )
    outputs.file(layout.buildDirectory.file("reports/native-node/native-symbol-archive-$nativeNodeReportVariant.txt"))
    outputs.upToDateWhen { false }

    doLast {
        val reportFile = layout.buildDirectory
            .file("reports/native-node/native-symbol-archive-$nativeNodeReportVariant.txt")
            .get()
            .asFile
        reportFile.parentFile.mkdirs()

        val tools = findNativeNodeReportTools(rootProject.projectDir)
        if (tools == null) {
            reportFile.writeText(
                """
                Native Node symbol archive report: $nativeNodeReportVariant
                NDK $nativeNodeReportNdkVersion tools were not found.
                Checked sdk.dir from local.properties and ANDROID_HOME/ANDROID_SDK_ROOT.
                embedded.node.native.symbol_archive.ready=false
                """.trimIndent()
            )
            logger.warn("Native Node symbol archive report skipped because NDK $nativeNodeReportNdkVersion tools were not found.")
            return@doLast
        }

        val sourceJniLibsRoot = projectDir.resolve("src/main/jniLibs")
        val cxxNativeLibsRoot = layout.buildDirectory.dir("intermediates/cxx").get().asFile
        val mergedNativeLibsRoot = layout.buildDirectory
            .dir("intermediates/merged_native_libs/$nativeNodeReportVariant/merge${nativeNodeReportVariantTaskSuffix}NativeLibs/out/lib")
            .get()
            .asFile
        val strippedNativeLibsRoot = layout.buildDirectory
            .dir("intermediates/stripped_native_libs/$nativeNodeReportVariant/strip${nativeNodeReportVariantTaskSuffix}DebugSymbols/out/lib")
            .get()
            .asFile

        val missingRequiredEntries = nativeNodeReportRequiredLibcxxAbis.flatMap { abi ->
            listOfNotNull(
                "src/main/jniLibs/$abi/$nativeNodeReportNodeLibraryName".takeUnless {
                    sourceJniLibsRoot.resolve("$abi/$nativeNodeReportNodeLibraryName").isFile
                },
                "CMake output $abi/$nativeNodeReportBridgeLibraryName".takeUnless {
                    latestNativeNodeReportLibraryFile(cxxNativeLibsRoot, abi, nativeNodeReportBridgeLibraryName)?.isFile == true
                },
                "merged_native_libs/$abi/$nativeNodeReportNodeLibraryName".takeUnless {
                    mergedNativeLibsRoot.resolve("$abi/$nativeNodeReportNodeLibraryName").isFile
                },
                "merged_native_libs/$abi/$nativeNodeReportBridgeLibraryName".takeUnless {
                    mergedNativeLibsRoot.resolve("$abi/$nativeNodeReportBridgeLibraryName").isFile
                },
            )
        }
        val ready = missingRequiredEntries.isEmpty()

        val report = buildString {
            appendLine("Native Node symbol archive report: $nativeNodeReportVariant")
            appendLine("Generated at: ${java.time.ZonedDateTime.now()}")
            appendLine()

            appendLine("Tools:")
            appendLine("  NDK: ${tools.ndkDir.absolutePath}")
            appendLine("  readelf: ${tools.readelf.absolutePath}")
            appendLine("  nm: ${tools.nm.absolutePath}")
            appendLine()

            appendLine("Scan roots:")
            appendLine("  src/main/jniLibs: ${sourceJniLibsRoot.absolutePath}")
            appendLine("  CMake outputs: ${cxxNativeLibsRoot.absolutePath}")
            appendLine("  merged native libs: ${mergedNativeLibsRoot.absolutePath}")
            appendLine("  stripped native libs: ${strippedNativeLibsRoot.absolutePath}")
            appendLine()

            nativeNodeReportRequiredLibcxxAbis.forEach { abi ->
                appendLine("ABI: $abi")
                appendLine("  $nativeNodeReportNodeLibraryName:")
                appendNativeNodeSymbolArchiveEntry(
                    tools,
                    "src/main/jniLibs",
                    sourceJniLibsRoot.resolve("$abi/$nativeNodeReportNodeLibraryName"),
                )
                appendNativeNodeSymbolArchiveEntry(
                    tools,
                    "merged native libs",
                    mergedNativeLibsRoot.resolve("$abi/$nativeNodeReportNodeLibraryName"),
                )
                appendNativeNodeSymbolArchiveEntry(
                    tools,
                    "stripped native libs",
                    strippedNativeLibsRoot.resolve("$abi/$nativeNodeReportNodeLibraryName"),
                )
                appendLine("  $nativeNodeReportBridgeLibraryName:")
                appendNativeNodeSymbolArchiveEntry(
                    tools,
                    "latest CMake output",
                    latestNativeNodeReportLibraryFile(cxxNativeLibsRoot, abi, nativeNodeReportBridgeLibraryName),
                )
                appendNativeNodeSymbolArchiveEntry(
                    tools,
                    "merged native libs",
                    mergedNativeLibsRoot.resolve("$abi/$nativeNodeReportBridgeLibraryName"),
                )
                appendNativeNodeSymbolArchiveEntry(
                    tools,
                    "stripped native libs",
                    strippedNativeLibsRoot.resolve("$abi/$nativeNodeReportBridgeLibraryName"),
                )
                appendLine()
            }

            appendLine("Summary:")
            appendLine("  embedded.node.native.symbol_archive.report=${reportFile.absolutePath}")
            appendLine("  embedded.node.native.symbol_archive.ready=$ready")
            appendLine("  embedded.node.native.symbol_archive.required_abis=${nativeNodeReportRequiredLibcxxAbis.joinToString()}")
            appendLine(
                "  embedded.node.native.symbol_archive.missing=" +
                        if (missingRequiredEntries.isEmpty()) "none" else missingRequiredEntries.joinToString()
            )
            appendLine()

            appendLine("Notes:")
            appendLine("  Keep this report with the APK, git commit, logcat, and tombstone or bugreport for crash triage.")
            appendLine("  For bridge crashes, prefer the latest CMake output symbol_file path when it has .debug_* or .symtab sections.")
            appendLine("  For libnode crashes, use the Build ID and sha256 to match the exact prebuilt libnode.so artifact.")
        }

        reportFile.writeText(report)
        logger.lifecycle("Native Node symbol archive report written to ${reportFile.absolutePath}")
        logger.lifecycle("Native Node symbol archive ready: $ready")
    }
}

tasks.matching { it.name == "package${nativeNodeReportVariantTaskSuffix}" }.configureEach {
    finalizedBy(nativeNodeDependencyReportAppDebug)
}
