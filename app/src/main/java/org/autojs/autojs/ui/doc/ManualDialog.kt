package org.autojs.autojs.ui.doc

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.ui.widget.EWebView
import org.autojs.autojs6.databinding.FloatingManualDialogBinding

/**
 * Created by Stardust on Oct 24, 2017.
 * Modified by SuperMonster003 as of May 26, 2022.
 * Transformed by SuperMonster003 on May 13, 2023.
 */
class ManualDialog(private val mContext: Context) {

    private var mTitle: TextView
    private var mEWebView: EWebView
    private var mPinToLeft: View
    private var mDialog: Dialog

    init {
        val binding = FloatingManualDialogBinding.inflate(LayoutInflater.from(mContext))
        val view = binding.root

        mTitle = binding.title
        mEWebView = binding.ewebView
        mPinToLeft = binding.pinToLeft

        binding.fullscreen.setOnClickListener { viewInNewActivity() }
        binding.close.setOnClickListener { close() }

        mDialog = MaterialDialog.Builder(mContext)
            .customView(view, false)
            .build()
            .apply {
                window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            }
    }

    fun title(title: String?) = also { mTitle.text = title }

    fun url(url: String?) = also { mEWebView.webView.loadUrl(url!!) }

    fun pinToLeft(listener: View.OnClickListener) = also {
        mPinToLeft.setOnClickListener { v: View? ->
            mDialog.dismiss()
            listener.onClick(v)
        }
    }

    fun show() = also { mDialog.show() }

    fun close() = mDialog.dismiss()

    fun viewInNewActivity() {
        mDialog.dismiss()
        Intent(mContext, DocumentationActivity::class.java)
            .putExtra(DocumentationActivity.EXTRA_URL, mEWebView.webView.url)
            .let { mContext.startActivity(it) }
    }

}
