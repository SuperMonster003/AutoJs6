package org.autojs.autojs.rhino

import android.os.Build
import android.util.Log
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
 * The bundled D8 (`com.android.tools.r8`) runs on every API level: `runtime.loadJar`, AAR loading and, from API 26, the
 * classes Rhino generates for `JavaAdapter` / `defineClass`. On API 24 / 25 its `java.nio.file` comes from
 * `desugar_jdk_libs_nio`. Those two API levels keep the repackaged legacy dx 1.7.0 (`com.legacy.android.dx`; the
 * platform's own `com.android.dex` classes shadow an unrepackaged dx there) as the fallback of D8, and as the first
 * converter of generated classes, because the first D8 run of a process costs about five times as much as the first dx
 * run and scripts create adapters far more often than they load jars. Evidence:
 * `docs/dev/evidence/loadjar-d8-api24-20261101.md`.
 *
 * zh-CN: 宿主运行时把 class 文件转成 DEX 的唯一入口. 内置的 D8 (`com.android.tools.r8`) 在所有 API 级别运行: `runtime.loadJar`,
 * AAR 加载, 以及 API 26 起 Rhino 为 `JavaAdapter` / `defineClass` 生成的类. API 24 / 25 上 D8 所需的 `java.nio.file` 来自
 * `desugar_jdk_libs_nio`. 这两个 API 级别保留重打包的旧版 dx 1.7.0 (`com.legacy.android.dx`; 平台自带的 `com.android.dex` 类
 * 会遮蔽未重打包的 dx), 作为 D8 的回退, 以及生成类的首选转换器: 进程内首次运行 D8 的开销约为首次运行 dx 的五倍, 而脚本创建适配器
 * 远比加载 jar 频繁. 证据见 `docs/dev/evidence/loadjar-d8-api24-20261101.md`.
 */
object ClassFileDexer {

    private const val TAG = "ClassFileDexer"

    /** API 24 / 25, the API levels that keep legacy dx. zh-CN: 保留旧版 dx 的 API 24 / 25. */
    private val hasLegacyDx: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.O

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
     * Converts the classes in [input] (a jar of `.class` entries) into [outputJar] (a jar of `classes*.dex`) with D8. On
     * API 24 / 25 legacy dx takes over when D8 fails, and the failure of D8 is the one reported when dx fails as well.
     * [workDir] receives a temporary D8 output directory.
     *
     * zh-CN: 用 D8 把 [input] (含 `.class` 条目的 jar) 转成 [outputJar] (含 `classes*.dex` 的 jar). API 24 / 25 上 D8 失败时由
     * 旧版 dx 接手, dx 也失败时报告 D8 的失败. [workDir] 用于存放临时的 D8 输出目录.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun convertToDexJar(input: File, outputJar: File, workDir: File) {
        try {
            convertWithD8(input, outputJar, workDir)
        } catch (failure: Throwable) {
            if (failure is VirtualMachineError || !hasLegacyDx) throw failure
            Log.w(TAG, "D8 failed, trying legacy dx: ${input.path}", failure)
            try {
                convertWithLegacyDx(input, outputJar)
            } catch (legacyFailure: IOException) {
                failure.addSuppressed(legacyFailure)
                throw failure
            }
        }
    }

    /**
     * [convertToDexJar] for the classes the host generates at runtime: on API 24 / 25 legacy dx goes first, which spares
     * the script the first D8 run of the process, and D8 takes over for what dx refuses (class file version 52 and later).
     *
     * zh-CN: 用于宿主运行时生成的类的 [convertToDexJar]: API 24 / 25 上先用旧版 dx, 免去脚本等待进程内首次运行 D8, dx 拒绝的类
     * (class 文件版本 52 及以上) 由 D8 接手.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun convertGeneratedToDexJar(input: File, outputJar: File, workDir: File) {
        if (hasLegacyDx) {
            try {
                convertWithLegacyDx(input, outputJar)
                return
            } catch (failure: IOException) {
                Log.w(TAG, "Legacy dx failed, trying D8: ${input.path}", failure)
            }
        }
        convertWithD8(input, outputJar, workDir)
    }

    private fun convertWithD8(input: File, outputJar: File, workDir: File) {
        val tmpOut = File(workDir, "${outputJar.nameWithoutExtension}_d8_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}")
        if (!tmpOut.mkdirs()) throw IOException("Failed to create temp output directory: ${tmpOut.path}")
        try {
            packDexJar(runD8(input, tmpOut), outputJar)
        } finally {
            tmpOut.deleteRecursively()
        }
    }

    /** API 24 / 25 only: legacy dx 1.7.0 with `jarOutput`. */
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
