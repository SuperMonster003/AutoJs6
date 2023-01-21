App.WECHAT.ensureInstalled();

let content = rawInput('请输入要分享的文本');

content && app.startActivity({
    action: 'android.intent.action.SEND',
    type: 'text/*',
    extras: { 'android.intent.extra.TEXT': content },
    packageName: App.WECHAT.getPackageName(),
    className: '@{packageName}.ui.tools.ShareImgUI',
});
