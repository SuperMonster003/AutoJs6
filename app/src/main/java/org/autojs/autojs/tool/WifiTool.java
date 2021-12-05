package org.autojs.autojs.tool;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;

/**
 * Created by Stardust on 2017/5/11.
 */

public class WifiTool {
    public static String getRouterIp(Context context) {
        byte[] ipAddressByte = getIpAddressByte(context);
        try {
            return InetAddress.getByAddress(ipAddressByte).getHostAddress();
        } catch (UnknownHostException e) {
            Log.e(WifiTool.class.getSimpleName(), "Error getting Hotspot IP address ", e);
            return "0.0.0.0";
        }
    }

    private static byte[] getIpAddressByte(Context context) {
        WifiManager manager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        DhcpInfo dhcp = manager.getDhcpInfo();
        int ipAddress = dhcp.gateway;
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress);
        }
        return BigInteger.valueOf(ipAddress).toByteArray();
    }
}
