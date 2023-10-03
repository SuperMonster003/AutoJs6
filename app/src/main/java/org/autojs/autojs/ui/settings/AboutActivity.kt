package org.autojs.autojs.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.animation.AlphaAnimation
import com.afollestad.materialdialogs.MaterialDialog
import com.jaredrummler.android.widget.AnimatedSvgView
import de.psdev.licensesdialog.LicenseResolver
import de.psdev.licensesdialog.LicensesDialog
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.ui.common.NotAskAgainDialog
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.DeviceUtils
import org.autojs.autojs.util.UpdateUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityAboutBinding
import org.autojs.autojs6.databinding.ActivityAboutFunctionButtonsBinding
import org.autojs.autojs6.databinding.ActivityAboutItemsBinding

/**
 * Created by Stardust on 2017/2/2.
 * Modified by SuperMonster003 as of Dec 1, 2021.
 */
open class AboutActivity : BaseActivity() {

    private lateinit var activityBinding: ActivityAboutBinding
    private lateinit var itemsBinding: ActivityAboutItemsBinding
    private lateinit var functionsButtonsBinding: ActivityAboutFunctionButtonsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityBinding = ActivityAboutBinding.inflate(layoutInflater).also {
            setContentView(it.root)
            it.version.text = BuildConfig.VERSION_NAME
            it.since.text = BuildConfig.VERSION_DATE
            itemsBinding = it.activityAboutItems
            functionsButtonsBinding = it.activityAboutFunctionButtons
        }
        setToolbarAsBack(R.string.text_about)
        setupBindingListeners()
    }

    private fun setupBindingListeners() {
        itemsBinding.avatarOriginalDeveloper.setOnClickListener { toastForUnderDevelopment() }
        itemsBinding.avatarDeveloper.setOnClickListener { toastForUnderDevelopment() }

        itemsBinding.avatarOriginalDeveloperUserContents.setOnClickListener { toastForUnderDevelopment() }
        itemsBinding.avatarDeveloperUserContents.setOnClickListener { toastForUnderDevelopment() }

        itemsBinding.icon1stDeveloperIdentifier.setOnClickListener { toastForFirstDeveloperIdentifier() }
        itemsBinding.icon2ndDeveloperIdentifier.setOnClickListener { toastForSecondDeveloperIdentifier() }

        activityBinding.iconAboutAppSvgView.setOnClickListener { showDeviceInfo() }
        activityBinding.iconAboutAppSvgView.setOnLongClickListener { true.also { launchDeveloperOptions() } }

        activityBinding.iconAboutApp.setOnClickListener { showDeviceInfo() }
        activityBinding.iconAboutApp.setOnLongClickListener { true.also { launchDeveloperOptions() } }

        functionsButtonsBinding.aboutFunctionsButtonLicenses.setOnClickListener { showLicensesDialog() }
        functionsButtonsBinding.aboutFunctionsButtonUpdate.setOnClickListener { checkForUpdates() }
        functionsButtonsBinding.aboutFunctionsButtonFeedback.setOnClickListener { startFeedbackActivity() }
    }

    override fun onResume() {
        super.onResume()

        val appIconView = activityBinding.iconAboutApp
        val animatedSvgView = activityBinding.iconAboutAppSvgView

        val animationDuration: Long = 500

        val onStateChangeListener: (state: Int) -> Unit = { state ->
            if (state == AnimatedSvgView.STATE_FINISHED) {
                AlphaAnimation(0f, 1f).also {
                    it.duration = animationDuration
                    appIconView.apply { visibility = View.VISIBLE }.startAnimation(it)
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

    private fun toastForUnderDevelopment() {
        ViewUtils.showToast(this, R.string.text_developer_details_under_development)
    }

    private fun showDeviceInfo() {
        MaterialDialog.Builder(this)
            .title(R.string.text_app_and_device_info)
            .content(DeviceUtils.getDeviceSummaryWithSimpleAppInfo(this))
            .neutralText(R.string.dialog_button_copy)
            .onNeutral { d, _ ->
                ClipboardUtils.setClip(this, d.contentView?.text)
                ViewUtils.showToast(this, R.string.text_already_copied_to_clip)
            }
            .neutralColorRes(R.color.dialog_button_hint)
            .negativeText(R.string.dialog_button_dismiss)
            .build()
            .apply { window?.setBackgroundDrawableResource(R.color.about_app_dev_info_dialog_background) }
            .show()
    }

    private fun launchDeveloperOptions() {
        startActivity(Intent(this, DeveloperOptionsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun toastForFirstDeveloperIdentifier() {
        ViewUtils.showToast(this, R.string.text_original_developer)
    }

    private fun toastForSecondDeveloperIdentifier() {
        ViewUtils.showToast(this, R.string.text_tailor_made_developer)
    }

    private fun showLicensesDialog() {
        LicenseResolver.registerLicense(MozillaPublicLicense20.instance)
        LicensesDialog.Builder(this)
            .setTitle(R.string.text_licenses)
            .setCloseText(R.string.dialog_button_dismiss)
            .setNotices(R.raw.licenses)
            .setIncludeOwnLicense(true)
            .setEnableDarkMode(true)
            .setNoticesCssStyle(R.string.css_style_license_dialog_notices)
            .setDividerColor(getColor(R.color.day_night))
            .build()
            .show()
    }

    private fun checkForUpdates() {
        UpdateUtils.getDialogChecker(this).checkNow()
    }

    private fun startFeedbackActivity() {
        val key = "${AboutActivity::class.simpleName}.start_feedback_activity"
        NotAskAgainDialog.Builder(this, key)
            .title(R.string.text_prompt)
            .content(R.string.content_github_feedback)
            .negativeText(R.string.dialog_button_cancel)
            .positiveText(R.string.dialog_button_continue)
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
            Intent(context, AboutActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .let { context.startActivity(it) }
        }

    }

}