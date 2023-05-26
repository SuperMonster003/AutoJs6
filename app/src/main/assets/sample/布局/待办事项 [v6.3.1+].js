'ui';

let themeColor = 'orange-800';

let materialColors = [
    'amber-500',
    'blue-500',
    'blue-grey-500',
    'brown-500',
    'cyan-500',
    'deep-orange-500',
    'deep-purple-500',
    'green-500',
    'grey-500',
    'indigo-500',
    'light-blue-500',
    'light-green-500',
    'lime-500',
    'orange-500',
    'pink-500',
    'purple-500',
    'red-500',
    'teal-500',
    'yellow-500',
];

let storage = storages.create('todoList');

storage.clear();

ui.layout(
    <frame>
        <vertical>
            <appbar>
                <toolbar id="toolbar" title="TODO" />
            </appbar>
            <button id="selectAll" text="全部完成" isColored margin="6 4" />
            <list id="todoList">
                <card w="*" h="70" margin="10 5" cardCornerRadius="2dp"
                      cardElevation="1dp" foreground="?selectableItemBackground">
                    <horizontal gravity="center_vertical">
                        <view bg="{{this.color}}" h="*" w="10" />
                        <vertical padding="10 8" h="auto" w="0" layout_weight="1">
                            <text id="title" text="{{this.title}}" textColor="blue-grey-900" textSize="16sp" maxLines="1" paddingBottom="4dp" />
                            <text text="{{this.summary}}" textColor="blue-grey-300" textSize="14sp" maxLines="1" />
                        </vertical>
                        <checkbox id="done" marginLeft="4" marginRight="6" checked="{{this.done}}" tint="blue-grey-500,pink-300" />
                    </horizontal>
                </card>
            </list>
        </vertical>
        <fab id="add" w="auto" h="auto" src="@drawable/ic_add_black_48dp"
             margin="16" layout_gravity="bottom|right" tint="white" backgroundTint="orange-800" />
    </frame>,
);

// 从 storage 获取待办事项列表
let todoList = storage.get('items', [{
    title: '写操作系统作业',
    summary: '明天第 1 - 2 节',
    color: 'red-500',
    done: false,
}, {
    title: '给 ui 模式增加若干 bug',
    summary: '无限期',
    color: 'orange-500',
    done: false,
}, {
    title: '发布 AutoJs6 v6.6.6',
    summary: '2066 年 6 月',
    color: 'teal-500',
    done: false,
}, {
    title: '完成 AutoJs6 文档撰写',
    summary: '2031 年 5 月',
    color: '#2196f3',
    done: false,
}]);

ui.statusBarColor(themeColor);
ui['toolbar'].attr('bg', themeColor);
ui['selectAll'].attr('backgroundTint', 'pink-300');

ui['todoList'].setDataSource(todoList);

ui['selectAll'].on('click', function () {
    todoList.forEach(item => {
        item.done = true;
    });
    // 通知数据全部更新
    ui['todoList'].adapter.notifyDataSetChanged();
});

ui['todoList'].on('item_bind', function (itemView, itemHolder) {
    // 绑定勾选框事件
    itemView.done.on('check', function (checked) {
        let item = itemHolder.item;
        item.done = checked;
        let paint = itemView.title.paint;
        // 设置或取消中划线效果
        if (checked) {
            paint.flags |= Paint.STRIKE_THRU_TEXT_FLAG;
        } else {
            paint.flags &= ~Paint.STRIKE_THRU_TEXT_FLAG;
        }
        itemView.title.invalidate();
    });
});

ui['todoList'].on('item_click', function (item, i, itemView) {
    itemView.done.checked = !itemView.done.checked;
});

ui['todoList'].on('item_long_click', function (e, item, i) {
    confirm(`确定要删除 "${item.title}" 吗?`)
        .then(ok => {
            if (ok) {
                todoList.splice(i, 1);
            }
        });
    e.consumed = true;
});

// 当离开本界面时保存 todoList
ui.emitter.on('pause', () => storage.put('items', todoList));

ui['add'].on('click', () => {
    dialogs.rawInput('请输入标题')
        .then((title) => {
            if (!title) {
                return;
            }
            dialogs.rawInput('请输入期限', '明天')
                .then(summary => {
                    todoList.push({
                        title: title,
                        summary: summary,
                        color: materialColors[random(0, materialColors.length - 1)],
                    });
                });
        });
});