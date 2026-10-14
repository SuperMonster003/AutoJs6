package org.autojs.autojs.ui.doc

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.widget.ImageViewCompat
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.ui.widget.EWebView
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.WebViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.FloatingManualDialogBinding

/**
 * Created by Stardust on Oct 24, 2017.
 * Modified by SuperMonster003 as of May 26, 2022.
 * Transformed by SuperMonster003 on May 13, 2023.
 */
class ManualDialog(
    private val mContext: Context,
    @param:ColorInt private val mBackground: Int,
    @param:ColorInt private val mForeground: Int,
) {

    private val mDarkMode = ColorUtils.isLuminanceDark(mBackground)
    private val mThemedContext = ContextThemeWrapper(
        mContext,
        if (mDarkMode) R.style.Material3DarkTheme else R.style.Material3LightTheme,
    )
    private val mBinding = FloatingManualDialogBinding.inflate(LayoutInflater.from(mThemedContext))
    private var mTitle: TextView
    private var mEWebView: EWebView
    private var mPinToLeft: View
    private var mDialog: Dialog
    private var mPageController: DocumentationPageController

    init {
        val binding = mBinding
        val view = binding.root

        mTitle = binding.title
        mEWebView = binding.ewebView
        mPinToLeft = binding.pinToLeft
        mPageController = DocumentationPageController(mContext, binding.root, mEWebView)
        WebViewUtils.adaptDarkMode(mEWebView.webView, mDarkMode)

        binding.fullscreen.setOnClickListener { viewInNewActivity() }
        binding.close.setOnClickListener { close() }

        mDialog = MaterialDialog.Builder(mContext)
            .customView(view, false)
            .build()
            .apply {
                window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setOnDismissListener { mPageController.dispose() }
            }

        palette(mBackground, mForeground)
    }

    fun title(title: String?) = also { mTitle.text = title }

    fun palette(
        @ColorInt background: Int,
        @ColorInt foreground: Int,
    ) = also {
        mBinding.root.setCardBackgroundColor(background)
        mBinding.title.setTextColor(foreground)
        listOf(mBinding.pinToLeft, mBinding.fullscreen, mBinding.close).forEach { icon ->
            ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(foreground))
        }
        mBinding.divider.setBackgroundColor(ColorUtils.applyAlpha(foreground, 0.18))
        mBinding.root.findViewById<OfflineDocsUnavailableView>(R.id.offline_docs_unavailable)
            .setPalette(background, foreground)
        mEWebView.setBackgroundColor(background)
        mEWebView.webView.setBackgroundColor(background)
    }

    fun url(url: String?) = also { mPageController.start(url) }

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
            .putExtra(DocumentationActivity.EXTRA_URL, mPageController.currentUrl)
            .startSafely(mContext)
    }

}
