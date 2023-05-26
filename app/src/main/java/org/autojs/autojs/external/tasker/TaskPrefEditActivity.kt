package org.autojs.autojs.external.tasker

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import com.twofortyfouram.locale.sdk.client.ui.activity.AbstractAppCompatPluginActivity
import org.autojs.autojs.external.ScriptIntents
import org.autojs.autojs.external.ScriptIntents.isTaskerJsonObjectValid
import org.autojs.autojs.model.explorer.ExplorerDirPage
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.ui.edit.EditorView
import org.autojs.autojs.ui.explorer.ExplorerView
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityTaskerEditBinding
import org.json.JSONException
import org.json.JSONObject

/**
 * Created by Stardust on 2017/3/27.
 * Modified by SuperMonster003 as of May 26, 2022.
 * Transformed by SuperMonster003 on May 13, 2023.
 */
class TaskPrefEditActivity : AbstractAppCompatPluginActivity() {

    private var mSelectedScriptFilePath: String? = null
    private var mPreExecuteScript: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityTaskerEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.editScript.setOnClickListener { editPreExecuteScript() }

        ViewUtils.setToolbarAsBack(this, R.string.text_please_choose_a_script)
        initScriptListRecyclerView()
    }

    private fun initScriptListRecyclerView() {
        val explorerView = findViewById<ExplorerView>(R.id.script_list)
        explorerView.setExplorer(Explorers.external(), ExplorerDirPage.createRoot(Environment.getExternalStorageDirectory()))
        explorerView.setOnItemClickListener { _, item ->
            mSelectedScriptFilePath = item.path
            finish()
        }
    }

    private fun editPreExecuteScript() {
        TaskerScriptEditActivity.edit(
            this,
            getString(R.string.text_pre_execute_script),
            getString(R.string.summary_pre_execute_script),
            if (mPreExecuteScript == null) "" else mPreExecuteScript
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_refresh -> Explorers.external().refreshAll()
            R.id.action_clear_file_selection -> mSelectedScriptFilePath = null
            else -> mPreExecuteScript = null
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.tasker_script_edit_menu, menu)
        return true
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            mPreExecuteScript = data!!.getStringExtra(EditorView.EXTRA_CONTENT)
        }
    }

    override fun isJsonValid(jsonObject: JSONObject): Boolean {
        return isTaskerJsonObjectValid(jsonObject)
    }

    override fun onPostCreateWithPreviousResult(jsonObject: JSONObject, s: String) {
        try {
            mSelectedScriptFilePath = jsonObject.getString(ScriptIntents.EXTRA_KEY_PATH)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        try {
            mPreExecuteScript = jsonObject.getString(ScriptIntents.EXTRA_KEY_PRE_EXECUTE_SCRIPT)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    override fun getResultJson(): JSONObject {
        val jsonObject = JSONObject()
        try {
            jsonObject.put(ScriptIntents.EXTRA_KEY_PATH, mSelectedScriptFilePath)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        try {
            jsonObject.put(ScriptIntents.EXTRA_KEY_PRE_EXECUTE_SCRIPT, mPreExecuteScript)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return jsonObject
    }

    override fun getResultBlurb(jsonObject: JSONObject): String {
        var blurb = ""
        try {
            blurb = jsonObject.getString(ScriptIntents.EXTRA_KEY_PATH)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        if (TextUtils.isEmpty(blurb)) {
            try {
                blurb = jsonObject.getString(ScriptIntents.EXTRA_KEY_PRE_EXECUTE_SCRIPT)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        if (TextUtils.isEmpty(blurb)) {
            blurb = getString(R.string.text_path_is_empty)
        }
        return blurb
    }
}
