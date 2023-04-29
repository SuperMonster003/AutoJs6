package org.autojs.autojs.external.tasker;

import static org.autojs.autojs.ui.edit.EditorView.EXTRA_CONTENT;

import android.content.Intent;
import android.os.Environment;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.twofortyfouram.locale.sdk.client.ui.activity.AbstractAppCompatPluginActivity;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.autojs.autojs.external.ScriptIntents;
import org.autojs.autojs.model.explorer.ExplorerDirPage;
import org.autojs.autojs.model.explorer.Explorers;
import org.autojs.autojs.ui.explorer.ExplorerView;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;


/**
 * Created by Stardust on 2017/3/27.
 */
@EActivity(R.layout.activity_tasker_edit)
public class TaskPrefEditActivity extends AbstractAppCompatPluginActivity {

    private String mSelectedScriptFilePath;
    private String mPreExecuteScript;

    @AfterViews
    void setUpViews() {
        ViewUtils.setToolbarAsBack(this, R.string.text_please_choose_a_script);
        initScriptListRecyclerView();
    }

    private void initScriptListRecyclerView() {
        ExplorerView explorerView = findViewById(R.id.script_list);
        explorerView.setExplorer(Explorers.external(), ExplorerDirPage.createRoot(Environment.getExternalStorageDirectory()));
        explorerView.setOnItemClickListener((view, item) -> {
            mSelectedScriptFilePath = item.getPath();
            finish();
        });
    }

    @Click(R.id.edit_script)
    void editPreExecuteScript() {
        TaskerScriptEditActivity.edit(this, getString(R.string.text_pre_execute_script), getString(R.string.summary_pre_execute_script), mPreExecuteScript == null ? "" : mPreExecuteScript);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_refresh) {
            Explorers.external().refreshAll();
        } else if (item.getItemId() == R.id.action_clear_file_selection) {
            mSelectedScriptFilePath = null;
        } else {
            mPreExecuteScript = null;
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.tasker_script_edit_menu, menu);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            mPreExecuteScript = data.getStringExtra(EXTRA_CONTENT);
        }
    }

    @Override
    public boolean isJsonValid(@NonNull JSONObject jsonObject) {
        return ScriptIntents.isTaskerJsonObjectValid(jsonObject);
    }

    @Override
    public void onPostCreateWithPreviousResult(@NonNull JSONObject jsonObject, @NonNull String s) {
        try {
            mSelectedScriptFilePath = jsonObject.getString(ScriptIntents.EXTRA_KEY_PATH);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        try {
            mPreExecuteScript = jsonObject.getString(ScriptIntents.EXTRA_KEY_PRE_EXECUTE_SCRIPT);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Nullable
    @Override
    public JSONObject getResultJson() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(ScriptIntents.EXTRA_KEY_PATH, mSelectedScriptFilePath);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        try {
            jsonObject.put(ScriptIntents.EXTRA_KEY_PRE_EXECUTE_SCRIPT, mPreExecuteScript);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    @NonNull
    @Override
    public String getResultBlurb(@NonNull JSONObject jsonObject) {
        String blurb = null;
        try {
            blurb = jsonObject.getString(ScriptIntents.EXTRA_KEY_PATH);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (TextUtils.isEmpty(blurb)) {
            try {
                blurb = jsonObject.getString(ScriptIntents.EXTRA_KEY_PRE_EXECUTE_SCRIPT);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        if (TextUtils.isEmpty(blurb)) {
            blurb = getString(R.string.text_path_is_empty);
        }
        return Objects.requireNonNull(blurb);
    }
}
