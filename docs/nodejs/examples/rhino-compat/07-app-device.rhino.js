var launched = app.launchPackage("org.example.app");
var screenOn = device.isScreenOn();
if (!screenOn) {
  device.wakeUp();
}
device.vibrate(50);
log(JSON.stringify({ launched: launched, screenOn: screenOn }));
