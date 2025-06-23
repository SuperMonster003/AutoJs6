const winOnloadBak = window.onload ? window.onload.bind(window) : null;

window.onload = function () {
    if (typeof winOnloadBak === 'function') {
        winOnloadBak();
    }
    document.getElementById('testButton').addEventListener('click', () => {
        $autojs.invoke('toast-log', '按钮已被点击');
    });
};