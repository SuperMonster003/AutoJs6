"ui";

declare const java: { lang: { System: { setProperty(key: string, value: string): string | null } } };
const marker = "__TSX_MARKER__";
const phase = compose.state(0);

compose.mount(function renderTsxMixedBoundary() {
    if (phase.value === 1) {
        try {
            return <compose.Column><text text="XML cannot enter Compose" /></compose.Column>;
        } catch (error) {
            if (!(error instanceof compose.ComposeError)) throw error;
            java.lang.System.setProperty(marker + '.mixed', error.code);
            throw error;
        }
    }
    return (
        <compose.Column padding={16} spacing={8}>
            <compose.Text key="value" testTag="tsx-retained-count" text={'Phase: ' + phase.value} />
            <compose.Button key="advance" testTag="tsx-advance" onClick={() => {
                phase.value++;
                java.lang.System.setProperty(marker + '.phase', String(phase.value));
            }}>Advance TSX</compose.Button>
        </compose.Column>
    );
});
