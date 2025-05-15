package org.autojs.autojs.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.google.android.material.snackbar.Snackbar
import org.autojs.autojs.external.fileprovider.AppFileProvider
import org.autojs.autojs.runtime.api.Mime
import org.autojs.autojs6.R
import java.io.File

object IntentUtils {

    fun chatWithQQ(context: Context, qq: String) = try {
        true.also {
            @Suppress("SpellCheckingInspection")
            val url = "mqqwpa://im/chat?chat_type=wpa&uin=$qq"
            Intent(Intent.ACTION_VIEW, url.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .let { context.startActivity(it) }
        }
    } catch (e: Exception) {
        false.also { e.printStackTrace() }
    }

    fun joinQQGroup(context: Context, key: String) = try {
        true.also {
            @Suppress("SpellCheckingInspection")
            val url = "mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26k%3D$key"
            Intent(Intent.ACTION_VIEW, url.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .let { context.startActivity(it) }
        }
    } catch (e: Exception) {
        false.also { e.printStackTrace() }
    }

    @JvmOverloads
    fun sendMailTo(context: Context, sendTo: String, title: String? = null, content: String? = null) = try {
        true.also {
            Intent(Intent.ACTION_SENDTO, "mailto:$sendTo".toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_CC, /* email */ arrayOf(sendTo))
                title?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
                content?.let { putExtra(Intent.EXTRA_TEXT, it) }
            }.let { context.startActivity(Intent.createChooser(it, "")) }
        }
    } catch (e: ActivityNotFoundException) {
        false.also { e.printStackTrace() }
    }

    @JvmStatic
    @JvmOverloads
    fun browse(context: Context, link: String, exceptionHolder: ExceptionHolder? = null) = try {
        true.also {
            Intent(Intent.ACTION_VIEW, link.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .let { context.startActivity(it) }
        }

    } catch (e: ActivityNotFoundException) {
        exceptionHolder?.show(R.string.error_no_applications_available_for_browsing_this_link)
        // ViewUtils.showToast(context, R.string.text_no_browser, true)
        false.also { e.printStackTrace() }
    }

    fun shareText(context: Context, text: String?) = try {
        true.also {
            Intent(Intent.ACTION_SEND)
                .putExtra(Intent.EXTRA_TEXT, text)
                .setType("text/plain")
                .let { context.startActivity(it) }
        }
    } catch (e: ActivityNotFoundException) {
        false.also { e.printStackTrace() }
    }

    @JvmOverloads
    fun goToAppDetailSettings(context: Context, packageName: String = context.packageName) = try {
        true.also {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                data = "package:$packageName".toUri()
            }.let { context.startActivity(it) }
        }
    } catch (_: ActivityNotFoundException) {
        false
    }

    @JvmStatic
    @JvmOverloads
    fun installApk(
        context: Context,
        path: String,
        fileProviderAuthority: String = AppFileProvider.AUTHORITY,
        exceptionHolder: ExceptionHolder? = null,
    ) = try {
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(
                getUriOfFile(context, path, fileProviderAuthority),
                Mime.APPLICATION_VND_ANDROID_PACKAGE_ARCHIVE,
            )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            .let { context.startActivity(it) }
        true
    } catch (e: ActivityNotFoundException) {
        exceptionHolder?.show(R.string.error_no_applications_available_for_installing_this_file)
        // ViewUtils.showToast(context, R.string.text_activity_not_found_for_apk_installing)
        e.printStackTrace()
        false
    }


    @JvmStatic
    @JvmOverloads
    fun getUriOfFile(context: Context, path: String?, fileProviderAuthority: String? = AppFileProvider.AUTHORITY): Uri {
        return fileProviderAuthority?.let {
            FileProvider.getUriForFile(context, it, File(path ?: ""))
        } ?: "file://$path".toUri()
    }

    @JvmStatic
    fun viewFile(context: Context, path: String, fileProviderAuthority: String = AppFileProvider.AUTHORITY): Boolean {
        return viewFile(context, path, Mime.fromFileOr(path, Mime.WILDCARD), fileProviderAuthority)
    }

    @JvmStatic
    @JvmOverloads
    fun viewFile(
        context: Context,
        uri: Uri,
        mimeType: String? = null,
        fileProviderAuthority: String = AppFileProvider.AUTHORITY,
        exceptionHolder: ExceptionHolder? = null,
    ): Boolean = when (uri.scheme) {
        uri.scheme -> {
            uri.path?.let { viewFile(context, it, mimeType, fileProviderAuthority) } ?: false
        }
        else -> launchViewIntent(context, uri, mimeType, exceptionHolder)
    }

    @JvmStatic
    fun viewFile(
        context: Context,
        path: String,
        mimeType: String? = null,
        fileProviderAuthority: String = AppFileProvider.AUTHORITY,
        exceptionHolder: ExceptionHolder? = null,
    ) = launchViewIntent(
        context = context,
        uri = getUriOfFile(context, path, fileProviderAuthority),
        mimeType = mimeType ?: Mime.fromFileOrWildcard(path),
        exceptionHolder = exceptionHolder,
    )

    private fun launchViewIntent(
        context: Context,
        uri: Uri,
        mimeType: String?,
        exceptionHolder: ExceptionHolder? = null,
    ) = try {
        true.also {
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mimeType)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                .let { context.startActivity(it) }
        }
    } catch (e: Exception) {
        exceptionHolder?.show(R.string.error_no_applications_available_for_viewing_this_file)
        false.also { e.printStackTrace() }
    }

    @JvmStatic
    @JvmOverloads
    fun editFile(
        context: Context,
        path: String,
        fileProviderAuthority: String = AppFileProvider.AUTHORITY,
        exceptionHolder: ExceptionHolder? = null,
    ) = try {
        true.also {
            Intent(Intent.ACTION_EDIT)
                .setDataAndType(
                    getUriOfFile(context, path, fileProviderAuthority),
                    Mime.fromFileOrWildcard(path),
                )
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                .let { context.startActivity(it) }
        }
    } catch (e: ActivityNotFoundException) {
        exceptionHolder?.show(R.string.error_no_applications_available_for_editing_this_file)
        false.also { e.printStackTrace() }
    }

    @JvmStatic
    @JvmOverloads
    fun sendFile(
        context: Context,
        path: String,
        fileProviderAuthority: String = AppFileProvider.AUTHORITY,
        exceptionHolder: ExceptionHolder? = null,
    ) = try {
        true.also {
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_STREAM, getUriOfFile(context, path, fileProviderAuthority))
                .let { Intent.createChooser(it, context.getString(R.string.text_send)) }
                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                .let { context.startActivity(it) }
        }
    } catch (e: ActivityNotFoundException) {
        exceptionHolder?.show(R.string.error_no_applications_available_for_sending_this_file)
        false.also { e.printStackTrace() }
    }

    fun requestAppUsagePermission(context: Context) = try {
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .let { context.startActivity(it) }
    } catch (e: ActivityNotFoundException) {
        e.printStackTrace()
    }

    data class SnackExceptionHolder @JvmOverloads constructor(
        private val view: View,
        private val overriddenMessage: String? = null,
        private val duration: Int = Snackbar.LENGTH_LONG,
    ) : ExceptionHolder {

        @JvmOverloads
        constructor(
            view: View,
            overriddenMessageRes: Int,
            duration: Int = Snackbar.LENGTH_LONG,
        ) : this(
            view = view,
            overriddenMessage = view.context.getString(overriddenMessageRes),
            duration = duration,
        )

        override fun show(message: String?) {
            ViewUtils.showSnack(view, overriddenMessage ?: message ?: "Failed to start an activity", duration)
        }

        override fun show(messageRes: Int) {
            show(view.context.getString(messageRes))
        }
    }

    data class ToastExceptionHolder @JvmOverloads constructor(
        private val context: Context,
        private val overriddenMessage: String? = null,
        private val isLong: Boolean = true,
    ) : ExceptionHolder {

        @JvmOverloads
        constructor(
            context: Context,
            overriddenMessageRes: Int,
            isLong: Boolean = true,
        ) : this(
            context = context,
            overriddenMessage = context.getString(overriddenMessageRes),
            isLong = isLong,
        )

        override fun show(message: String?) {
            ViewUtils.showToast(context, overriddenMessage ?: message ?: "Failed to start an activity", isLong)
        }

        override fun show(messageRes: Int) {
            show(context.getString(messageRes))
        }
    }

    interface ExceptionHolder {
        fun show(message: String? = null)
        fun show(messageRes: Int)
    }

}