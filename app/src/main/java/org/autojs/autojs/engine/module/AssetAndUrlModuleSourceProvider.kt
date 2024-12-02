package org.autojs.autojs.engine.module

import android.content.Context
import android.net.Uri
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.autojs.autojs.engine.encryption.ScriptEncryption.decrypt
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.script.EncryptedScriptFileHeader
import org.autojs.autojs.script.EncryptedScriptFileHeader.isValidFile
import org.autojs.autojs.util.FileUtils.TYPE.JAVASCRIPT
import org.mozilla.javascript.commonjs.module.provider.ModuleSource
import java.io.*
import java.io.File.separator
import java.net.URI
import java.net.URISyntaxException
import java.net.URLConnection
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Created by Stardust on May 9, 2017.
 * Transformed by SuperMonster003 on Jul 14, 2023.
 */
// @Inspired by aiselp (https://github.com/aiselp) on Jul 14, 2023.
//  ! Related PR:
//  ! http://pr.autojs6.com/75
//  ! http://pr.autojs6.com/78
// @Hint by SuperMonster003 on Jul 17, 2023.
//  ! Project-structured dirs with package.json was not yet adapted,
//  ! as it doesn't seem to matter as much. :)
//  ! zh-CN:
//  ! 暂未适配含 package.json 的项目结构目录,
//  ! 因其似乎并不具备高紧急性. [笑脸符号]
class AssetAndUrlModuleSourceProvider(
    private val context: Context,
    private val assetDirPath: String,
    list: List<URI>,
) : UrlModuleSourceProvider(list, null) {

    private val mOkHttpClient by lazy { OkHttpClient.Builder().followRedirects(true).build() }
    private val mAssetBaseURI = URI.create("file:///android_asset$separator$assetDirPath")
    private val mAssetManager = context.assets
    private val mJavaScriptExtensionName = JAVASCRIPT.extensionWithDot
    private val mRegexUrl = "^(https?|ftp|file)://[-a-zA-Z\\d+&@#/%?=~_|!:,.;]*[-a-zA-Z\\d+&@#/%=~_|]".toRegex()

    @Throws(IOException::class, URISyntaxException::class)
    override fun loadFromPrivilegedLocations(moduleId: String, validator: Any?): ModuleSource? = when {
        moduleId.matches(mRegexUrl) -> loadFromURL(moduleId, validator)
        else -> runCatching { loadFromAsset(moduleId, validator) }.getOrElse {
            if (moduleId.startsWith(separator)) loadFromFile(File(moduleId), validator = validator) else null
        }
    }

    private fun loadFromAsset(moduleId: String, validator: Any?): ModuleSource? {
        val moduleIdWithExtension = when (moduleId.endsWith(mJavaScriptExtensionName, true)) {
            true -> moduleId
            else -> moduleId + mJavaScriptExtensionName
        }
        return try {
            val moduleDirPrefix = assetDirPath + separator
            when {
                moduleIdWithExtension.startsWith(moduleDirPrefix) -> {
                    Log.d(TAG, "moduleIdWithExtension: $moduleIdWithExtension")
                    val id = moduleIdWithExtension.substring(moduleDirPrefix.length)
                    createModuleSource(
                        mAssetManager.open("$assetDirPath$separator$id"),
                        URI("$mAssetBaseURI$separator$id"),
                        mAssetBaseURI,
                        validator,
                    )
                }
                else -> createModuleSource(
                    mAssetManager.open("$assetDirPath$separator$moduleIdWithExtension"),
                    URI("$mAssetBaseURI$separator$moduleIdWithExtension"),
                    mAssetBaseURI,
                    validator,
                )
            }
        } catch (_: FileNotFoundException) {
            super.loadFromPrivilegedLocations(moduleId, validator)
        }
    }

    private fun loadFromURL(url: String, validator: Any?): ModuleSource? {
        return try {
            Request.Builder().url(url).build().let { request ->
                val response = mOkHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    return null.also { response.close() }
                }
                return response.body?.let {
                    createModuleSource(it.byteStream(), URI.create(url), null, validator, it.contentType()?.charset())
                }
            }
        } catch (e: Exception) {
            null.also { ScriptRuntime.popException(e.message) }
        }
    }

    private fun loadFromFile(file: File, parentFile: File? = file.parentFile, validator: Any?): ModuleSource? {
        return loadFromFile(file.toURI(), parentFile?.toURI(), validator)
    }

    private fun loadFromFile(uri: URI, parent: URI?, validator: Any?): ModuleSource? {
        val inputStream = context.contentResolver.openInputStream(Uri.parse(uri.toString())) ?: return null
        return try {
            createModuleSource(inputStream, uri, parent, validator)
        } catch (e: FileNotFoundException) {
            null.also { ScriptRuntime.popException(e.message) }
        }
    }

    private fun createModuleSource(stream: InputStream, uri: URI, base: URI?, validator: Any?, charset: Charset? = null): ModuleSource {
        val streamReader = InputStreamReader(stream, charset ?: StandardCharsets.UTF_8)
        return ModuleSource(streamReader, null, uri, base, validator)
    }

    @Throws(IOException::class)
    override fun getReader(urlConnection: URLConnection): Reader {
        val stream = urlConnection.getInputStream()
        val bytes = ByteArray(stream.available()).also {
            stream.read(it)
            stream.close()
        }
        return if (isValidFile(bytes)) {
            val clearText = decrypt(bytes, EncryptedScriptFileHeader.BLOCK_SIZE, bytes.size)
            InputStreamReader(ByteArrayInputStream(clearText))
        } else {
            InputStreamReader(ByteArrayInputStream(bytes))
        }
    }

    companion object {

        private val TAG = AssetAndUrlModuleSourceProvider::class.java.simpleName

    }

}