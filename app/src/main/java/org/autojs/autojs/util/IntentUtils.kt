package org.autojs.autojs.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import org.autojs.autojs6.R
import java.io.File

object IntentUtils {

    fun chatWithQQ(context: Context, qq: String) = try {
        @Suppress("SpellCheckingInspection")
        val url = "mqqwpa://im/chat?chat_type=wpa&uin=$qq"
        Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .let { context.startActivity(it) }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }

    fun joinQQGroup(context: Context, key: String): Boolean {
        @Suppress("SpellCheckingInspection")
        return try {
            val url = "mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26k%3D$key"
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .let { context.startActivity(it) }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @JvmOverloads
    fun sendMailTo(context: Context, sendTo: String, title: String? = null, content: String? = null) = try {
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$sendTo"))
            .apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_CC, /* email */ arrayOf(sendTo))
                title?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
                content?.let { putExtra(Intent.EXTRA_TEXT, it) }
            }
            .let { context.startActivity(Intent.createChooser(it, "")) }
        true
    } catch (e: ActivityNotFoundException) {
        e.printStackTrace()
        false
    }

    @JvmStatic
    fun browse(context: Context, link: String?) = try {
        Intent(Intent.ACTION_VIEW, Uri.parse(link))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .let { context.startActivity(it) }
        true
    } catch (ignored: ActivityNotFoundException) {
        ViewUtils.showToast(context, R.string.text_no_browser, true)
        false
    }

    fun shareText(context: Context, text: String?) = try {
        Intent(Intent.ACTION_SEND)
            .putExtra(Intent.EXTRA_TEXT, text)
            .setType("text/plain")
            .let { context.startActivity(it) }
        true
    } catch (e: ActivityNotFoundException) {
        e.printStackTrace()
        false
    }

    @JvmOverloads
    fun goToAppDetailSettings(context: Context, packageName: String = context.packageName) = try {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                data = Uri.parse("package:$packageName")
            }
            .let { context.startActivity(it) }
        true
    } catch (ignored: ActivityNotFoundException) {
        false
    }

    @Throws(ActivityNotFoundException::class)
    fun installApk(context: Context, path: String?, fileProviderAuthority: String?) {
        Intent(Intent.ACTION_VIEW)
            .apply {
                setDataAndType(
                    getUriOfFile(context, path, fileProviderAuthority),
                    "application/vnd.android.package-archive",
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            .let { context.startActivity(it) }
    }

    @JvmStatic
    fun installApkOrToast(context: Context, path: String?, fileProviderAuthority: String?) = try {
        installApk(context, path, fileProviderAuthority)
    } catch (e: ActivityNotFoundException) {
        e.printStackTrace()
        ViewUtils.showToast(context, R.string.text_activity_not_found_for_apk_installing)
    }

    @JvmStatic
    fun viewFile(context: Context, path: String?, fileProviderAuthority: String?): Boolean {
        return viewFile(context, path, MimeTypesUtils.fromFileOr(path, "*/*"), fileProviderAuthority)
    }

    fun getUriOfFile(context: Context, path: String?, fileProviderAuthority: String?): Uri {
        return fileProviderAuthority?.let {
            FileProvider.getUriForFile(context, it, File(path ?: ""))
        } ?: Uri.parse("file://$path")
    }

    fun viewFile(context: Context, uri: Uri, mimeType: String?, fileProviderAuthority: String?): Boolean {
        if (uri.scheme == "file") {
            return viewFile(context, uri.path, mimeType, fileProviderAuthority)
        }
        return try {
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mimeType)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                .let { context.startActivity(it) }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun viewFile(context: Context, path: String?, mimeType: String?, fileProviderAuthority: String?) = try {
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(getUriOfFile(context, path, fileProviderAuthority), mimeType)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            .let { context.startActivity(it) }
        true
    } catch (e: ActivityNotFoundException) {
        e.printStackTrace()
        false
    }

    fun editFile(context: Context, path: String?, fileProviderAuthority: String?) = try {
        Intent(Intent.ACTION_EDIT)
            .setDataAndType(
                getUriOfFile(context, path, fileProviderAuthority),
                MimeTypesUtils.fromFileOr(path, "*/*"),
            )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            .let { context.startActivity(it) }
        true
    } catch (e: ActivityNotFoundException) {
        e.printStackTrace()
        false
    }

    fun requestAppUsagePermission(context: Context) = try {
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .let { context.startActivity(it) }
    } catch (e: ActivityNotFoundException) {
        e.printStackTrace()
    }

}