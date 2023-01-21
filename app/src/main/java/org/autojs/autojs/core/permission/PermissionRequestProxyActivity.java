package org.autojs.autojs.core.permission;

public interface PermissionRequestProxyActivity {

    void addRequestPermissionsCallback(OnRequestPermissionsResultCallback callback);

    boolean removeRequestPermissionsCallback(OnRequestPermissionsResultCallback callback);

    void requestPermissions(String[] permissions, int requestCode);

}
