// Type definitions for AutoJs6 internal module device
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 5.1.3
//
// Last modified: Jun 14, 2026

/// <reference path="./index.d.ts" />

/**
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/device/Device.kt
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/Device.java
 */

declare namespace Internal {

    class Device extends org.autojs.autojs.runtime.api.Device {

        /**
         * @Overrides for JSDoc or more specific types
         */

        /**
         * @example
         * console.log(device.width); // e.g. 720
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static final int width = ScreenMetrics.getDeviceScreenWidth();
         * @see org.autojs.autojs.runtime.api.ScreenMetrics
         */
        get width(): number;

        /**
         * @example
         * console.log(device.height); // e.g. 1280
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static final int width = ScreenMetrics.getDeviceScreenHeight();
         * @see org.autojs.autojs.runtime.api.ScreenMetrics
         */
        get height(): number;

        get rotation(): number;

        get orientation(): number;

        get density(): number;

        summary(): string;

        digest(): string;

        /**
         * Either a changelist number, or a label like "M4-rc20"
         * @example
         * console.log(device.buildId); // e.g. 47.1.A.16.20
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static final String buildId = Build.ID;
         * @see android.os.Build
         */
        buildId: string;

        /**
         * @example
         * console.log(device.buildDisplay); // e.g. 47.1.A.16.20
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static final String buildDisplay = Build.DISPLAY;
         * @see android.os.Build
         */
        buildDisplay: string;

        /**
         * The name of the overall product
         * @example
         * console.log(device.product); // e.g. G8441
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static final String product = Build.PRODUCT;
         * @see android.os.Build
         */
        product: string;

        /**
         * The consumer-visible brand with which the product/hardware will be associated, if any
         * @example
         * console.log(device.board); // e.g. msm8998
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static final String board = Build.BOARD;
         * @see android.os.Build
         */
        board: string;

        /**
         * @example
         * console.log(device.brand); // e.g. Sony
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static final String brand = Build.BRAND;
         * @see android.os.Build
         */
        brand: string;

        /**
         * The name of the industrial design
         * @example
         * console.log(device.device); // e.g. G8441
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static final String device = Build.DEVICE;
         * @see android.os.Build
         */
        device: string;

        /**
         * The end-user-visible name for the end product
         * @example
         * console.log(device.model); // e.g. G8441
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static final String model = Build.MODEL;
         * @see android.os.Build
         */
        model: string;

        /**
         * The system bootloader version number
         * @example
         * console.log(device.bootloader); // e.g. xboot
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static final String bootloader = Build.BOOTLOADER;
         * @see android.os.Build
         */
        bootloader: string;

        /**
         * The name of the hardware (from the kernel command line or /proc)
         * @example
         * console.log(device.hardware); // e.g. qcom
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static final String hardware = Build.HARDWARE;
         * @see android.os.Build
         */
        hardware: string;

        /**
         * A string that uniquely identifies this build.
         * Do not attempt to parse this value
         * @example
         * // e.g. Sony/G8441/G8441:8.0.0/47.1.A.12.235/16**7***03:user/release-keys
         * console.log(device.fingerprint);
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static final String fingerprint = Build.FINGERPRINT;
         * @see android.os.Build
         */
        fingerprint: string;

        /**
         * The user-visible SDK version of the framework;
         * its possible values are defined in Build.VERSION_CODES
         * @example
         * console.log(device.sdkInt); // e.g. 26
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static final int sdkInt = Build.VERSION.SDK_INT
         * @see android.os.Build.VERSION
         */
        sdkInt: number;

        /**
         * The internal value used by the underlying source control to represent this build.
         * E.g., a perforce changelist number or a git hash
         * @example
         * console.log(device.incremental); // e.g. 3744219090
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static final String incremental = Build.VERSION.INCREMENTAL;
         * @see android.os.Build.VERSION
         */
        incremental: string;

        /**
         * The user-visible version string. E.g., "1.0" or "3.4b5"
         * @example
         * console.log(device.release); // e.g. 8.0.0
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static final String release = Build.VERSION.RELEASE;
         * @see android.os.Build.VERSION
         */
        release: string;

        /**
         * The base OS build the product is based on
         * @example
         * console.log(device.baseOS); // e.g.
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static final String baseOS;
         * static {
         *     if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
         *         baseOS = Build.VERSION.BASE_OS;
         *         securityPatch = Build.VERSION.SECURITY_PATCH;
         *     } else {
         *         baseOS = null;
         *         securityPatch = null;
         *     }
         * }
         * @see android.os.Build
         */
        baseOS: string;

        /**
         * The user-visible security patch level
         * @example
         * console.log(device.securityPatch); // e.g. 2018-09-01
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static final String securityPatch;
         * static {
         *     if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
         *         baseOS = Build.VERSION.BASE_OS;
         *         securityPatch = Build.VERSION.SECURITY_PATCH;
         *     } else {
         *         baseOS = null;
         *         securityPatch = null;
         *     }
         * }
         * @see android.os.Build
         */
        securityPatch: string;

        /**
         * The current development codename, or the string "REL" if this is a release build
         * @example
         * console.log(device.codename); // e.g. REL
         * @example Source code summary (zh-CN: 源代码摘要)
         * public static final String codename = Build.VERSION.CODENAME;
         * @see android.os.Build.VERSION
         */
        codename: string;

        /**
         * A hardware serial number, if available. Alphanumeric only, case-insensitive
         *
         * @example
         * console.log(device.serial); // e.g. BH9****9EL
         */
        serial: string | null;

        /**
         * Returns the IMEI (International Mobile Equipment Identity). Return null if IMEI is not available.
         *
         * If the calling app's target SDK is API level 28 or lower and the app has the READ_PHONE_STATE permission then null is returned.
         * If the calling app's target SDK is API level 28 or lower and the app does not have the READ_PHONE_STATE permission,
         * or if the calling app is targeting API level 29 or higher, then null is returned.
         *
         * @example
         * console.log(device.imei); // e.g. 358354***89***4
         */
        imei: string | null;

        /**
         * The manufacturer of the product/hardware.
         *
         * @example
         * console.log(device.manufacturer); // e.g. Sony
         */
        manufacturer: string;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * private PowerManager.WakeLock mWakeLock;
         * public void cancelKeepingAwake() {
         *     if (mWakeLock != null && mWakeLock.isHeld())
         *         mWakeLock.release();
         * }
         * @see android.os.PowerManager
         */
        cancelKeepingAwake(): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * public void cancelVibration() {
         *     ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).cancel();
         * }
         * // just to avoid warning of null pointer to make android studio happy..
         * -- @NonNull
         * -- @SuppressWarnings("unchecked")
         * private <T> T getSystemService(String service) {
         *     Object systemService = mContext.getSystemService(service);
         *     if (systemService == null) {
         *         throw new RuntimeException("should never happen..." + service);
         *     }
         *     return (T) systemService;
         * }
         * @see android.content.Context
         * @see android.os.Vibrator
         */
        cancelVibration(): void;

        /**
         * @example
         * let percentage = device.getAlarmVolume() / device.getAlarmMaxVolume() * 100;
         * console.log("current alarm volume: " + +percentage.toFixed(2) + "%"); // e.g. 57.14%
         * @example Source code summary (zh-CN: 源代码摘要)
         * public int getAlarmMaxVolume() {
         *     return ((AudioManager) getSystemService(Context.AUDIO_SERVICE))
         *             .getStreamMaxVolume(AudioManager.STREAM_ALARM);
         * }
         * // just to avoid warning of null pointer to make android studio happy..
         * -- @NonNull
         * -- @SuppressWarnings("unchecked")
         * private <T> T getSystemService(String service) {
         *     Object systemService = mContext.getSystemService(service);
         *     if (systemService == null) {
         *         throw new RuntimeException("should never happen..." + service);
         *     }
         *     return (T) systemService;
         * }
         * @see android.content.Context
         * @see android.media.AudioManager
         */
        getAlarmMaxVolume(): number;

        /**
         * @example
         * let percentage = device.getAlarmVolume() / device.getAlarmMaxVolume() * 100;
         * console.log("current alarm volume: " + +percentage.toFixed(2) + "%"); // e.g. 57.14%
         * @example Source code summary (zh-CN: 源代码摘要)
         * public int getAlarmVolume() {
         *     return ((AudioManager) getSystemService(Context.AUDIO_SERVICE))
         *             .getStreamVolume(AudioManager.STREAM_ALARM);
         * }
         * // just to avoid warning of null pointer to make android studio happy..
         * -- @NonNull
         * -- @SuppressWarnings("unchecked")
         * private <T> T getSystemService(String service) {
         *     Object systemService = mContext.getSystemService(service);
         *     if (systemService == null) {
         *         throw new RuntimeException("should never happen..." + service);
         *     }
         *     return (T) systemService;
         * }
         * @see android.content.Context
         * @see android.media.AudioManager
         */
        getAlarmVolume(): number;

        /**
         * Returns ANDROID_ID of current device
         * @example
         * console.log(device.getAndroidId()); // e.g. 87b***f275***0e1
         * @example Source code summary (zh-CN: 源代码摘要)
         * -- @SuppressLint("HardwareIds")
         * public String getAndroidId() {
         *     return Settings.Secure.getString(mContext.getContentResolver(), Settings.Secure.ANDROID_ID);
         * }
         * @see android.provider.Settings
         */
        getAndroidId(): string;

        /**
         * @example
         * let mem = device.getAvailMem();
         * let units = ["B", "KB", "MB", "GB"];
         * let idx = 0;
         * let idx_max = units.length - 1;
         * let base = 1024;
         * while (mem > base) {
         *     mem /= base;
         *     if (++idx === idx_max) {
         *         break;
         *     }
         * }
         * console.log(+mem.toFixed(2) + units[idx]); // e.g. 853.8MB
         * @example Source code summary (zh-CN: 源代码摘要)
         * public long getAvailMem() {
         *     ActivityManager activityManager = getSystemService(Context.ACTIVITY_SERVICE);
         *     ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
         *     activityManager.getMemoryInfo(info);
         *     return info.availMem;
         * }
         * // just to avoid warning of null pointer to make android studio happy..
         * -- @NonNull
         * -- @SuppressWarnings("unchecked")
         * private <T> T getSystemService(String service) {
         *     Object systemService = mContext.getSystemService(service);
         *     if (systemService == null) {
         *         throw new RuntimeException("should never happen..." + service);
         *     }
         *     return (T) systemService;
         * }
         * @see android.app.ActivityManager
         * @see android.content.Context
         */
        getAvailMem(): number;

        /**
         * @example
         * console.log(device.getBattery()); // e.g. 76
         * @example Source code summary (zh-CN: 源代码摘要)
         * public float getBattery() {
         *     Intent batteryIntent = mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
         *     if (batteryIntent == null) {
         *         return -1;
         *     }
         *     int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
         *     int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
         *     float battery = ((float) level / scale) * 100.0f;
         *     return Math.round(battery * 10) / 10;
         * }
         * @see android.content.Intent
         * @see android.content.IntentFilter
         * @see android.os.BatteryManager
         */
        getBattery(): number;

        /**
         * The screen backlight brightness between 0 and 255
         * @example
         * console.log(device.getBrightness()); // e.g. 54
         * @example Source code summary (zh-CN: 源代码摘要)
         * public int getBrightness() throws Settings.SettingNotFoundException {
         *     return Settings.System.getInt(mContext.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
         * }
         * @see android.provider.Settings
         */
        getBrightness(): number;

        /**
         * Control whether to enable automatic brightness mode
         * @example
         * // 0: manually; 1: automatically
         * console.log(device.getBrightnessMode());
         * @example Source code summary (zh-CN: 源代码摘要)
         * public int getBrightnessMode() throws Settings.SettingNotFoundException {
         *     return Settings.System.getInt(mContext.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE);
         * }
         * @see android.provider.Settings
         */
        getBrightnessMode(): number;

        /**
         * @example
         * console.log(device.getMacAddress()); // e.g. 38:78:62:**:**:*6
         * @example Source code summary (zh-CN: 源代码摘要)
         * private static final String FAKE_MAC_ADDRESS = "02:00:00:00:00:00";
         * public String getMacAddress() throws Exception {
         *     WifiManager wifiMan = (WifiManager) mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
         *     if (wifiMan == null) {
         *         return null;
         *     }
         *     WifiInfo wifiInf = wifiMan.getConnectionInfo();
         *     if (wifiInf == null) {
         *         return getMacByFile();
         *     }
         *
         *     String mac = wifiInf.getMacAddress();
         *     if (FAKE_MAC_ADDRESS.equals(mac)) {
         *         mac = null;
         *     }
         *     if (mac == null) {
         *         mac = getMacByInterface();
         *         if (mac == null) {
         *             mac = getMacByFile();
         *         }
         *     }
         *     return mac;
         * }
         * private static String getMacByInterface() throws SocketException {
         *     List<NetworkInterface> networkInterfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
         *     for (NetworkInterface networkInterface : networkInterfaces) {
         *         if (networkInterface.getName().equalsIgnoreCase('wlan0')) {
         *             byte[] macBytes = networkInterface.getHardwareAddress();
         *             if (macBytes == null) {
         *                 return null;
         *             }
         *
         *             StringBuilder mac = new StringBuilder();
         *             for (byte b : macBytes) {
         *                 mac.append(String.format("%02X:", b));
         *             }
         *
         *             if (mac.length() > 0) {
         *                 mac.deleteCharAt(mac.length() - 1);
         *             }
         *             return mac.toString();
         *         }
         *     }
         *     return null;
         * }
         * private static String getMacByFile() throws Exception {
         *     try {
         *         return PFiles.read('/sys/class/net/wlan0/address');
         *     } catch (UncheckedIOException e) {
         *         return null;
         *     }
         * }
         * @see android.content.Context
         * @see android.net.wifi.WifiInfo
         * @see android.net.wifi.WifiManager
         * @see org.autojs.autojs.pio.PFiles
         * @see org.autojs.autojs.pio.UncheckedIOException
         * @see java.net.NetworkInterface
         * @see java.net.SocketException
         * @see java.util.Collections
         * @see java.util.List
         */
        getMacAddress(): string;

        /**
         * @example
         * let percentage = device.getMusicVolume() / device.getMusicMaxVolume() * 100;
         * console.log("current music volume: " + +percentage.toFixed(2) + "%"); // e.g. 50%
         * @example Source code summary (zh-CN: 源代码摘要)
         * public int getMusicMaxVolume() {
         *     return ((AudioManager) getSystemService(Context.AUDIO_SERVICE))
         *             .getStreamMaxVolume(AudioManager.STREAM_MUSIC);
         * }
         * // just to avoid warning of null pointer to make android studio happy..
         * -- @NonNull
         * -- @SuppressWarnings("unchecked")
         * private <T> T getSystemService(String service) {
         *     Object systemService = mContext.getSystemService(service);
         *     if (systemService == null) {
         *         throw new RuntimeException("should never happen..." + service);
         *     }
         *     return (T) systemService;
         * }
         * @see android.content.Context
         * @see android.media.AudioManager
         */
        getMusicMaxVolume(): number;

        /**
         * @example
         * let percentage = device.getMusicVolume() / device.getMusicMaxVolume() * 100;
         * console.log("current music volume: " + +percentage.toFixed(2) + "%"); // e.g. 50%
         * @example Source code summary (zh-CN: 源代码摘要)
         * public int getMusicVolume() {
         *     return ((AudioManager) getSystemService(Context.AUDIO_SERVICE))
         *             .getStreamVolume(AudioManager.STREAM_MUSIC);
         * }
         * // just to avoid warning of null pointer to make android studio happy..
         * -- @NonNull
         * -- @SuppressWarnings("unchecked")
         * private <T> T getSystemService(String service) {
         *     Object systemService = mContext.getSystemService(service);
         *     if (systemService == null) {
         *         throw new RuntimeException("should never happen..." + service);
         *     }
         *     return (T) systemService;
         * }
         * @see android.content.Context
         * @see android.media.AudioManager
         */
        getMusicVolume(): number;

        /**
         * @example
         * let percentage = device.getNotificationVolume() / device.getNotificationMaxVolume() * 100;
         * console.log("current notification volume: " + +percentage.toFixed(2) + "%"); // e.g. 71.43%
         * @example Source code summary (zh-CN: 源代码摘要)
         * public int getNotificationMaxVolume() {
         *     return ((AudioManager) getSystemService(Context.AUDIO_SERVICE))
         *             .getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION);
         * }
         * // just to avoid warning of null pointer to make android studio happy..
         * -- @NonNull
         * -- @SuppressWarnings("unchecked")
         * private <T> T getSystemService(String service) {
         *     Object systemService = mContext.getSystemService(service);
         *     if (systemService == null) {
         *         throw new RuntimeException("should never happen..." + service);
         *     }
         *     return (T) systemService;
         * }
         * @see android.content.Context
         * @see android.media.AudioManager
         */
        getNotificationMaxVolume(): number;

        /**
         * @example
         * let percentage = device.getNotificationVolume() / device.getNotificationMaxVolume() * 100;
         * console.log("current notification volume: " + +percentage.toFixed(2) + "%"); // e.g. 71.43%
         * @example Source code summary (zh-CN: 源代码摘要)
         * public int getNotificationVolume() {
         *     return ((AudioManager) getSystemService(Context.AUDIO_SERVICE))
         *             .getStreamVolume(AudioManager.STREAM_NOTIFICATION);
         * }
         * // just to avoid warning of null pointer to make android studio happy..
         * -- @NonNull
         * -- @SuppressWarnings("unchecked")
         * private <T> T getSystemService(String service) {
         *     Object systemService = mContext.getSystemService(service);
         *     if (systemService == null) {
         *         throw new RuntimeException("should never happen..." + service);
         *     }
         *     return (T) systemService;
         * }
         * @see android.content.Context
         * @see android.media.AudioManager
         */
        getNotificationVolume(): number;

        /**
         * @example
         * let mem = device.getTotalMem();
         * let units = ["B", "KB", "MB", "GB"];
         * let idx = 0;
         * let idx_max = units.length - 1;
         * let base = 1024;
         * while (mem > base) {
         *     mem /= base;
         *     if (++idx === idx_max) {
         *         break;
         *     }
         * }
         * console.log(+mem.toFixed(2) + units[idx]); // e.g. 3.64GB
         * @example Source code summary (zh-CN: 源代码摘要)
         * public long getTotalMem() {
         *     ActivityManager activityManager = getSystemService(Context.ACTIVITY_SERVICE);
         *     ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
         *     activityManager.getMemoryInfo(info);
         *     return info.totalMem;
         * }
         * // just to avoid warning of null pointer to make android studio happy..
         * -- @NonNull
         * -- @SuppressWarnings("unchecked")
         * private <T> T getSystemService(String service) {
         *     Object systemService = mContext.getSystemService(service);
         *     if (systemService == null) {
         *         throw new RuntimeException("should never happen..." + service);
         *     }
         *     return (T) systemService;
         * }
         * @see android.app.ActivityManager
         * @see android.content.Context
         */
        getTotalMem(): number;

        /**
         * Returns the IMEI (International Mobile Equipment Identity). Return null if IMEI is not available.
         *
         * If the calling app's target SDK is API level 28 or lower and the app has the READ_PHONE_STATE permission then null is returned.
         * If the calling app's target SDK is API level 28 or lower and the app does not have the READ_PHONE_STATE permission,
         * or if the calling app is targeting API level 29 or higher, then null is returned.
         *
         * @example
         * console.log(device.getIMEI()); // e.g. 358354***89***4
         */
        getIMEI(): string | null;

        /**
         * A hardware serial number, if available. Alphanumeric only, case-insensitive
         *
         * @example
         * console.log(device.getSerial()); // e.g. BH9****9EL
         */
        getSerial(): string | null;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * public boolean isCharging() {
         *     Intent intent = mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
         *     if (intent == null) {
         *         throw new ScriptException('Cannot retrieve the battery state');
         *     }
         *     int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
         *     return plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB;
         * }
         * @see android.content.Intent
         * @see android.content.IntentFilter
         * @see android.os.BatteryManager
         * @see org.autojs.autojs.runtime.exception.ScriptException
         */
        isCharging(): boolean;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * public boolean isScreenOn() {
         *     // 按照 API 文档来说不应该使用 PowerManager.isScreenOn()
         *     // 但是 isScreenOn() 和实际不一致的情况通常只会出现在安卓智能手表的类似设备上
         *     // 因此这里仍然使用 PowerManager.isScreenOn()
         *     // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
         *     //   return ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getState() == Display.STATE_ON;
         *     // } else {
         *     return ((PowerManager) getSystemService(Context.POWER_SERVICE)).isScreenOn();
         *     // }
         * }
         * // just to avoid warning of null pointer to make android studio happy..
         * -- @NonNull
         * -- @SuppressWarnings("unchecked")
         * private <T> T getSystemService(String service) {
         *     Object systemService = mContext.getSystemService(service);
         *     if (systemService == null) {
         *         throw new RuntimeException("should never happen..." + service);
         *     }
         *     return (T) systemService;
         * }
         * @see android.content.Context
         * @see android.os.PowerManager
         */
        isScreenOn(): boolean;

        isScreenOff(): boolean;

        isScreenPortrait(): boolean;

        isScreenLandscape(): boolean;

        /**
         * @example
         * let PowerManager = android.os.PowerManager;
         * device.keepAwake(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP);
         * @example Source code summary (zh-CN: 源代码摘要)
         * private PowerManager.WakeLock mWakeLock;
         * private int mWakeLockFlag;
         * -- @SuppressLint("WakelockTimeout")
         * public void keepAwake(int flags) {
         *     checkWakeLock(flags);
         *     mWakeLock.acquire();
         * }
         * private void checkWakeLock(int flags) {
         *     if (mWakeLock == null || flags != mWakeLockFlag) {
         *         cancelKeepingAwake();
         *         mWakeLock = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(flags, Device.class.getName());
         *     }
         * }
         * public void cancelKeepingAwake() {
         *     if (mWakeLock != null && mWakeLock.isHeld())
         *         mWakeLock.release();
         * }
         * // just to avoid warning of null pointer to make android studio happy..
         * -- @NonNull
         * -- @SuppressWarnings("unchecked")
         * private <T> T getSystemService(String service) {
         *     Object systemService = mContext.getSystemService(service);
         *     if (systemService == null) {
         *         throw new RuntimeException("should never happen..." + service);
         *     }
         *     return (T) systemService;
         * }
         * @see android.annotation.SuppressLint
         * @see android.content.Context
         * @see android.os.PowerManager
         * @see org.autojs.autojs.runtime.api.Device
         */
        keepAwake(flags: number): void;

        /**
         * @example
         * let PowerManager = android.os.PowerManager;
         * device.keepAwake(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, 800); // 0.8 seconds
         * @example Source code summary (zh-CN: 源代码摘要)
         * private PowerManager.WakeLock mWakeLock;
         * private int mWakeLockFlag;
         * public void keepAwake(int flags, long timeout) {
         *     checkWakeLock(flags);
         *     mWakeLock.acquire(timeout);
         * }
         * private void checkWakeLock(int flags) {
         *     if (mWakeLock == null || flags != mWakeLockFlag) {
         *         cancelKeepingAwake();
         *         mWakeLock = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(flags, Device.class.getName());
         *     }
         * }
         * public void cancelKeepingAwake() {
         *     if (mWakeLock != null && mWakeLock.isHeld())
         *         mWakeLock.release();
         * }
         * // just to avoid warning of null pointer to make android studio happy..
         * -- @NonNull
         * -- @SuppressWarnings("unchecked")
         * private <T> T getSystemService(String service) {
         *     Object systemService = mContext.getSystemService(service);
         *     if (systemService == null) {
         *         throw new RuntimeException("should never happen..." + service);
         *     }
         *     return (T) systemService;
         * }
         * @see android.content.Context
         * @see android.os.PowerManager
         * @see org.autojs.autojs.runtime.api.Device
         */
        keepAwake(flags: number, timeout: number): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * private PowerManager.WakeLock mWakeLock;
         * private int mWakeLockFlag;
         * public void keepScreenDim() {
         *     keepAwake(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP);
         * }
         * -- @SuppressLint("WakelockTimeout")
         * public void keepAwake(int flags) {
         *     checkWakeLock(flags);
         *     mWakeLock.acquire();
         * }
         * private void checkWakeLock(int flags) {
         *     if (mWakeLock == null || flags != mWakeLockFlag) {
         *         cancelKeepingAwake();
         *         mWakeLock = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(flags, Device.class.getName());
         *     }
         * }
         * public void cancelKeepingAwake() {
         *     if (mWakeLock != null && mWakeLock.isHeld())
         *         mWakeLock.release();
         * }
         * // just to avoid warning of null pointer to make android studio happy..
         * -- @NonNull
         * -- @SuppressWarnings("unchecked")
         * private <T> T getSystemService(String service) {
         *     Object systemService = mContext.getSystemService(service);
         *     if (systemService == null) {
         *         throw new RuntimeException("should never happen..." + service);
         *     }
         *     return (T) systemService;
         * }
         * @see android.annotation.SuppressLint
         * @see android.content.Context
         * @see android.os.PowerManager
         * @see org.autojs.autojs.runtime.api.Device
         */
        keepScreenDim(): void;

        /**
         * @example
         * device.keepScreenDim(5 * 60e3); // 5 minutes
         * @example Source code summary (zh-CN: 源代码摘要)
         * private PowerManager.WakeLock mWakeLock;
         * private int mWakeLockFlag;
         * public void keepScreenDim(long timeout) {
         *     keepAwake(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, timeout);
         * }
         * public void keepAwake(int flags, long timeout) {
         *     checkWakeLock(flags);
         *     mWakeLock.acquire(timeout);
         * }
         * private void checkWakeLock(int flags) {
         *     if (mWakeLock == null || flags != mWakeLockFlag) {
         *         cancelKeepingAwake();
         *         mWakeLock = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(flags, Device.class.getName());
         *     }
         * }
         * public void cancelKeepingAwake() {
         *     if (mWakeLock != null && mWakeLock.isHeld())
         *         mWakeLock.release();
         * }
         * // just to avoid warning of null pointer to make android studio happy..
         * -- @NonNull
         * -- @SuppressWarnings("unchecked")
         * private <T> T getSystemService(String service) {
         *     Object systemService = mContext.getSystemService(service);
         *     if (systemService == null) {
         *         throw new RuntimeException("should never happen..." + service);
         *     }
         *     return (T) systemService;
         * }
         * @see android.content.Context
         * @see android.os.PowerManager
         * @see org.autojs.autojs.runtime.api.Device
         */
        keepScreenDim(timeout: number): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * private PowerManager.WakeLock mWakeLock;
         * private int mWakeLockFlag;
         * public void keepScreenOn() {
         *     keepAwake(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP);
         * }
         * -- @SuppressLint("WakelockTimeout")
         * public void keepAwake(int flags) {
         *     checkWakeLock(flags);
         *     mWakeLock.acquire();
         * }
         * private void checkWakeLock(int flags) {
         *     if (mWakeLock == null || flags != mWakeLockFlag) {
         *         cancelKeepingAwake();
         *         mWakeLock = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(flags, Device.class.getName());
         *     }
         * }
         * public void cancelKeepingAwake() {
         *     if (mWakeLock != null && mWakeLock.isHeld())
         *         mWakeLock.release();
         * }
         * // just to avoid warning of null pointer to make android studio happy..
         * -- @NonNull
         * -- @SuppressWarnings("unchecked")
         * private <T> T getSystemService(String service) {
         *     Object systemService = mContext.getSystemService(service);
         *     if (systemService == null) {
         *         throw new RuntimeException("should never happen..." + service);
         *     }
         *     return (T) systemService;
         * }
         * @see android.annotation.SuppressLint
         * @see android.content.Context
         * @see android.os.PowerManager
         * @see org.autojs.autojs.runtime.api.Device
         */
        keepScreenOn(): void;

        /**
         * @example
         * device.keepScreenOn(5 * 60e3); // 5 minutes
         * @example Source code summary (zh-CN: 源代码摘要)
         * private PowerManager.WakeLock mWakeLock;
         * private int mWakeLockFlag;
         * public void keepScreenOn(long timeout) {
         *     keepAwake(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, timeout);
         * }
         * public void keepAwake(int flags, long timeout) {
         *     checkWakeLock(flags);
         *     mWakeLock.acquire(timeout);
         * }
         * private void checkWakeLock(int flags) {
         *     if (mWakeLock == null || flags != mWakeLockFlag) {
         *         cancelKeepingAwake();
         *         mWakeLock = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(flags, Device.class.getName());
         *     }
         * }
         * public void cancelKeepingAwake() {
         *     if (mWakeLock != null && mWakeLock.isHeld())
         *         mWakeLock.release();
         * }
         * // just to avoid warning of null pointer to make android studio happy..
         * -- @NonNull
         * -- @SuppressWarnings("unchecked")
         * private <T> T getSystemService(String service) {
         *     Object systemService = mContext.getSystemService(service);
         *     if (systemService == null) {
         *         throw new RuntimeException("should never happen..." + service);
         *     }
         *     return (T) systemService;
         * }
         * @see android.content.Context
         * @see android.os.PowerManager
         * @see org.autojs.autojs.runtime.api.Device
         */
        keepScreenOn(timeout: number): void;

        /**
         * @example
         * device.setAlarmVolume(0);
         * @example Source code summary (zh-CN: 源代码摘要)
         * public void setAlarmVolume(int i) {
         *     checkWriteSettingsPermission();
         *     ((AudioManager) getSystemService(Context.AUDIO_SERVICE))
         *             .setStreamVolume(AudioManager.STREAM_ALARM, i, 0);
         * }
         * private void checkWriteSettingsPermission() {
         *     if (SettingsCompat.canWriteSettings(mContext)) {
         *         return;
         *     }
         *     SettingsCompat.manageWriteSettings(mContext);
         *     throw new SecurityException(mContext.getString(R.string.no_write_settings_permission));
         * }
         * @see android.content.Context
         * @see android.media.AudioManager
         * @see ezy.assist.compat.SettingsCompat
         * @see org.autojs.autojs6.R
         */
        setAlarmVolume(i: number): void;

        /**
         * @example
         * device.setBrightness(54);
         * @example Source code summary (zh-CN: 源代码摘要)
         * public void setBrightness(int b) throws Settings.SettingNotFoundException {
         *     checkWriteSettingsPermission();
         *     Settings.System.putInt(mContext.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, b);
         * }
         * private void checkWriteSettingsPermission() {
         *     if (SettingsCompat.canWriteSettings(mContext)) {
         *         return;
         *     }
         *     SettingsCompat.manageWriteSettings(mContext);
         *     throw new SecurityException(mContext.getString(R.string.no_write_settings_permission));
         * }
         * @see android.provider.Settings
         * @see ezy.assist.compat.SettingsCompat
         * @see org.autojs.autojs6.R
         */
        setBrightness(b: number): void;

        /**
         * @example
         * device.setBrightnessMode(1); // auto mode on
         * @example
         * device.setBrightnessMode(0); // auto mode off
         * @example
         * device.setBrightnessMode(+!device.getBrightnessMode()); // toggle
         * @example Source code summary (zh-CN: 源代码摘要)
         * public void setBrightnessMode(int b) throws Settings.SettingNotFoundException {
         *     checkWriteSettingsPermission();
         *     Settings.System.putInt(mContext.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, b);
         * }
         * private void checkWriteSettingsPermission() {
         *     if (SettingsCompat.canWriteSettings(mContext)) {
         *         return;
         *     }
         *     SettingsCompat.manageWriteSettings(mContext);
         *     throw new SecurityException(mContext.getString(R.string.no_write_settings_permission));
         * }
         * @see android.provider.Settings
         * @see ezy.assist.compat.SettingsCompat
         * @see org.autojs.autojs6.R
         */
        setBrightnessMode(b: number): void;

        /**
         * @example
         * device.setMusicVolume(0);
         * @example Source code summary (zh-CN: 源代码摘要)
         * public void setMusicVolume(int i) {
         *     checkWriteSettingsPermission();
         *     ((AudioManager) getSystemService(Context.AUDIO_SERVICE))
         *             .setStreamVolume(AudioManager.STREAM_MUSIC, i, 0);
         * }
         * private void checkWriteSettingsPermission() {
         *     if (SettingsCompat.canWriteSettings(mContext)) {
         *         return;
         *     }
         *     SettingsCompat.manageWriteSettings(mContext);
         *     throw new SecurityException(mContext.getString(R.string.no_write_settings_permission));
         * }
         * @see android.content.Context
         * @see android.media.AudioManager
         * @see ezy.assist.compat.SettingsCompat
         * @see org.autojs.autojs6.R
         */
        setMusicVolume(i: number): void;

        /**
         * @example
         * device.setNotificationVolume(0);
         * @example Source code summary (zh-CN: 源代码摘要)
         * public void setNotificationVolume(int i) {
         *     checkWriteSettingsPermission();
         *     ((AudioManager) getSystemService(Context.AUDIO_SERVICE))
         *             .setStreamVolume(AudioManager.STREAM_NOTIFICATION, i, 0);
         * }
         * private void checkWriteSettingsPermission() {
         *     if (SettingsCompat.canWriteSettings(mContext)) {
         *         return;
         *     }
         *     SettingsCompat.manageWriteSettings(mContext);
         *     throw new SecurityException(mContext.getString(R.string.no_write_settings_permission));
         * }
         * @see android.content.Context
         * @see android.media.AudioManager
         * @see ezy.assist.compat.SettingsCompat
         * @see org.autojs.autojs6.R
         */
        setNotificationVolume(i: number): void;

        /**
         * @example
         * console.log(device.toString());
         * console.log(device); // same as above
         * @example Source code summary (zh-CN: 源代码摘要)
         * -- @Override
         * public String toString() {
         *     return "Device{" +
         *             "width=" + width +
         *             ", height=" + height +
         *             ", buildId='" + buildId + '\'' +
         *             ", buildDisplay='" + buildDisplay + '\'' +
         *             ", product='" + product + '\'' +
         *             ", board='" + board + '\'' +
         *             ", brand='" + brand + '\'' +
         *             ", device='" + device + '\'' +
         *             ", model='" + model + '\'' +
         *             ", bootloader='" + bootloader + '\'' +
         *             ", hardware='" + hardware + '\'' +
         *             ", fingerprint='" + fingerprint + '\'' +
         *             ", sdkInt=" + sdkInt +
         *             ", incremental='" + incremental + '\'' +
         *             ", release='" + release + '\'' +
         *             ", baseOS='" + baseOS + '\'' +
         *             ", securityPatch='" + securityPatch + '\'' +
         *             ", serial='" + serial + '\'' +
         *             '}';
         * }
         */
        toString(): string;

        /**
         * @param pattern – The pattern of alternating on-off timings, starting with off.
         *
         * @example number[]
         * // 0: off, 200: on, 400: off, ...
         * let pattern = [0, 200, 400, 200, 400, 200, 100, 200, 100, 200];
         * device.vibrate(pattern);
         * @example
         * // 'SOS' in Morse -- zh-CN: 'SOS' 的摩斯编码
         * device.vibrate([0, 100, 60, 100, 60, 100, 200, 200, 60, 200, 60, 200, 200, 100, 60, 100, 60, 100]);
         * // Also, you can use vibrate(text: string) for an easier way.
         * device.vibrate('SOS');
         *
         * @example string
         * // case-insensitive
         * device.vibrate('love');
         * @see Util.MorseCode.vibrate
         */
        vibrate(pattern: OmniVibrationPattern, delay?: number): void;

        /**
         * @example
         * device.vibrate(800); // 0.8 seconds
         * @example Source code summary (zh-CN: 源代码摘要)
         * public void vibrate(long millis) {
         *     ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(millis);
         * }
         * @see android.content.Context
         * @see android.os.Vibrator
         */
        vibrate(millis: number): void;

        /**
         * @example
         * device.vibrate(1e3, 800); // vibrate for 0.8 seconds after 1 second's off
         */
        vibrate(off: number, millis: number): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * private PowerManager.WakeLock mWakeLock;
         * private int mWakeLockFlag;
         * public void wakeUp() {
         *     keepScreenOn(200);
         * }
         * public void keepScreenOn(long timeout) {
         *     keepAwake(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, timeout);
         * }
         * public void keepAwake(int flags, long timeout) {
         *     checkWakeLock(flags);
         *     mWakeLock.acquire(timeout);
         * }
         * private void checkWakeLock(int flags) {
         *     if (mWakeLock == null || flags != mWakeLockFlag) {
         *         cancelKeepingAwake();
         *         mWakeLock = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(flags, Device.class.getName());
         *     }
         * }
         * public void cancelKeepingAwake() {
         *     if (mWakeLock != null && mWakeLock.isHeld())
         *         mWakeLock.release();
         * }
         * // just to avoid warning of null pointer to make android studio happy..
         * -- @NonNull
         * -- @SuppressWarnings("unchecked")
         * private <T> T getSystemService(String service) {
         *     Object systemService = mContext.getSystemService(service);
         *     if (systemService == null) {
         *         throw new RuntimeException("should never happen..." + service);
         *     }
         *     return (T) systemService;
         * }
         * @see android.content.Context
         * @see android.os.PowerManager
         * @see org.autojs.autojs.runtime.api.Device
         */
        wakeUp(): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * public void wakeUpIfNeeded() {
         *     if (!isScreenOn()) {
         *         wakeUp();
         *     }
         * }
         * @see isScreenOn
         * @see wakeUp
         */
        wakeUpIfNeeded(): void;

        getIpAddress(useIPv4?: boolean | null): string;

        getIpv6Address(): string;

        getGatewayAddress(): string;

        isActiveNetworkMetered(): boolean;

        isConnectedOrConnecting(): boolean;

        isWifiAvailable(): boolean;

        getSharedDeviceId(): string | null;

        setPointerLocation(enabled: boolean): boolean;

        setPointerLocationEnabled(): boolean;

        setPointerLocationDisabled(): boolean;

        isPointerLocationEnabled(): boolean;

        isPointerLocationDisabled(): boolean;

        togglePointerLocation(): boolean;

    }

}

declare namespace Device {

    type Keycode = 'home' | 'back' | 'recents' | 'power_dialog' | 'notifications' | 'quick_settings' | 'split_screen';

}
