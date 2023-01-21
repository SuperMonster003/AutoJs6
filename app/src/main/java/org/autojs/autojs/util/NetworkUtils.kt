package org.autojs.autojs.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.util.Log
import org.autojs.autojs.app.GlobalAppContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.math.BigInteger
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.UnknownHostException
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Collections
import java.util.Locale

/**
 * Created by Stardust on 2017/4/9.
 * Modified by SuperMonster003 as of Jun 3, 2022.
 */
@Suppress("DEPRECATION", "unused")
object NetworkUtils {

    private val globalAppContext = GlobalAppContext.get()

    private fun getConnectivityManager() = globalAppContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private fun getNetworkInfo() = getConnectivityManager().activeNetworkInfo

    @JvmStatic
    fun isActiveNetworkMetered() = getConnectivityManager().isActiveNetworkMetered

    @JvmStatic
    fun isConnectedOrConnecting() = getNetworkInfo()?.isConnectedOrConnecting == true

    @JvmStatic
    fun isWifiAvailable() = getNetworkInfo()?.type == ConnectivityManager.TYPE_WIFI

    @JvmStatic
    fun getGatewayAddress() = try {
        InetAddress.getByAddress(getIpAddressByte()).hostAddress
    } catch (e: UnknownHostException) {
        Log.e(NetworkUtils::class.java.simpleName, "Error getting Hotspot IP address ", e)
        null
    } ?: "0.0.0.0"

    private fun getIpAddressByte(): ByteArray {
        val manager = globalAppContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        var ipAddress = manager.dhcpInfo.gateway
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            ipAddress = Integer.reverseBytes(ipAddress)
        }
        return BigInteger.valueOf(ipAddress.toLong()).toByteArray()
    }

    /**
     * Convert byte array to hex string
     * @param bytes toConvert
     * @return hexValue
     */
    @JvmStatic
    fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (idx in bytes.indices) {
            val intVal = bytes[idx].toInt() and 0xff
            if (intVal < 0x10) sb.append("0")
            sb.append(Integer.toHexString(intVal).uppercase(Locale.getDefault()))
        }
        return sb.toString()
    }

    /**
     * Get utf8 byte array.
     * @param str which to be converted
     * @return  array of NULL if error was found
     */
    @JvmStatic
    fun getUTF8Bytes(str: String): ByteArray? {
        return try {
            str.toByteArray(charset("UTF-8"))
        } catch (ex: Exception) {
            null
        }
    }

    /**
     * Load UTF8withBOM or any ansi text file.
     * @param filename which to be converted to string
     * @return String value of File
     * @throws java.io.IOException if error occurs
     */
    @JvmStatic
    @Throws(IOException::class)
    fun loadFileAsString(filename: String?): String? {
        val bufLen = 1024
        val `is` = BufferedInputStream(FileInputStream(filename), bufLen)
        return try {
            val baos = ByteArrayOutputStream(bufLen)
            val bytes = ByteArray(bufLen)
            var isUTF8 = false
            var read: Int
            var count = 0
            while (`is`.read(bytes).also { read = it } != -1) {
                if (count == 0 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
                    isUTF8 = true
                    baos.write(bytes, 3, read - 3) // drop UTF8 bom marker
                } else {
                    baos.write(bytes, 0, read)
                }
                count += read
            }
            if (isUTF8) String(baos.toByteArray(), UTF_8) else String(baos.toByteArray())
        } finally {
            try {
                `is`.close()
            } catch (ignored: Exception) {
            }
        }
    }

    /**
     * Returns MAC address of the given interface name.
     * @param interfaceName eth0, wlan0 or NULL=use first interface
     * @return  mac address or empty string
     */
    @JvmStatic
    fun getMacAddress(interfaceName: String?): String {
        try {
            val interfaces: List<NetworkInterface> = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (interfaceName != null) {
                    if (intf.name.lowercase() != interfaceName.lowercase()) continue
                }
                val mac: ByteArray = intf.hardwareAddress ?: return ""
                val buf = StringBuilder()
                for (aMac in mac) buf.append(String.format("%02X:", aMac))
                if (buf.isNotEmpty()) buf.deleteCharAt(buf.length - 1)
                return buf.toString()
            }
        } catch (ignored: Exception) {
        } // for now eat exceptions
        return ""
        /*try {
            // this is so Linux hack
            return loadFileAsString("/sys/class/net/" +interfaceName + "/address").toUpperCase().trim();
        } catch (IOException ex) {
            return null;
        }*/
    }

    /**
     * Get IP address from first non-localhost interface
     * @param useIPv4   true=return ipv4, false=return ipv6
     * @return  address or empty string
     */
    @JvmStatic
    fun getIpAddress(useIPv4: Boolean): String {
        try {
            val interfaces: List<NetworkInterface> = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (`interface` in interfaces) {
                val addresses: List<InetAddress> = Collections.list(`interface`.inetAddresses)
                for (addr in addresses) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress!!
                        //boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
                        val isIPv4 = sAddr.indexOf(':') < 0
                        if (useIPv4) {
                            if (isIPv4) return sAddr
                        } else {
                            if (!isIPv4) {
                                val delim = sAddr.indexOf('%') // drop ip6 zone suffix
                                return if (delim < 0) sAddr.uppercase(Locale.getDefault()) else sAddr.substring(0, delim).uppercase(Locale.getDefault())
                            }
                        }
                    }
                }
            }
        } catch (ignored: Exception) {
            // for now eat exceptions
        }
        return "0.0.0.0"
    }

    @JvmStatic
    fun getIpAddress() = getIpAddress(true)

    @JvmStatic
    fun getIpv6Address() = getIpAddress(false)

}