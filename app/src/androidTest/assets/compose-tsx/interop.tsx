"ui";

declare const java: { lang: { System: { setProperty(key: string, value: string): string | null } } };
const marker = "__TSX_MARKER__";
const count = compose.state(0);
let factoryCalls = 0;

ui.layout(<vertical padding={12}>
    <text text="F2 legacy TSX header" />
    <compose id="tsx_interop_slot" w="*" h={280} />
</vertical>);
const slot = ui.findView('tsx_interop_slot');
if (slot === null) throw new Error('XML compose widget was not indexed');
const native = <compose.AndroidView view={(context) => {
    if (!ui.isUiThread() || context === null) throw new Error('Native factory did not run on main with Context');
    factoryCalls++;
    java.lang.System.setProperty(marker + '.factories', String(factoryCalls));
    return ui.inflate(<button text="F2 inflated TSX native button" textAllCaps={false} onClick={() => {
        for (const invalid of [<compose.Text text="not XML" />, <><compose.Text text="not an XML fragment" /></>]) {
            try {
                ui.inflate(invalid);
                throw new Error('ui.inflate accepted a Compose handle');
            } catch (error) {
                if (!(error instanceof compose.ComposeError) || error.code !== 'INVALID_ARGUMENT') throw error;
            }
        }
        count.value++;
        java.lang.System.setProperty(marker + '.clicks', String(count.value));
    }} />);
}} />;

compose.attach(slot, () => <compose.Column spacing={8}>
    <compose.Text text={'F2 TSX count: ' + count.value} testTag="tsx-interop-count" />
    {native}
</compose.Column>);
