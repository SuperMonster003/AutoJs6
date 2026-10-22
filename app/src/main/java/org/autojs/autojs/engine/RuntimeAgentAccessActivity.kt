package org.autojs.autojs.engine

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs6.R

/** The Android result caller selects the application; Intent extras never select a grantee. */
class RuntimeAgentAccessActivity : BaseActivity() {
    private var dialog: AlertDialog? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val caller = callingPackage ?: return finish()
        if (packageName != RuntimeAgentBridge.HOST || intent.data != null || intent.clipData != null || intent.extras != null ||
            packageManager.checkPermission(RuntimeAgentRemote.PERMISSION, caller) != PackageManager.PERMISSION_GRANTED) return finish()
        val store = RuntimeAgentAccessStore(this)
        val identity = store.identity(caller) ?: return finish()
        val label = packageManager.getApplicationLabel(packageManager.getApplicationInfo(caller, 0)).toString()
        title = getString(R.string.agent_runtime_access_title)
        setContentView(TextView(this).apply { text = title })
        dialog = AlertDialog.Builder(this).setTitle(R.string.agent_runtime_access_title)
            .setMessage(getString(R.string.agent_runtime_request_app, label, caller) + "\n\n" + getString(R.string.agent_runtime_description))
            .setNegativeButton(R.string.agent_runtime_deny) { _, _ -> finish() }
            .setPositiveButton(R.string.agent_runtime_allow) { _, _ ->
                if (store.grant(identity)) setResult(Activity.RESULT_OK)
                finish()
            }.setOnCancelListener { finish() }.show().also {
                it.getButton(AlertDialog.BUTTON_POSITIVE).filterTouchesWhenObscured = true
                it.getButton(AlertDialog.BUTTON_NEGATIVE).filterTouchesWhenObscured = true
            }
    }
    override fun onDestroy() {
        dialog?.setOnCancelListener(null); dialog?.dismiss(); dialog = null
        super.onDestroy()
    }
}

/** Local Plugin Center settings can revoke an application's current and detached tasks. */
class RuntimeAgentAccessManagementActivity : BaseActivity() {
    private var dialog: AlertDialog? = null
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render() }
    private fun render() {
        val store = RuntimeAgentAccessStore(this)
        val entries = store.applications()
        title = getString(R.string.agent_runtime_access_title)
        setContentView(TextView(this).apply { text = title })
        val builder = AlertDialog.Builder(this).setTitle(R.string.agent_runtime_access_title)
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }.setOnCancelListener { finish() }
        if (entries.isEmpty()) builder.setMessage(R.string.agent_runtime_empty)
        else builder.setItems(entries.map { entry ->
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(entry.packageName, 0)).toString() + "\n" + entry.packageName
        }.toTypedArray()) { _, position ->
            val identity = entries[position]
            val label = packageManager.getApplicationLabel(packageManager.getApplicationInfo(identity.packageName, 0)).toString()
            dialog = AlertDialog.Builder(this).setTitle(R.string.agent_runtime_revoke)
                .setMessage(getString(R.string.agent_runtime_revoke_message, label, identity.packageName))
                .setNegativeButton(android.R.string.cancel) { _, _ -> render() }
                .setPositiveButton(R.string.agent_runtime_revoke) { _, _ -> store.revoke(identity); render() }
                .setOnCancelListener { render() }.show().also {
                    it.getButton(AlertDialog.BUTTON_POSITIVE).filterTouchesWhenObscured = true
                }
        }
        dialog = builder.show()
    }
    override fun onDestroy() { dialog?.setOnCancelListener(null); dialog?.dismiss(); dialog = null; super.onDestroy() }
}
