declare const java: { lang: { System: { setProperty(key: string, value: string): string | null } } };
const marker = "__TSX_MARKER__";

try {
    compose.mount(<><compose.Text text="No Activity" /></>);
    throw new Error('Non-UI Compose TSX mount unexpectedly succeeded');
} catch (error) {
    if (!(error instanceof compose.ComposeError)) throw error;
    if (error.code !== 'UI_MODE_REQUIRED') throw error;
    java.lang.System.setProperty(marker + '.error', error.code);
}
