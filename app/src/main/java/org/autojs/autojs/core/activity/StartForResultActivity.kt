package org.autojs.autojs.core.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import org.autojs.autojs.tool.IntentExtras

/**
 * Created by SuperMonster003 on Dec 14, 2023.
 * Transformed by SuperMonster003 on Jan 9, 2024.
 */
// @Reference to com.stardust.autojs.core.activity.StartForResultActivity from Auto.js Pro 9.3.11 by SuperMonster003 on Dec 14, 2023.
class StartForResultActivity : Activity() {

    private val mFindViewCache: MutableMap<Int, View> = LinkedHashMap()

    private var mCallback: Callback? = null

    interface Callback {
        fun onActivityCreate(activity: StartForResultActivity?)
        fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        mCallback?.onActivityResult(requestCode, resultCode, data)
        finish()
    }

    public override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        var extras: IntentExtras? = null
        val intExtra = intent.getIntExtra(IntentExtras.EXTRA_ID, -1)
        if (intExtra >= 0) {
            IntentExtras.extraStore[intExtra]?.let { map ->
                IntentExtras.extraStore.remove(intExtra)
                extras = IntentExtras(intExtra, map)
            }
        }
        if (extras == null) {
            finish().also { return }
        }
        val callback = extras!!.map["callback"]
        if (callback is Callback) {
            mCallback = callback
        }
        if (callback == null) {
            mCallback = null
            finish().also { return }
        }
        mCallback!!.onActivityCreate(this)
    }

    fun clearFindViewByIdCache() = mFindViewCache.clear()

    fun findCachedViewById(id: Int) = mFindViewCache[id] ?: findViewById<View>(id)?.also { mFindViewCache[id] = it }

    companion object {

        @JvmStatic
        fun start(context: Context, callback: Callback?) {
            Intent(context, StartForResultActivity::class.java).also { intent ->
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.putExtra(IntentExtras.EXTRA_ID, IntentExtras().apply { map["callback"] = callback }.id)
                context.startActivity(intent)
            }
        }

    }

}
