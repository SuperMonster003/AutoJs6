package org.autojs.autojs.runtime.api.augment.zip

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ExcludeFileFilter
import net.lingala.zip4j.model.FileHeader
import net.lingala.zip4j.model.UnzipParameters
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.ZipParameters.SymbolicLinkAction
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.AesVersion
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoStandardFunctionInterface
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component1
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component2
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component3
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component4
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNumber
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.extension.IterableExtensions.toNativeArray
import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.StringReadable
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.callFunction
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceLongNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.coerceStringUppercase
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.autojs.autojs.util.RhinoUtils.undefined
import org.autojs.autojs.util.StringUtils.toFile
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import java.io.File

@Suppress("unused", "UNUSED_PARAMETER")
class ZipNativeObject(
    private val scriptRuntime: ScriptRuntime,
    private val operationName: String,
    rawPath: Any?,
    rawOptions: Any? = null,
) : NativeObject(), StringReadable {

    @JvmField
    val path: String = when {
        rawPath.isJsNullish() -> throw WrappedIllegalArgumentException("Argument path ${rawPath.jsBrief()} is invalid for zip.$operationName")
        else -> {
            val pathString = coerceString(rawPath)
            require(pathString.isNotBlank()) {
                "Argument \"path\" ${pathString.jsBrief()} for zip.$operationName cannot be empty"
            }
            scriptRuntime.files.nonNullPath(pathString)
        }
    }

    @JvmField
    val options: ScriptableObject = when {
        rawOptions.isJsNullish() -> newNativeObject()
        else -> {
            val niceOptions = rawOptions
            require(niceOptions is ScriptableObject) {
                "Argument \"options\" ${niceOptions.jsBrief()} for zip.$operationName must be a JavaScript Object"
            }
            niceOptions
        }
    }

    private val optionsJavaMap = mutableMapOf<String, Any?>()

    @JvmField
    val zipFile = ZipFile(path)

    @JvmField
    val zipParameters: ZipParameters = buildZipParameters(operationName, options)

    @JvmField
    val unzipParameters: UnzipParameters = buildUnzipParameters(operationName, options)

    private val mPropertyNames = arrayOf(
        "name" to operationName,
        "path" to path,
        "zipFile" to zipFile,
        "options" to options,
        "zipParameters" to zipParameters,
        "unzipParameters" to unzipParameters,
    )

    private val mFunctionNames = arrayOf(
        "toString",
        ::addFile.name,
        ::addFiles.name,
        ::addFolder.name,
        ::extractAll.name,
        ::extractFile.name,
        ::setPassword.name,
        ::getFileHeader.name,
        ::getFileHeaders.name,
        ::isEncrypted.name,
        ::removeFile.name,
        ::isValidZipFile.name,
        ::getPath.name,
        ::getZipFile.name,
    )

    init {
        RhinoUtils.initNativeObjectPrototype(this)
        defineFunctionProperties(mFunctionNames, javaClass, PERMANENT)
        defineProperty(StringReadable.KEY, newBaseFunction(StringReadable.KEY, { toStringReadable() }, NOT_CONSTRUCTABLE), READONLY or DONTENUM or PERMANENT)
        mPropertyNames.forEach { pair ->
            val (propertyName, value) = pair
            defineProperty(propertyName, value, READONLY or PERMANENT)
        }
    }

    private fun buildZipParameters(funcName: String, opts: Any?): ZipParameters {
        val params = ZipParameters()
        if (opts.isJsNullish()) {
            return params
        }
        require(opts is ScriptableObject) {
            "Argument \"options\" ${opts.jsBrief()} for zip.$funcName must be a JavaScript Object"
        }
        params.aesKeyStrength = opts.inquire("aesKeyStrength", { o, def ->
            o.toAesKeyStrength() ?: def
        }, AesKeyStrength.KEY_STRENGTH_256).also { optionsJavaMap["aesKeyStrength"] = it }
        params.aesVersion = opts.inquire("aesVersion", { o, def ->
            o.toAesVersion() ?: def
        }, AesVersion.TWO).also { optionsJavaMap["aesVersion"] = it }
        params.compressionLevel = opts.inquire("compressionLevel", { o, def ->
            o.toCompressionLevel() ?: def
        }, CompressionLevel.NORMAL).also { optionsJavaMap["compressionLevel"] = it }
        params.compressionMethod = opts.inquire("compressionMethod", { o, def ->
            o.toCompressionMethod() ?: def
        }, CompressionMethod.DEFLATE).also { optionsJavaMap["compressionMethod"] = it }
        params.encryptionMethod = opts.inquire("encryptionMethod", { o, def ->
            o.toEncryptionMethod() ?: def
        }, EncryptionMethod.AES).also { optionsJavaMap["encryptionMethod"] = it }
        opts.inquire("defaultFolderPath", ::coerceString, null)?.let {
            params.defaultFolderPath = it.also { optionsJavaMap["defaultFolderPath"] = it }
        }
        opts.inquire("entryCRC", ::coerceLongNumber, null)?.let {
            params.entryCRC = it.also { optionsJavaMap["entryCRC"] = it }
        }
        opts.inquire("entrySize", ::coerceLongNumber, null)?.let {
            params.entrySize = it.also { optionsJavaMap["entrySize"] = it }
        }
        opts.inquire("excludeFileFilter") { o ->
            when {
                o.isJsNullish() -> null
                o is BaseFunction -> ExcludeFileFilter { file ->
                    coerceBoolean(callFunction(o, opts, opts, arrayOf(file)), false)
                }
                o is ExcludeFileFilter -> o
                else -> listOf(
                    "Options property excludeFileFilter ${o.jsBrief()}",
                    "for zip.$funcName must be a ExcludeFileFilter",
                    "or a JavaScript Function that can be used as an ExcludeFileFilter",
                ).joinToString(" ").let { throw WrappedIllegalArgumentException(it) }
            }
        }?.let {
            params.excludeFileFilter = it.also { optionsJavaMap["excludeFileFilter"] = it }
        }
        opts.inquire(listOf("fileComment", "comment"), ::coerceString, null)?.let {
            params.fileComment = it.also {
                optionsJavaMap["fileComment"] = it
                optionsJavaMap["comment"] = it
            }
        }
        opts.inquire("fileNameInZip", ::coerceString, null)?.let {
            params.fileNameInZip = it.also { optionsJavaMap["fileNameInZip"] = it }
        }
        opts.inquire(listOf("isIncludeRootFolder", "includeRootFolder"), ::coerceBoolean, null)?.let {
            params.isIncludeRootFolder = it.also {
                optionsJavaMap["isIncludeRootFolder"] = it
                optionsJavaMap["includeRootFolder"] = it
            }
        }
        opts.inquire(listOf("isOverrideExistingFilesInZip", "overrideExistingFilesInZip"), ::coerceBoolean, null)?.let {
            params.isOverrideExistingFilesInZip = it.also {
                optionsJavaMap["isOverrideExistingFilesInZip"] = it
                optionsJavaMap["overrideExistingFilesInZip"] = it
            }
        }
        opts.inquire(listOf("isReadHiddenFiles", "readHiddenFiles"), ::coerceBoolean, null)?.let {
            params.isReadHiddenFiles = it.also {
                optionsJavaMap["isReadHiddenFiles"] = it
                optionsJavaMap["readHiddenFiles"] = it
            }
        }
        opts.inquire(listOf("isReadHiddenFolders", "readHiddenFolders"), ::coerceBoolean, null)?.let {
            params.isReadHiddenFolders = it.also {
                optionsJavaMap["isReadHiddenFolders"] = it
                optionsJavaMap["readHiddenFolders"] = it
            }
        }
        opts.inquire(listOf("isUnixMode", "unixMode"), ::coerceBoolean, null)?.let {
            params.isUnixMode = it.also {
                optionsJavaMap["isUnixMode"] = it
                optionsJavaMap["unixMode"] = it
            }
        }
        opts.inquire(listOf("isWriteExtendedLocalFileHeader", "writeExtendedLocalFileHeader"), ::coerceBoolean, null)?.let {
            params.isWriteExtendedLocalFileHeader = it.also {
                optionsJavaMap["isWriteExtendedLocalFileHeader"] = it
                optionsJavaMap["writeExtendedLocalFileHeader"] = it
            }
        }
        opts.inquire("lastModifiedFileTime", ::coerceLongNumber, null)?.let {
            params.lastModifiedFileTime = it.also { optionsJavaMap["lastModifiedFileTime"] = it }
        }
        opts.inquire("rootFolderNameInZip", ::coerceString, null)?.let {
            params.rootFolderNameInZip = it.also { optionsJavaMap["rootFolderNameInZip"] = it }
        }
        params.symbolicLinkAction = opts.inquire("symbolicLinkAction", { o, def ->
            o.toSymbolicLinkAction() ?: def
        }, SymbolicLinkAction.INCLUDE_LINKED_FILE_ONLY).also { optionsJavaMap["symbolicLinkAction"] = it }
        opts.inquire(listOf("isEncryptFiles", "encryptFiles"), ::coerceBoolean, null)?.let {
            params.isEncryptFiles = it.also {
                optionsJavaMap["isEncryptFiles"] = it
                optionsJavaMap["encryptFiles"] = it
            }
        }
        opts.inquire("password") { coerceString(it) }?.let { password ->
            zipFile.setPassword(password.toCharArray())
            params.isEncryptFiles = true
            optionsJavaMap["password"] = password
        }
        return params
    }

    private fun buildUnzipParameters(funcName: String, opts: Any?): UnzipParameters {
        val params = UnzipParameters()
        if (opts.isJsNullish()) {
            return params
        }
        require(opts is ScriptableObject) {
            "Argument \"options\" ${opts.jsBrief()} for zip.$funcName must be a JavaScript Object"
        }
        opts.inquire("isExtractSymbolicLinks", ::coerceBoolean, true)
        opts.inquire("password") { coerceString(it) }?.let { password ->
            zipFile.setPassword(password.toCharArray())
        }
        opts.inquire(listOf("isIgnoreDateTimeAttributes", "ignoreDateTimeAttributes"), ::coerceBoolean, null)?.let {
            throw WrappedIllegalArgumentException("Options property ignoreDateTimeAttributes for zip.$funcName is no longer supported since Zip4j library version 2.x")
        }
        return params
    }

    override fun toStringReadable() = toStringRhino(this)

    private fun Any.toAesKeyStrength(): AesKeyStrength? = when {
        this is AesKeyStrength -> this
        this.isJsNullish() -> null
        this.isJsNumber() -> AesKeyStrength.entries.find { it.rawCode == coerceIntNumber(this) } ?: when (coerceIntNumber(this)) {
            128 -> AesKeyStrength.KEY_STRENGTH_128
            192 -> AesKeyStrength.KEY_STRENGTH_192
            256 -> AesKeyStrength.KEY_STRENGTH_256
            else -> throw WrappedIllegalArgumentException("Options property aesKeyStrength ${this.jsBrief()} is invalid for zip.$operationName")
        }
        else -> when (val s = coerceStringUppercase(this).removePrefix("AES_STRENGTH_")) {
            "128" -> AesKeyStrength.KEY_STRENGTH_128
            "192" -> AesKeyStrength.KEY_STRENGTH_192
            "256" -> AesKeyStrength.KEY_STRENGTH_256
            else -> AesKeyStrength.valueOf(s)
        }
    }

    private fun Any.toAesVersion(): AesVersion? = when {
        this is AesVersion -> this
        this.isJsNullish() -> null
        this.isJsNumber() -> AesVersion.entries.find { it.versionNumber == coerceIntNumber(this) }
        else -> when (val s = coerceStringUppercase(this)) {
            "1" -> AesVersion.ONE
            "2" -> AesVersion.TWO
            else -> AesVersion.valueOf(s)
        }
    }

    private fun Any.toCompressionLevel(): CompressionLevel? = when {
        this is CompressionLevel -> this
        this.isJsNullish() -> null
        this.isJsNumber() -> CompressionLevel.entries.find { it.level == coerceIntNumber(this) }
        else -> CompressionLevel.valueOf(coerceStringUppercase(this).removePrefix("DEFLATE_LEVEL_"))
    }

    private fun Any.toCompressionMethod(): CompressionMethod? = when {
        this is CompressionMethod -> this
        this.isJsNullish() -> null
        this.isJsNumber() -> CompressionMethod.entries.find { it.code == coerceIntNumber(this) }
        else -> when (val methodName = coerceStringUppercase(this).removePrefix("COMP_")) {
            "AES_ENC" -> CompressionMethod.AES_INTERNAL_ONLY
            else -> CompressionMethod.valueOf(methodName)
        }
    }

    private fun Any.toEncryptionMethod(): EncryptionMethod? = when {
        this is EncryptionMethod -> this
        this.isJsNullish() -> null
        this.isJsNumber() -> when (coerceIntNumber(this)) {
            -1 -> EncryptionMethod.NONE
            0 -> EncryptionMethod.ZIP_STANDARD
            1 -> EncryptionMethod.ZIP_STANDARD_VARIANT_STRONG
            99 -> EncryptionMethod.AES
            else -> throw WrappedIllegalArgumentException("Options property encryptionMethod ${this.jsBrief()} is invalid for zip.$operationName")
        }
        else -> when (val s = coerceStringUppercase(this).removePrefix("ENC_METHOD_").removePrefix("ENC_")) {
            "NO_ENCRYPTION", "NO" -> EncryptionMethod.NONE
            "STANDARD" -> EncryptionMethod.ZIP_STANDARD
            "STRONG" -> EncryptionMethod.ZIP_STANDARD_VARIANT_STRONG
            else -> EncryptionMethod.valueOf(s)
        }
    }

    private fun Any.toSymbolicLinkAction(): SymbolicLinkAction? = when {
        this is SymbolicLinkAction -> this
        this.isJsNullish() -> null
        else -> SymbolicLinkAction.valueOf(coerceStringUppercase(this))
    }

    companion object : ArgumentGuards() {

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun toString(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): String = ensureArgumentsIsEmpty(args) {
            toStringRhino(thisObj as ZipNativeObject)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun toStringRhino(thisObj: ZipNativeObject): String {
            val opts = thisObj.options
            val optsString: String = when (opts) {
                !is NativeObject -> opts.toString()
                else -> {
                    val entries = opts.entries
                    when {
                        entries.isEmpty() -> "{}"
                        else -> "{\n${
                            entries.joinToString("\n") {
                                var s = "    ${it.key}: ${Context.toString(it.value)}"
                                if (it.key in thisObj.optionsJavaMap) {
                                    s += " [ ${thisObj.optionsJavaMap[it.key]} ]"
                                }
                                "$s,"
                            }
                        }\n  }"
                    }
                }
            }
            return listOf(
                "${Zip::class.java.simpleName} {",
                "  operation: ${thisObj.operationName},",
                "  path: ${thisObj.path},",
                "  options: $optsString,",
                "}",
            ).joinToString("\n")
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun addFile(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Undefined = ensureArgumentsLengthInRange(args, 1..2) { argList ->
            val zip = thisObj as ZipNativeObject
            val (filePath, options) = argList
            val file = zip.scriptRuntime.files.nonNullPath(coerceString(filePath)).toFile()
            val zipParameters = zip.buildZipParameters("addFile", options)
            undefined { zip.zipFile.addFile(file, zipParameters) }
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun addFiles(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Undefined = ensureArgumentsLengthInRange(args, 1..2) { argList ->
            val zip = thisObj as ZipNativeObject
            val (filePathList, options) = argList
            require(filePathList is Iterable<*>) {
                "Argument \"filePathList\" ${filePathList.jsBrief()} for ${ZipNativeObject::class.java.simpleName}#${"addFiles"} must be an Iterable"
            }
            val fileList = mutableListOf<File>()
            filePathList.forEach { rawFilePath ->
                fileList.add(zip.scriptRuntime.files.nonNullPath(coerceString(rawFilePath)).toFile())
            }
            val zipParameters = zip.buildZipParameters("addFiles", options)
            undefined { zip.zipFile.addFiles(fileList, zipParameters) }
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun addFolder(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Undefined = ensureArgumentsLengthInRange(args, 1..2) { argList ->
            val zip = thisObj as ZipNativeObject
            val (filePath, options) = argList
            val file = zip.scriptRuntime.files.nonNullPath(coerceString(filePath)).toFile()
            val zipParameters = zip.buildZipParameters("addFolder", options)
            undefined { zip.zipFile.addFolder(file, zipParameters) }
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun extractAll(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Undefined = ensureArgumentsLengthInRange(args, 1..2) { argList ->
            val zip = thisObj as ZipNativeObject
            val (rawDestPath, options) = argList
            val destPath = zip.scriptRuntime.files.nonNullPath(coerceString(rawDestPath))
            val unzipParameters = zip.buildUnzipParameters("extractAll", options)
            undefined { zip.zipFile.extractAll(destPath, unzipParameters) }
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun extractFile(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Undefined = ensureArgumentsLengthInRange(args, 2..4) { argList ->
            val zip = thisObj as ZipNativeObject
            val (rawZipFilePath, rawDestPath, options, rawNewFileName) = argList
            val zipFilePath = zip.scriptRuntime.files.nonNullPath(coerceString(rawZipFilePath))
            val destPath = zip.scriptRuntime.files.nonNullPath(coerceString(rawDestPath))
            val newFileName = rawNewFileName.takeUnless { it.isJsNullish() }?.let { coerceString(it) }
            val unzipParameters = zip.buildUnzipParameters("extractFile", options)
            undefined { zip.zipFile.extractFile(zipFilePath, destPath, newFileName, unzipParameters) }
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun setPassword(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Undefined = ensureArgumentsOnlyOne(args) { password ->
            val zip = thisObj as ZipNativeObject
            undefined { zip.zipFile.setPassword(coerceString(password).toCharArray()) }
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun getFileHeader(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): FileHeader = ensureArgumentsOnlyOne(args) { fileName ->
            val zip = thisObj as ZipNativeObject
            zip.zipFile.getFileHeader(coerceString(fileName))
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun getFileHeaders(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): NativeArray = ensureArgumentsIsEmpty(args) {
            val zip = thisObj as ZipNativeObject
            zip.zipFile.getFileHeaders().toNativeArray()
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun isEncrypted(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Boolean = ensureArgumentsIsEmpty(args) {
            val zip = thisObj as ZipNativeObject
            zip.zipFile.isEncrypted
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun removeFile(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Undefined = ensureArgumentsOnlyOne(args) { fileName ->
            val zip = thisObj as ZipNativeObject
            undefined { zip.zipFile.removeFile(coerceString(fileName)) }
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun isValidZipFile(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Boolean = ensureArgumentsIsEmpty(args) {
            val zip = thisObj as ZipNativeObject
            zip.zipFile.isValidZipFile
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun getPath(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): String = ensureArgumentsIsEmpty(args) {
            val zip = thisObj as ZipNativeObject
            zip.path
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun getZipFile(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): ZipFile = ensureArgumentsIsEmpty(args) {
            val zip = thisObj as ZipNativeObject
            zip.zipFile
        }

    }

}
