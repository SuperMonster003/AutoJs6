package org.autojs.autojs.core.ui.inflater.util

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Base64
import android.view.View
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import org.autojs.autojs.core.ui.inflater.ImageLoader
import org.autojs.autojs.core.ui.inflater.ImageLoader.BitmapCallback
import org.autojs.autojs.core.ui.inflater.ImageLoader.DrawableCallback
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.ColorUtils.toInt
import org.autojs.autojs6.R
import java.io.File
import java.net.URL
import java.util.concurrent.Executors
import java.util.regex.Pattern
import androidx.core.graphics.drawable.toDrawable

/**
 * Created by Stardust on Nov 3, 2017.
 * Transformed by SuperMonster003 on Jun 5, 2023.
 */
open class Drawables {

    private var imageLoader = sDefaultImageLoader

    fun parse(context: Context, value: String): Drawable? {
        if (Regex("^(#|@(android:)?color/)").containsMatchIn(value)) {
            return ColorDrawable(ColorUtils.parse(context, value))
        }
        if (value.startsWith("?")) {
            return loadAttrResources(context, value)
        }
        if (value.startsWith("file://")) {
            return decodeImage(context, value.substring(7))
        }
        try {
            return loadDrawableResources(context, value)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            return ColorDrawable(toInt(value))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (File(value).exists()) {
                return decodeImage(context, value)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return try {
            throw Exception(context.getString(R.string.error_failed_to_convert_into_drawable, value))
        } catch (e: Exception) {
            e.printStackTrace()
            ScriptRuntime.popException(e.message)
            null
        }
    }

    private fun loadDrawableResources(context: Context, value: String): Drawable? {
        val resId = context.resources.getIdentifier(value, "drawable", context.packageName)
        if (resId == 0) {
            throw Resources.NotFoundException("drawable not found: $value")
        }
        return AppCompatResources.getDrawable(context, resId)
    }

    private fun loadAttrResources(context: Context, value: String): Drawable? {
        val attr = intArrayOf(context.resources.getIdentifier(value.substring(1), "attr", context.packageName))
        val ta = context.obtainStyledAttributes(attr)
        return ta.getDrawable(0 /* index */).also { ta.recycle() }
    }

    open fun decodeImage(context: Context, path: String?): Drawable? {
        return BitmapFactory.decodeFile(path)?.toDrawable(context.resources)
    }

    fun parse(view: View, name: String) = parse(view.context, name)

    private fun loadInto(view: ImageView?, uri: Uri?) {
        imageLoader.loadInto(view, uri)
    }

    private fun loadIntoBackground(view: View?, uri: Uri?) {
        imageLoader.loadIntoBackground(view, uri)
    }

    fun <V : ImageView> setupWithImage(view: V, value: String) {
        if (value.startsWith("http://") || value.startsWith("https://")) {
            loadInto(view, Uri.parse(value))
        } else if (value.startsWith("data:")) {
            loadDataInto(view, value)
        } else {
            view.setImageDrawable(parse(view, value))
        }
    }

    private fun loadDataInto(view: ImageView, data: String) {
        view.setImageBitmap(loadBase64Data(data))
    }

    fun setupWithViewBackground(view: View, value: String) {
        if (Regex("^https?://").containsMatchIn(value)) {
            loadIntoBackground(view, Uri.parse(value))
        } else {
            view.background = parse(view, value)
        }
    }

    private class DefaultImageLoader : ImageLoader {

        private val mExecutor = Executors.newSingleThreadExecutor()

        override fun loadInto(view: ImageView, uri: Uri) {
            load(view, uri) { drawable: Drawable? -> view.setImageDrawable(drawable) }
        }

        override fun loadIntoBackground(view: View, uri: Uri) {
            load(view, uri) { background: Drawable? -> view.background = background }
        }

        override fun load(view: View, uri: Uri): Drawable? {
            return try {
                val url = URL(uri.toString())
                val bmp = BitmapFactory.decodeStream(url.openConnection().getInputStream())
                BitmapDrawable(view.resources, bmp)
            } catch (e: Exception) {
                null
            }
        }

        override fun load(view: View, uri: Uri, callback: DrawableCallback) {
            load(view, uri) { bitmap: Bitmap? -> callback.onLoaded(BitmapDrawable(view.resources, bitmap)) }
        }

        override fun load(view: View, uri: Uri, callback: BitmapCallback) {
            mExecutor.execute {
                try {
                    val url = URL(uri.toString())
                    val bmp = BitmapFactory.decodeStream(url.openConnection().getInputStream())
                    view.post { callback.onLoaded(bmp) }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    companion object {

        private val DATA_PATTERN = Pattern.compile("data:(\\w+/\\w+);base64,(.+)")

        private var sDefaultImageLoader: ImageLoader = DefaultImageLoader()

        var defaultImageLoader: ImageLoader?
            get() = sDefaultImageLoader
            set(defaultImageLoader) {
                defaultImageLoader ?: throw NullPointerException()
                sDefaultImageLoader = defaultImageLoader
            }

        @JvmStatic
        fun loadBase64Data(data: String): Bitmap {
            val matcher = DATA_PATTERN.matcher(data)
            val base64 = if (!matcher.matches() || matcher.groupCount() != 2) {
                data
            } else {
                // val mimeType = matcher.group(1)
                matcher.group(2)
            }
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }
}