package org.autojs.sdk37.balfixture;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import org.json.JSONObject;

/** Disposable fixture: fixed target only, callable only by debuggable host / inrt packages. */
public class TokenProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String id, Bundle extras) {
        String caller = getCallingPackage();
        if (!"org.autojs.autojs6".equals(caller) && !"org.autojs.autojs6.inrt".equals(caller)) {
            throw new SecurityException("Only the SDK37 host probes may call this fixture");
        }
        try {
            if ((getContext().getPackageManager().getApplicationInfo(caller, 0).flags
                    & ApplicationInfo.FLAG_DEBUGGABLE) == 0) throw new SecurityException("Debug builds only");
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            throw new SecurityException(e);
        }
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,80}")) throw new IllegalArgumentException("Invalid case id");
        Bundle result = new Bundle();
        if ("read".equals(method)) {
            result.putString("event", getContext().getSharedPreferences("events", 0).getString(id, "{}"));
            return result;
        }
        if (!"activity".equals(method) && !"broadcast".equals(method)) throw new IllegalArgumentException(method);
        boolean broadcast = "broadcast".equals(method);
        Intent intent = new Intent(getContext(), broadcast ? CallbackReceiver.class : TargetActivity.class)
                .setData(Uri.parse("sdk37-bal://fixture/" + id)).putExtra("case", id);
        if (!broadcast && extras != null) intent.putExtra("hold", extras.getBoolean("hold", false));
        Bundle options = null;
        if (Build.VERSION.SDK_INT >= 34) {
            options = ActivityOptions.makeBasic().setPendingIntentCreatorBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_DENIED).toBundle();
        }
        PendingIntent token = broadcast
                ? PendingIntent.getBroadcast(getContext(), id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE)
                : PendingIntent.getActivity(getContext(), id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE, options);
        result.putParcelable("token", token);
        result.putInt("uid", Process.myUid());
        result.putInt("targetSdk", getContext().getApplicationInfo().targetSdkVersion);
        return result;
    }

    static void record(Context context, Intent intent, String kind) {
        String id = intent.getStringExtra("case");
        if (id == null) id = "launcher";
        try {
            JSONObject event = new JSONObject().put("case", id).put("kind", kind)
                    .put("pid", Process.myPid()).put("uid", Process.myUid())
                    .put("time", System.currentTimeMillis())
                    .put("targetSdk", context.getApplicationInfo().targetSdkVersion);
            if (context instanceof android.app.Activity) {
                event.put("taskId", ((android.app.Activity) context).getTaskId())
                        .put("allowBelow", intent.hasExtra("allowBelow")
                                ? intent.getBooleanExtra("allowBelow", false) : JSONObject.NULL);
            }
            context.getSharedPreferences("events", 0).edit().putString(id, event.toString()).commit();
            android.util.Log.i("SDK37-BAL-Fixture", event.toString());
        } catch (org.json.JSONException e) { throw new IllegalStateException(e); }
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
