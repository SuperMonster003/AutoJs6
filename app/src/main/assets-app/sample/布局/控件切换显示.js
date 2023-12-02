'ui';

ui.layout(<vertical>
    <viewswitcher id="vs">
        <text size="52" gravity="center" margin="20" color="dark-green">HELLO</text>
        <text size="52" gravity="center" margin="20" color="dark-red">WORLD</text>
    </viewswitcher>
    <button id="btn" text="switch view"/>
</vertical>);

/** @type {JsButton} */
let btnView = ui['btn'];
/** @type {JsViewSwitcher} */
let viewSwitcher = ui['vs'];

btnView.on('click', () => viewSwitcher.showNext());