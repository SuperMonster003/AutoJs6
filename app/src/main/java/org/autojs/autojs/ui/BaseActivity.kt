package org.autojs.autojs.ui

import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle

import androidx.annotation.CallSuper
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar

import android.view.Menu
import android.view.View

import com.stardust.app.GlobalAppContext
import com.stardust.theme.ThemeColorManager

import org.autojs.autojs.Pref
import org.autojs.autojs.R

import java.util.ArrayList
import java.util.Arrays

import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import butterknife.ButterKnife

/**
 * Created by Stardust on 2017/1/23.
 */

abstract class BaseActivity(private val layout: Int) : AppCompatActivity() {
    private var mShouldApplyDayNightModeForOptionsMenu = true

    override fun onCreate(@Nullable savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (layout != 0) {
            setContentView(layout)
            ButterKnife.bind(this)
            setUpViews()
        }
    }

    protected open fun setUpViews() {

    }

    protected fun applyDayNightMode() {
        GlobalAppContext.post {
            if (Pref.isNightModeEnabled()) {
                setNightModeEnabled(Pref.isNightModeEnabled())
            }
        }
    }

    fun setNightModeEnabled(enabled: Boolean) {
        if (enabled) {
            delegate.setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            delegate.setLocalNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
        if (delegate.applyDayNight()) {
            recreate()
        }
    }

    override fun onStart() {
        super.onStart()
        if (window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN == 0) {
            ThemeColorManager.addActivityStatusBar(this)
        }
    }

    protected fun checkPermission(vararg permissions: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val requestPermissions = getRequestPermissions(permissions)
            if (requestPermissions.isNotEmpty()) {
                requestPermissions(requestPermissions, PERMISSION_REQUEST_CODE)
                return false
            }
            return true
        } else {
            val grantResults = IntArray(permissions.size)
            Arrays.fill(grantResults, PERMISSION_GRANTED)
            onRequestPermissionsResult(PERMISSION_REQUEST_CODE, permissions, grantResults)
            return false
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.M)
    private fun getRequestPermissions(permissions: Array<out String>): Array<String> {
        val list = ArrayList<String>()
        for (permission in permissions) {
            if (checkSelfPermission(permission) == PERMISSION_DENIED) {
                list.add(permission)
            }
        }
        return list.toTypedArray()
    }

    fun setToolbarAsBack(title: String) {
        setToolbarAsBack(this, R.id.toolbar, title)
    }

    @CallSuper
    override fun onRequestPermissionsResult(requestCode: Int, @NonNull permissions: Array<out String>, @NonNull grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if (mShouldApplyDayNightModeForOptionsMenu && Pref.isNightModeEnabled()) {
            for (i in 0 until menu.size()) {
                val drawable = menu.getItem(i).icon
                if (drawable != null) {
                    drawable.mutate()
                    drawable.setColorFilter(ContextCompat.getColor(this, R.color.toolbar), PorterDuff.Mode.SRC_ATOP)
                }
            }
            mShouldApplyDayNightModeForOptionsMenu = false
        }
        return super.onPrepareOptionsMenu(menu)
    }

    companion object {

        protected const val PERMISSION_REQUEST_CODE = 11186

        fun setToolbarAsBack(activity: AppCompatActivity, id: Int, title: String) {
            val toolbar = activity.findViewById<Toolbar>(id)
            toolbar.title = title
            activity.setSupportActionBar(toolbar)
            activity.supportActionBar?.let {
                toolbar.setNavigationOnClickListener { activity.finish() }
                it.setDisplayHomeAsUpEnabled(true)
            }
        }
    }
}
