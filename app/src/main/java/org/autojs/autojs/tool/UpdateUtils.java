package org.autojs.autojs.tool;

import android.content.Context;
import android.view.View;

import com.stardust.autojs.annotation.ScriptInterface;

import org.autojs.autojs.network.UpdateChecker;
import org.autojs.autojs.network.UpdateChecker.PromptMode;

import okhttp3.ResponseBody;

/**
 * Created by SuperMonster003 on May 29, 2022.
 */

public class UpdateUtils {

    public static String BASE_URL = "https://raw.githubusercontent.com/";
    public static String RELATIVE_URL = "/SuperMonster003/AutoJs6/master/version.properties";
    public static String URL = BASE_URL + RELATIVE_URL.substring(1);

    @ScriptInterface
    public static UpdateChecker getDialogChecker(Context context, String url, SimpleObserver<ResponseBody> callback) {
        return getBuilder(context, url, callback)
                .setPromptMode(PromptMode.DIALOG)
                .build();
    }

    public static UpdateChecker getDialogChecker(Context context) {
        return getDialogChecker(context, null, null);
    }

    @ScriptInterface
    public static UpdateChecker getSnackbarChecker(View view, String url, SimpleObserver<ResponseBody> callback) {
        return getBuilder(view, url, callback)
                .setPromptMode(PromptMode.SNACKBAR)
                .build();
    }

    public static UpdateChecker getSnackbarChecker(View view) {
        return getSnackbarChecker(view, null, null);
    }

    private static UpdateChecker.Builder getBuilder(Context context, String url, SimpleObserver<ResponseBody> callback) {
        return new UpdateChecker.Builder(context)
                .setBaseUrl(BASE_URL)
                .setUrl(url != null ? url : RELATIVE_URL)
                .setCallback(callback);
    }

    private static UpdateChecker.Builder getBuilder(View view, String url, SimpleObserver<ResponseBody> callback) {
        return new UpdateChecker.Builder(view)
                .setBaseUrl(BASE_URL)
                .setUrl(url != null ? url : RELATIVE_URL)
                .setCallback(callback);
    }

}
