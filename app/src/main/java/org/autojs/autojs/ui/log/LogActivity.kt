package org.autojs.autojs.ui.log

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import org.autojs.autojs.AutoJs.Companion.instance
import org.autojs.autojs.core.console.GlobalConsole
import org.autojs.autojs.runtime.api.Mime
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.showToast
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityLogBinding

class LogActivity : BaseActivity() {

    private lateinit var mConsoleImpl: GlobalConsole

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val activityLogBinding = ActivityLogBinding.inflate(layoutInflater)

        setContentView(activityLogBinding.root)
        setToolbarAsBack(R.string.text_log)

        activityLogBinding.console.apply {
            setConsole(instance.globalConsole.also { mConsoleImpl = it })
            setLogActivity(this@LogActivity)
            setPinchToZoomEnabled(true)
        }

        activityLogBinding.fab.apply {
            setOnClickListener { mConsoleImpl.clear() }
            ViewUtils.excludeFloatingActionButtonFromNavigationBar(this)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_console, menu)
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RESULT_CODE_EXPORT && resultCode == RESULT_OK) {
            mConsoleImpl.export(data?.data)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_copy -> {
                mConsoleImpl.copyAll()
            }
            R.id.action_send -> {
                mConsoleImpl.send()
            }
            R.id.action_export -> {
                mConsoleImpl.export()
            }
            R.id.action_clear -> {
                mConsoleImpl.clear()
            }
            R.id.action_settings -> {
                // TODO by SuperMonster003 on Jul 3, 2023.
                showToast(this, R.string.text_under_development)
            }
        }
        return true
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.no_anim_fade_in, R.anim.no_anim_fade_out)
    }

    @Suppress("DEPRECATION")
    fun export(fileName: String?) {
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            setType(Mime.TEXT_PLAIN)
            putExtra(Intent.EXTRA_TITLE, fileName)
            startActivityForResult(this, RESULT_CODE_EXPORT)
        }
    }

    companion object {

        private const val RESULT_CODE_EXPORT = 33128

        @JvmStatic
        fun launch(context: Context) {
            val intent = Intent(context, LogActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            val options = ActivityOptions.makeCustomAnimation(context, R.anim.no_anim_fade_in, R.anim.no_anim_fade_out)
            context.startActivity(intent, options.toBundle())
        }

    }

}
