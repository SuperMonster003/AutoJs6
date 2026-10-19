package android.graphics;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * JVM fake of the Android framework class for local unit tests (see the note on the {@code android.util.Base64}
 * fake): the mockable android.jar refuses even the constructor, so code under test that builds a point could not
 * run. Mirrors the public API of the framework class; the {@link Parcelable} methods delegate to the mockable
 * {@link Parcel} and fail there as before.
 * <p>
 * zh-CN: Android 框架类的 JVM fake, 供本地单元测试使用 (参见 android.util.Base64 fake 的说明): mockable android.jar
 * 连构造函数都拒绝, 构造点对象的被测代码无法运行. 公开 API 与框架类一致, Parcelable 方法委托给 mockable Parcel, 照旧在那里失败.
 */
@SuppressWarnings("unused")
public class Point implements Parcelable {

    public int x;
    public int y;

    public Point() {
        /* Empty body. */
    }

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public Point(Point src) {
        this.x = src.x;
        this.y = src.y;
    }

    public void set(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public final void negate() {
        x = -x;
        y = -y;
    }

    public final void offset(int dx, int dy) {
        x += dx;
        y += dy;
    }

    public final boolean equals(int x, int y) {
        return this.x == x && this.y == y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Point point = (Point) o;
        return x == point.x && y == point.y;
    }

    @Override
    public int hashCode() {
        return 31 * x + y;
    }

    @Override
    public String toString() {
        return "Point(" + x + ", " + y + ")";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(x);
        out.writeInt(y);
    }

    public void readFromParcel(Parcel in) {
        x = in.readInt();
        y = in.readInt();
    }

    public static final Creator<Point> CREATOR = new Creator<Point>() {
        @Override
        public Point createFromParcel(Parcel in) {
            Point r = new Point();
            r.readFromParcel(in);
            return r;
        }

        @Override
        public Point[] newArray(int size) {
            return new Point[size];
        }
    };

}
