package org.autojs.autojs.ui.keystore

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.mcal.apksigner.CertCreator
import com.mcal.apksigner.utils.DistinguishedNameValues
import com.mcal.apksigner.utils.KeyStoreHelper
import org.autojs.autojs.apkbuilder.keystore.AESUtils
import org.autojs.autojs.apkbuilder.keystore.KeyStore
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.ui.keystore.NewKeyStoreDialog.NewKeyStoreConfigs
import org.autojs.autojs.ui.viewmodel.KeyStoreViewModel
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.setMenuIconsColorByThemeColorLuminance
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityManageKeyStoreBinding
import java.io.File
import java.io.IOException


class ManageKeyStoreActivity : BaseActivity() {

    private lateinit var binding: ActivityManageKeyStoreBinding
    private lateinit var keyStoreAdapter: KeyStoreAdaptor
    private lateinit var keyStoreViewModel: KeyStoreViewModel

    companion object {
        fun startActivity(context: Context) {
            Intent(context, ManageKeyStoreActivity::class.java).apply {}.also {
                ContextCompat.startActivity(context, it, null)
            }
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
            ViewUtils.excludeFloatingActionButtonFromBottomNavigationBar(this)
        }

        keyStoreAdapter = KeyStoreAdaptor(keyStoreAdapterCallback)
        binding.recyclerView.apply {
            adapter = keyStoreAdapter
            layoutManager = LinearLayoutManager(this@ManageKeyStoreActivity)
            itemAnimator = DefaultItemAnimator()
            ViewUtils.excludePaddingClippableViewFromBottomNavigationBar(this)
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

        val distinguishedNameValues = DistinguishedNameValues().apply {
            setCommonName(configs.firstAndLastName)
            setOrganization(configs.organization)
            setOrganizationalUnit(configs.organizationalUnit)
            setCountry(configs.countryCode)
            setState(configs.stateOrProvince)
            setLocality(configs.cityOrLocality)
            setStreet(configs.street)
        }

        try {
            CertCreator.createKeystoreAndKey(
                file,
                configs.password.toCharArray(),
                "RSA",
                2048,
                configs.alias,
                configs.aliasPassword.toCharArray(),
                configs.signatureAlgorithm,
                configs.validityYears,
                distinguishedNameValues
            )
            val newKeyStore = KeyStore(
                absolutePath = file.absolutePath,
                filename = file.name,
                password = AESUtils.encrypt(configs.password),
                alias = configs.alias,
                aliasPassword = AESUtils.encrypt(configs.aliasPassword),
                verified = true
            )
            keyStoreViewModel.upsertKeyStore(newKeyStore)
            showToast(R.string.text_successfully_created_key_store)
        } catch (e: IOException) {
            showToast(getString(R.string.text_failed_to_create_key_store) + " " + e.message)
        } catch (e: Exception) {
            showToast(getString(R.string.text_failed_to_create_key_store) + " " + e.message)
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
        // 验证密钥库密码
        val tmpKeyStore = runCatching {
            KeyStoreHelper.loadKeyStore(File(keyStore.absolutePath), configs.password.toCharArray())
        }.getOrElse {
            showToast(R.string.text_verify_failed)
            return
        }

        // 验证别名和别名密码
        runCatching {
            tmpKeyStore.getKey(configs.alias, configs.aliasPassword.toCharArray())
        }.getOrElse {
            showToast(R.string.text_verify_failed)
            return
        }

        val verifiedKeyStore = KeyStore(
            absolutePath = keyStore.absolutePath,
            filename = keyStore.filename,
            password = AESUtils.encrypt(configs.password),
            alias = configs.alias,
            aliasPassword = AESUtils.encrypt(configs.aliasPassword),
            verified = true
        )
        keyStoreViewModel.upsertKeyStore(verifiedKeyStore)
        showToast(R.string.text_verify_success)
    }

    private fun showToast(@StringRes messageResId: Int) {
        Toast.makeText(this, getString(messageResId), Toast.LENGTH_SHORT).show()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

}
