#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>

#include <vector>
#include <setjmp.h>
#include "libimagequant.h"
#include "png.h"

#define ALOGE(fmt, ...) __android_log_print(ANDROID_LOG_ERROR, "PNGQ", fmt, ##__VA_ARGS__)

/* RAII: 保证函数结束时一定会 unlockPixels(). */
class BitmapLocker {
public:
    BitmapLocker(JNIEnv *env, jobject bmp)
            : mEnv(env), mBmp(bmp), mPixels(nullptr), mLocked(false) {}

    bool lock() {
        if (AndroidBitmap_lockPixels(mEnv, mBmp, &mPixels) != 0 || !mPixels) {
            return false;
        }
        mLocked = true;
        return true;
    }

    void *pixels() const { return mPixels; }

    ~BitmapLocker() {
        if (mLocked) {
            AndroidBitmap_unlockPixels(mEnv, mBmp);
        }
    }

private:
    JNIEnv *mEnv;
    jobject mBmp;
    void *mPixels;
    bool mLocked;
};

extern "C"
JNIEXPORT jbyteArray

JNICALL
Java_org_autojs_autojs_runtime_api_PngQuantBridge_quantize(
        JNIEnv *env, jclass /*cls*/, jobject jbitmap, jint quality) {

    /* 参数检查 & Bitmap 信息. */
    if (!jbitmap) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Bitmap is null");
        return nullptr;
    }
    AndroidBitmapInfo info{};
    if (AndroidBitmap_getInfo(env, jbitmap, &info) != 0 ||
        info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),
                      "Bitmap must be RGBA_8888");
        return nullptr;
    }
    const int width = static_cast<int>(info.width);
    const int height = static_cast<int>(info.height);

    /* 提前声明所有资源. */
    BitmapLocker locker(env, jbitmap);
    liq_attr *attr = nullptr;
    liq_image *image = nullptr;
    liq_result *res = nullptr;
    std::vector <uint8_t> indexed;      // 后面 resize()
    const liq_palette *pal = nullptr;
    std::vector <uint8_t> compressed;   // 输出 PNG

    /* 锁定像素. */
    if (!locker.lock()) {
        ALOGE("AndroidBitmap_lockPixels failed");
        env->ThrowNew(env->FindClass("java/io/IOException"),
                      "AndroidBitmap_lockPixels failed");
        return nullptr;
    }

    /* libimagequant. */
    attr = liq_attr_create();
    if (!attr) {
        ALOGE("liq_attr_create failed");
        goto fail;
    }
    if (quality < 0) quality = 0;
    if (quality > 100) quality = 100;
    liq_set_speed(attr, 8);
    liq_set_quality(attr, quality, quality);
    liq_set_max_colors(attr, 256);

    image = liq_image_create_rgba(attr,
                                  static_cast<uint8_t *>(locker.pixels()),
                                  width, height, 0);
    if (!image) {
        ALOGE("liq_image_create_rgba failed");
        goto fail;
    }
    if (liq_image_quantize(image, attr, &res) != LIQ_OK || !res) {
        ALOGE("liq_image_quantize failed");
        goto fail;
    }

    /* Remap. */
    indexed.resize(static_cast<size_t>(width) * height);
    liq_write_remapped_image(res, image, indexed.data(), indexed.size());

    pal = liq_get_palette(res);
    if (!pal || pal->count == 0 || pal->count > 256) {
        ALOGE("invalid palette, count=%d", pal ? pal->count : -1);
        goto fail;
    }

    /* libpng 写 PNG 到内存. */
    {
        png_structp png_ptr = png_create_write_struct(PNG_LIBPNG_VER_STRING,
                                                      nullptr,
                                                      [](png_structp png_ptr, png_const_charp msg) {
                                                          __android_log_print(ANDROID_LOG_ERROR, "PNGQ", "libpng error: %s", msg);
                                                          longjmp(png_jmpbuf(png_ptr), 1);
                                                      },
                                                      nullptr);
        if (!png_ptr) {
            ALOGE("png_create_write_struct failed");
            goto fail;
        }
        png_infop info_ptr = png_create_info_struct(png_ptr);
        if (!info_ptr) {
            png_destroy_write_struct(&png_ptr, nullptr);
            ALOGE("png_create_info_struct failed");
            goto fail;
        }
        if (setjmp(png_jmpbuf(png_ptr))) {            // libpng error
            png_destroy_write_struct(&png_ptr, &info_ptr);
            ALOGE("libpng longjmp error");
            goto fail;
        }

        /* 自定义写函数 => vector. */
        struct Writer {
            static void PNGAPI write(png_structp png_ptr, png_bytep data, png_size_t len) {
                auto *vec = static_cast<std::vector <uint8_t> *>(png_get_io_ptr(png_ptr));
                vec->insert(vec->end(), data, data + len);
            }

            static void PNGAPI flush(png_structp) {}
        };
        png_set_write_fn(png_ptr, &compressed, Writer::write, Writer::flush);

        /* IHDR. */
        png_set_IHDR(png_ptr, info_ptr, width, height,
                     8, PNG_COLOR_TYPE_PALETTE, PNG_INTERLACE_NONE,
                     PNG_COMPRESSION_TYPE_DEFAULT, PNG_FILTER_TYPE_DEFAULT);
        png_set_compression_level(png_ptr, 3);
        png_set_filter(png_ptr, 0, PNG_FILTER_NONE);

        /* PLTE & tRNS. */
        png_color plte[256];
        png_byte trns[256];
        int num_trns = 0;
        for (int i = 0; i < pal->count; ++i) {
            plte[i].red = pal->entries[i].r;
            plte[i].green = pal->entries[i].g;
            plte[i].blue = pal->entries[i].b;
            trns[i] = pal->entries[i].a;
            if (trns[i] < 255) num_trns = i + 1;
        }
        png_set_PLTE(png_ptr, info_ptr, plte, pal->count);
        if (num_trns) png_set_tRNS(png_ptr, info_ptr, trns, num_trns, nullptr);

        png_write_info(png_ptr, info_ptr);

        /* Rows. */
        std::vector <png_bytep> rows(height);
        for (int y = 0; y < height; ++y) {
            rows[y] = indexed.data() + y * width;
        }
        png_write_image(png_ptr, rows.data());
        png_write_end(png_ptr, info_ptr);

        png_destroy_write_struct(&png_ptr, &info_ptr);
    }

    /* 成功: 回传 Java. */
    if (compressed.empty()) {
        ALOGE("compression produced empty data");
        goto fail;
    }
    {
        jbyteArray out = env->NewByteArray(static_cast<jsize>(compressed.size()));
        env->SetByteArrayRegion(out, 0, static_cast<jsize>(compressed.size()),
                                reinterpret_cast<const jbyte *>(compressed.data()));
        /* 释放 libimagequant 资源. */
        liq_result_destroy(res);
        liq_image_destroy(image);
        liq_attr_destroy(attr);
        return out;
    }

    fail:
    /* 统一错误处理. */
    if (res) liq_result_destroy(res);
    if (image) liq_image_destroy(image);
    if (attr) liq_attr_destroy(attr);
    env->ThrowNew(env->FindClass("java/io/IOException"), "pngquant native failed");
    return nullptr;
}