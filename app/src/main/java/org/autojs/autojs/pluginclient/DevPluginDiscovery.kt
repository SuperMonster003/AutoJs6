package org.autojs.autojs.pluginclient

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import org.autojs.autojs.runtime.api.Device
import org.autojs.autojs6.BuildConfig

/**
 * Advertises the local server as `_autojs6._tcp` via mDNS (Android NSD) so the VSCode extension (1.1.0+) can list
 * this device in its LAN connect picker (roadmap 3.10). TXT records: `v` (app version), `p` (protocol version),
 * `n` (device name), `id` (device id).
 * zh-CN: 通过 mDNS (Android NSD) 以 `_autojs6._tcp` 广播本地服务端, 使 VSCode 插件 (1.1.0+) 可在局域网连接选择器中列出本设备
 * (roadmap 3.10). TXT 记录: `v` (应用版本), `p` (协议版本), `n` (设备名), `id` (设备 ID).
 */
class DevPluginDiscovery(private val context: Context) {

    private val nsdManager: NsdManager? by lazy { context.getSystemService(Context.NSD_SERVICE) as? NsdManager }

    @Volatile
    private var listener: NsdManager.RegistrationListener? = null

    /** Service name confirmed by the system (it may append a suffix on conflicts). zh-CN: 系统确认的服务名 (冲突时可能追加后缀). */
    @Volatile
    var registeredName: String? = null
        private set

    val isRegistered: Boolean
        get() = listener != null

    @Synchronized
    fun register(port: Int) {
        if (listener != null) return
        val manager = nsdManager ?: run {
            Log.w(TAG, "NSD is not available on this device")
            return
        }
        val info = NsdServiceInfo().apply {
            serviceName = "AutoJs6 ${Build.MODEL}".take(MAX_SERVICE_NAME_LENGTH)
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute("v", BuildConfig.VERSION_NAME)
            setAttribute("p", JsonSocket.PROTOCOL_VERSION.toString())
            setAttribute("n", "${Build.BRAND} ${Build.MODEL}".take(MAX_TXT_VALUE_LENGTH))
            setAttribute("id", runCatching { Device(context).getAndroidId() }.getOrNull().orEmpty().take(MAX_TXT_VALUE_LENGTH))
        }
        val registration = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                registeredName = serviceInfo.serviceName
                Log.i(TAG, "registered as ${serviceInfo.serviceName} on port $port")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "registration failed: $errorCode")
                synchronized(this@DevPluginDiscovery) {
                    if (listener === this) listener = null
                }
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "unregistered ${serviceInfo.serviceName}")
                registeredName = null
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "unregistration failed: $errorCode")
            }
        }
        listener = registration
        try {
            manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration)
        } catch (e: Exception) {
            Log.w(TAG, "registerService failed", e)
            listener = null
        }
    }

    @Synchronized
    fun unregister() {
        val registration = listener ?: return
        listener = null
        registeredName = null
        try {
            nsdManager?.unregisterService(registration)
        } catch (e: Exception) {
            Log.w(TAG, "unregisterService failed", e)
        }
    }

    companion object {

        private val TAG = DevPluginDiscovery::class.java.simpleName

        /** Must match the extension's browser (`MDNS_SERVICE_TYPE`). zh-CN: 须与插件端浏览的类型一致. */
        const val SERVICE_TYPE = "_autojs6._tcp."

        private const val MAX_SERVICE_NAME_LENGTH = 63
        private const val MAX_TXT_VALUE_LENGTH = 60
    }
}
