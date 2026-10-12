'ui';

/*
 * Requires a matching AutoJs6 6.8.0 local build (versionCode >= 5316) and
 * Compose UI 1.0.0 installed and enabled in the plugin center.
 * Run as a UI script. No accessibility or overlay permission is required.
 * Change state in an event callback; the render function updates the page.
 */

var count = compose.state(0);

compose.mount(function () {
    return compose.Surface({modifier: compose.modifier().fillMaxSize()},
        compose.Column({padding: 24, spacing: 12},
            compose.Text({key: 'title', text: 'Compose counter', style: 'headlineMedium'}),
            compose.Text({key: 'count', testTag: 'counter-value', text: 'Count: ' + count.value,
                style: 'titleLarge'}),
            compose.Button({key: 'increment', testTag: 'counter-increment', onClick: function () {
                count.value++;
            }}, 'Increment'),
            compose.OutlinedButton({key: 'reset', testTag: 'counter-reset', onClick: function () {
                count.value = 0;
            }}, 'Reset')));
});
