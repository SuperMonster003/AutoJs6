let MAX_LENGTH_TO_PRINT = 3000;

let url = 'https://www.npmjs.com';
let res = http.get(url);
if (res.statusCode !== 200) {
    toast(`请求失败: ${res.statusMessage}`);
    exit();
}
let str = res.body.string();
toastLog(`请求响应体字符长度: ${str.length}`);
console.log(str.length > MAX_LENGTH_TO_PRINT
    ? str.slice(0, MAX_LENGTH_TO_PRINT) + '\n\n... ...'
    : str);
console.show();
