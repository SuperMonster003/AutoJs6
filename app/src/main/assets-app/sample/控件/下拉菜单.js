'ui';

ui.layout(
    <vertical padding="16">
        <horizontal>
            <text textSize="16sp">下拉菜单</text>
            <spinner id="sp1" entries="选项 1|选项 2|选项 3"/>
        </horizontal>
        <horizontal>
            <text textSize="16sp">对话框菜单</text>
            <spinner id="sp2" entries="选项 4|选项 5|选项 6" spinnerMode="dialog"/>
        </horizontal>
        <button id="ok">确定</button>
        <button id="select3">选择选项 3</button>
    </vertical>,
);

ui.ok.on('click', () => {
    var i = ui.sp1.getSelectedItemPosition();
    var j = ui.sp2.getSelectedItemPosition();
    toast('您的选择是选项 ' + (i + 1) + ' 和选项 ' + (j + 4));
});

ui.select3.on('click', () => {
    ui.sp1.setSelection(2);
});
