package org.autojs.autojs.compat

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.Parcelable
import android.os.Process
import org.autojs.plugin.common.api.PluginInfo

/** Private, debug-only receiver. The separate process must actually decode the incoming values. */
class ParcelCompatibilityProbeService : Service() {
    private val endpoint = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != ECHO && code != CHECK_VALUE) return super.onTransact(code, data, reply, flags)
            data.enforceInterface(DESCRIPTOR)
            val result = Bundle()
            try {
                if (code == CHECK_VALUE) {
                    result.putString("valueClass", data.readValue(javaClass.classLoader)?.javaClass?.name)
                } else {
                    val payload = requireNotNull(data.readBundle(javaClass.classLoader))
                    materialize(payload)
                    result.putBundle("payload", payload)
                }
            } catch (failure: RuntimeException) {
                result.putString("errorClass", failure.javaClass.name)
                result.putString("errorMessage", failure.message)
            }
            result.putInt("receiverPid", Process.myPid())
            result.putInt("receiverTarget", applicationInfo.targetSdkVersion)
            requireNotNull(reply).writeNoException()
            reply.writeBundle(result)
            return true
        }
    }

    override fun onBind(intent: Intent): IBinder = endpoint

    @Suppress("DEPRECATION")
    private fun materialize(bundle: Bundle) {
        bundle.classLoader = javaClass.classLoader
        for (key in bundle.keySet()) {
            when (val value = bundle.get(key)) {
                is Bundle -> materialize(value)
                is Intent -> value.extras?.let(::materialize)
                is PluginInfo -> value.capabilities?.let(::materialize)
            }
        }
    }

    companion object {
        const val DESCRIPTOR = "org.autojs.autojs.compat.SDK37_PARCEL_PROBE"
        const val ECHO = IBinder.FIRST_CALL_TRANSACTION
        const val CHECK_VALUE = IBinder.FIRST_CALL_TRANSACTION + 1
    }
}

/** Deliberate writer/reader mismatch, kept out of every release APK. */
class UnderReadingParcelable : Parcelable {
    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(17)
        dest.writeInt(37)
    }

    override fun describeContents() = 0

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<UnderReadingParcelable> {
            override fun createFromParcel(source: Parcel): UnderReadingParcelable {
                check(source.readInt() == 17)
                return UnderReadingParcelable()
            }

            override fun newArray(size: Int): Array<UnderReadingParcelable?> = arrayOfNulls(size)
        }
    }
}
