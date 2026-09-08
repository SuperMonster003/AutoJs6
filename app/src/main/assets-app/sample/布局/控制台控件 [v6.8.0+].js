'ui';

/* <console> 显示当前脚本自身的控制台, <globalconsole> 显示所有脚本共用的全局控制台. */
/* <globalconsole> 等价于 <console global="true"> 或 <console global>. */
/* 属性与 console.setXxx 一一对应, 如 titleBackgroundColor 对应 console.setTitleBackgroundColor. */

ui.layout(
    <vertical>
        <text text="脚本控制台 (console)" textSize="14sp" padding="12 8 12 4"/>
        <console id="rt" h="0" layout_weight="1"
                 title="运行时控制台" titleBackgroundColor="#3F51B5"
                 inputVisible="true"/>
        <text text="全局控制台 (globalconsole)" textSize="14sp" padding="12 8 12 4"/>
        <globalconsole id="gl" h="0" layout_weight="1"
                       timeVisible="true" timeFormat="HH:mm:ss"
                       colorful="false" textSize="12"/>
    </vertical>,
);

console.log('这一行会同时出现在两个控制台中');
console.info('info 级别');
console.warn('warn 级别');
console.error('error 级别 (下方全局控制台已关闭着色)');

/* console.rawInput 会阻塞线程, 因此在子线程中读取输入. */
threads.start(() => {
    while (true) {
        let line = console.rawInput('请在上方控制台的输入栏输入内容:');
        console.log('收到: %s', line);
    }
});
