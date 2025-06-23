'ui';

const themeColor = Color(autojs.themeColor);

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
    ui.keepScreenOn();

    ui.backgroundColor(themeColor);

    ui.statusBarColor(themeColor);
    ui.statusBarIconLightBy(themeColor);

    ui.navigationBarColor(themeColor);
    ui.navigationBarIconLightBy(themeColor);

    /** @type {JsTextClock} */
    let textClockView = ui['text_clock'];

    textClockView.attr('color', ColorUtils.adjustThemeColorForContrast(themeColor.toInt()));
    textClockView.attr('size', '64');
    textClockView.attr('layout_gravity', 'center');
    textClockView.attr('format24Hour', 'HH:mm:ss');

    let textSizeSp = DisplayUtils.pxToSp(textClockView.getTextSize());
    let detector = new ScaleGestureDetector(context,
        new JavaAdapter(ScaleGestureDetector.SimpleOnScaleGestureListener, {
            onScale(det) {
                textSizeSp *= det.getScaleFactor();
                textSizeSp = Math.max(8, Math.min(textSizeSp, 512));
                textClockView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
                return true;
            },
        }),
    );
    ui.root.setOnTouchListener({
        onTouch(v, me) {
            return detector.onTouchEvent(me);
        },
    });
}, 1e3);