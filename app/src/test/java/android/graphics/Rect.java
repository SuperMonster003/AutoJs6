package android.graphics;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * JVM fake of the Android framework class for local unit tests (see the note on the {@code android.util.Base64}
 * fake): the mockable android.jar refuses even the constructor, so code under test that reads a rectangle (as
 * {@code Mathx.dist}, roadmap P2.1) could not run. Mirrors the public API of the framework class that does not need
 * other framework classes; the {@link Parcelable} methods delegate to the mockable {@link Parcel} and fail there as
 * before.
 * <p>
 * zh-CN: Android 框架类的 JVM fake, 供本地单元测试使用 (参见 android.util.Base64 fake 的说明): mockable android.jar
 * 连构造函数都拒绝, 读取矩形的被测代码 (如 P2.1 的 Mathx.dist) 无法运行. 公开 API 与框架类一致 (不含依赖其他框架类的方法),
 * Parcelable 方法委托给 mockable Parcel, 照旧在那里失败.
 */
@SuppressWarnings("unused")
public final class Rect implements Parcelable {

    public int left;
    public int top;
    public int right;
    public int bottom;

    public Rect() {
        /* Empty body. */
    }

    public Rect(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public Rect(Rect r) {
        if (r != null) set(r);
    }

    public final boolean isEmpty() {
        return left >= right || top >= bottom;
    }

    public final int width() {
        return right - left;
    }

    public final int height() {
        return bottom - top;
    }

    public final int centerX() {
        return (left + right) >> 1;
    }

    public final int centerY() {
        return (top + bottom) >> 1;
    }

    public final float exactCenterX() {
        return (left + right) * 0.5f;
    }

    public final float exactCenterY() {
        return (top + bottom) * 0.5f;
    }

    public void setEmpty() {
        left = right = top = bottom = 0;
    }

    public void set(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public void set(Rect src) {
        this.left = src.left;
        this.top = src.top;
        this.right = src.right;
        this.bottom = src.bottom;
    }

    public void offset(int dx, int dy) {
        left += dx;
        top += dy;
        right += dx;
        bottom += dy;
    }

    public boolean contains(int x, int y) {
        return left < right && top < bottom && x >= left && x < right && y >= top && y < bottom;
    }

    public void sort() {
        if (left > right) {
            int temp = left;
            left = right;
            right = temp;
        }
        if (top > bottom) {
            int temp = top;
            top = bottom;
            bottom = temp;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Rect r = (Rect) o;
        return left == r.left && top == r.top && right == r.right && bottom == r.bottom;
    }

    @Override
    public int hashCode() {
        int result = left;
        result = 31 * result + top;
        result = 31 * result + right;
        result = 31 * result + bottom;
        return result;
    }

    @Override
    public String toString() {
        return "Rect(" + left + ", " + top + " - " + right + ", " + bottom + ")";
    }

    public String toShortString() {
        return "[" + left + "," + top + "][" + right + "," + bottom + "]";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(left);
        out.writeInt(top);
        out.writeInt(right);
        out.writeInt(bottom);
    }

    public void readFromParcel(Parcel in) {
        left = in.readInt();
        top = in.readInt();
        right = in.readInt();
        bottom = in.readInt();
    }

    public static final Creator<Rect> CREATOR = new Creator<Rect>() {
        @Override
        public Rect createFromParcel(Parcel in) {
            Rect r = new Rect();
            r.readFromParcel(in);
            return r;
        }

        @Override
        public Rect[] newArray(int size) {
            return new Rect[size];
        }
    };

}
