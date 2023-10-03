package org.autojs.autojs.runtime.api

import java.lang.Exception

interface IPermissionToggleable {

    val description: String

    fun has(): Boolean

    fun toggle(forcible: Boolean = false) {
        try {
            if (!has()) request(forcible) else revoke()
        } catch (e: Exception) {
            when (e) {
                is PermissionRequestException -> {
                    throw PermissionToggleException("$description can't ..., may be not able to request")
                }
                is PermissionRevokeException -> {
                    throw PermissionToggleException("$description can't ..., may be not able to revoke")
                }
                else -> throw e
            }
        }
    }

    fun request(forcible: Boolean) {
        if (forcible) {
            try {
                revoke()
            } catch (e: PermissionRevokeException) {
                throw PermissionRequestException("$description can't ...")
            }
        }
        request()
    }

    fun request() {
        try {
            config()
        } catch (e: PermissionConfigException) {
            throw PermissionRequestException("$description can't ...")
        }
    }

    fun requestIfNeeded(forcible: Boolean = false) {
        if (!has()) request(forcible)
    }

    fun revoke() {
        try {
            config()
        } catch (e: PermissionConfigException) {
            throw PermissionRevokeException("$description can't ...")
        }
    }

    fun revokeIfNeeded() {
        if (has()) revoke()
    }

    fun config() {
        throw PermissionConfigException("$description can't ...")
    }

    class PermissionRequestException(e: String) : Exception(e)

    class PermissionRevokeException(e: String) : Exception(e)

    class PermissionConfigException(e: String) : Exception(e)

    class PermissionToggleException(e: String) : Exception(e)

}
