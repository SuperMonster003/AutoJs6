package org.autojs.sdk37.balfixture;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

public class TargetActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView label = new TextView(this);
        label.setText("SDK37 BAL target: " + getIntent().getStringExtra("case"));
        label.setTextSize(24);
        setContentView(label);
        if (Build.VERSION.SDK_INT >= 35 && getIntent().hasExtra("allowBelow")) {
            setAllowCrossUidActivitySwitchFromBelow(getIntent().getBooleanExtra("allowBelow", false));
        }
        TokenProvider.record(this, getIntent(), "activity-created");
        setResult(RESULT_OK, new android.content.Intent().putExtra("case", getIntent().getStringExtra("case")));
        if (!getIntent().getBooleanExtra("hold", false)) new Handler(getMainLooper()).postDelayed(this::finish, 800);
    }
}
