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
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import androidx.fragment.app.DialogFragment
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.DialogNewKeyStoreBinding

open class NewKeyStoreDialog(
    private val callback: Callback,
) : DialogFragment() {

    private lateinit var binding: DialogNewKeyStoreBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        binding = DialogNewKeyStoreBinding.inflate(inflater)
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

        val signatureAlgorithms = arrayOf("MD5withRSA", "SHA1withRSA", "SHA256withRSA", "SHA512withRSA")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, signatureAlgorithms)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.signatureAlgorithms.adapter = adapter

        binding.confirm.setOnClickListener {
            var error = false
            val filename = binding.filename.text.toString()
            val password = binding.password.text.toString()
            val alias = binding.alias.text.toString()
            val aliasPassword = binding.aliasPassword.text.toString()
            var valvalidityYears = 25

            // 检查文件名是否符合Android命名规格
            when {
                filename.isEmpty() -> {
                    binding.filenameTextInputLayout.error = getString(R.string.text_filename_cannot_be_empty)
                    error = true
                }

                !containsSpecialCharacters(filename) -> {
                    binding.filenameTextInputLayout.error = getString(R.string.text_filename_cannot_contain_invalid_character)
                    error = true
                }

                filename.length > 255 -> {
                    binding.filenameTextInputLayout.error = getString(R.string.text_filename_is_too_long)
                    error = true
                }

                else -> binding.filenameTextInputLayout.error = null
            }

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

            // 检查有效期是否符合要求
            if (binding.validityYears.text.toString().isEmpty()) {
                binding.validityYearsTextInputLayout.error = getString(R.string.text_validity_years_cannot_be_empty)
                error = true
            } else {
                val years = binding.validityYears.text.toString().toInt()
                if (years == 0) {
                    binding.validityYearsTextInputLayout.error = getString(R.string.text_validity_years_cannot_be_zero)
                    error = true
                } else {
                    binding.validityYearsTextInputLayout.error = null
                    valvalidityYears = years
                }
            }


            val firstAndLastName = binding.firstAndLastName.text.toString()

            val organization = binding.organization.text.toString()
            val organizationalUnit = binding.organizationalUnit.text.toString()

            val countryCode = binding.countryCode.text.toString()
            val stateOrProvince = binding.stateOrProvince.text.toString()
            val cityOrLocality = binding.cityOrLocality.text.toString()
            val street = binding.street.text.toString()

            if (firstAndLastName.isEmpty() && organization.isEmpty() &&
                organizationalUnit.isEmpty() && stateOrProvince.isEmpty() &&
                cityOrLocality.isEmpty() && street.isEmpty() && countryCode.isEmpty()
            ) {
                binding.firstAndLastNameTextInputLayout.error = getString(R.string.text_at_least_one_certificate_issuer_field_is_not_empty)
                binding.organizationTextInputLayout.error = getString(R.string.text_at_least_one_certificate_issuer_field_is_not_empty)
                binding.organizationalUnitTextInputLayout.error = getString(R.string.text_at_least_one_certificate_issuer_field_is_not_empty)
                binding.countryCodeTextInputLayout.error = getString(R.string.text_at_least_one_certificate_issuer_field_is_not_empty)
                binding.stateOrProvinceTextInputLayout.error = getString(R.string.text_at_least_one_certificate_issuer_field_is_not_empty)
                binding.cityOrLocalityTextInputLayout.error = getString(R.string.text_at_least_one_certificate_issuer_field_is_not_empty)
                binding.streetTextInputLayout.error = getString(R.string.text_at_least_one_certificate_issuer_field_is_not_empty)
                error = true
            } else {
                binding.firstAndLastNameTextInputLayout.error = null
                binding.organizationTextInputLayout.error = null
                binding.organizationalUnitTextInputLayout.error = null
                binding.countryCodeTextInputLayout.error = null
                binding.stateOrProvinceTextInputLayout.error = null
                binding.cityOrLocalityTextInputLayout.error = null
                binding.streetTextInputLayout.error = null
            }

            // 检查国家代码是否符合要求 (ISO3166-1-Alpha-2: https://countrycodedata.com/)
            val countryCodeRegex = "^[A-Z]{2}$".toRegex()
            if (countryCode.isNotEmpty() && !countryCodeRegex.matches(countryCode)) {
                binding.countryCodeTextInputLayout.error = getString(R.string.text_country_code_must_be_two_capital_letters)
                error = true
            }

            if (error) return@setOnClickListener

            val suffix = getString(
                if (binding.typeJks.isChecked) R.string.text_jks
                else R.string.text_bks
            ).lowercase()

            val signatureAlgorithm = binding.signatureAlgorithms.selectedItem.toString()

            val configs = NewKeyStoreConfigs(
                filename = "$filename.$suffix",
                password = password,
                alias = alias,
                aliasPassword = aliasPassword,
                signatureAlgorithm = signatureAlgorithm,
                validityYears = valvalidityYears,
                firstAndLastName = firstAndLastName,
                organization = organization,
                organizationalUnit = organizationalUnit,
                countryCode = countryCode,
                stateOrProvince = stateOrProvince,
                cityOrLocality = cityOrLocality,
                street = street
            )
            callback.onConfirmButtonClicked(configs)
            dismiss()
        }

        binding.cancel.setOnClickListener {
            dismiss()
        }

        binding.moreOptions.setOnCheckedChangeListener { _, isChecked ->
            binding.moreOptionsContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
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

    private fun containsSpecialCharacters(fileName: String): Boolean {
        // 定义不允许的字符
        val invalidCharacters = listOf("\\", "/", ":", "*", "?", "\"", "<", ">", "|")

        // 检查文件名是否包含无效字符
        for (char in invalidCharacters) {
            if (fileName.contains(char)) {
                return false
            }
        }

        return true
    }

    data class NewKeyStoreConfigs(
        val filename: String,
        val password: String,
        val alias: String,
        val aliasPassword: String,
        val signatureAlgorithm: String,
        val validityYears: Int,
        val firstAndLastName: String,
        val organizationalUnit: String,
        val organization: String,
        val countryCode: String,
        val stateOrProvince: String,
        val cityOrLocality: String,
        val street: String,
    )


    interface Callback {
        fun onConfirmButtonClicked(configs: NewKeyStoreConfigs)
    }
}

