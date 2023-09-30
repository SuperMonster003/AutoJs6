package org.autojs.autojs.inrt

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import org.autojs.autojs.AutoJs
import org.autojs.autojs.inrt.launch.GlobalProjectLauncher
import org.autojs.autojs6.R

/**
 * Created by Stardust on 2018/2/2.
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(@Nullable savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_inrt)
        val slug = findViewById<TextView>(R.id.slug)
        slug.typeface = Typeface.createFromAsset(assets, "roboto_medium.ttf")
        if (!Pref.isFirstUsing) {
            main()
        } else {
            Handler(Looper.myLooper()!!).postDelayed({ this@SplashActivity.main() }, INIT_TIMEOUT)
        }
    }

    private fun main() {
        checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_PHONE_STATE)
    }


    private fun runScript() {
        Thread {
            try {
                GlobalProjectLauncher.launch(this)
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@SplashActivity, e.message, Toast.LENGTH_LONG).show()
                    startActivity(Intent(this@SplashActivity, LogActivity::class.java))
                    AutoJs.instance.globalConsole.printAllStackTrace(e)
                }
            }
        }.start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, @NonNull permissions: Array<String>, @NonNull grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        runScript()
    }

    private fun checkPermission(vararg permissions: String) {
        val requestPermissions = getRequestPermissions(permissions)
        if (requestPermissions.isNotEmpty()) {
            requestPermissions(requestPermissions, PERMISSION_REQUEST_CODE)
        } else {
            runScript()
        }
    }


    private fun getRequestPermissions(permissions: Array<out String>): Array<String> {
        val list = ArrayList<String>()
        for (permission in permissions) {
            if (checkSelfPermission(permission) == PERMISSION_DENIED) {
                list.add(permission)
            }
        }
        return list.toTypedArray()
    }

    companion object {

        private const val PERMISSION_REQUEST_CODE = 11186
        private const val INIT_TIMEOUT: Long = 1000
    }

}

