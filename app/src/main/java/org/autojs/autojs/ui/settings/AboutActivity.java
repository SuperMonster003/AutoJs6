package org.autojs.autojs.ui.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.widget.TextView;

import com.stardust.app.GlobalAppContext;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.ViewById;
import org.autojs.autojs6.BuildConfig;
import org.autojs.autojs6.R;
import org.autojs.autojs.ui.BaseActivity;
import org.autojs.autojs.ui.error.IssueReporterActivity;
import org.autojs.autojs.ui.update.UpdateCheckDialog;

import de.psdev.licensesdialog.LicenseResolver;
import de.psdev.licensesdialog.LicensesDialog;
import de.psdev.licensesdialog.licenses.License;

/**
 * Created by Stardust on 2017/2/2.
 */
@SuppressLint("NonConstantResourceId")
@EActivity(R.layout.activity_about)
public class AboutActivity extends BaseActivity {
    @ViewById(R.id.version)
    TextView mVersion;

    @ViewById(R.id.since)
    TextView mSince;

    @AfterViews
    void setUpViews() {
        setVersionName();
        setSinceDate();
        setToolbarAsBack(getString(R.string.text_about));
    }

    @SuppressLint("SetTextI18n")
    private void setVersionName() {
        mVersion.setText(BuildConfig.VERSION_NAME);
    }

    @SuppressLint("SetTextI18n")
    private void setSinceDate() {
        mSince.setText(BuildConfig.VERSION_DATE);
    }

//    @SuppressLint("NonConstantResourceId")
//    @Click(R.id.github_1)
//    void openGitHub() {
//        IntentTool.browse(this, getString(R.string.my_github));
//    }
//
//    @SuppressLint("NonConstantResourceId")
//    @Click(R.id.email_1)
//    void openEmailToSendMe() {
//        String email = getString(R.string.email);
//        IntentUtil.sendMailTo(this, email);
//    }


//    @SuppressLint("NonConstantResourceId")
//    @Click(R.id.share)
//    void share() {
//        IntentUtil.shareText(this, getString(R.string.share_app));
//    }

    @SuppressLint("NonConstantResourceId")
    @Click({
            R.id.avatar_original_developer,
            R.id.avatar_developer,
            R.id.avatar_original_developer_user_contents,
            R.id.avatar_developer_user_contents,
    })
    void toastForUnderDevelopment() {
        GlobalAppContext.toast(R.string.text_developer_details_under_development);
    }

    @SuppressLint("NonConstantResourceId")
    @Click(R.id.icon_about_app)
    void toastForIconAboutApp() {
        GlobalAppContext.toast("Version code: " + BuildConfig.VERSION_CODE);
    }

    @SuppressLint("NonConstantResourceId")
    @Click(R.id.icon_1st_developer_identifier)
    void toastForFirstDeveloperIdentifier() {
        GlobalAppContext.toast(R.string.text_original_developer);
    }

    @SuppressLint("NonConstantResourceId")
    @Click(R.id.icon_2nd_developer_identifier)
    void toastForSecondDeveloperIdentifier() {
        GlobalAppContext.toast(R.string.text_tailor_made_developer);
    }

    @SuppressLint("NonConstantResourceId")
    @Click(R.id.about_functions_button_licenses)
    void showLicensesDialog() {
        LicenseResolver.registerLicense(MozillaPublicLicense20.instance);
        new LicensesDialog.Builder(this)
                .setTitle(R.string.text_licenses)
                .setNotices(R.raw.licenses)
                .setIncludeOwnLicense(true)
                .build()
                .show();
    }

    @SuppressLint("NonConstantResourceId")
    @Click(R.id.about_functions_button_update)
    void checkForUpdated() {
        new UpdateCheckDialog(this).show();
    }

    @SuppressLint("NonConstantResourceId")
    @Click(R.id.about_functions_button_feedback)
    void startFeedbackActivity() {
        startActivity(new Intent(this, IssueReporterActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    public static class MozillaPublicLicense20 extends License {

        public static MozillaPublicLicense20 instance = new MozillaPublicLicense20();

        @Override
        public String getName() {
            return "Mozilla Public License 2.0";
        }

        @Override
        public String readSummaryTextFromResources(Context context) {
            return getContent(context, R.raw.mpl_20_summary);
        }

        @Override
        public String readFullTextFromResources(Context context) {
            return getContent(context, R.raw.mpl_20_full);
        }

        @Override
        public String getVersion() {
            return "2.0";
        }

        @Override
        public String getUrl() {
            return "https://www.mozilla.org/en-US/MPL/2.0/";
        }
    }

}
