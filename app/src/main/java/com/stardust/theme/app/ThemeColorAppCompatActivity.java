package com.stardust.theme.app;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.stardust.theme.ThemeColorManager;

/**
 * Created by Stardust on 2017/3/5.
 */

public class ThemeColorAppCompatActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeColorManager.addActivityStatusBar(this);
    }
}
