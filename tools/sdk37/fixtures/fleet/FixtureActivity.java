package org.autojs.sdk37.fleetfixture;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;

/** Only generated media and a known solid color; no device or user file access. */
public class FixtureActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 100, 20, 20);
        root.setBackgroundColor(Color.rgb(18, 52, 86));
        Button copy = new Button(this);
        copy.setText("Read fixture clipboard");
        copy.setOnClickListener(v -> {
            ClipboardManager clipboard = getSystemService(ClipboardManager.class);
            ClipData clip = clipboard.getPrimaryClip();
            copy.setText(clip == null ? "Clipboard empty" : "Clipboard: " + clip.getItemAt(0).coerceToText(this));
        });
        root.addView(copy);
        setContentView(root);
        String player = getIntent().getStringExtra("player");
        if (state == null && ("ember".equals(player) || "terra".equals(player))) {
            root.postDelayed(() -> {
                boolean video = "ember".equals(player);
                String pkg = "io.github.supermonster003.autojs6.plugin." + (video ? "videoplayer" : "audioplayer");
                // The public receiver is resolved by its documented ACTION_VIEW contract.
                Uri uri = Uri.parse("content://org.autojs.sdk37.fleetfixture.files/fixture." + (video ? "mp4" : "m4a"));
                Intent view = new Intent(Intent.ACTION_VIEW).setPackage(pkg)
                    .setDataAndType(uri, video ? "video/mp4" : "audio/mp4")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                view.setClipData(ClipData.newRawUri("Owned synthetic media", uri));
                startActivity(view);
            }, 700);
        }
    }
}
