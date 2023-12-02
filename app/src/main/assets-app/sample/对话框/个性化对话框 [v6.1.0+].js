let dialog = dialogs.build({
    title: 'Time',
    content: null,
    positive: 'EXIT',
    neutral: 'CHANGE_BG',
    stubborn: true,
    linkify: 'webUrls',
    onBackKey: (d) => Snackbar
        .make(d.getView(), 'Please choose a button', 0)
        .setDuration(1.2e3)
        .show(),
    animation: true,
    dimAmount: 90,
    background: '#D7FFD9',
    keepScreenOn: true,
    buttonRippleColor: colors.rgba('#00bbff20'),
    contentLineSpacing: 1.9,
}).on('neutral', (d) => {
    let bg = materialColor.pick();
    let win = d.getWindow();
    ui.post(() => win.setBackgroundDrawable(new ColorDrawable(colors.toInt(bg))));
}).on('positive', (d) => {
    exitNow(d);
}).show();

let urlStr = 'Visit https://time.is/beijing for more info';
let cachedTimeStr;
let materialColor = {
    _index: 0,
    _list: [
        '#E2F1F8', '#FFFFFF', '#EFDCD5', '#FFDDC1', '#FFFFB0',
        '#FFFFB3', '#FFFFCF', '#FFFFCE', '#F8FFD7', '#D7FFD9',
        '#B2FEF7', '#B4FFFF', '#B6FFFF', '#C3FDFF', '#D1D9FF',
        '#E6CEFF', '#FFC4FF', '#FFC1E3', '#FFCCCB', '#C1D5E0',
    ],
    pick() {
        let color = this._list[this._index++];
        if (this._index === this._list.length) {
            this._index = 0;
        }
        return color;
    },
};
let exitNow = (d) => {
    d.dismiss();
    exit();
};

let getTimeStr = () => {
    let now = new Date();
    let hh = now.getHours().toString().padStart(2, '0');
    let mm = now.getMinutes().toString().padStart(2, '0');
    let ss = now.getSeconds().toString().padStart(2, '0');
    return `${hh}:${mm}:${ss}`;
};

setInterval(() => {
    let timeStr = getTimeStr();
    if (cachedTimeStr !== timeStr) {
        cachedTimeStr = timeStr;
        dialog.setContent(`${timeStr}\n${urlStr}`);
    }
}, 30);