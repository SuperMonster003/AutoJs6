'ui';

ui.useAndroidLayout();

ui.layout(
    <ScrollView
        xmlns:android="http://schemas.android.com/apk/res/android"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:fillViewport="true"
        android:background="#F4F7FB">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="24dp">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="系统状态面板"
                android:textColor="#172033"
                android:textSize="26sp"
                android:textStyle="bold"/>

            <TextClock
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:format24Hour="HH:mm:ss"
                android:textColor="#536078"
                android:textSize="18sp"/>

            <Switch
                android:id="@+id/automatic"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="32dp"
                android:checked="true"
                android:text="自动优化"/>

            <TextView
                android:id="@+id/level_text"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="24dp"
                android:text="优化强度: 40%"
                android:textColor="#172033"
                android:textSize="16sp"/>

            <SeekBar
                android:id="@+id/level"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:max="100"
                android:progress="40"/>

            <ProgressBar
                android:id="@+id/progress"
                style="?android:attr/progressBarStyleHorizontal"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="24dp"
                android:indeterminate="false"
                android:max="100"
                android:progress="40"/>

            <Button
                android:id="@+id/apply"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="24dp"
                android:text="应用设置"
                android:textAllCaps="false"/>

            <TextView
                android:id="@+id/status"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="16dp"
                android:gravity="center"
                android:text="设置尚未应用"
                android:textColor="#536078"
                android:textSize="14sp"/>
        </LinearLayout>
    </ScrollView>,
);

ui.statusBarColor('#F4F7FB');
ui.statusBarIconLight(true);

ui['level'].setOnSeekBarChangeListener({
    onProgressChanged(seekBar, progress) {
        ui['level_text'].setText(`优化强度: ${progress}%`);
        ui['progress'].setProgress(progress);
    },
    onStartTrackingTouch() {
        /* Empty body. */
    },
    onStopTrackingTouch() {
        /* Empty body. */
    },
});

ui['apply'].on('click', () => {
    const mode = ui['automatic'].isChecked() ? '自动' : '手动';
    const progress = ui['level'].getProgress();
    ui['status'].setText(`已应用: ${mode}模式, 强度 ${progress}%.`);
});
