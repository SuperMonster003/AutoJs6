'ui';

ui.layout(
    <vertical>
        <webview id="web" url="index.html" w="*" h="*"/>
    </vertical>,
);

let web = ui['web'];

web.jsBridge.handle('toast-log', (event, msg) => {
    toastLog(msg);
});