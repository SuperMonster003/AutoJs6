"ui";

declare const java: { lang: { System: { setProperty(key: string, value: string): string | null } } };
const marker = "__TSX_MARKER__";
const wideSelected = compose.state(0);
const active = compose.state(false);
const report = (key: string, value: unknown) => java.lang.System.setProperty(marker + '.' + key, String(value));
let dialog: Internal.ComposeSession | null = null;

compose.mount(() => <compose.Column padding={12} spacing={8}>
    <compose.Text testTag="tsx-v2-status" text={'Page: ' + wideSelected.value + ', selected: ' + active.value} />
    <compose.NavigationBar>
        <compose.NavigationBarItem selected={wideSelected.value === 0} icon={<compose.Icon name="Home" />}
            label={<compose.Text text="First TSX page" />} onClick={() => { wideSelected.value = 0; report('page', 0); }} />
        <compose.NavigationBarItem selected={wideSelected.value === 1} icon={<compose.Icon name="Settings" />}
            label={<compose.Text text="Second TSX page" />} onClick={() => { wideSelected.value = 1; report('page', 1); }} />
    </compose.NavigationBar>
    <compose.HorizontalPager h={80} modifier={compose.modifier().fillMaxWidth()} page={wideSelected.value}
        onPageChange={(page) => { wideSelected.value = page; report('settled', page); }}>
        <compose.Text key="first" text="TSX V2 first body" />
        <compose.Text key="second" text="TSX V2 second body" />
    </compose.HorizontalPager>
    <compose.FilterChip testTag="tsx-v2-chip" selected={active.value} label={<compose.Text text="Controlled TSX chip" />}
        onSelectedChange={function (value) {
            if (this.type !== 'FilterChip') throw new Error('Lost V2 callback receiver');
            active.value = value;
            report('selected', value);
        }} />
    <compose.Button testTag="tsx-v2-dialog" onClick={() => {
        dialog = compose.dialog(<compose.Column padding={16} spacing={8}>
            <compose.AssistChip label={<compose.Text text="V2 dialog chip" />} onClick={() => report('dialogChip', true)} />
            <compose.AndroidView view={() => ui.inflate(<text text="V2 dialog native text" />)} />
            <compose.Button onClick={() => { dialog?.close(); report('dialogClosed', true); }}>Close V2 TSX dialog</compose.Button>
        </compose.Column>, { cancelable: false });
    }}>Open V2 TSX dialog</compose.Button>
</compose.Column>);
