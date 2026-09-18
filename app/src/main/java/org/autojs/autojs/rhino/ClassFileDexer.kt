package org.autojs.autojs.rhino

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import com.android.tools.r8.CompilationMode
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.OutputMode
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import java.io.File
import java.io.IOException
import kotlin.random.Random
import com.legacy.android.dx.command.dexer.Main as LegacyDxMain

/**
 * The single place where the host turns class files into DEX at runtime.
 *
 * API 26+ uses the bundled D8 (`com.android.tools.r8`) for everything: `runtime.loadJar`, AAR loading and the
 * classes Rhino generates for `JavaAdapter` / `defineClass`. API 24 / 25 cannot run D8 at all, because D8 needs
 * `java.nio.file` and the host desugars with the plain `desugar_jdk_libs` (no `nio` variant), so those two API levels
 * keep the repackaged legacy dx 1.7.0 (`com.legacy.android.dx`; the platform's own `com.android.dex` classes shadow an
 * unrepackaged dx there). Evidence: `docs/dev/evidence/apk-size-p5-dexer-20260918.md`.
 *
 * zh-CN: 宿主运行时把 class 文件转成 DEX 的唯一入口. API 26+ 全部使用内置的 D8 (`com.android.tools.r8`): `runtime.loadJar`,
 * AAR 加载, 以及 Rhino 为 `JavaAdapter` / `defineClass` 生成的类. API 24 / 25 完全无法运行 D8 (D8 需要 `java.nio.file`, 而宿主
 * 使用的是不含 `nio` 变体的 `desugar_jdk_libs`), 因此这两个 API 级别保留重打包的旧版 dx 1.7.0 (`com.legacy.android.dx`;
 * 平台自带的 `com.android.dex` 类会遮蔽未重打包的 dx). 证据见 `docs/dev/evidence/apk-size-p5-dexer-20260918.md`.
 */
object ClassFileDexer {

    /** D8 needs `java.nio.file`, which exists on the device only from API 26. */
    @JvmStatic
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    val isD8Available: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    private val DEX_NAME = Regex("classes\\d*\\.dex")

    /**
     * Runs D8 on [program] (a jar, zip or class file) and writes `classes*.dex` into [outputDir].
     * Failures surface as exceptions (`CompilationFailedException` and friends); nothing here calls `System.exit`.
     *
     * zh-CN: 对 [program] (jar, zip 或 class 文件) 运行 D8, 把 `classes*.dex` 写入 [outputDir]. 失败以异常抛出
     * (`CompilationFailedException` 等), 这里不会调用 `System.exit`.
     */
    @JvmStatic
    @JvmOverloads
    @RequiresApi(Build.VERSION_CODES.O)
    @Throws(IOException::class)
    fun runD8(
        program: File,
        outputDir: File,
        minApi: Int = Build.VERSION.SDK_INT,
        runtimeLibraries: List<File> = collectRuntimeLibraries(),
        classpath: List<File> = emptyList(),
    ): List<File> {
        val builder = D8Command.builder()
            .addProgramFiles(program.toPath())
            .setOutput(outputDir.toPath(), OutputMode.DexIndexed)
            .setMode(CompilationMode.RELEASE)
            .setMinApiLevel(minApi)
        classpath.forEach { builder.addClasspathFiles(it.toPath()) }
        runtimeLibraries.forEach { builder.addLibraryFiles(it.toPath()) }
        D8.run(builder.build())
        return dexOutputs(outputDir)
    }

    /** The `classes*.dex` files D8 left in [outputDir], in `classes.dex`, `classes2.dex`, ... order. */
    @JvmStatic
    fun dexOutputs(outputDir: File): List<File> =
        outputDir.listFiles { _, name -> DEX_NAME.matches(name) }?.sortedBy { it.name } ?: emptyList()

    /**
     * Packs [dexFiles] into [outputJar] as top-level `classes*.dex` entries, the layout `DexClassLoader` reads and
     * the one dx produced with `jarOutput`.
     *
     * zh-CN: 把 [dexFiles] 作为顶层 `classes*.dex` 条目打包进 [outputJar], 即 `DexClassLoader` 读取的布局, 也是 dx
     * 以 `jarOutput` 写出的布局.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun packDexJar(dexFiles: List<File>, outputJar: File) {
        if (dexFiles.isEmpty()) throw IOException("No classes*.dex to pack into ${outputJar.path}")
        if (outputJar.exists() && !outputJar.delete()) throw IOException("Failed to replace ${outputJar.path}")
        val zip = ZipFile(outputJar)
        dexFiles.forEach { dex ->
            dex.inputStream().use { input ->
                zip.addStream(input, ZipParameters().apply { fileNameInZip = dex.name })
            }
        }
    }

    /**
     * Converts the classes in [input] (a jar of `.class` entries) into [outputJar] (a jar of `classes*.dex`), using D8
     * on API 26+ and legacy dx on API 24 / 25. [workDir] receives a temporary D8 output directory.
     *
     * zh-CN: 把 [input] (含 `.class` 条目的 jar) 转成 [outputJar] (含 `classes*.dex` 的 jar): API 26+ 用 D8, API 24 / 25
     * 用旧版 dx. [workDir] 用于存放临时的 D8 输出目录.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun convertToDexJar(input: File, outputJar: File, workDir: File) {
        if (!isD8Available) {
            convertWithLegacyDx(input, outputJar)
            return
        }
        val tmpOut = File(workDir, "${outputJar.nameWithoutExtension}_d8_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}")
        if (!tmpOut.mkdirs()) throw IOException("Failed to create temp output directory: ${tmpOut.path}")
        try {
            packDexJar(runD8(input, tmpOut), outputJar)
        } finally {
            tmpOut.deleteRecursively()
        }
    }

    /** API 24 / 25 only: legacy dx 1.7.0 with `jarOutput`, the pre-2026 behaviour of these API levels. */
    @JvmStatic
    @Throws(IOException::class)
    fun convertWithLegacyDx(input: File, outputJar: File) {
        val status = LegacyDxMain.run(LegacyDxMain.Arguments().apply {
            fileNames = arrayOf(input.path)
            outName = outputJar.path
            jarOutput = true
        })
        if (status != 0 || !outputJar.isFile) {
            throw IOException("Legacy dx failed (status $status) for ${input.path}")
        }
    }

    /**
     * Boot class path jars readable by the app, handed to D8 as library input.
     * zh-CN: 应用可读的引导类路径 jar, 作为 D8 的 library 输入.
     */
    @JvmStatic
    fun collectRuntimeLibraries(): List<File> = buildList {
        System.getProperty("java.boot.class.path")?.let(::add)
        System.getenv("BOOTCLASSPATH")?.let(::add)
        System.getenv("DEX2OATBOOTCLASSPATH")?.let(::add)
    }.asSequence()
        .flatMap { it.split(File.pathSeparator).asSequence() }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map(::File)
        .filter { it.exists() && it.canRead() }
        .distinctBy { it.path }
        .toList()

}
