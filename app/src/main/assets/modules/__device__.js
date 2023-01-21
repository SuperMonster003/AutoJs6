/* Overwritten protection. */

let { util } = global;

/**
 * @param {org.autojs.autojs.runtime.ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Device}
 */
module.exports = function (scriptRuntime, scope) {
    const ScreenMetrics = org.autojs.autojs.runtime.api.ScreenMetrics;
    const NetworkUtils = org.autojs.autojs.util.NetworkUtils;
    const DeviceUtils = org.autojs.autojs.util.DeviceUtils;

    const rtDevice = scriptRuntime.device;

    let _ = {
        Device: ( /* @IIFE */ () => {
            /**
             * @extends Internal.Device
             */
            const Device = function () {
                // Empty interface body.
            };

            Device.prototype = {
                constructor: Device,
                get width() {
                    return ScreenMetrics.getDeviceScreenWidth();
                },
                get height() {
                    return ScreenMetrics.getDeviceScreenHeight();
                },
                get rotation() {
                    return ScreenMetrics.getRotation();
                },
                get density() {
                    return ScreenMetrics.getDeviceScreenDensity();
                },
                get summary() {
                    return DeviceUtils.getDeviceSummary(context);
                },
                get digest() {
                    let digestList = [
                        `${this.brand}${this.manufacturer === this.brand ? `` : ` (${this.manufacturer})`}`,
                        `${this.device}${this.model === this.device ? `` : ` (${this.model})`}`,
                        `${this.release} (${this.sdkInt})`,
                    ];
                    return digestList.join(' / ');
                },
                vibrate(off, millis) {
                    if (typeof arguments[0] === 'string') {
                        util.morseCode.vibrate.apply(util.morseCode, arguments);
                    } else {
                        rtDevice.vibrate.apply(rtDevice, arguments);
                    }
                },
                isScreenOff() {
                    return !rtDevice.isScreenOn();
                },
                getIpAddress(useIPv4) {
                    return useIPv4 === undefined
                        ? NetworkUtils.getIpAddress()
                        : NetworkUtils.getIpAddress(useIPv4);
                },
                getIpv6Address() {
                    return NetworkUtils.getIpv6Address();
                },
                getGatewayAddress() {
                    return NetworkUtils.getGatewayAddress();
                },
                isActiveNetworkMetered() {
                    return NetworkUtils.isActiveNetworkMetered();
                },
                isConnectedOrConnecting() {
                    return NetworkUtils.isConnectedOrConnecting();
                },
                isWifiAvailable() {
                    return NetworkUtils.isWifiAvailable();
                },
            };

            Object.setPrototypeOf(Device.prototype, rtDevice);

            return Device;
        })(),
    };

    /**
     * @type {Internal.Device}
     */
    const device = new _.Device();

    return device;
};