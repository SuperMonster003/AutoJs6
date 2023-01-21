// noinspection BadExpressionStatementJS,SpellCheckingInspection

App.ALIPAY.ensureInstalled();

/* 相对简单的情况 */

// 使用 data 选项参数
0 && app.startActivity({
    data: 'alipays://platformapi/startapp?appId=60000002',
    packageName: App.ALIPAY.getPackageName(),
});

// 使用 url 选项参数
1 && app.startActivity({
    url: {
        src: 'alipays://platformapi/startapp',
        query: { appId: '60000002' },
    },
    packageName: App.ALIPAY.getPackageName(),
});

/* 相对复杂的情况 */

// 使用 data 选项参数
0 && app.startActivity({
    data: 'alipays://platformapi/startapp?appId=20000067&url=https://60000002.h5app.alipay.com/www/listRank.html?conf=%255B%2522totalRank%2522%255D&__webview_options__=&transparentTitle=none&backgroundColor=-1&canPullDown=NO&backBehavior=back&enableCubeView=NO&startMultApp=YES&showOptionMenu=YES&enableScrollBar=NO&closeCurrentWindow=YES&readTitle=NO&defaultTitle=Reserved',
    packageName: App.ALIPAY.getPackageName(),
});

// 使用 url 选项参数
0 && app.startActivity({
    url: {
        src: 'alipays://platformapi/startapp',
        query: {
            appId: 20000067,
            url: {
                src: 'https://60000002.h5app.alipay.com/www/listRank.html',
                query: { conf: '["totalRank"]' },
            },
            __webview_options__: {
                transparentTitle: 'none',
                backgroundColor: -1,
                canPullDown: 'NO',
                backBehavior: 'back',
                enableCubeView: 'NO',
                startMultApp: 'YES',
                showOptionMenu: 'YES',
                enableScrollBar: 'NO',
                closeCurrentWindow: 'YES',
                readTitle: 'NO',
                defaultTitle: 'Reserved',
            },
        },
    },
    packageName: App.ALIPAY.getPackageName(),
});