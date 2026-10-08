"ui";

declare const java: { lang: { System: { setProperty(key: string, value: string): string | null } } };
const marker = "__TSX_MARKER__";
let clicks = 0;

function rejectMixedLayout(name: string, action: () => void): void {
    try {
        action();
        throw new Error(name + ' unexpectedly replaced the XML page');
    } catch (error) {
        if (!(error instanceof compose.ComposeError)) throw error;
        if (error.code !== 'INVALID_ARGUMENT') throw error;
        java.lang.System.setProperty(marker + '.mixed', error.code);
    }
}

ui.layout(
    <>
        <vertical padding={16}>
            <>
                <text id="tsx_legacy_status" text="Legacy TSX ready" />
                <button text="Legacy TSX click" onClick={() => {
                    rejectMixedLayout('nested Compose', () => {
                        ui.layout(<vertical><compose.Text text="Compose cannot enter XML" /></vertical>);
                    });
                    rejectMixedLayout('direct Compose node', () => {
                        ui.layout(<compose.Text text="Compose node is not an XML layout" />);
                    });
                    rejectMixedLayout('single Compose fragment', () => {
                        ui.layout(<><compose.Text text="Compose fragment is not an XML layout" /></>);
                    });
                    clicks++;
                    const status = ui.findView('tsx_legacy_status');
                    if (status === null) throw new Error('Mixed XML rejection removed the previous page');
                    status.setText('Legacy clicks: ' + clicks);
                    java.lang.System.setProperty(marker + '.clicks', String(clicks));
                }} />
            </>
        </vertical>
    </>
);
