package com.stardust.autojs.core.image;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.Image;

import androidx.annotation.NonNull;

import com.stardust.autojs.core.opencv.Mat;
import com.stardust.autojs.core.opencv.OpenCVHelper;
import com.stardust.pio.UncheckedIOException;

import org.opencv.android.Utils;
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

    private boolean isRecycled;

    private final static ArrayList<WeakReference<Object>> imageList = new ArrayList<>();

    @NonNull
    public static ArrayList<Object> getImageList() {
        ArrayList<Object> list = new ArrayList<>();
        for (WeakReference<Object> ref : imageList) {
            Object image = ref.get();
            if (image != null) {
                list.add(image);
            }
        }
        return list;
    }

    public static Object addToList(Object image) {
        imageList.add(new WeakReference<>(image));
        return image;
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
                throw new Error("Both bitmap and mat are null");
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
        if (mBitmap != null) {
            return mBitmap.getPixel(x, y);
        }
        double[] channels = mMat.get(x, y);
        return Color.argb((int) channels[3], (int) channels[0], (int) channels[1], (int) channels[2]);
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
    public boolean isRecycled() {
        return isRecycled;
    }

    public void ensureNotRecycled() {
        if (isRecycled()) {
            throw new IllegalStateException("Image has been recycled");
        }
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @NonNull
    public ImageWrapper clone() {
        ensureNotRecycled();
        if (mBitmap == null) {
            return ImageWrapper.ofMat(mMat.clone());
        }
        if (mMat == null) {
            return ImageWrapper.ofBitmap(mBitmap.copy(mBitmap.getConfig(), true));
        }
        return new ImageWrapper(mBitmap.copy(mBitmap.getConfig(), true), mMat.clone());
    }
}
