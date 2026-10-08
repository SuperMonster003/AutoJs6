"ui";

declare const java: { lang: { System: { setProperty(key: string, value: string): string | null } } };
const marker = "__TSX_MARKER__";
type Row = { id: string; title: string };
const rows: Row[] = [];
for (let index = 0; index < 50; index++) rows.push({ id: 'row-' + index, title: 'TSX memo row ' + index });
const items = compose.state(rows);
const clicks = compose.state(0);
let builds = 0;

compose.mount(() => {
    const children = items.value.map(item => compose.memo(() => {
        builds++;
        java.lang.System.setProperty(marker + '.builds', String(builds));
        return <compose.Text key={item.id} testTag={'tsx-memo-' + item.id} text={item.title} />;
    }, [item.id, item.title], { key: item.id }));
    return (
        <compose.Column padding={16} spacing={8}>
            <compose.Text key="clicks" testTag="tsx-memo-clicks" text={'TSX memo clicks: ' + clicks.value} />
            <compose.Button key="bump" testTag="tsx-memo-bump" onClick={() => {
                clicks.value++;
            }}>Bump</compose.Button>
            <compose.Button key="edit" testTag="tsx-memo-edit" onClick={() => {
                const next = items.value.slice();
                next[0] = { id: next[0].id, title: 'TSX memo edited' };
                items.value = next;
            }}>Edit</compose.Button>
            <compose.LazyColumn key="list" weight={1}>{children}</compose.LazyColumn>
        </compose.Column>
    );
});
