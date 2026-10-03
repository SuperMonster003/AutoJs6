'ui';

/*
 * Requires a matching AutoJs6 6.8.0 local build (versionCode >= 5316) and
 * Compose UI 1.0.0 installed and enabled in the plugin center.
 * Run as a UI script with a system keyboard. No overlay permission is required.
 * This example keeps node handles and updates them without a render function.
 */

var nameField = compose.TextField({testTag: 'form-name', text: '', singleLine: true,
    imeAction: 'done', modifier: compose.modifier().fillMaxWidth()});
var previousNameText = nameField.text;
nameField.slot('label', compose.Text({text: 'Name'}));
nameField.on('valueChange', function (text) {
    // User edits already update the handle's text; there is no need to echo it.
    // Selection or IME composition can change without new text, including on blur.
    // Keep the validation or saved result until the user actually changes the name.
    if (text === previousNameText) return;
    previousNameText = text;
    nameField.isError = false;
    result.text = text.trim() ? 'Ready to save' : 'Enter your name';
});

var notifications = compose.Switch({testTag: 'form-notifications', checked: false,
    contentDescription: 'Enable notifications', onCheckedChange: function (checked) {
        notifications.checked = checked;
    }});
var levelLabel = compose.Text({testTag: 'form-level-label', text: 'Level: 50'});
var level = compose.Slider({testTag: 'form-level', value: 50, range: [0, 100], steps: 3,
    contentDescription: 'Notification level', onValueChange: function (value) {
        level.value = value;
        levelLabel.text = 'Level: ' + Math.round(value);
    }});
var result = compose.Text({testTag: 'form-result', text: 'Not saved'});
var save = compose.Button({testTag: 'form-save', onClick: function () {
    var name = nameField.text.trim();
    if (!name) {
        nameField.isError = true;
        result.text = 'Enter your name';
        nameField.focus();
        return;
    }
    nameField.blur();
    result.text = 'Saved: ' + name + ' | Notifications: ' +
        (notifications.checked ? 'on' : 'off') + ' | Level: ' + Math.round(level.value);
}}, 'Save');

compose.mount(compose.Surface({modifier: compose.modifier().fillMaxSize()},
    compose.Column({spacing: 12, modifier: compose.modifier().fillMaxSize().verticalScroll().padding(20)},
        compose.Text({text: 'Compose form', style: 'headlineMedium'}),
        nameField, result,
        compose.Row({alignment: 'center', spacing: 12},
            compose.Text({text: 'Notifications'}), notifications),
        levelLabel, level, save)));
