'ui';

ui.useAndroidLayout();

ui.layout(
    <LinearLayout
        xmlns:android="http://schemas.android.com/apk/res/android"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:gravity="center_horizontal"
        android:padding="24dp"
        android:background="#FFF8F3">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Android 原生布局"
            android:textColor="#2B211D"
            android:textSize="28sp"
            android:textStyle="bold"/>

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="这个界面只使用标准 Android 控件和 android:* 属性"
            android:textColor="#75645C"
            android:textSize="14sp"/>

        <EditText
            android:id="@+id/name"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="32dp"
            android:hint="请输入名字"
            android:inputType="textPersonName"
            android:maxLines="1"/>

        <Button
            android:id="@+id/greet"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:text="打个招呼"
            android:textAllCaps="false"/>

        <TextView
            android:id="@+id/result"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:text="等待输入"
            android:textColor="#D85B32"
            android:textSize="18sp"/>
    </LinearLayout>,
);

ui.statusBarColor('#FFF8F3');
ui.statusBarIconLight(true);

ui['greet'].on('click', () => {
    const name = String(ui['name'].getText()).trim();
    ui['result'].setText(name ? `你好, ${name}!` : '请先输入名字.');
});
