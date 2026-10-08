'ui';

// Packed by the real APK Builder plugin from the paired INRT Runtime Kit.
// This fixture uses the default application signer and grants no plugin trust or enable state.
let runtimeBuild = Number(Packages.org.autojs.autojs6.BuildConfig.VERSION_CODE);
let applicationBuild = Number(context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode);
let identity = 'App: ' + applicationBuild + '; runtime: ' + runtimeBuild;

try {
    if (!Packages.org.autojs.autojs6.BuildConfig.isInrt) {
        throw new Error('This fixture must run inside a packaged INRT application');
    }
    let count = compose.state(0);
    compose.mount(function () {
        return compose.Column({padding: 16},
            compose.Text({testTag: 'compose-inrt-identity', text: identity}),
            compose.Text({testTag: 'compose-inrt-package', text: String(context.getPackageName())}),
            compose.Text({testTag: 'compose-inrt-count', text: 'Count: ' + count.value}),
            compose.Button({testTag: 'compose-inrt-increment', onClick: function () {
                count.value++;
            }}, 'Increment'));
    });
} catch (error) {
    // A packaged application can show the same typed, localized error in its existing XML UI.
    ui.layout('<vertical padding="16">' +
        '<text id="packagedIdentity" contentDescription="compose-inrt-identity" />' +
        '<text id="packagedCode" contentDescription="compose-inrt-error-code" />' +
        '<text id="packagedMessage" contentDescription="compose-inrt-error-message" />' +
        '</vertical>');
    ui.packagedIdentity.setText(identity);
    ui.packagedCode.setText(String(error.code || 'UNEXPECTED_ERROR'));
    ui.packagedMessage.setText(String(error.message || error));
}
