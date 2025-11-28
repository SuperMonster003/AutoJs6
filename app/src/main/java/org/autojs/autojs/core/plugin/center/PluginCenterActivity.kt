package org.autojs.autojs.core.plugin.center

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityPluginCenterBinding

@SuppressLint("NotifyDataSetChanged")
class PluginCenterActivity : BaseActivity() {

    private lateinit var binding: ActivityPluginCenterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPluginCenterBinding.inflate(layoutInflater).also {
            setContentView(it.root)
        }

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_plugin_center, PluginCenterFragment())
            .commit()

        setToolbarAsBack(R.string.text_plugin_center)
    }

    companion object {

        fun startActivity(context: Context) {
            Intent(context, PluginCenterActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .let { context.startActivity(it) }
        }

    }

}