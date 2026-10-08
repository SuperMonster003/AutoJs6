declare const java: { lang: { System: { setProperty(key: string, value: string): string | null } } };
const marker = "__TSX_MARKER__";
const count = compose.state(0);
const floatingWindow = compose.floaty(() => (
    <>
        <compose:Column padding={12} spacing={8}>
            <compose.Text key="label" testTag="tsx-floaty-count" text={'Floaty: ' + count.value} />
            <compose.Button key="increment" testTag="tsx-floaty-increment" onClick={() => {
                count.value++;
                java.lang.System.setProperty(marker + '.count', String(count.value));
            }}>Increment floating TSX</compose.Button>
            <compose:Button key="close" testTag="tsx-floaty-close" onClick={() => floatingWindow.close()}>Close floating TSX</compose:Button>
        </compose:Column>
    </>
), { width: Math.min(720, device.width - 32), height: -2, x: 16, y: 80 });
floatingWindow.on('close', () => java.lang.System.setProperty(marker + '.closed', 'true'));
