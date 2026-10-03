/*
 * Requires a matching AutoJs6 6.8.0 local build (versionCode >= 5316) and
 * Compose UI 1.0.0 installed and enabled in the plugin center.
 * Run as a normal script, without the 'ui' directive. Grant AutoJs6 permission
 * to display over other apps before running. On HyperOS, return to the desktop.
 * Window position and size use physical pixels; component dimensions use dp.
 * The floating session keeps the script alive. Closing it stops this worker.
 */

var density = context.getResources().getDisplayMetrics().density;
var margin = Math.round(16 * density);
var startedAt = android.os.SystemClock.elapsedRealtime();
var updater = null;
var elapsed = compose.Text({testTag: 'hud-elapsed', text: 'Elapsed: 0 s', style: 'titleLarge'});
var hud = compose.floaty(compose.Surface({shape: {shape: 'rounded', radius: 12}},
    compose.Column({padding: 16, spacing: 8},
        compose.Text({text: 'Compose HUD', style: 'titleMedium'}),
        elapsed,
        compose.Row({spacing: 8},
            compose.OutlinedButton({testTag: 'hud-reset', onClick: function () {
                startedAt = android.os.SystemClock.elapsedRealtime();
                elapsed.text = 'Elapsed: 0 s';
            }}, 'Reset'),
            compose.Button({testTag: 'hud-close', onClick: function () { hud.close(); }}, 'Close')))),
    {raw: false, x: margin, y: Math.round(80 * density),
        width: Math.min(Math.round(280 * density), device.width - margin * 2), height: -2});
hud.setAdjustEnabled(true);
hud.on('close', function () {
    if (updater) updater.interrupt();
});

updater = threads.start(function () {
    while (true) {
        sleep(1000);
        // A worker posts work back to the owning script thread before touching nodes.
        compose.post(function () {
            if (!hud.isClosed()) {
                elapsed.text = 'Elapsed: ' + Math.floor((android.os.SystemClock.elapsedRealtime() - startedAt) / 1000) + ' s';
            }
        });
    }
});
