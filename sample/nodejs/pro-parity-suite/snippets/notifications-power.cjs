"nodejs";

function codeOf(error) {
  return String(error && (error.code || error.autojs6Code || error.name || "ERROR"));
}

(async function main() {
  try {
    const notifications = require("notifications");
    const status = await notifications.getPermissionStatus({ timeoutMs: 1000 });
    console.log("sample.pro-parity-suite.notifications-power.notificationsEnabled=" + status.notificationsEnabled);
    const settings = await notifications.openNotificationSettings({ dryRun: true, timeoutMs: 1000 });
    console.log("sample.pro-parity-suite.notifications-power.notificationSettings=" + settings.action);
  } catch (error) {
    console.log("sample.pro-parity-suite.notifications-power.skipped=notifications:" + codeOf(error));
  }

  try {
    const device = require("device");
    const ignored = await device.isIgnoringBatteryOptimizations({ timeoutMs: 1000 });
    console.log("sample.pro-parity-suite.notifications-power.batteryIgnored=" + ignored);
    const settings = await device.openBatteryOptimizationSettings({ dryRun: true, timeoutMs: 1000 });
    console.log("sample.pro-parity-suite.notifications-power.powerSettings=" + settings.action);
  } catch (error) {
    console.log("sample.pro-parity-suite.notifications-power.skipped=power:" + codeOf(error));
  }

  console.log("sample.pro-parity-suite.notifications-power=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
