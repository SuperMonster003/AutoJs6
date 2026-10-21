package org.autojs.autojs.engine;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;

/** Separate test APK UID, without dependencies on the target APK or its Kotlin runtime. */
public class RuntimeEventsToastFixtureActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String text = getIntent().getStringExtra("ownedText");
        if (text == null || !text.startsWith("EVENTS_")) { finish(); return; }
        TextView label = new TextView(this);
        label.setText("Owned event fixture");
        setContentView(label);
        handler.postDelayed(() -> Toast.makeText(this, text, Toast.LENGTH_SHORT).show(), 250);
        if (!getIntent().getBooleanExtra("holdUntilBack", false)) handler.postDelayed(this::finish, 2000);
    }
    @Override public void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
}
