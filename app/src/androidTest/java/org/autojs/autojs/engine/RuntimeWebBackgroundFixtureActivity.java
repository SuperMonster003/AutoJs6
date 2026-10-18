package org.autojs.autojs.engine;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Separate test APK UID, with no dependency on target APK classes or Kotlin runtime. */
public class RuntimeWebBackgroundFixtureActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int frame;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL); layout.setGravity(Gravity.CENTER);
        TextView label = new TextView(this); label.setText("Web display fixture"); label.setTextSize(24); label.setTextColor(Color.WHITE);
        Button close = new Button(this); close.setAllCaps(false); close.setText("Close display fixture"); close.setContentDescription("Close display fixture"); close.setOnClickListener(view -> finish());
        layout.addView(label); layout.addView(close); setContentView(layout);
        handler.post(new Runnable() {
            @Override public void run() {
                frame++; layout.setBackgroundColor(frame % 2 == 0 ? Color.rgb(210, 20, 40) : Color.rgb(20, 150, 60));
                label.setText("Web display fixture " + frame); handler.postDelayed(this, 400);
            }
        });
    }
    @Override public void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
}
