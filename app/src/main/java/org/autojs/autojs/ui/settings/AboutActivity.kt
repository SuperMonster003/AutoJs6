package org.autojs.autojs.ui.settings

import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Handler
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.ImageView
import android.widget.TextView
import com.afollestad.materialdialogs.MaterialDialog
import com.jaredrummler.android.widget.AnimatedSvgView
import de.psdev.licensesdialog.LicenseResolver
import de.psdev.licensesdialog.LicensesDialog
import org.androidannotations.annotations.AfterViews
import org.androidannotations.annotations.Click
import org.androidannotations.annotations.EActivity
import org.androidannotations.annotations.LongClick
import org.androidannotations.annotations.ViewById
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.ui.common.NotAskAgainDialog
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.DeviceUtils
import org.autojs.autojs.util.UpdateUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R

/**
 * Created by Stardust on 2017/2/2.
 * Modified by SuperMonster003 as of Dec 1, 2021.
 */

@EActivity(R.layout.activity_about)
open class AboutActivity : BaseActivity() {

    @ViewById(R.id.version)
    lateinit var mVersionView: TextView

    @ViewById(R.id.since)
    lateinit var mSinceView: TextView

    @AfterViews
    fun setUpViews() {
        setVersionName()
        setSinceDate()
        setToolbarAsBack(R.string.text_about)
    }

    override fun onResume() {
        super.onResume()

        val iconView = findViewById<ImageView>(R.id.icon_about_app)
        val animatedSvgView = findViewById<AnimatedSvgView>(R.id.icon_about_app_svg_view)

        val animationDuration: Long = 500

        val onStateChangeListener: (state: Int) -> Unit = { state ->
            if (state == AnimatedSvgView.STATE_FINISHED) {
                AlphaAnimation(0f, 1f).also {
                    it.duration = animationDuration
                    iconView.apply { visibility = View.VISIBLE }.startAnimation(it)
                }
                AlphaAnimation(1f, 0f).also {
                    it.duration = animationDuration
                    animatedSvgView.apply { visibility = View.INVISIBLE }.startAnimation(it)
                }
            }
        }

        Handler(mainLooper).postDelayed({
            animatedSvgView.run {
                setOnStateChangeListener(onStateChangeListener)
                start()
            }
        }, 100)
    }

    private fun setVersionName() {
        mVersionView.text = BuildConfig.VERSION_NAME
    }

    private fun setSinceDate() {
        mSinceView.text = BuildConfig.VERSION_DATE
    }

    //    @Click(R.id.github_1)
    //    void openGitHub() {
    //        IntentUtils.browse(this, getString(R.string.my_github));
    //    }

    //    @Click(R.id.email_1)
    //    void openEmailToSendMe() {
    //        String email = getString(R.string.email);
    //        IntentUtils.sendMailTo(this, email);
    //    }

    //    @Click(R.id.share)
    //    void share() {
    //        IntentUtils.shareText(this, getString(R.string.share_app));
    //    }

    @Click(R.id.avatar_original_developer, R.id.avatar_developer, R.id.avatar_original_developer_user_contents, R.id.avatar_developer_user_contents)
    fun toastForUnderDevelopment() {
        ViewUtils.showToast(this, R.string.text_developer_details_under_development)
    }

    @Click(R.id.icon_about_app_svg_view, R.id.icon_about_app)
    fun showDeviceInfo() {
        MaterialDialog.Builder(this)
            .title(R.string.text_app_and_device_info)
            .content(DeviceUtils.getDeviceSummaryWithSimpleAppInfo(this))
            .negativeText(R.string.text_back)
            .neutralText(R.string.text_copy)
            .onNeutral { d, _ ->
                ClipboardUtils.setClip(this, d.contentView?.text)
                ViewUtils.showToast(this, R.string.text_already_copied_to_clip)
            }
            .neutralColorRes(R.color.dialog_button_hint)
            .build()
            .apply { window?.setBackgroundDrawableResource(R.color.about_app_dev_info_dialog_background) }
            .show()
    }

    @LongClick(R.id.icon_about_app_svg_view, R.id.icon_about_app)
    fun launchDeveloperOptions() {
        startActivity(Intent(this, DeveloperOptionsActivity_::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @Click(R.id.icon_1st_developer_identifier)
    fun toastForFirstDeveloperIdentifier() {
        ViewUtils.showToast(this, R.string.text_original_developer)
    }

    @Click(R.id.icon_2nd_developer_identifier)
    fun toastForSecondDeveloperIdentifier() {
        ViewUtils.showToast(this, R.string.text_tailor_made_developer)
    }

    @Click(R.id.about_functions_button_licenses)
    fun showLicensesDialog() {
        LicenseResolver.registerLicense(MozillaPublicLicense20.instance)
        LicensesDialog.Builder(this)
            .setTitle(R.string.text_licenses)
            .setCloseText(R.string.dialog_button_back)
            .setNotices(R.raw.licenses)
            .setIncludeOwnLicense(true)
            .setEnableDarkMode(true)
            .setNoticesCssStyle(R.string.css_style_license_dialog_notices)
            .setDividerColor(getColor(R.color.day_night))
            .build()
            .show()
    }

    @Click(R.id.about_functions_button_update)
    fun checkForUpdates() {
        UpdateUtils.getDialogChecker(this).checkNow()
    }

    @Click(R.id.about_functions_button_feedback)
    fun startFeedbackActivity() {
        val key = "${AboutActivity::class.simpleName}.start_feedback_activity"
        NotAskAgainDialog.Builder(this, key)
            .title(R.string.text_prompt)
            .content(R.string.content_github_feedback)
            .negativeText(R.string.text_back)
            .positiveText(R.string.text_continue)
            .onNegative { d, _ -> d.dismiss() }
            .onPositive { _, _ -> launchGithubIssuesPage() }
            .cancelable(false)
            .show() ?: launchGithubIssuesPage()
    }

    private fun launchGithubIssuesPage() {
        Intent(Intent.ACTION_VIEW)
            .setData(Uri.parse(getString(R.string.url_github_autojs6_issues)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .let { startActivity(it) }
    }

    companion object {

        fun startActivity(context: Context) {
            Intent(context, AboutActivity_::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .let { context.startActivity(it) }
        }

    }

}