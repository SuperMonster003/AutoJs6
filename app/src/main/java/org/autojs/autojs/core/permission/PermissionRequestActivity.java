package org.autojs.autojs.core.permission;

import android.app.Activity;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.autojs.autojs.permission.Base;

public class PermissionRequestActivity extends Activity {

    public static final String EXTRA_PERMISSIONS = "permissions";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String[] permissions = getIntent().getStringArrayExtra(EXTRA_PERMISSIONS);
        if (permissions == null || permissions.length == 0) {
            finish();
            return;
        }
        requestPermissions(permissions, Base.REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        finish();
    }

}
