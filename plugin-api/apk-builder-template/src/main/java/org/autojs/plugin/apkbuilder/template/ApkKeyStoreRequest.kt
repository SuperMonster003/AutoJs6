package org.autojs.plugin.apkbuilder.template

import android.os.ParcelFileDescriptor
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ApkKeyStoreRequest(
    var operation: Int = OPERATION_VERIFY,
    var keyStoreFd: ParcelFileDescriptor? = null,
    var keyStoreType: String? = null,
    var keyStorePassword: String? = null,
    var keyAlias: String? = null,
    var keyAliasPassword: String? = null,
    var keyAlgorithm: String = "RSA",
    var keySize: Int = 2048,
    var certificateSignatureAlgorithm: String = "SHA256withRSA",
    var certificateValidityYears: Int = 25,
    var commonName: String? = null,
    var organization: String? = null,
    var organizationalUnit: String? = null,
    var country: String? = null,
    var state: String? = null,
    var locality: String? = null,
    var street: String? = null,
) : Parcelable {

    companion object {
        const val OPERATION_CREATE = 1
        const val OPERATION_VERIFY = 2

        const val TYPE_BKS = "BKS"
        const val TYPE_JKS = "JKS"
    }
}
