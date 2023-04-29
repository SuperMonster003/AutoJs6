package org.autojs.autojs.core.image;

import static org.autojs.autojs.util.StringUtils.str;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.Image;

import androidx.annotation.NonNull;

import org.autojs.autojs.AutoJs;
import org.autojs.autojs.core.opencv.Mat;
import org.autojs.autojs.core.opencv.OpenCVHelper;
import org.autojs.autojs.pio.UncheckedIOException;
import org.autojs.autojs.runtime.ScriptRuntime;
import org.autojs.autojs6.R;
import org.opencv.android.Utils;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * Created by Stardust on 2017/11/25.
 */
public class ImageWrapper implements Recyclable {

    private Mat mMat;
    private Bitmap mBitmap;

    private final int mWidth;
    private final int mHeight;

    private final ScriptRuntime mScriptRuntime = AutoJs.getInstance().getRuntime();

    private boolean isRecycled;

    private final static ArrayList<WeakReference<Object>> imageList = new ArrayList<>();
    private boolean mOneShot;

    private void addToList(Object image) {
        imageList.add(new WeakReference<>(image));
    }

    public static synchronized void recycleAll() {
        for (WeakReference<Object> reference : imageList) {
            Object image = reference.get();
            if (image instanceof Recyclable) {
                ((Recyclable) image).recycle();
            } else if (image instanceof Bitmap) {
                ((Bitmap) image).recycle();
            }
        }
        imageList.clear();
    }

    protected ImageWrapper(Mat mat) {
        this(null, mat);
    }

    protected ImageWrapper(Bitmap bitmap) {
        this(bitmap, null);
    }

    protected ImageWrapper(int width, int height) {
        this(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888));
    }

    protected ImageWrapper(Bitmap bitmap, Mat mat) {
        if (mat != null) {
            mMat = mat;
        }
        if (bitmap != null) {
            mBitmap = bitmap;
            mWidth = bitmap.getWidth();
            mHeight = bitmap.getHeight();
        } else {
            if (mat == null) {
                throw new Error(str(R.string.error_both_bitmap_and_mat_are_null));
            }
            mWidth = mat.cols();
            mHeight = mat.rows();
        }
        addToList(this);
    }

    public static ImageWrapper ofImage(Image image) {
        if (image == null) {
            return null;
        }
        return new ImageWrapper(toBitmap(image));
    }

    public static ImageWrapper ofMat(Mat mat) {
        if (mat == null) {
            return null;
        }
        return new ImageWrapper(mat);
    }

    public static ImageWrapper ofBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        return new ImageWrapper(bitmap);
    }

    public static Bitmap toBitmap(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        buffer.position(0);
        int pixelStride = plane.getPixelStride();
        int rowPadding = plane.getRowStride() - pixelStride * image.getWidth();
        Bitmap bitmap = Bitmap.createBitmap(image.getWidth() + rowPadding / pixelStride, image.getHeight(), Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        if (rowPadding == 0) {
            return bitmap;
        }
        try {
            return Bitmap.createBitmap(bitmap, 0, 0, image.getWidth(), image.getHeight());
        } catch (IllegalStateException e) {
            // Wrapped java.lang.IllegalStateException: Image is already closed
            e.printStackTrace();
            return null;
        }
    }

    public int getWidth() {
        ensureNotRecycled();
        return mWidth;
    }

    public int getHeight() {
        ensureNotRecycled();
        return mHeight;
    }

    public Size getSize() {
        ensureNotRecycled();
        return new Size(mWidth, mHeight);
    }

    public Mat getMat() {
        ensureNotRecycled();
        if (mMat == null && mBitmap != null) {
            mMat = new Mat();
            Utils.bitmapToMat(mBitmap, mMat);
        }
        return mMat;
    }

    public void saveTo(String path) {
        ensureNotRecycled();
        path = mScriptRuntime.files.path(path);
        if (mBitmap != null) {
            try {
                mBitmap.compress(Bitmap.CompressFormat.PNG, 100, new FileOutputStream(path));
            } catch (FileNotFoundException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            Imgcodecs.imwrite(path, mMat);
        }
    }

    public int pixel(int x, int y) {
        ensureNotRecycled();
        int result;
        if (mBitmap != null) {
            result = mBitmap.getPixel(x, y);
        } else {
            double[] channels = mMat.get(x, y);
            result = Color.argb((int) channels[3], (int) channels[0], (int) channels[1], (int) channels[2]);
        }
        return result;
    }

    public Bitmap getBitmap() {
        ensureNotRecycled();
        if (mBitmap == null && mMat != null) {
            mBitmap = Bitmap.createBitmap(mMat.width(), mMat.height(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(mMat, mBitmap);
        }
        return mBitmap;
    }

    @Override
    public void recycle() {
        if (mBitmap != null) {
            mBitmap.recycle();
            mBitmap = null;
        }
        if (mMat != null) {
            OpenCVHelper.release(mMat);
            mMat = null;
        }
        isRecycled = true;
    }

    @Override
    public ImageWrapper setOneShot(boolean b) {
        mOneShot = b;
        return this;
    }

    @Override
    public void shoot() {
        if (mOneShot) {
            recycle();
        }
    }

    @Override
    public boolean isRecycled() {
        return isRecycled;
    }

    public void ensureNotRecycled() {
        if (isRecycled()) {
            throw new IllegalStateException(str(R.string.error_image_has_been_recycled));
        }
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @NonNull
    public ImageWrapper clone() {
        ensureNotRecycled();
        ImageWrapper imageWrapper;
        if (mBitmap == null) {
            imageWrapper = ImageWrapper.ofMat(mMat.clone());
        } else if (mMat == null) {
            imageWrapper = ImageWrapper.ofBitmap(mBitmap.copy(mBitmap.getConfig(), true));
        } else {
            imageWrapper = new ImageWrapper(mBitmap.copy(mBitmap.getConfig(), true), mMat.clone());
        }
        return imageWrapper;
    }

}
