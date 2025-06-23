'ui';

const themeColor = Color(autojs.themeColor);
const colorRelativeRate = 1.8;

ui.layout(
    <org.autojs.autojs.core.ui.widget.JsTextClock
        xmlns:android="http://schemas.android.com/apk/res/android"
        android:id="@+id/text_clock"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:format24Hour="HH:mm"
        android:textSize="36sp"/>,
);

/* 1 秒钟后应用自定义样式. */
setTimeout(() => {
    ui.statusBarColor(themeColor);
    ui.backgroundColor(
        Color(themeColor)
            .setRedRelative(colorRelativeRate)
            .setGreenRelative(colorRelativeRate)
            .setBlueRelative(colorRelativeRate)
            .toInt(),
    );

    /** @type {JsTextClock} */
    let textClockView = ui.text_clock;

    textClockView.attr('color', themeColor.toInt());
    textClockView.attr('size', '56');
    textClockView.attr('layout_gravity', 'center');
    textClockView.attr('format24Hour', 'HH:mm:ss');
}, 1e3);