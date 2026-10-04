"ui";

declare const java: { lang: { System: { setProperty(key: string, value: string): string | null } } };
const marker = "__TSX_MARKER__";
let unauthorizedCalls = 0;
const Unauthorized = (_props: unknown) => {
    unauthorizedCalls++;
    return <compose.Text text="Unregistered function must not execute" />;
};

const session = compose.mount(
    <>
        <compose.Column padding={16} spacing={8}>
            <compose.Text testTag="tsx-fragment-page" text="Fragment page retained" />
            <compose.OutlinedTextField text="One slot" label={<><compose:Text text="Single slot fragment" /></>} />
            <compose.OutlinedTextField text="Empty slot" label={<></>} />
        </compose.Column>
    </>,
);

function reject(name: string, action: () => void): void {
    try {
        action();
        throw new Error(name + ' unexpectedly succeeded');
    } catch (error) {
        if (!(error instanceof compose.ComposeError)) throw error;
        if (error.code !== 'INVALID_ARGUMENT') throw error;
        java.lang.System.setProperty(marker + '.' + name, error.code);
    }
    if (session.isClosed() || compose.sessions.length !== 1) throw new Error('Failed TSX input replaced the live session');
}

reject('roots', () => {
    compose.mount(<><compose.Text text="First root" /><compose.Text text="Second root" /></>);
});
reject('slots', () => {
    const invalid = <compose.TopAppBar title={<><compose.Text text="First title" /><compose.Text text="Second title" /></>} />;
    void invalid;
});
reject('function', () => {
    const invalid = <Unauthorized />;
    void invalid;
});
java.lang.System.setProperty(marker + '.calls', String(unauthorizedCalls));
java.lang.System.setProperty(marker + '.ready', 'true');
