'ui';

( /* @IIFE(registerIconView) */ () => {
    // 继承 ui.Widget
    util.extend(IconView, ui.Widget);

    /**
     * @constructor
     * @extends Internal.UI.Widget
     */
    function IconView() {
        // 调用父类构造函数
        ui.Widget.call(this);
        // 自定义属性 packageName
        this.defineAttr('packageName', () => {
            return this._icon || null;
        }, (view, name, value) => {
            this._icon = iconMap[value /* as packageName */];
            view.setImageDrawable(this._icon);
        });
    }

    IconView.prototype.render = function () {
        return '<img/>';
    };
    ui.registerWidget('iconLoader', IconView);
})();

ui.layout(
    <vertical bg="#ffffff">
        <list id="apps" layout_weight="1">
            <linear bg="?selectableItemBackground" w="*" gravity="center_vertical" marginRight="16">
                <iconLoader packageName="{{this.packageName}}" w="50" h="70" margin="16"/>
                <vertical>
                    <text id="name" textSize="16sp" textColor="#000000" text="{{this.appName}}" maxLines="1" ellipsize="middle"/>
                    <text id="path" textSize="13sp" textColor="#929292" text="{{this.packageName}}" maxLines="2" marginTop="3"/>
                </vertical>
            </linear>
        </list>
        <progressbar id="progressbar" indeterminate="true" style="@style/Base.Widget.AppCompat.ProgressBar.Horizontal"/>
    </vertical>,
);

let appList = [];
let iconMap = {};

ui.apps.setDataSource(appList);

ui.apps.on('item_click', item => {
    new com.afollestad.materialdialogs.MaterialDialog.Builder(context).limitIconToDefaultSize()
    dialogs.build({
        title: '应用信息',
        icon: item.icon,
        content: `名称: ${item.appName}\n`
            + `包名: ${item.packageName}\n`
            + `版本: ${item.versionName}\n`
            + `版本号: ${item.versionCode}`,
        positive: '返回',
        limitIconToDefaultSize: true,
    }).show();
});

// 启动线程扫描应用程序
threads.start(function () {
    listApps(appList);
    ui.run(() => ui.progressbar.setVisibility(android.view.View.GONE));
});

function listApps(appList) {
    let pm = context.getPackageManager();
    let appPackageList = pm.getInstalledPackages(0);
    for (let i = 0; i < appPackageList.size(); i++) {
        let p = appPackageList.get(i);
        let icon = p.applicationInfo.loadIcon(pm);
        appList.push({
            appName: p.applicationInfo.loadLabel(pm).toString(),
            packageName: p.packageName,
            versionName: p.versionName,
            versionCode: p.versionCode,
            icon,
        });
        iconMap[p.packageName] = icon;
    }
}