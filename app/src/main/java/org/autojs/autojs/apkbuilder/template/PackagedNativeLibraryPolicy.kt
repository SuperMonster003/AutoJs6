package org.autojs.autojs.apkbuilder.template

import org.autojs.autojs.core.plugin.nativealign.ElfDynamicInfo
import java.io.File
import java.io.IOException

/**
 * Decides which file a packaged APK receives for every `lib/<abi>/<name>` that the host and the
 * selected plugins provide (docs/dev/opencv-ocr-runtime-decoupling-roadmap.md, P1.1 / P1.2).
 *
 * - Identical bytes from several sources are packaged once.
 * - Different bytes under the same name stop the build, except for `libc++_shared.so`.
 * - `libc++_shared.so` is the newest runtime among the sources, ordered by the toolchain build
 *   number in its Android ident note, with the host copy preferred on ties. Every packaged library
 *   that needs it must find its `std::__ndk1` symbols in the chosen runtime.
 *
 * The logic is pure JVM code. [RemoteApkBuildRequestPreparer] stages the candidates and turns the
 * typed failures into localized messages.
 *
 * zh-CN: 决定打包 APK 中每个 `lib/<abi>/<name>` 采用哪份文件. 字节相同的多个来源只写入一份;
 * 同名不同内容时终止打包, `libc++_shared.so` 除外. `libc++_shared.so` 取 Android ident 注记中工具链
 * 构建号最新的一份, 相同时优先宿主副本; 所有依赖它的库都必须能在所选运行时中找到 `std::__ndk1` 符号.
 */
internal object PackagedNativeLibraryPolicy {

    const val CXX_SHARED = "libc++_shared.so"
    private const val LIBCXX_NAMESPACE_MARKER = "__ndk1"
    private const val MISSING_SYMBOL_SAMPLE_SIZE = 3

    data class Source(
        val label: String,
        val packageName: String?,
        val versionName: String? = null,
        val isHost: Boolean = false,
    ) {
        val description: String
            get() = buildString {
                append(label)
                if (packageName != null && packageName != label) append(" (").append(packageName).append(')')
                if (!versionName.isNullOrBlank()) append(' ').append(versionName)
            }
    }

    data class Candidate(
        val abi: String,
        val name: String,
        val source: Source,
        val sha256: String,
        val file: File,
    ) {
        val entryName: String
            get() = "lib/$abi/$name"
    }

    data class Selected(
        val abi: String,
        val name: String,
        val file: File,
        val sha256: String,
        /** Every source that provides exactly these bytes, in candidate order. */
        val sources: List<Source>,
    ) {
        val entryName: String
            get() = "lib/$abi/$name"
    }

    sealed class Failure(message: String) : IOException(message) {

        /** Two sources provide different bytes for [entryName]. */
        class Conflict(
            val entryName: String,
            val first: Source,
            val second: Source,
        ) : Failure("$entryName differs between ${first.description} and ${second.description}")

        /** [library] needs `std::__ndk1` symbols that the packaged `libc++_shared.so` lacks. */
        class CxxRuntimeTooOld(
            val library: Selected,
            val runtime: Selected,
            val missingSymbols: List<String>,
        ) : Failure(
            "${library.entryName} from ${library.sources.first().description} needs a newer $CXX_SHARED than the one from " +
                    "${runtime.sources.first().description}; ${missingSymbols.size} symbols are missing, such as " +
                    missingSymbols.take(MISSING_SYMBOL_SAMPLE_SIZE).joinToString(", "),
        )
    }

    /**
     * @param dynamicInfo returns the dynamic linking facts of a staged file, or null when they are
     *        unavailable. Unreadable libraries are not checked against the C++ runtime.
     */
    @Throws(Failure::class)
    fun resolve(
        candidates: List<Candidate>,
        dynamicInfo: (File) -> ElfDynamicInfo?,
    ): List<Selected> {
        val selected = candidates
            .groupBy { it.abi to it.name }
            .map { (key, group) ->
                val (abi, name) = key
                val byContent = group.groupBy(Candidate::sha256)
                when {
                    byContent.size == 1 -> group.toSelected()
                    name == CXX_SHARED -> selectCxxRuntime(byContent.values, dynamicInfo)
                    else -> {
                        val (first, second) = byContent.values.take(2).map(List<Candidate>::first)
                        throw Failure.Conflict("lib/$abi/$name", first.source, second.source)
                    }
                }
            }
        verifyCxxRuntime(selected, dynamicInfo)
        return selected
    }

    private fun List<Candidate>.toSelected(): Selected {
        val first = first()
        return Selected(first.abi, first.name, first.file, first.sha256, map(Candidate::source).distinct())
    }

    private fun selectCxxRuntime(
        groups: Collection<List<Candidate>>,
        dynamicInfo: (File) -> ElfDynamicInfo?,
    ): Selected = groups
        .maxWithOrNull(
            compareBy<List<Candidate>> { group -> dynamicInfo(group.first().file)?.androidIdent?.buildNumber ?: 0L }
                .thenBy { group -> group.any { it.source.isHost } },
        )!!
        .toSelected()

    private fun verifyCxxRuntime(selected: List<Selected>, dynamicInfo: (File) -> ElfDynamicInfo?) {
        selected.groupBy(Selected::abi).values.forEach { libraries ->
            val runtime = libraries.firstOrNull { it.name == CXX_SHARED } ?: return@forEach
            val provided by lazy { dynamicInfo(runtime.file)?.definedSymbols.orEmpty() }
            val providedElsewhere by lazy {
                libraries.filter { it !== runtime }.flatMapTo(HashSet()) { dynamicInfo(it.file)?.definedSymbols.orEmpty() }
            }
            libraries.filter { it !== runtime }.forEach { library ->
                val info = dynamicInfo(library.file) ?: return@forEach
                if (CXX_SHARED !in info.needed) return@forEach
                val missing = info.requiredSymbols
                    .filter { LIBCXX_NAMESPACE_MARKER in it && it !in provided }
                    .filter { it !in providedElsewhere }
                    .sorted()
                if (missing.isNotEmpty()) {
                    throw Failure.CxxRuntimeTooOld(library, runtime, missing)
                }
            }
        }
    }
}
