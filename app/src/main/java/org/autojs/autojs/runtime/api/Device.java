package org.autojs.autojs.runtime.api;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.*;
import android.provider.Settings;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ezy.assist.compat.SettingsCompat;
import org.autojs.autojs.deviceid.AndroidIdReader;
import org.autojs.autojs.pio.PFiles;
import org.autojs.autojs.pio.UncheckedIOException;
import org.autojs.autojs.runtime.ScriptRuntime;
import org.autojs.autojs.runtime.api.augment.sysprops.SystemProperties;
import org.autojs.autojs.util.DeviceUtils;
import org.autojs.autojs.util.RomUtils;
import org.autojs.autojs.util.RomUtils.Brand;
import org.autojs.autojs6.R;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.List;

import static android.content.Context.WINDOW_SERVICE;

/**
 * Created by Stardust on Dec 2, 2017.
 * Modified by SuperMonster003 as of Jan 1, 2022.
 */
public class Device {

    private Intent mBatteryChangedActionIntent;

    private static int getWidth() {
        return ScreenMetrics.getDeviceScreenWidth();
    }

    private static int getHeight() {
        return ScreenMetrics.getDeviceScreenHeight();
    }

    /**
     * The static screen size fields of Auto.js 4's {@code com.stardust.autojs.runtime.api.Device}, kept because that
     * class name resolves to this class through {@link org.autojs.autojs.rhino.StardustClassAliases} (roadmap P7.3);
     * refreshed whenever a runtime creates its {@code device} object.
     * zh-CN: Auto.js 4 {@code com.stardust.autojs.runtime.api.Device} 的静态屏幕尺寸字段, 因该类名经
     * {@link org.autojs.autojs.rhino.StardustClassAliases} 解析到本类而保留 (roadmap P7.3); 每次运行时创建 {@code device}
     * 对象时刷新.
     */
    public static int width = getWidth();

    public static int height = getHeight();

    public static final String buildId = Build.DISPLAY;

    public static final String buildDisplay = Build.DISPLAY;

    public static final String product = Build.PRODUCT;

    public static final String board = Build.BOARD;

    public static final String brand = Build.BRAND;

    public static final String manufacturer = Build.MANUFACTURER;

    public static final String device = Build.DEVICE;

    public static final String model = Build.MODEL;

    public static final String bootloader = Build.BOOTLOADER;

    public static final String hardware = Build.HARDWARE;

    public static final String fingerprint = Build.FINGERPRINT;

    public static final int sdkInt = Build.VERSION.SDK_INT;

    public static final String incremental = Build.VERSION.INCREMENTAL;

    public static final String release = Build.VERSION.RELEASE;

    public static final String baseOS = Build.VERSION.BASE_OS;

    public static final String securityPatch = Build.VERSION.SECURITY_PATCH;

    public static final String codename = Build.VERSION.CODENAME;

    private static final String PROPERTY_PRODUCT_MARKET_NAME = "ro.product.marketname";
    private static final String PROPERTY_SONY_MARKET_NAME = "ro.semc.product.name";
    private static final String PROPERTY_MARKETING_NAME = "ro.config.marketing_name";
    private static final String PROPERTY_PRODUCT_MODEL = "ro.product.model";

    public static String serial;
    public static String imei;
    private final static Vibrator sVibrator = ScriptRuntime.getApplicationContext().getSystemService(Vibrator.class);

    public final Manufacturers manufacturers;
    public final RomUtils roms;
    public final Brand brands;

    private final Context mContext;
    private PowerManager.WakeLock mWakeLock;
    private volatile AudioManager mAudioManager;
    private int mWakeLockFlag;

    public Device(Context context) {
        mContext = context;
        width = getWidth();
        height = getHeight();
        manufacturers = new Manufacturers();
        roms = RomUtils.INSTANCE;
        brands = Brand.INSTANCE;
        imei = DeviceUtils.getIMEI(context);
        serial = DeviceUtils.getSerial();
    }

    @Nullable
    public String getIMEI() {
        return imei;
    }

    @Nullable
    public String getSerial() {
        return serial;
    }

    public String getAndroidId() {
        return getAndroidId(false);
    }

    public String getAndroidId(boolean useSystemId) {
        return AndroidIdReader.getAndroidId(mContext, useSystemId);
    }

    public int getBrightness() throws Settings.SettingNotFoundException {
        return Settings.System.getInt(mContext.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
    }

    public int getBrightnessMode() throws Settings.SettingNotFoundException {
        return Settings.System.getInt(mContext.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE);
    }

    public void setBrightness(int b) throws Settings.SettingNotFoundException {
        ensureWriteSettingsPermission();
        Settings.System.putInt(mContext.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, b);
    }

    public void setBrightnessMode(int b) throws Settings.SettingNotFoundException {
        ensureWriteSettingsPermission();
        Settings.System.putInt(mContext.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, b);
    }

    /**
     * The AudioManager of the context. AudioManager binds its event handler to the Looper of the thread that first obtains it
     * from a context, and the application context keeps that instance for the lifetime of the process; obtained first on a script
     * thread, it kept that thread and its whole ScriptRuntime reachable (docs/memory/ROADMAP.md H22). On a thread with its own
     * Looper the instance is therefore first obtained on a helper thread without one, which binds the handler to the main Looper.
     * zh-CN: 该 Context 的 AudioManager. AudioManager 把事件 Handler 绑定到最先从 Context 取得它的线程的 Looper 上, 而应用 Context 会在进程生命周期内一直保留该实例;
     * 若最先在脚本线程上取得, 就会让该线程及其整个 ScriptRuntime 一直可达 (docs/memory/ROADMAP.md H22). 因此在自带 Looper 的线程上, 先在不带 Looper 的辅助线程上取得该实例, 使 Handler 绑定到主 Looper.
     */
    private AudioManager getAudioManager() {
        AudioManager cached = mAudioManager;
        if (cached != null) {
            return cached;
        }
        Looper looper = Looper.myLooper();
        if (looper == null || looper == Looper.getMainLooper()) {
            return mAudioManager = getSystemService(Context.AUDIO_SERVICE);
        }
        AudioManager[] result = new AudioManager[1];
        Thread thread = new Thread(() -> {
            try {
                result[0] = getSystemService(Context.AUDIO_SERVICE);
            } catch (RuntimeException ignored) {
                /* Falls back to the calling thread below. */
            }
        }, "AudioManager-Init");
        thread.start();
        boolean interrupted = false;
        while (thread.isAlive()) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        return mAudioManager = result[0] != null ? result[0] : getSystemService(Context.AUDIO_SERVICE);
    }

    public int getMusicVolume() {
        return getAudioManager().getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    public int getNotificationVolume() {
        return getAudioManager().getStreamVolume(AudioManager.STREAM_NOTIFICATION);
    }

    public int getAlarmVolume() {
        return getAudioManager().getStreamVolume(AudioManager.STREAM_ALARM);
    }

    public int getMusicMaxVolume() {
        return getAudioManager().getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    }

    public int getNotificationMaxVolume() {
        return getAudioManager().getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION);
    }

    public int getAlarmMaxVolume() {
        return getAudioManager().getStreamMaxVolume(AudioManager.STREAM_ALARM);
    }

    public void setMusicVolume(int i) {
        ensureWriteSettingsPermission();
        getAudioManager().setStreamVolume(AudioManager.STREAM_MUSIC, i, 0);
    }

    public void setAlarmVolume(int i) {
        ensureWriteSettingsPermission();
        getAudioManager().setStreamVolume(AudioManager.STREAM_ALARM, i, 0);
    }

    public void setNotificationVolume(int i) {
        ensureWriteSettingsPermission();
        getAudioManager().setStreamVolume(AudioManager.STREAM_NOTIFICATION, i, 0);
    }

    public float getBattery() {
        Intent batteryIntent = mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryIntent == null) {
            return -1;
        }
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        float battery = ((float) level / scale) * 100f;
        return Math.round(battery * 10) / 10f;
    }

    public long getTotalMem() {
        ActivityManager activityManager = getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(info);
        return info.totalMem;
    }

    public long getAvailMem() {
        ActivityManager activityManager = getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(info);
        return info.availMem;
    }

    public boolean isCharging() {
        return DeviceUtils.isCharging(mContext);
    }

    public boolean isPowerSourceAC() {
        return DeviceUtils.isPowerSourceAC(mContext);
    }

    public boolean isPowerSourceUSB() {
        return DeviceUtils.isPowerSourceUSB(mContext);
    }

    public boolean isPowerSourceWireless() {
        return DeviceUtils.isPowerSourceWireless(mContext);
    }

    public boolean isPowerSourceDock() {
        return DeviceUtils.isPowerSourceDock(mContext);
    }

    public void keepAwake(int flags, long timeout) {
        checkWakeLock(flags);
        mWakeLock.acquire(timeout);
    }

    @SuppressLint("WakelockTimeout")
    public void keepAwake(int flags) {
        checkWakeLock(flags);
        mWakeLock.acquire();
    }

    public boolean isScreenOn() {
        return getDefaultDisplay().getState() == Display.STATE_ON;
    }

    public void wakeUpIfNeeded() {
        if (!isScreenOn()) {
            wakeUp();
        }
    }

    public void wakeUp() {
        keepScreenOn(200);
    }

    public void keepScreenOn() {
        keepAwake(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP);
    }

    public void keepScreenOn(long timeout) {
        keepAwake(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, timeout);
    }

    public void keepScreenDim() {
        keepAwake(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP);
    }

    public void keepScreenDim(long timeout) {
        keepAwake(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, timeout);
    }

    private void checkWakeLock(int flags) {
        if (mWakeLock == null || flags != mWakeLockFlag) {
            mWakeLockFlag = flags;
            cancelKeepingAwake();
            mWakeLock = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(flags, Device.class.getName());
        }
    }

    public void cancelKeepingAwake() {
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
        }
    }

    public void vibrate(long millis) {
        doVibrate(millis);
    }

    public void vibrate(long off, long millis) {
        doVibrate(off, millis);
    }

    public void vibrate(long[] timings) {
        doVibrate(timings);
    }

    public static void doVibrate(long millis) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            sVibrator.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            sVibrator.vibrate(millis);
        }
    }

    public static void doVibrate(long off, long millis) {
        doVibrate(new long[]{off, millis});
    }

    public static void doVibrate(long[] timings) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            sVibrator.vibrate(VibrationEffect.createWaveform(timings, -1));
        } else {
            sVibrator.vibrate(timings, -1);
        }
    }

    public void cancelVibration() {
        sVibrator.cancel();
    }

    public int getOrientation() {
        if (isScreenLandscape()) {
            return Configuration.ORIENTATION_LANDSCAPE;
        }
        if (isScreenPortrait()) {
            return Configuration.ORIENTATION_PORTRAIT;
        }
        return Configuration.ORIENTATION_UNDEFINED;
    }

    public int getRotation() {
        return getDefaultDisplay().getRotation();
    }

    public boolean isScreenPortrait() {
        int rotation = getRotation();
        return rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180;
    }

    public boolean isScreenLandscape() {
        int rotation = getRotation();
        return rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270;
    }

    private void ensureWriteSettingsPermission() {
        if (SettingsCompat.canWriteSettings(mContext)) {
            return;
        }
        SettingsCompat.manageWriteSettings(mContext);
        throw new SecurityException(mContext.getString(R.string.error_no_write_settings_permission));
    }

    public void ensureReadPhoneStatePermission() {
        if (!hasReadPhoneStatePermission()) {
            throw new SecurityException(mContext.getString(R.string.error_no_read_phone_state_permission));
        }
    }

    public boolean hasReadPhoneStatePermission() {
        return mContext.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
    }

    // just to avoid warning of null pointer to make android studio happy..
    @NonNull
    @SuppressWarnings("unchecked")
    private <T> T getSystemService(String service) {
        Object systemService = mContext.getSystemService(service);
        if (systemService == null) {
            throw new RuntimeException("Should never happen... " + service);
        }
        return (T) systemService;
    }

    private static final String FAKE_MAC_ADDRESS = "02:00:00:00:00:00";

    @SuppressLint("HardwareIds")
    public String getMacAddress() throws Exception {
        WifiManager wifiMan = (WifiManager) mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiMan == null) {
            return null;
        }
        WifiInfo wifiInf = wifiMan.getConnectionInfo();
        if (wifiInf == null) {
            return getMacByFile();
        }

        @SuppressLint("MissingPermission")
        String mac = wifiInf.getMacAddress();

        if (FAKE_MAC_ADDRESS.equals(mac)) {
            mac = null;
        }
        if (mac == null) {
            mac = getMacByInterface();
            if (mac == null) {
                mac = getMacByFile();
            }
        }
        return mac;
    }

    private static String getMacByInterface() throws SocketException {
        List<NetworkInterface> networkInterfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
        for (NetworkInterface networkInterface : networkInterfaces) {
            if (networkInterface.getName().equalsIgnoreCase("wlan0")) {
                byte[] macBytes = networkInterface.getHardwareAddress();
                if (macBytes == null) {
                    return null;
                }

                StringBuilder mac = new StringBuilder();
                for (byte b : macBytes) {
                    mac.append(String.format("%02X:", b));
                }

                if (mac.length() > 0) {
                    mac.deleteCharAt(mac.length() - 1);
                }
                return mac.toString();
            }
        }
        return null;
    }

    private static String getMacByFile() {
        try {
            return PFiles.read("/sys/class/net/wlan0/address");
        } catch (UncheckedIOException e) {
            return null;
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "Device{" +
               "width=" + getWidth() +
               ", height=" + getHeight() +
               ", buildId='" + buildId + '\'' +
               ", buildDisplay='" + buildDisplay + '\'' +
               ", product='" + product + '\'' +
               ", board='" + board + '\'' +
               ", brand='" + brand + '\'' +
               ", device='" + device + '\'' +
               ", model='" + model + '\'' +
               ", bootloader='" + bootloader + '\'' +
               ", hardware='" + hardware + '\'' +
               ", fingerprint='" + fingerprint + '\'' +
               ", sdkInt=" + sdkInt +
               ", incremental='" + incremental + '\'' +
               ", release='" + release + '\'' +
               ", baseOS='" + baseOS + '\'' +
               ", securityPatch='" + securityPatch + '\'' +
               ", serial='" + serial + '\'' +
               '}';
    }

    @NonNull
    public String getMarketName() {
        String[] propKeys = new String[]{
                PROPERTY_PRODUCT_MARKET_NAME,
                PROPERTY_SONY_MARKET_NAME,
                PROPERTY_MARKETING_NAME,
                PROPERTY_PRODUCT_MODEL,
        };
        for (String key : propKeys) {
            String value = SystemProperties.get(key);
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return model;
    }

    private Display getDefaultDisplay() {
        return ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
    }

    public boolean isManufacturer(String manufacturer) {
        return manufacturers.is(manufacturer);
    }

    public class Manufacturers {

        private boolean is(String manufacturer) {
            return manufacturer.trim().equalsIgnoreCase(Build.MANUFACTURER);
        }

        /**
         * HTC.
         */
        public boolean isHtc() {
            return is("htc");
        }

        /**
         * LG.
         */
        public boolean isLG() {
            return is("lg");
        }

        /**
         * 一加.
         */
        public boolean isOnePlus() {
            return is("oneplus");
        }

        /**
         * 三星.
         */
        public boolean isSamsung() {
            return is("samsung");
        }

        /**
         * 中兴.
         */
        public boolean isZte() {
            return is("zte");
        }

        /**
         * 乐视.
         */
        public boolean isLetv() {
            return is("letv");
        }

        /**
         * 华为.
         */
        public boolean isHuawei() {
            return is("huawei") || isNova() || isHonor();
        }

        /**
         * Huawei (华为) Nova.
         */
        public boolean isNova() {
            return is("nova");
        }

        /**
         * Huawei (华为) 荣耀.
         */
        public boolean isHonor() {
            return is("honor");
        }

        /**
         * 小米.
         */
        public boolean isXiaomi() {
            return is("xiaomi") || brands.isRedmi() || brands.isMiMix();
        }

        /**
         * 欧珀.
         */
        public boolean isOppo() {
            return is("oppo");
        }

        /**
         * 索尼.
         */
        public boolean isSony() {
            return is("sony") || brands.isXperia();
        }

        /**
         * 维沃.
         */
        public boolean isVivo() {
            return is("vivo");
        }

        /**
         * 联想.
         */
        public boolean isLenovo() {
            return is("lenovo");
        }

        /**
         * 酷派.
         * 宇龙计算机通信科技 (深圳) 有限公司.
         */
        public boolean isCoolpad() {
            return is("yulong");
        }

        /**
         * 锤子.
         */
        public boolean isSmartisan() {
            return is("smartisan");
        }

        /**
         * 魅族.
         */
        public boolean isMeizu() {
            return is("meizu");
        }

    }

}
