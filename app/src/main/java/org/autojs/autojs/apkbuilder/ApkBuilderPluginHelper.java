package org.autojs.autojs.apkbuilder;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.autojs.autojs.pio.UncheckedIOException;

import org.autojs.autojs6.BuildConfig;

import org.autojs.autojs.util.DeveloperUtils;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by Stardust on 2017/11/29.
 */
public class ApkBuilderPluginHelper {

    private static final String PLUGIN_PACKAGE_NAME = "org.autojs.apkbuilderplugin";
    private static final String TEMPLATE_APK_PATH = "template.apk";
    private static final boolean DEBUG_APK_PLUGIN = false;

    public static boolean isPluginAvailable(Context context) {
//        return DeveloperUtils.checkSignature(context, PLUGIN_PACKAGE_NAME);
        try {
            context.getPackageManager().getPackageInfo(PLUGIN_PACKAGE_NAME, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
        return true;
    }

    public static InputStream openTemplateApk(Context context) {
        try {
            if (DEBUG_APK_PLUGIN && BuildConfig.DEBUG) {
                return context.getAssets().open(TEMPLATE_APK_PATH);
            }
            return context.getPackageManager().getResourcesForApplication(PLUGIN_PACKAGE_NAME)
                    .getAssets().open(TEMPLATE_APK_PATH);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static int getPluginVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(PLUGIN_PACKAGE_NAME, 0);
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }
}


