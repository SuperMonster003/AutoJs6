package org.autojs.autojs.ui.settings;

import android.os.Bundle;

import androidx.annotation.Nullable;

import org.autojs.autojs.ui.BaseActivity;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;
import org.autojs.autojs6.databinding.ActivityDeveloperOptionsBinding;

/**
 * Created by SuperMonster003 on Jun 2, 2022.
 */
public class DeveloperOptionsActivity extends BaseActivity {

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityDeveloperOptionsBinding binding = ActivityDeveloperOptionsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewUtils.setToolbarAsBack(this, R.string.text_developer_options);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_developer_options, new DeveloperOptionsFragment())
                .disallowAddToBackStack()
                .commit();
    }

}
