package org.autojs.autojs.engine.module

import android.content.Context
import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import org.autojs.autojs.engine.encryption.ScriptEncryption.decrypt
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.script.EncryptedScriptFileHeader
import org.autojs.autojs.script.EncryptedScriptFileHeader.isValidFile
import org.mozilla.javascript.commonjs.module.provider.ModuleSource
import java.io.ByteArrayInputStream
import java.io.File
import java.io.File.separator
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
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
//  ! Project-structured directories with package.json
//  ! was not yet adapted,
//  ! as it doesn't seem to matter as much. :)
class AssetAndUrlModuleSourceProvider(
    private val context: Context,
    private val assetDirPath: String,
    list: List<URI>
) : UrlModuleSourceProvider(list, null) {

    private val mOkHttpClient by lazy { OkHttpClient.Builder().followRedirects(true).build() }
    private val mAssetBaseURI = URI.create("file:///android_asset$separator$assetDirPath")
    private val mAssetManager = context.assets
    private val mJavaScriptExtensionName = ".js"
    private val mRegexUrl = "^(https?|ftp|file)://[-a-zA-Z\\d+&@#/%?=~_|!:,.;]*[-a-zA-Z\\d+&@#/%=~_|]".toRegex()

    @Throws(IOException::class, URISyntaxException::class)
    override fun loadFromPrivilegedLocations(moduleId: String, validator: Any?): ModuleSource? {
        return when (moduleId.matches(mRegexUrl)) {
            true -> loadFromURL(moduleId, validator)
            else -> try {
                loadFromAsset(moduleId, validator)
            } catch (e: Exception) {
                return when (moduleId.startsWith(separator)) {
                    true -> loadFromFile(File(moduleId), validator = validator)
                    else -> null
                }
            }
        }
    }

    private fun loadFromAsset(moduleId: String, validator: Any?): ModuleSource? {
        val moduleIdWithExtension = when (moduleId.endsWith(mJavaScriptExtensionName, true)) {
            true -> moduleId
            else -> moduleId + mJavaScriptExtensionName
        }
        return try {
            createModuleSource(
                mAssetManager.open("$assetDirPath$separator$moduleIdWithExtension"),
                URI("$mAssetBaseURI$separator$moduleIdWithExtension"),
                mAssetBaseURI,
                validator,
            )
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
                response.body.let {
                    return createModuleSource(it.byteStream(), URI.create(url), null, validator, it.contentType()?.charset())
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

}