#include <jni.h>
#include <vector>
#include "libimagequant.h"
#include "png.h"
#include <android/log.h>

#define ALOGE(fmt, ...) __android_log_print(ANDROID_LOG_ERROR, "PNGQ", fmt, ##__VA_ARGS__)

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_org_autojs_autojs_runtime_api_PngQuantBridge_quantize(
        JNIEnv *env, jclass, jbyteArray srcRgba,
        jint width, jint height, jint quality) {

    /* 读取像素. */

    jsize len = env->GetArrayLength(srcRgba);
    std::vector<uint8_t> rgba(len);
    env->GetByteArrayRegion(srcRgba, 0, len, reinterpret_cast<jbyte *>(rgba.data()));

    if (len != width * height * 4) {
        ALOGE("rgba length mismatch, len=%d, expect=%d", len, width * height * 4);
        return nullptr;
    }

    /* libimagequant 量化. */

    liq_attr *attr = liq_attr_create();
    if (!attr) {
        ALOGE("liq_attr_create failed");
        return nullptr;
    }

    liq_set_speed(attr, 8);
    liq_set_quality(attr, quality, quality);
    liq_set_max_colors(attr, 256);

    liq_image *image = liq_image_create_rgba(attr, rgba.data(), width, height, 0);
    liq_result *res;

    liq_error err = liq_image_quantize(image, attr, &res);
    if (err != LIQ_OK) {
        ALOGE("liq_image_quantize failed, code=%d", err);
        liq_image_destroy(image);
        liq_attr_destroy(attr);
        return nullptr;
    }

    size_t outSize = width * height;
    std::vector<uint8_t> indexed(outSize);
    liq_write_remapped_image(res, image, indexed.data(), outSize);
    const liq_palette *pal = liq_get_palette(res);

    /* 写索引色 PNG 到内存. */

    std::vector<uint8_t> compressed;
    do {
        /* palette 检查. */
        if (!pal || pal->count == 0 || pal->count > 256) {
            ALOGE("invalid palette count=%d", pal ? pal->count : -1);
            goto png_fail;
        }

        /* libpng 结构体. */
        png_structp png_ptr = png_create_write_struct(PNG_LIBPNG_VER_STRING,
            nullptr,
            [](png_structp png_ptr, png_const_charp msg) {
                ALOGE("libpng error: %s", msg);
                longjmp(png_jmpbuf(png_ptr), 1);
            },
            nullptr);
        if (!png_ptr) {
            ALOGE("png_create_write_struct failed");
            goto png_fail;
        }

        png_infop info_ptr = png_create_info_struct(png_ptr);
        if (!info_ptr) {
            png_destroy_write_struct(&png_ptr, nullptr);
            goto png_fail;
        }

        /* longjmp error handler. */
        if (setjmp(png_jmpbuf(png_ptr))) {
            png_destroy_write_struct(&png_ptr, &info_ptr);
            goto png_fail;
        }

        /* custom mem writer. */
        struct Writer {
            static void PNGAPI write(png_structp png_ptr, png_bytep data, png_size_t len) {
                auto *vec = static_cast<std::vector<uint8_t> *>(png_get_io_ptr(png_ptr));
                vec->insert(vec->end(), data, data + len);
            }
            static void PNGAPI flush(png_structp) {}
        };
        std::vector<uint8_t> tmpBuf;
        png_set_write_fn(png_ptr, &tmpBuf, Writer::write, Writer::flush);

        /* IHDR. */
        png_set_IHDR(png_ptr, info_ptr,
                    static_cast<png_uint_32>(width),
                    static_cast<png_uint_32>(height),
                    8,                              // bit depth
                    PNG_COLOR_TYPE_PALETTE, PNG_INTERLACE_NONE,
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

        /* rows. */
        std::vector<png_bytep> rows(height);
        for (int y = 0; y < height; ++y) {
            rows[y] = indexed.data() + y * width;
        }
        png_write_image(png_ptr, rows.data());

        png_write_end(png_ptr, info_ptr);
        compressed.swap(tmpBuf);

        /* 无论是否成功, 均销毁. */
        png_destroy_write_struct(&png_ptr, &info_ptr);
    } while (false);

png_fail:
    if (compressed.empty()) {
        ALOGE("compress failed, return null to Java");
        env->ThrowNew(env->FindClass("java/io/IOException"),
                    "pngquant native: compress failed");
        return nullptr;
    }

    /* 清理. */
    liq_result_destroy(res);
    liq_image_destroy(image);
    liq_attr_destroy(attr);

    /* 回 JNI. */
    jbyteArray out = env->NewByteArray(compressed.size());
    env->SetByteArrayRegion(out, 0, compressed.size(),
                         reinterpret_cast<jbyte *>(compressed.data()));
    return out;
}