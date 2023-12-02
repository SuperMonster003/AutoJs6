// noinspection HtmlUnknownAttribute

let win = floaty.window(
    <vertical bg="white" padding="24">
        <input id="input" hint="请输入任意内容" textSize="16sp" margin="0 12"/>
        <view h="1" w="180" layout_weight="1"/>
        <button id="ok" text="确定" bgTint="light-blue-600" color="white" layout_gravity="bottom"/>
    </vertical>,
);

win.exitOnClose();

/** @type {JsButton} */
let buttonView = win['ok'];

/** @type {JsEditText} */
let inputView = win['input'];

toast(`长按 "${buttonView.attr('text')}" 键可调整浮动窗口样式`, 'long');

inputView.on('click', () => {
    win.requestFocus();
});

inputView.on('key_up', function (keyCode, event) {
    if (keyCode === keys.back) {
        win.disableFocus();
        event.consumed = true;
    }
});

buttonView.on('click', () => {
    let text = inputView.text();
    text
        ? toast('输入内容: ' + text)
        : toast('未输入任何内容');
    win.disableFocus();
});

buttonView.on('long_click', (event) => {
    toast.dismissAll();
    win.setAdjustEnabled(!win.isAdjustEnabled());
    event.consumed = true;
});

setInterval(() => null, 1000);