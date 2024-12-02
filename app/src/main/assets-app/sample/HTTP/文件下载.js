let url = 'https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png';
let res = http.get(url);
if (res.statusCode !== 200) {
    toast('请求失败');
    exit();
}
let path = './github-mark.png';
files.writeBytes(path, res.body.bytes());
toast(`下载成功: ${path}`);
app.viewFile(path);