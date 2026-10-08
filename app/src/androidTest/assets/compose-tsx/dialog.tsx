"ui";

declare const java: { lang: { System: { setProperty(key: string, value: string): string | null } } };
const marker = "__TSX_MARKER__";
const value = compose.state(0);
const session = compose.dialog(() => <compose.Column padding={16} spacing={8}>
    <compose.Text testTag="tsx-dialog-count" text={'TSX dialog count: ' + value.value} />
    <compose.Button testTag="tsx-dialog-increment" onClick={() => {
        value.value++;
        java.lang.System.setProperty(marker + '.count', String(value.value));
    }}>Increment TSX dialog</compose.Button>
    <compose.Button testTag="tsx-dialog-close" onClick={() => session.close()}>Close TSX dialog</compose.Button>
</compose.Column>, { type: 'bottomSheet', cancelable: false, theme: { dark: false } });
session.on('close', function () {
    if (this !== session) throw new Error('Dialog close lost the normal session receiver');
    java.lang.System.setProperty(marker + '.closed', 'true');
});
