'ui';

/*
 * Requires a matching AutoJs6 6.8.0 local build (versionCode >= 5316) and
 * Compose UI 1.0.0 installed and enabled in the plugin center.
 * Run as a UI script. No accessibility or overlay permission is required.
 * Dynamic wallpaper colors are available on Android 12+; older versions use
 * the seed palette. Turn dynamic color off to compare the three seed colors.
 */

var seeds = ['#6750A4', '#006C4C', '#984061'];
var seedIndex = 0;
var useDark = false;
var useDynamic = false;
compose.theme({seed: seeds[seedIndex], dark: useDark, dynamicColor: useDynamic});

var summary = compose.Text({testTag: 'theme-summary', text: '', style: 'bodyLarge'});
function applyTheme() {
    compose.theme({seed: seeds[seedIndex], dark: useDark, dynamicColor: useDynamic});
    summary.text = 'Seed: ' + seeds[seedIndex] + ' | Dark: ' + useDark + ' | Dynamic: ' + useDynamic;
}
var darkSwitch = compose.Switch({testTag: 'theme-dark', checked: useDark,
    contentDescription: 'Dark theme', onCheckedChange: function (checked) {
        useDark = checked;
        darkSwitch.checked = checked;
        applyTheme();
    }});
var dynamicSwitch = compose.Switch({testTag: 'theme-dynamic', checked: useDynamic,
    contentDescription: 'Dynamic wallpaper colors', onCheckedChange: function (checked) {
        useDynamic = checked;
        dynamicSwitch.checked = checked;
        applyTheme();
    }});

compose.mount(compose.Surface({testTag: 'theme-surface', modifier: compose.modifier().fillMaxSize()},
    compose.Column({padding: 20, spacing: 12},
        compose.Text({text: 'Compose theme', style: 'headlineMedium'}),
        summary,
        compose.Button({testTag: 'theme-seed', onClick: function () {
            seedIndex = (seedIndex + 1) % seeds.length;
            applyTheme();
        }}, 'Next seed'),
        compose.Row({alignment: 'center', spacing: 12}, compose.Text({text: 'Dark theme'}), darkSwitch),
        compose.Row({alignment: 'center', spacing: 12}, compose.Text({text: 'Dynamic colors'}), dynamicSwitch),
        compose.Text({text: 'Dynamic colors use the wallpaper on Android 12+. Earlier versions keep the seed palette.'}),
        compose.FilledTonalButton({}, 'Palette preview'))));
applyTheme();
