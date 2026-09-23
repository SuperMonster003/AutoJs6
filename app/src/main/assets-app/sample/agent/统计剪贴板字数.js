/**
 * @agent
 * @description 统计当前剪贴板文本的 Unicode 字符数与非空白字符数, 不返回剪贴板内容
 * @risk readonly
 * @confirm never
 * @timeout 10000
 * @example 请统计剪贴板字数
 * @tag clipboard
 */
// Android 可能要求宿主处于前台才能读取剪贴板.
let text = String(getClip() || '');
let content = new java.lang.String(text);
let compact = new java.lang.String(text.replace(/\s/g, ''));
let result = {
    characters: content.codePointCount(0, content.length()),
    nonWhitespaceCharacters: compact.codePointCount(0, compact.length()),
};
ai.agent.result(result);
console.log(JSON.stringify(result));
