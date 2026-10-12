package org.autojs.autojs.rhino

import android.os.Build
import android.system.Os
import android.util.Log
import androidx.annotation.RequiresApi
import com.android.tools.r8.CompilationMode
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.OutputMode
import dalvik.system.DexClassLoader
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.ZipParameters
import org.autojs.autojs.core.plugin.dex.AndroidDexCompilerRuntime
import org.autojs.autojs.core.plugin.dex.DexCompilerClasspathDecisionRunner
import org.autojs.autojs.core.plugin.dex.DexCompilerLocalD8Plan
import org.autojs.autojs.core.plugin.dex.DexCompilerLocalRuntimeLibraryFingerprint
import org.autojs.autojs.core.plugin.dex.DexCompilerRetainedInput
import org.autojs.autojs.core.plugin.dex.DexCompilerRuntimeAndroidTestInstrumentation
import org.autojs.autojs.core.plugin.dex.DexCompilerRuntimeDecision
import org.autojs.autojs.core.plugin.dex.DexCompilerRuntimeDiagnostics
import org.autojs.autojs.core.plugin.dex.DexCompilerVerifiedArtifact
import org.autojs.autojs.core.plugin.r8.R8CompilerLoadableDexZip
import org.autojs.autojs.pio.PFiles.deleteFilesOfDir
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.util.MD5Utils.md5
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R
import org.mozilla.javascript.GeneratedClassLoader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileNotFoundException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarInputStream
import kotlin.random.Random

/**
 * Created by Stardust on Apr 5, 2017.
 * Transformed by SuperMonster003 on Jul 5, 2023.
 * Modified by SuperMonster003 as of Jul 5, 2023.
 * Modified by LYS86 (https://github.com/LYS86) as of Dec 26, 2025.
 * Modified by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) as of Mar 20, 2026.
 */
/**
 * Create a new instance with the given parent classloader and cache directory
 *
 * @param parent   the parent
 * @param cacheDir the cache directory
 * @param clearCacheOnInit internal benchmark seam; the production constructor always clears
 */
class AndroidClassLoader internal constructor(
    private val parent: ClassLoader,
    private val cacheDir: File,
    private val clearCacheOnInit: Boolean,
) : ClassLoader(), GeneratedClassLoader {

    constructor(parent: ClassLoader, cacheDir: File) : this(
        parent = parent,
        cacheDir = cacheDir,
        clearCacheOnInit = true,
    )

    private val mDexClassLoaders = ConcurrentHashMap<String, DexClassLoader>()

    // Ensure per-jar conversion is thread-safe.
    // zh-CN: 确保对同一个 jar 的转换过程线程安全.
    private val conversionLocks = ConcurrentHashMap<String, Any>()

    init {
        AndroidJvmCompatibility.ensureInitialized()
        if (clearCacheOnInit && cacheDir.exists()) {
            deleteFilesOfDir(cacheDir)
        } else if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            Log.e(TAG, "dir.mkdirs() failed")
        }
    }

    /**
     * Might be thrown in any Rhino method that loads bytecode if the loading failed
     */
    class FatalLoadingException internal constructor(t: Throwable?) : RuntimeException("Failed to define class", t)

    @Throws(FileNotFoundException::class)
    fun loadDex(file: File): DexClassLoader {
        val originalPath = file.path
        Log.d(TAG, "loadDex: file = $originalPath")

        if (!file.exists() || !file.canRead()) {
            throw FileNotFoundException(str(R.string.file_not_exist_or_readable_with_path, originalPath))
        }

        // @Hint by SuperMonster003 on Nov 30, 2023.
        //  ! Try to avoid the exception which looks like below (API Level 34+):
        //  # java.lang.SecurityException: Writable dex file '/data/user/0/org.autojs.autojs6/cache/classes/xxx.jar' is not allowed.
        //  ! zh-CN: 尝试避免如下异常 (API 级别 34+):
        //  # java.lang.SecurityException: 不允许可写的 dex 文件 '/data/user/0/org.autojs.autojs6/cache/classes/xxx.jar'.
        //  !
        // @Hint by SuperMonster003 on Jan 13, 2025.
        //  ! Copy the dex file to the cache path and set it as read-only.
        //  ! zh-CN: 将 dex 文件复制到缓存路径并设置只读.
        //  # file.setReadOnly()
        val safeDexFile = File(cacheDir, "safe_${file.name}")
        try {
            if (!safeDexFile.exists() || md5(file) != md5(safeDexFile)) {
                file.copyTo(safeDexFile, overwrite = true)
            }
            if (!safeDexFile.setReadOnly()) {
                Log.e(TAG, "Failed to set file as read-only: ${safeDexFile.path}")
            }
        } catch (e: IOException) {
            throw FatalLoadingException(e)
        }

        return DexClassLoader(safeDexFile.path, optimizationDirectory(), null, parent).also {
            mDexClassLoaders[safeDexFile.path] = it
        }
    }

    private fun optimizationDirectory(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return cacheDir.path
        // Before API 26, ART derives its output name from the input basename and uses .dex.
        // zh-CN: API 26 之前 ART 用输入文件名生成 .dex 输出, 必须与原始 DEX 所在目录分开.
        val directory = File(cacheDir, "optimized")
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Unable to create DEX optimization directory: ${directory.path}")
        }
        return directory.path
    }

    @Throws(IOException::class, FileNotFoundException::class)
    fun loadJar(jar: File): DexClassLoader {
        return loadJarInternal(jar, allowExperimentalCompiler = false)
    }

    /** Production runtime.loadJar entry; AAR, defineClass, and compatibility helpers stay local. */
    @Throws(IOException::class, FileNotFoundException::class)
    internal fun loadJarWithExperimentalCompiler(jar: File): DexClassLoader {
        return loadJarInternal(jar, allowExperimentalCompiler = true)
    }

    /**
     * Dormant R3 seam for one program plus an ordered compile-time-only classpath.
     *
     * Classpath JARs are inputs to D8 only: they are not emitted, loaded, or inserted between the
     * resulting [DexClassLoader] and this loader's existing [parent]. Program references therefore
     * remain loadable only when their runtime definitions are already visible from that base parent.
     */
    @Throws(IOException::class, FileNotFoundException::class)
    internal fun loadJarWithExperimentalCompilerClasspath(
        jar: File,
        orderedClasspath: Collection<File>,
    ): DexClassLoader {
        val classpath = orderedClasspath.toList()
        require(classpath.isNotEmpty()) { "Ordered classpath must not be empty" }
        throwIfInterrupted()
        val coordinator = AndroidDexCompilerRuntime.coordinatorOrNull()
            ?: throw IOException("DEX compiler input snapshot store is unavailable")
        val decision = coordinator.compileWithClasspath(jar, classpath)
        return DexCompilerClasspathDecisionRunner.consume(
            decision = decision,
            loadPublished = ::loadVerifiedDexZip,
            loadLocal = ::loadRetainedInputSetLocallyWithD8,
            checkInterrupted = ::throwIfInterrupted,
            onUseLocal = { local ->
                Log.i(
                    TAG,
                    "DEX compiler classpath fallback: reason=${local.reason}, " +
                        "failure=${local.experimentalFailure}, remote=${local.remoteFailure}",
                )
            },
            onLocalLoaded = DexCompilerRuntimeDiagnostics::recordFallback,
            onPublishedLoaded = { published ->
                DexCompilerRuntimeDiagnostics.recordPublished(published.cacheHit)
            },
            onPublishedLoadFailure = { failure ->
                Log.e(
                    TAG,
                    "Verified classpath compilation could not be loaded; using pinned local D8",
                    failure,
                )
            },
            onPublishedFallbackLoaded = {
                DexCompilerRuntimeDiagnostics.recordPublishedLoadFallback()
            },
            cancelled = { throw ScriptInterruptedException() },
        )
    }

    @Throws(IOException::class, FileNotFoundException::class)
    private fun loadJarInternal(
        jar: File,
        allowExperimentalCompiler: Boolean,
    ): DexClassLoader {
        Log.d(TAG, "loadJar: jar = ${jar.path}")
        if (!jar.exists() || !jar.canRead()) {
            throw FileNotFoundException(str(R.string.file_not_exist_or_readable_with_path, jar.path))
        }

        val testDecision = if (allowExperimentalCompiler) {
            DexCompilerRuntimeAndroidTestInstrumentation.compileDecisionOverrideOrNull(jar)
        } else {
            null
        }
        val coordinator = if (allowExperimentalCompiler) {
            AndroidDexCompilerRuntime.coordinatorOrNull()
        } else {
            null
        }
        val decision = testDecision ?: coordinator?.compile(jar)
        if (decision == null) {
            return loadJarLocallySynchronized(jar).also {
                DexCompilerRuntimeDiagnostics.recordBuiltIn()
            }
        }

        return when (decision) {
            is DexCompilerRuntimeDecision.Published -> {
                try {
                    try {
                        loadVerifiedDexZip(decision.artifact).also {
                            DexCompilerRuntimeDiagnostics.recordPublished(decision.cacheHit)
                        }
                    } catch (remoteLoadError: Exception) {
                        throwIfInterrupted(remoteLoadError)
                        runCatching { decision.artifact.invalidate() }
                        Log.e(TAG, "Verified DEX compiler output could not be loaded; using local compiler", remoteLoadError)
                        loadRetainedSnapshotLocally(decision.fallbackInput).also {
                            DexCompilerRuntimeDiagnostics.recordPublishedLoadFallback()
                        }
                    }
                } finally {
                    decision.fallbackInput.close()
                }
            }
            is DexCompilerRuntimeDecision.UseLocal -> {
                val retained = decision.fallbackInput
                try {
                    throwIfInterrupted()
                    Log.i(
                        TAG,
                        "DEX compiler provider fallback: reason=${decision.reason}, " +
                            "failure=${decision.experimentalFailure}, remote=${decision.remoteFailure}",
                    )
                    val loaded = if (retained == null) {
                        loadJarLocallySynchronized(jar)
                    } else {
                        loadRetainedSnapshotLocally(retained)
                    }
                    loaded.also { DexCompilerRuntimeDiagnostics.recordFallback(decision) }
                } finally {
                    retained?.close()
                }
            }
            DexCompilerRuntimeDecision.Cancelled -> throw ScriptInterruptedException()
        }
    }

    private fun loadRetainedSnapshotLocally(input: DexCompilerRetainedInput): DexClassLoader {
        throwIfInterrupted()
        val snapshot = input.resolve() ?: throw IOException("Pinned DEX compiler input snapshot is unavailable")
        if (snapshot.length() != input.sizeBytes || sha256Hex(snapshot) != input.sha256) {
            throw IOException("Pinned DEX compiler input snapshot changed")
        }
        throwIfInterrupted()
        return loadJarLocallySynchronized(snapshot)
    }

    private fun loadRetainedInputSetLocallyWithD8(input: DexCompilerRetainedInput): DexClassLoader {
        throwIfInterrupted()
        val inputSetSha256 = input.inputSetSha256
            ?: throw IOException("Pinned DEX compiler input-set fingerprint is unavailable")
        val resolved = try {
            input.resolveInputSet { Thread.currentThread().isInterrupted }
        } catch (fatal: VirtualMachineError) {
            throw fatal
        } catch (failure: Throwable) {
            throwIfInterrupted(failure)
            throw failure
        } ?: throw IOException("Pinned DEX compiler input set is unavailable")
        if (resolved.classpath.isEmpty()) {
            throw IOException("Pinned DEX compiler classpath is empty")
        }
        if (resolved.program.length() != input.sizeBytes || sha256HexInterruptible(resolved.program) != input.sha256) {
            throw IOException("Pinned DEX compiler program snapshot changed")
        }
        throwIfInterrupted()

        val runtimeLibraries = collectRuntimeLibrariesForD8()
        val runtimeLibrarySha256 = DexCompilerLocalRuntimeLibraryFingerprint.sha256(
            runtimeBuildFingerprint = Build.FINGERPRINT,
            orderedLibraries = runtimeLibraries,
            fileSha256 = ::sha256HexInterruptible,
        )
        val plan = DexCompilerLocalD8Plan(
            program = resolved.program,
            orderedClasspath = resolved.classpath,
            orderedRuntimeLibraries = runtimeLibraries,
            minimumApi = Build.VERSION.SDK_INT,
            inputSetSha256 = inputSetSha256,
            runtimeLibrarySha256 = runtimeLibrarySha256,
        )
        val lock = conversionLocks.getOrPut(plan.cacheKeySha256) { Any() }
        return synchronized(lock) {
            throwIfInterrupted()
            DexCompilerRuntimeAndroidTestInstrumentation.localCompilerStarted(plan.program)
            throwIfInterrupted()
            try {
                jarToDexR8(plan)
            } catch (fatal: VirtualMachineError) {
                throw fatal
            } catch (error: Throwable) {
                throwIfInterrupted(error)
                throw error
            }
        }
    }

    private fun loadJarLocallySynchronized(jar: File): DexClassLoader {
        val key = generateArtifactsCacheName(jar)
        val lock = conversionLocks.getOrPut(key) { Any() }
        return synchronized(lock) { loadJarLocally(jar) }
    }

    private fun loadJarLocally(jar: File): DexClassLoader {
        throwIfInterrupted()
        DexCompilerRuntimeAndroidTestInstrumentation.localCompilerStarted(jar)
        throwIfInterrupted()
        if (!ClassFileDexer.isD8Available) {
            // API 24 / 25: D8 cannot run there (no java.nio.file), legacy dx is the only converter.
            // zh-CN: API 24 / 25: D8 无法运行 (没有 java.nio.file), 旧版 dx 是唯一的转换器.
            return jarToDexRepacked(jar)
        }
        try {
            return jarToDexR8(jar)
        } catch (eR8: Throwable) {
            throwIfInterrupted(eR8)
            Log.e(TAG, "Failed: jarToDexR8", eR8)
        }
        throwIfInterrupted()
        // Second D8 attempt on a copy re-packed by zip4j (drops directory entries, normalises headers, keeps the last
        // of duplicate entries); this slot used to be the dx fallback.
        // zh-CN: 对 zip4j 重新打包的副本 (去掉目录条目, 规范化文件头, 重名条目保留最后一个) 再试一次 D8; 这个位置以前是 dx 回退.
        try {
            return jarToDexRepacked(jar)
        } catch (eRepacked: Throwable) {
            throwIfInterrupted(eRepacked)
            Log.e(TAG, "Failed: jarToDexRepacked", eRepacked)
            throw eRepacked
        }
    }

    private fun loadVerifiedDexZip(artifact: DexCompilerVerifiedArtifact): DexClassLoader {
        throwIfInterrupted()
        val source = artifact.openRead() ?: throw IOException("Verified DEX compiler artifact is unavailable")
        return loadVerifiedDexZipSource(
            outputSha256 = artifact.outputSha256,
            sizeBytes = artifact.sizeBytes,
            source = source,
            adoptionPrefix = "verified",
        )
    }

    /** R8-only loading seam; it never invokes the raw-JAR D8 / legacy dx conversion methods. */
    internal fun loadR8CompilerDexZip(artifact: R8CompilerLoadableDexZip): DexClassLoader {
        throwIfInterrupted()
        return loadVerifiedDexZipSource(
            outputSha256 = artifact.sha256,
            sizeBytes = artifact.sizeBytes,
            source = artifact.openRead(),
            adoptionPrefix = "r8_verified",
        )
    }

    private fun loadVerifiedDexZipSource(
        outputSha256: String,
        sizeBytes: Long,
        source: InputStream,
        adoptionPrefix: String,
    ): DexClassLoader {
        var primaryFailure: Throwable? = null
        var adoptedTarget: File? = null
        val adoption = try {
            val adoptionKey = "${adoptionPrefix}_$outputSha256"
            val lock = conversionLocks.getOrPut(adoptionKey) { Any() }
            synchronized(lock) {
                val target = File(cacheDir, "$adoptionKey.dex.zip")
                val targetMatches = try {
                    target.isFile && target.length() == sizeBytes &&
                        sha256HexInterruptible(target) == outputSha256
                } catch (_: Exception) {
                    false
                }
                if (!targetMatches) {
                    val temporary = File(cacheDir, "$adoptionKey-${UUID.randomUUID()}.partial")
                    try {
                        val digest = MessageDigest.getInstance("SHA-256")
                        var total = 0L
                        FileOutputStream(temporary).use { output ->
                            // Android 14 requires dynamically loaded code to be read-only. Mark the
                            // pathname before writing; this already-open descriptor remains the sole
                            // writer until the verified copy is synced and closed.
                            Os.chmod(temporary.absolutePath, READ_ONLY_MODE)
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                throwIfInterrupted()
                                val count = source.read(buffer)
                                if (count < 0) break
                                if (count == 0) continue
                                total = Math.addExact(total, count.toLong())
                                if (total > sizeBytes) {
                                    throw IOException("Verified DEX compiler artifact grew during adoption")
                                }
                                output.write(buffer, 0, count)
                                digest.update(buffer, 0, count)
                            }
                            output.fd.sync()
                        }
                        throwIfInterrupted()
                        val copiedSha256 = digest.digest().toHex()
                        if (total != sizeBytes || copiedSha256 != outputSha256) {
                            throw IOException("Verified DEX compiler artifact changed during adoption")
                        }
                        Os.rename(temporary.absolutePath, target.absolutePath)
                        val finalCopyMatches = try {
                            target.length() == sizeBytes && sha256HexInterruptible(target) == outputSha256
                        } catch (_: Exception) {
                            false
                        }
                        if (!finalCopyMatches) {
                            target.delete()
                            throw IOException("Adopted DEX compiler artifact failed final verification")
                        }
                    } finally {
                        temporary.delete()
                    }
                }
                Os.chmod(target.absolutePath, READ_ONLY_MODE)
                throwIfInterrupted()

                try {
                    val loader = DexClassLoader(target.path, optimizationDirectory(), null, parent)
                    throwIfInterrupted()
                    adoptedTarget = target
                    target to loader
                } catch (error: Throwable) {
                    target.delete()
                    throw error
                }
            }
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            try {
                closeInputPreservingPrimary(source, primaryFailure)
            } catch (closeFailure: Throwable) {
                adoptedTarget?.delete()
                throw closeFailure
            }
        }
        val (target, loader) = adoption
        try {
            throwIfInterrupted()
            mDexClassLoaders[target.path] = loader
            throwIfInterrupted()
            return loader
        } catch (failure: Throwable) {
            mDexClassLoaders.remove(target.path, loader)
            target.delete()
            throw failure
        }
    }

    private fun closeInputPreservingPrimary(input: InputStream, primary: Throwable?) {
        try {
            input.close()
        } catch (closeFailure: Throwable) {
            if (closeFailure === primary) return
            val closeWasInterrupted = closeFailure is InterruptedException ||
                ScriptInterruptedException.causedByInterrupt(closeFailure)
            if (closeWasInterrupted) Thread.currentThread().interrupt()
            if (primary == null) throw closeFailure
            if (closeFailure is VirtualMachineError && primary !is VirtualMachineError) {
                closeFailure.addSuppressed(primary)
                throw closeFailure
            }
            if (closeWasInterrupted && primary !is VirtualMachineError) {
                closeFailure.addSuppressed(primary)
                throw closeFailure
            }
            primary.addSuppressed(closeFailure)
        }
    }

    private fun throwIfInterrupted(cause: Throwable? = null) {
        val causedByInterrupt = cause != null && ScriptInterruptedException.causedByInterrupt(cause)
        if (causedByInterrupt) Thread.currentThread().interrupt()
        if (Thread.currentThread().isInterrupted) {
            if (cause is ScriptInterruptedException) throw cause
            throw if (cause == null) ScriptInterruptedException() else ScriptInterruptedException(cause)
        }
    }

    @Throws(IOException::class, FileNotFoundException::class)
    fun loadAar(aar: File): DexClassLoader {
        Log.d(TAG, "loadAar: aar = ${aar.path}")
        if (!aar.exists() || !aar.canRead()) {
            throw FileNotFoundException(str(R.string.file_not_exist_or_readable_with_path, aar.path))
        }

        val key = "aar_${generateArtifactsCacheName(aar)}"
        val lock = conversionLocks.getOrPut(key) { Any() }
        return synchronized(lock) {
            val mergedJar = File(cacheDir, "$key.jar")
            if (!mergedJar.exists()) {
                mergeAarToJar(aar, mergedJar)
            }
            loadJar(mergedJar)
        }
    }

    /**
     * Re-packs [jar] with zip4j and converts the copy with [dexJar] (D8 on API 26+, legacy dx on API 24 / 25).
     * zh-CN: 用 zip4j 重新打包 [jar], 再以 [dexJar] 转换副本 (API 26+ 用 D8, API 24 / 25 用旧版 dx).
     */
    @Throws(IOException::class, FileNotFoundException::class)
    private fun jarToDexRepacked(jar: File): DexClassLoader {
        val cacheName = generateArtifactsCacheName(jar)
        val cacheFile = File(cacheDir, "$cacheName.jar")
        if (cacheFile.exists()) {
            return loadDex(cacheFile)
        }
        try {
            val classFile = generateTempFile(jar.path)
            val zipFile = ZipFile(classFile)
            val jarFile = ZipFile(jar)
            for (header in jarFile.fileHeaders) {
                if (!header.isDirectory) {
                    zipFile.addStream(jarFile.getInputStream(header), ZipParameters().apply {
                        fileNameInZip = header.fileName
                    })
                }
            }
            val classLoader = dexJar(classFile, cacheFile)
            if (!classFile.delete()) {
                Log.e(TAG, "classFile.delete() failed")
            }
            return classLoader
        } catch (e: ZipException) {
            throw IOException(e)
        }
    }

    /**
     * Try to load a class. This will search all defined classes, all loaded jars and the parent class loader.
     *
     * @param name    the name of the class to load
     * @param resolve ignored
     * @return the class
     * @throws ClassNotFoundException if the class could not be found in any of the locations
     */
    @Throws(ClassNotFoundException::class)
    public override fun loadClass(name: String, resolve: Boolean): Class<*> {
        // Rhino's own name lookups of the aliased com.stardust names receive the host classes (roadmap P7.3,
        // StardustClassAliases); the VM and java.lang.Class.forName keep getting the class they asked for, because
        // ART rejects a loader that returns a class of a different name.
        // zh-CN: Rhino 自身查找别名表中的 com.stardust 类名时得到宿主类 (roadmap P7.3, StardustClassAliases); VM 与
        // java.lang.Class.forName 仍拿到所请求的类, 因为 ART 拒绝加载器返回名字不同的类.
        StardustClassAliases.resolve(name)?.let { target ->
            if (isRhinoLookup()) return parent.loadClass(target)
        }
        findLoadedClass(name)?.let { return it }
        for (dex in mDexClassLoaders.values) try {
            dex.loadClass(name)?.let { return it }
        } catch (e: Exception) {
            e.printStackTrace()
            continue
        }
        return parent.loadClass(name)
    }

    /**
     * True when the running [loadClass] call was issued by Rhino itself (`Kit.classOrNull` behind `Packages`,
     * `importClass`, `importPackage` and the other LiveConnect lookups). The VM links classes through this loader
     * without a Java frame above it and `java.lang.Class.forName` shows `java.lang.Class` as the caller; neither may
     * be answered with an aliased class. Only evaluated for the few names in [StardustClassAliases].
     * zh-CN: 当前 [loadClass] 调用是否由 Rhino 自身发起 (`Packages`, `importClass`, `importPackage` 等 LiveConnect 查找背后的
     * `Kit.classOrNull`). VM 链接类时上方没有 Java 栈帧, `java.lang.Class.forName` 的调用方显示为 `java.lang.Class`,
     * 两者都不能返回别名类. 只对 [StardustClassAliases] 中的少数名字求值.
     */
    private fun isRhinoLookup(): Boolean {
        for (frame in Thread.currentThread().stackTrace) {
            when (frame.className) {
                "dalvik.system.VMStack", "java.lang.Thread", "java.lang.ClassLoader", javaClass.name -> continue
                else -> return frame.className.startsWith("org.mozilla.javascript.")
            }
        }
        return false
    }

    /**
     * Does nothing
     *
     * @param aClass ignored
     */
    override fun linkClass(aClass: Class<*>?) {
        // doesn't make sense on android
    }

    /**
     * {@inheritDoc}
     */
    override fun defineClass(name: String, data: ByteArray): Class<*> {
        Log.d(TAG, "defineClass: name = $name data.length = ${data.size}")
        var classFile: File? = null
        return try {
            classFile = generateTempFile(name)
            ZipFile(classFile).addStream(ByteArrayInputStream(data), ZipParameters().apply {
                fileNameInZip = name.replace('.', '/') + ".class"
            })
            dexJar(classFile, null).loadClass(name)
        } catch (e: IOException) {
            throw FatalLoadingException(e)
        } catch (e: ClassNotFoundException) {
            throw FatalLoadingException(e)
        } finally {
            when {
                // Treat as a temporary file that has been generated.
                // zh-CN: 视为临时文件已生成.
                classFile != null -> {
                    if (!classFile.delete()) {
                        Log.e(TAG, "classFile.delete() failed")
                    }
                }
            }
        }
    }

    /**
     * Converts a jar of class files into a jar of `classes*.dex` and loads it: D8 on API 26+ (also the path of
     * every Rhino-generated class since 2026-09-18), legacy dx on API 24 / 25. See [ClassFileDexer].
     * zh-CN: 把 class 文件的 jar 转成 `classes*.dex` 的 jar 并加载: API 26+ 用 D8 (自 2026-09-18 起也是 Rhino 生成类的路径),
     * API 24 / 25 用旧版 dx. 见 [ClassFileDexer].
     */
    @Throws(IOException::class)
    private fun dexJar(classFile: File, dexFile: File?): DexClassLoader {
        val niceDexFile = dexFile ?: generateTempFile("dex-" + classFile.path)
        ClassFileDexer.convertToDexJar(classFile, niceDexFile, cacheDir)
        val loader = loadDex(niceDexFile)
        when (dexFile) {
            // Treat as a temporary file that has been generated.
            // zh-CN: 视为临时文件已生成.
            null -> {
                if (!niceDexFile.delete()) {
                    Log.e(TAG, "dexFile.delete() failed")
                }
            }
        }
        return loader
    }

    /** A fresh `.jar` path under the cache directory; any stale file of that name is removed first. */
    @Throws(IOException::class)
    private fun generateTempFile(name: String): File {
        val file = File(cacheDir, "${(name.hashCode() + System.currentTimeMillis())}.jar")
        if (file.exists() && !file.delete()) {
            Log.e(TAG, "file.delete() failed")
        }
        return file
    }

    private fun generateArtifactsCacheName(jar: File) = md5("${jar.path}_${jar.lastModified()}")

    @RequiresApi(Build.VERSION_CODES.O)
    @Throws(IOException::class, FileNotFoundException::class)
    private fun jarToDexR8(jar: File): DexClassLoader {
        val cacheName = generateArtifactsCacheName(jar)

        val dexFile = File(cacheDir, "$cacheName.dex")
        val dexJarFile = File(cacheDir, "$cacheName.jar")

        if (dexJarFile.exists()) {
            return loadDex(dexJarFile)
        }
        if (dexFile.exists()) {
            return loadDex(dexFile)
        }

        val tmpOut = File(cacheDir, "${cacheName}_tmp_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}")
        if (!tmpOut.mkdirs()) {
            throw IOException("Failed to create temp output directory: ${tmpOut.path}")
        }

        try {
            val dexFiles = ClassFileDexer.runD8(jar, tmpOut)
            if (dexFiles.isEmpty()) {
                throw IOException("D8 did not produce any classes*.dex")
            }

            return if (dexFiles.size == 1) {
                val classesDex = dexFiles.first()
                if (!classesDex.renameTo(dexFile)) {
                    dexFile.outputStream().use { out ->
                        classesDex.inputStream().use { it.copyTo(out) }
                    }
                }
                loadDex(dexFile)
            } else {
                // Multi-dex output: pack all classes*.dex into a single jar for DexClassLoader.
                // zh-CN: 多 dex 输出: 将所有 classes*.dex 打包到单个 jar 文件中供 DexClassLoader 使用.
                ClassFileDexer.packDexJar(dexFiles, dexJarFile)
                loadDex(dexJarFile)
            }
        } finally {
            tmpOut.deleteRecursively()
        }
    }

    /** D8-only implementation for the frozen ordered-classpath seam; it intentionally has no dx fallback. */
    @Throws(IOException::class, FileNotFoundException::class)
    private fun jarToDexR8(plan: DexCompilerLocalD8Plan): DexClassLoader {
        val cacheName = "classpath_${plan.cacheKeySha256}"
        val dexFile = File(cacheDir, "$cacheName.dex")
        val dexJarFile = File(cacheDir, "$cacheName.jar")

        if (dexJarFile.exists()) {
            throwIfInterrupted()
            return loadDexInterruptibly(dexJarFile)
        }
        if (dexFile.exists()) {
            throwIfInterrupted()
            return loadDexInterruptibly(dexFile)
        }

        val tmpOut = File(cacheDir, "${cacheName}_tmp_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}")
        if (!tmpOut.mkdirs()) {
            throw IOException("Failed to create temp output directory: ${tmpOut.path}")
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val builder = D8Command.builder()
                    .addProgramFiles(plan.program.toPath())
                    .setOutput(tmpOut.toPath(), OutputMode.DexIndexed)
                    .setMode(CompilationMode.RELEASE)
                    .setMinApiLevel(plan.minimumApi)
                plan.orderedClasspath.forEach { classpathJar ->
                    builder.addClasspathFiles(classpathJar.toPath())
                }
                plan.orderedRuntimeLibraries.forEach { library ->
                    builder.addLibraryFiles(library.toPath())
                }
                D8.run(builder.build())
            } else {
                D8.main(plan.commandLine(tmpOut).toTypedArray())
            }
            throwIfInterrupted()

            val dexFiles = tmpOut.listFiles { _, name ->
                Regex("classes\\d*\\.dex").matches(name)
            }?.sortedBy { it.name } ?: emptyList()
            if (dexFiles.isEmpty()) throw IOException("D8 did not produce any classes*.dex")
            throwIfInterrupted()

            val partial = File(cacheDir, "$cacheName-${UUID.randomUUID()}.partial")
            val published = try {
                if (dexFiles.size == 1) {
                    throwIfInterrupted()
                    val classesDex = dexFiles.first()
                    if (!classesDex.renameTo(dexFile)) {
                        FileOutputStream(partial).use { output ->
                            interruptibleInput(classesDex).use { input -> input.copyTo(output) }
                            throwIfInterrupted()
                            output.fd.sync()
                        }
                        throwIfInterrupted()
                        Os.rename(partial.absolutePath, dexFile.absolutePath)
                    }
                    dexFile
                } else {
                    val zip = ZipFile(partial)
                    dexFiles.forEach { dex ->
                        throwIfInterrupted()
                        interruptibleInput(dex).use { input ->
                            zip.addStream(input, ZipParameters().apply {
                                fileNameInZip = dex.name
                            })
                        }
                    }
                    throwIfInterrupted()
                    FileOutputStream(partial, true).use { output -> output.fd.sync() }
                    throwIfInterrupted()
                    Os.rename(partial.absolutePath, dexJarFile.absolutePath)
                    dexJarFile
                }
            } finally {
                partial.delete()
            }
            try {
                throwIfInterrupted()
                return loadDexInterruptibly(published)
            } catch (failure: Throwable) {
                published.delete()
                throw failure
            }
        } finally {
            tmpOut.deleteRecursively()
        }
    }

    /** Classpath-only adoption that cannot register a loader after observing cancellation. */
    private fun loadDexInterruptibly(file: File): DexClassLoader {
        throwIfInterrupted()
        if (!file.exists() || !file.canRead()) {
            throw FileNotFoundException(str(R.string.file_not_exist_or_readable_with_path, file.path))
        }
        val safeDexFile = File(cacheDir, "safe_${file.name}")
        val safeMatches = safeDexFile.isFile &&
            sha256HexInterruptible(file) == sha256HexInterruptible(safeDexFile)
        if (!safeMatches) {
            if (safeDexFile.exists() && !safeDexFile.delete()) {
                throw IOException("Failed to replace local D8 adoption file: ${safeDexFile.path}")
            }
            try {
                FileOutputStream(safeDexFile).use { output ->
                    interruptibleInput(file).use { input -> input.copyTo(output) }
                    throwIfInterrupted()
                    output.fd.sync()
                }
            } catch (failure: Throwable) {
                safeDexFile.delete()
                throw failure
            }
        }
        throwIfInterrupted()
        if (!safeDexFile.setReadOnly()) {
            throw IOException("Failed to make local D8 adoption file read-only: ${safeDexFile.path}")
        }
        throwIfInterrupted()
        val loader = DexClassLoader(safeDexFile.path, optimizationDirectory(), null, parent)
        throwIfInterrupted()
        mDexClassLoaders[safeDexFile.path] = loader
        try {
            throwIfInterrupted()
        } catch (failure: Throwable) {
            mDexClassLoaders.remove(safeDexFile.path, loader)
            throw failure
        }
        return loader
    }

    @Throws(IOException::class)
    private fun mergeAarToJar(aar: File, outputJar: File) {
        if (outputJar.exists() && !outputJar.delete()) {
            throw IOException("Failed to delete old merged jar: ${outputJar.path}")
        }

        val addedEntries = HashSet<String>()
        var mergedEntryCount = 0

        try {
            val aarZip = ZipFile(aar)
            val outputZip = ZipFile(outputJar)

            aarZip.fileHeaders
                .filter { !it.isDirectory && isAarJarEntry(it.fileName) }
                .forEach { header ->
                    aarZip.getInputStream(header).use { nestedJarStream ->
                        JarInputStream(nestedJarStream).use { jarInput ->
                            var entry = jarInput.nextJarEntry
                            while (entry != null) {
                                val entryName = entry.name
                                if (!entry.isDirectory && isLoadableJarEntry(entryName) && addedEntries.add(entryName)) {
                                    val entryData = ByteArrayOutputStream().use { buffer ->
                                        jarInput.copyTo(buffer)
                                        buffer.toByteArray()
                                    }
                                    outputZip.addStream(ByteArrayInputStream(entryData), ZipParameters().apply {
                                        fileNameInZip = entryName
                                    })
                                    mergedEntryCount += 1
                                }
                                jarInput.closeEntry()
                                entry = jarInput.nextJarEntry
                            }
                        }
                    }
                }
        } catch (e: ZipException) {
            if (outputJar.exists() && !outputJar.delete()) {
                Log.e(TAG, "outputJar.delete() failed")
            }
            throw IOException(e)
        }

        if (mergedEntryCount <= 0) {
            if (outputJar.exists() && !outputJar.delete()) {
                Log.e(TAG, "outputJar.delete() failed")
            }
            throw IOException("No loadable classes/resources were found in aar: ${aar.path}")
        }
    }

    private fun collectRuntimeLibrariesForD8(): List<File> = ClassFileDexer.collectRuntimeLibraries()

    private fun sha256Hex(file: File): String = FileInputStream(file).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) digest.update(buffer, 0, count)
        }
        digest.digest().toHex()
    }

    private fun sha256HexInterruptible(file: File): String = FileInputStream(file).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            throwIfInterrupted()
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) digest.update(buffer, 0, count)
        }
        digest.digest().toHex()
    }

    private fun interruptibleInput(file: File): InputStream = object : FilterInputStream(FileInputStream(file)) {
        override fun read(): Int {
            throwIfInterrupted()
            return super.read()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            throwIfInterrupted()
            return super.read(buffer, offset, length)
        }
    }

    private fun isAarJarEntry(name: String): Boolean {
        val normalized = name.lowercase()
        return normalized == AAR_CLASSES_JAR || normalized.startsWith(AAR_LIBS_PREFIX) && normalized.endsWith(".jar")
    }

    private fun isLoadableJarEntry(name: String): Boolean {
        val normalized = name.lowercase()
        if (!normalized.startsWith("meta-inf/")) return true
        return normalized != "meta-inf/manifest.mf"
            && !normalized.endsWith(".sf")
            && !normalized.endsWith(".rsa")
            && !normalized.endsWith(".dsa")
    }

    companion object {
        private val TAG = AndroidClassLoader::class.java.simpleName
        private const val AAR_CLASSES_JAR = "classes.jar"
        private const val AAR_LIBS_PREFIX = "libs/"
        private const val READ_ONLY_MODE = 0x100
    }
}

private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
