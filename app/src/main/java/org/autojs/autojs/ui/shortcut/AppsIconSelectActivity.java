package org.autojs.autojs.ui.shortcut;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.autojs.autojs.groundwork.WrapContentGridLayoutManger;
import org.autojs.autojs.ui.BaseActivity;
import org.autojs.autojs6.R;
import org.autojs.autojs6.databinding.ActivityAppsIconSelectBinding;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by Stardust on Oct 25, 2017.
 */
public class AppsIconSelectActivity extends BaseActivity {

    private RecyclerView mApps;

    public static final String EXTRA_PACKAGE_NAME = "extra_package_name";
    private PackageManager mPackageManager;
    private final List<AppItem> mAppList = new ArrayList<>();

    public static void launchForResult(Activity activity, int requestCode) {
        ActivityCompat.startActivityForResult(activity, new Intent(activity, AppsIconSelectActivity.class), requestCode, null);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityAppsIconSelectBinding binding = ActivityAppsIconSelectBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setToolbarAsBack(R.string.text_select_icon);

        mApps = binding.apps;
        mApps.setAdapter(new AppsAdapter());

        WrapContentGridLayoutManger manager = new WrapContentGridLayoutManger(this, 5);
        manager.setDebugInfo("IconSelectView");
        mApps.setLayoutManager(manager);

        loadApps();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint("CheckResult")
    private void loadApps() {
        mPackageManager = getPackageManager();
        List<ApplicationInfo> packages = mPackageManager.getInstalledApplications(PackageManager.GET_META_DATA);
        Observable.fromIterable(packages)
                .observeOn(Schedulers.computation())
                .filter(appInfo -> appInfo.icon != 0)
                .map(AppItem::new)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(icon -> {
                    mAppList.add(icon);
                    mApps.getAdapter().notifyItemInserted(mAppList.size() - 1);
                });

    }

    private void selectApp(AppItem appItem) {
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_PACKAGE_NAME, appItem.info.packageName));
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_icon_select, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT)
                .setType("image/*"), 11234);
        return true;
    }

    @SuppressLint("MissingSuperCall")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            setResult(RESULT_OK, data);
            finish();
        }
    }

    public static Observable<Drawable> getDrawableFromIntent(Context context, Intent data) {
        String packageName = data.getStringExtra(EXTRA_PACKAGE_NAME);
        if (packageName != null) {
            return Observable.fromCallable(() -> context.getPackageManager().getApplicationIcon(packageName));
        }
        Uri uri = data.getData();
        if (uri == null) {
            return Observable.error(new IllegalArgumentException("invalid intent"));
        }
        return Observable.fromCallable(() -> Drawable.createFromStream(context.getContentResolver().openInputStream(uri), null));
    }

    private class AppItem {
        Drawable icon;
        ApplicationInfo info;

        public AppItem(ApplicationInfo info) {
            this.info = info;
            icon = info.loadIcon(mPackageManager);
        }
    }

    private class AppIconViewHolder extends RecyclerView.ViewHolder {

        ImageView icon;

        public AppIconViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.thumbAppIcon);
            icon.setOnClickListener(v -> selectApp(mAppList.get(getAdapterPosition())));
        }
    }


    private class AppsAdapter extends RecyclerView.Adapter<AppIconViewHolder> {

        @NonNull
        @Override
        public AppIconViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new AppIconViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.app_icon_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(AppIconViewHolder holder, int position) {
            holder.icon.setImageDrawable(mAppList.get(position).icon);
        }

        @Override
        public int getItemCount() {
            return mAppList.size();
        }
    }

}
