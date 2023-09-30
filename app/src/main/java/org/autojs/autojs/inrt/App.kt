package org.autojs.autojs.inrt

import android.app.Application
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.View
import android.widget.ImageView

import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import org.autojs.autojs.inrt.App
import org.autojs.autojs.AutoJs
import org.autojs.autojs.inrt.autojs.GlobalKeyObserver
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.ui.inflater.ImageLoader
import org.autojs.autojs.core.ui.inflater.util.Drawables
import java.lang.ref.WeakReference

/**
 * Created by Stardust on 2017/7/1.
 */
class App : Application() {
    companion object {

        private const val BUGLY_APP_ID = "19b3607b53"

        private lateinit var instance: WeakReference<App>

        val app: App
            get() = instance.get()!!

    }

    override fun onCreate() {
        super.onCreate()
        GlobalAppContext.set(this)
        instance = WeakReference(this)
        AutoJs.initInstance(this)
        GlobalKeyObserver.init()
        Drawables.defaultImageLoader=object : ImageLoader {
            override fun loadInto(imageView: ImageView, uri: Uri) {
                Glide.with(this@App)
                    .load(uri)
                    .into(imageView)
            }

            override fun loadIntoBackground(view: View, uri: Uri) {
                Glide.with(this@App)
                    .load(uri)
                    .into(object : SimpleTarget<Drawable>() {
                        override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                            view.background = resource
                        }
                    })
            }

            override fun load(view: View, uri: Uri): Drawable {
                throw UnsupportedOperationException()
            }

            override fun load(view: View, uri: Uri, drawableCallback: ImageLoader.DrawableCallback) {
                Glide.with(this@App)
                    .load(uri)
                    .into(object : SimpleTarget<Drawable>() {
                        override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                            drawableCallback.onLoaded(resource)
                        }
                    })
            }

            override fun load(view: View, uri: Uri, bitmapCallback: ImageLoader.BitmapCallback) {
                Glide.with(this@App)
                    .asBitmap()
                    .load(uri)
                    .into(object : SimpleTarget<Bitmap>() {
                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                            bitmapCallback.onLoaded(resource)
                        }
                    })
            }
        }
    }

}
