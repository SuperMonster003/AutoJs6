package org.autojs.autojs.ui.keystore

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.apkbuilder.keystore.AESUtils
import org.autojs.autojs.apkbuilder.keystore.KeyStore
import org.autojs.autojs.apkbuilder.template.ApkBuilderKeyStoreClient
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.ui.keystore.NewKeyStoreDialog.NewKeyStoreConfigs
import org.autojs.autojs.ui.viewmodel.KeyStoreViewModel
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils.excludeFloatingActionButtonFromBottomNavigationBar
import org.autojs.autojs.util.ViewUtils.excludePaddingClippableViewFromBottomNavigationBar
import org.autojs.autojs.util.ViewUtils.setMenuIconsColorByThemeColorLuminance
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityManageKeyStoreBinding
import org.autojs.plugin.apkbuilder.template.ApkKeyStoreRequest
import java.io.File


class ManageKeyStoreActivity : BaseActivity() {

    private lateinit var binding: ActivityManageKeyStoreBinding
    private lateinit var keyStoreAdapter: KeyStoreAdaptor
    private lateinit var keyStoreViewModel: KeyStoreViewModel

    companion object {
        fun startActivity(context: Context) {
            Intent(context, ManageKeyStoreActivity::class.java).startSafely(context)
        }
    }

    private val newKeyStoreDialogCallback = object : NewKeyStoreDialog.Callback {
        override fun onConfirmButtonClicked(configs: NewKeyStoreConfigs) {
            createKeyStore(configs)
        }
    }

    private val verifyKeyStoreDialog = object : VerifyKeyStoreDialog.Callback {
        override fun onVerifyButtonClicked(
            configs: VerifyKeyStoreDialog.VerifyKeyStoreConfigs, keyStore: KeyStore,
        ) {
            verifyKeyStore(configs, keyStore)
        }
    }

    private val keyStoreAdapterCallback = object : KeyStoreAdaptor.KeyStoreAdapterCallback {
        override fun onDeleteButtonClicked(keyStore: KeyStore) {
            MaterialDialog.Builder(this@ManageKeyStoreActivity)
                .title(getString(R.string.text_confirm_to_delete))
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_caution)
                .onPositive { _: MaterialDialog, _: DialogAction ->
                    deleteKeyStore(keyStore)
                }.show()
        }

        override fun onVerifyButtonClicked(keyStore: KeyStore) {
            VerifyKeyStoreDialog(verifyKeyStoreDialog, keyStore).show(supportFragmentManager, null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityManageKeyStoreBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setToolbarAsBack(getString(R.string.text_manage_key_store))

        keyStoreViewModel =
            ViewModelProvider(this, KeyStoreViewModel.Factory(this))[KeyStoreViewModel::class.java]

        binding.fab.apply {
            setOnClickListener {
                NewKeyStoreDialog(newKeyStoreDialogCallback).show(supportFragmentManager, null)
            }
            excludeFloatingActionButtonFromBottomNavigationBar()
        }

        keyStoreAdapter = KeyStoreAdaptor(keyStoreAdapterCallback)
        binding.recyclerView.apply {
            adapter = keyStoreAdapter
            layoutManager = LinearLayoutManager(this@ManageKeyStoreActivity)
            itemAnimator = DefaultItemAnimator()
            excludePaddingClippableViewFromBottomNavigationBar()
        }
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadKeyStores()
            binding.recyclerView.postDelayed({
                binding.swipeRefreshLayout.isRefreshing = false
            }, 800)
        }

        keyStoreViewModel.allKeyStores.observe(this@ManageKeyStoreActivity) {
            keyStoreAdapter.submitList(it.toList())
        }

        loadKeyStores()
    }

    override fun onResume() {
        super.onResume()
        loadKeyStores()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_manage_key_store, menu)
        binding.toolbar.setMenuIconsColorByThemeColorLuminance(this)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_delete_all -> {
                MaterialDialog.Builder(this@ManageKeyStoreActivity)
                    .title(getString(R.string.text_delete_all))
                    .negativeText(R.string.dialog_button_cancel)
                    .negativeColorRes(R.color.dialog_button_default)
                    .positiveText(R.string.dialog_button_confirm)
                    .positiveColorRes(R.color.dialog_button_caution)
                    .onPositive { _: MaterialDialog, _: DialogAction ->
                        deleteAllKeyStores()
                    }.show()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadKeyStores() {
        val path = File(Pref.getKeyStorePath())
        if (!path.isDirectory) {
            return
        }

        val filteredFiles = path.listFiles { _, name ->
            name.endsWith(".bks") || name.endsWith(".jks")
        } ?: emptyArray()

        keyStoreViewModel.updateAllKeyStoresFromFiles(filteredFiles)
    }

    fun createKeyStore(configs: NewKeyStoreConfigs) {
        val keyStorePath = File(Pref.getKeyStorePath())
        keyStorePath.mkdirs()
        val file = File(keyStorePath, configs.filename)

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                ApkBuilderKeyStoreClient.createBlocking(
                    this@ManageKeyStoreActivity,
                    file,
                    ApkKeyStoreRequest(
                        keyStoreType = keyStoreType(configs.filename),
                        keyStorePassword = configs.password,
                        keyAlias = configs.alias,
                        keyAliasPassword = configs.aliasPassword,
                        certificateSignatureAlgorithm = configs.signatureAlgorithm,
                        certificateValidityYears = configs.validityYears,
                        commonName = configs.firstAndLastName,
                        organization = configs.organization,
                        organizationalUnit = configs.organizationalUnit,
                        country = configs.countryCode,
                        state = configs.stateOrProvince,
                        locality = configs.cityOrLocality,
                        street = configs.street,
                    ),
                )
            }
            if (!result.isSuccess) {
                showToast(getString(R.string.text_failed_to_create_key_store) + " " + result.message.orEmpty())
                return@launch
            }
            val newKeyStore = KeyStore(
                absolutePath = file.absolutePath,
                filename = file.name,
                password = AESUtils.encrypt(configs.password),
                alias = configs.alias,
                aliasPassword = AESUtils.encrypt(configs.aliasPassword),
                verified = true,
            )
            keyStoreViewModel.upsertKeyStore(newKeyStore)
            showToast(R.string.text_successfully_created_key_store)
        }
    }

    fun deleteKeyStore(keyStore: KeyStore) {
        val keyStorePath = keyStore.absolutePath
        val keyStoreFile = File(keyStorePath)

        try {
            if (keyStoreFile.delete()) {
                keyStoreViewModel.deleteKeyStore(keyStore)
                showToast(getString(R.string.text_already_deleted) + " " + keyStore.filename)
            } else {
                showToast(getString(R.string.text_failed_to_delete))
            }
        } catch (e: Exception) {
            showToast(getString(R.string.text_failed_to_delete) + ": " + e.message)
        }
    }

    private fun deleteAllKeyStores() {
        val path = File(Pref.getKeyStorePath())
        if (!path.isDirectory) return

        val files = path.listFiles { _, name -> name.endsWith(".bks") || name.endsWith(".jks") }
        files?.forEach { file ->
            file.delete()
        }

        keyStoreViewModel.deleteAllKeyStores()
        showToast(getString(R.string.text_already_deleted))
    }

    fun verifyKeyStore(
        configs: VerifyKeyStoreDialog.VerifyKeyStoreConfigs, keyStore: KeyStore,
    ) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                ApkBuilderKeyStoreClient.verifyBlocking(
                    this@ManageKeyStoreActivity,
                    File(keyStore.absolutePath),
                    ApkKeyStoreRequest(
                        keyStoreType = keyStoreType(keyStore.filename),
                        keyStorePassword = configs.password,
                        keyAlias = configs.alias,
                        keyAliasPassword = configs.aliasPassword,
                    ),
                )
            }
            if (!result.isSuccess) {
                showToast(R.string.text_verify_failed)
                return@launch
            }
            val verifiedKeyStore = KeyStore(
                absolutePath = keyStore.absolutePath,
                filename = keyStore.filename,
                password = AESUtils.encrypt(configs.password),
                alias = configs.alias,
                aliasPassword = AESUtils.encrypt(configs.aliasPassword),
                verified = true,
            )
            keyStoreViewModel.upsertKeyStore(verifiedKeyStore)
            showToast(R.string.text_verify_success)
        }
    }

    private fun keyStoreType(filename: String): String {
        return if (filename.endsWith(".bks", ignoreCase = true)) {
            ApkKeyStoreRequest.TYPE_BKS
        } else {
            ApkKeyStoreRequest.TYPE_JKS
        }
    }

    private fun showToast(@StringRes messageResId: Int) {
        Toast.makeText(this, getString(messageResId), Toast.LENGTH_SHORT).show()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

}
