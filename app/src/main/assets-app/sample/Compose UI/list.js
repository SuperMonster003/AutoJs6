'ui';

/*
 * Requires a matching AutoJs6 6.8.0 local build (versionCode >= 5316) and
 * Compose UI 1.0.0 installed and enabled in the plugin center.
 * Run as a UI script. No accessibility or overlay permission is required.
 * Stable keys identify all 1000 items across reordering. Only visible items
 * are composed. A ref exposes the current list handle after a frame is accepted.
 */

var reversed = compose.state(false);
var listRef = compose.ref();

compose.mount(function () {
    var rows = [];
    for (var index = 0; index < 1000; index++) {
        var value = reversed.value ? 999 - index : index;
        rows.push(compose.Text({key: 'item-' + value, testTag: 'list-item-' + value,
            text: 'Item ' + value, modifier: compose.modifier().fillMaxWidth().height(48).padding(12)}));
    }
    return compose.Surface({modifier: compose.modifier().fillMaxSize()},
        compose.Column({padding: 12, spacing: 8, modifier: compose.modifier().fillMaxSize()},
            compose.Text({key: 'title', text: '1000 keyed items', style: 'headlineSmall'}),
            compose.Text({key: 'order', testTag: 'list-order',
                text: reversed.value ? 'Order: reverse' : 'Order: forward'}),
            compose.Row({key: 'controls', spacing: 4},
                compose.Button({key: 'jump', testTag: 'list-jump', weight: 1, onClick: function () {
                    if (listRef.current) listRef.current.scrollTo(reversed.value ? 99 : 900);
                }}, 'Item 900'),
                compose.OutlinedButton({key: 'reverse', testTag: 'list-reverse', weight: 1, onClick: function () {
                    reversed.value = !reversed.value;
                }}, 'Reverse'),
                compose.OutlinedButton({key: 'top', testTag: 'list-top', weight: 1, onClick: function () {
                    if (listRef.current) listRef.current.scrollTo(0);
                }}, 'Top')),
            compose.LazyColumn({key: 'list', ref: listRef, testTag: 'list-items',
                modifier: compose.modifier().weight(1).fillMaxWidth()}, rows)));
});
