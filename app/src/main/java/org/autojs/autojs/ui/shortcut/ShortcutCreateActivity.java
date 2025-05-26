package org.autojs.autojs.ui.shortcut;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import com.afollestad.materialdialogs.MaterialDialog;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import org.autojs.autojs.external.ScriptIntents;
import org.autojs.autojs.external.shortcut.Shortcut;
import org.autojs.autojs.external.shortcut.ShortcutActivity;
import org.autojs.autojs.model.script.ScriptFile;
import org.autojs.autojs.theme.ThemeColorManager;
import org.autojs.autojs.util.BitmapUtils;
import org.autojs.autojs.util.ColorUtils;
import org.autojs.autojs.util.ShortcutUtils;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;
import org.autojs.autojs6.databinding.ShortcutCreateDialogBinding;

/**
 * Created by Stardust on Oct 25, 2017.
 */
public class ShortcutCreateActivity extends AppCompatActivity {

    public static final String EXTRA_FILE = "file";
    private static final String LOG_TAG = "ShortcutCreateActivity";
    private ScriptFile mScriptFile;
    private boolean mIsDefaultIcon = true;

    TextView mName;
    ImageView mIcon;
    CheckBox mUseAndroidNShortcut;

    private int mAdjustedContrastColor;
    private int mAdjustedContrastColorConsiderable;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View view = View.inflate(this, R.layout.shortcut_create_dialog, null);
        ShortcutCreateDialogBinding binding = ShortcutCreateDialogBinding.bind(view);

        mName = binding.name;
        mIcon = binding.icon;

        mUseAndroidNShortcut = binding.useAndroidNShortcut;

        mAdjustedContrastColor = ColorUtils.adjustColorForContrast(mIcon.getContext().getColor(R.color.window_background), ThemeColorManager.getColorPrimary(), 1.15);
        mAdjustedContrastColorConsiderable = ColorUtils.adjustColorForContrast(mIcon.getContext().getColor(R.color.window_background), ThemeColorManager.getColorPrimary(), 2.3);
        Drawable background = mIcon.getBackground();
        if (background != null) {
            background.setTint(mAdjustedContrastColor);
        }
        mIcon.setBackground(background);
        mIcon.setImageTintList(ColorStateList.valueOf(ViewUtils.getDayOrNightColorByLuminance(this, mAdjustedContrastColor)));
        mIcon.setOnClickListener(v -> AppsIconSelectActivity.launchForResult(this, 21209));

        mScriptFile = (ScriptFile) getIntent().getSerializableExtra(EXTRA_FILE);
        showDialog(view);
    }

    private void showDialog(View view) {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.N_MR1) {
            mUseAndroidNShortcut.setVisibility(View.VISIBLE);
        } else {
            mUseAndroidNShortcut.setVisibility(View.GONE);
        }
        mName.setText(mScriptFile.getSimplifiedName());
        new MaterialDialog.Builder(this)
                .customView(view, false)
                .title(R.string.text_send_shortcut)
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_attraction)
                .onPositive((dialog, which) -> {
                    createShortcut();
                    finish();
                })
                .cancelListener(dialog -> finish())
                .show();
    }

    private void createShortcut() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createShortcutByShortcutManager();
            return;
        }
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.N_MR1 && mUseAndroidNShortcut.isChecked()) {
            createShortcutByShortcutManager();
            return;
        }
        Shortcut shortcut = new Shortcut(this);
        if (mIsDefaultIcon) {
            shortcut.iconRes(R.drawable.ic_node_js_black);
        } else {
            Bitmap bitmap = BitmapUtils.drawableToBitmap(mIcon.getDrawable());
            shortcut.icon(bitmap);
        }
        shortcut.name(mName.getText().toString())
                .targetClass(ShortcutActivity.class)
                .extras(new Intent().putExtra(ScriptIntents.EXTRA_KEY_PATH, mScriptFile.getPath()))
                .send();
    }

    @RequiresApi(api = Build.VERSION_CODES.N_MR1)
    private void createShortcutByShortcutManager() {

        // @Caution by SuperMonster003 on Dec 7, 2024.
        //  ! It is necessary to convert to String type here,
        //  ! rather than retaining the CharSequence type obtained from getText().
        //  ! This is because on certain devices [such as MIUI 14 (Android 13)],
        //  ! ShortcutInfoCompat.Builder#setShortLabel and setLongLabel
        //  ! can only accept String type data returned by getText().
        //  ! If CharSequence type data is directly passed in, the shortcut will fail to create properly.
        //  !
        //  ! Refer to: http://issues.autojs6.com/221
        //  !
        //  ! zh-CN:
        //  !
        //  ! 此处需要转换为 String 类型, 而不能保留 getText() 方法得到的 CharSequence 类型.
        //  ! 这是因为在一些特殊设备上 [如 MIUI 14 (Android 13)],
        //  ! ShortcutInfoCompat.Builder#setShortLabel 及 setLongLabel 只能接受 getText() 返回的 String 类型数据,
        //  ! 如果直接传入 CharSequence 类型数据, 快捷方式将无法正常创建.
        //  !
        //  ! 参阅: http://issues.autojs6.com/221
        //  !
        //  # CharSequence name = mName.getText();
        String name = mName.getText().toString();

        /* To make each script be able to have its own individual icon. */
        String id = ShortcutActivity.class.getName() + "$" + name + "@" + System.currentTimeMillis();
        Intent intent = new Intent(this, ShortcutActivity.class)
                .putExtra(ScriptIntents.EXTRA_KEY_PATH, mScriptFile.getPath())
                .setAction(Intent.ACTION_MAIN);
        if (mIsDefaultIcon) {
            ShortcutUtils.requestPinShortcut(this, id, intent, name, name, R.drawable.ic_file_type_js_dark_green);
        } else {
            ShortcutUtils.requestPinShortcut(this, id, intent, name, name, mIcon.getDrawable());
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint({"CheckResult", "MissingSuperCall"})
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            return;
        }
        String packageName = data.getStringExtra(AppsIconSelectActivity.EXTRA_PACKAGE_NAME);
        if (packageName != null) {
            try {
                mIcon.setImageDrawable(getPackageManager().getApplicationIcon(packageName));
                mIcon.setImageTintList(null);
                Drawable background = AppCompatResources.getDrawable(this, R.drawable.shortcut_create_dialog_icon_border);
                if (background != null) {
                    background.setTint(mAdjustedContrastColorConsiderable);
                }
                mIcon.setBackground(background);
                mIsDefaultIcon = false;
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        Observable.fromCallable(() -> BitmapFactory.decodeStream(getContentResolver().openInputStream(uri)))
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((bitmap -> {
                    mIcon.setImageBitmap(bitmap);
                    mIsDefaultIcon = false;
                }), error -> Log.e(LOG_TAG, "decode stream", error));

    }
}
