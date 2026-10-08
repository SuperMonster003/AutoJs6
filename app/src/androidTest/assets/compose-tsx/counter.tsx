"ui";

declare const java: { lang: { System: { setProperty(key: string, value: string): string | null } } };
const marker = "__TSX_MARKER__";
const count = compose.state(0);
const button = compose.ref<Internal.ComposeNode<'Button'>>();
const Increment = compose.Button;
let firstId: number | undefined;

compose.mount(() => (
    <>
        <compose:Column padding={16} spacing={8}>
            <compose.TopAppBar title={<><compose:Text text="TSX slot title" /></>} />
            <>
                <compose.Text key="count" testTag="tsx-count" text={'Count: ' + count.value} />
                <compose:Text key="namespace" testTag="tsx-namespace" text="Qualified TSX" />
            </>
            <Increment key="button" testTag="tsx-increment" ref={button} onClick={function () {
                if (this !== button.current) throw new Error('TSX callback this lost its published ref');
                if (firstId === undefined) firstId = this.nodeId;
                if (this.nodeId !== firstId) throw new Error('TSX key lost its mounted identity');
                count.value++;
                java.lang.System.setProperty(marker + '.count', String(count.value));
                java.lang.System.setProperty(marker + '.ref', String(this === button.current));
                java.lang.System.setProperty(marker + '.node', String(this.nodeId));
            }}>Increment TSX</Increment>
        </compose:Column>
    </>
));
