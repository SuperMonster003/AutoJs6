package org.autojs.autojs.ui.keystore

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.fragment.app.DialogFragment
import org.autojs.autojs.apkbuilder.keystore.AESUtils
import org.autojs.autojs.apkbuilder.keystore.KeyStore
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.DialogVerifyKeyStoreBinding

open class VerifyKeyStoreDialog(
    private val callback: Callback,
    private val keyStore: KeyStore,
) : DialogFragment() {

    private lateinit var binding: DialogVerifyKeyStoreBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        binding = DialogVerifyKeyStoreBinding.inflate(inflater)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85f).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        dialog?.setCanceledOnTouchOutside(true)

        binding.filePath.text = keyStore.absolutePath

        if (keyStore.verified) {
            binding.imgVerifyState.setImageResource(R.drawable.ic_key_store_verified)
            binding.textVerifyState.text = getString(R.string.text_verified)
            binding.password.setText(AESUtils.decrypt(keyStore.password))
            binding.alias.setText(keyStore.alias)
            binding.aliasPassword.setText(AESUtils.decrypt(keyStore.aliasPassword))
        } else {
            binding.imgVerifyState.setImageResource(R.drawable.ic_key_store_unverified)
            binding.textVerifyState.text = getString(R.string.text_unverified)
        }

        binding.verify.setOnClickListener {
            var error = false
            val password = binding.password.text.toString()
            val alias = binding.alias.text.toString()
            val aliasPassword = binding.aliasPassword.text.toString()

            // 检查密码是否符合要求
            when {
                password.isEmpty() -> {
                    binding.passwordTextInputLayout.error = getString(R.string.text_password_cannot_be_empty)
                    error = true
                }

                password.length < 6 -> {
                    binding.passwordTextInputLayout.error = getString(R.string.text_password_requires_at_least_n_characters, 6)
                    error = true
                }

                else -> binding.passwordTextInputLayout.error = null
            }

            // 检查别名密码是否符合要求
            when {
                aliasPassword.isEmpty() -> {
                    binding.aliasPasswordTextInputLayout.error = getString(R.string.text_password_cannot_be_empty)
                    error = true
                }

                aliasPassword.length < 6 -> {
                    binding.aliasPasswordTextInputLayout.error = getString(R.string.text_password_requires_at_least_n_characters, 6)
                    error = true
                }

                else -> binding.aliasPasswordTextInputLayout.error = null
            }

            // 检查别名是否符合要求
            if (alias.isEmpty()) {
                binding.aliasTextInputLayout.error = getString(R.string.text_alias_cannot_be_empty)
                error = true
            } else {
                binding.aliasTextInputLayout.error = null
            }

            if (error) return@setOnClickListener

            val configs = VerifyKeyStoreConfigs(
                password = password,
                alias = alias,
                aliasPassword = aliasPassword,
            )
            callback.onVerifyButtonClicked(configs, keyStore)
            dismiss()
        }

        binding.cancel.setOnClickListener {
            dismiss()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
            }
        }
    }

    data class VerifyKeyStoreConfigs(
        val password: String,
        val alias: String,
        val aliasPassword: String,
    )

    interface Callback {
        fun onVerifyButtonClicked(configs: VerifyKeyStoreConfigs, keyStore: KeyStore)
    }
}

