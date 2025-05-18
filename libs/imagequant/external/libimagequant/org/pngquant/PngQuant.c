#include "org/pngquant/PngQuant.h"
#include "org/pngquant/Image.h"
#include "org/pngquant/Result.h"
#include "libimagequant.h"
#include <stdlib.h>

typedef struct {
  liq_image *image;
  jbyte *data;
} liq_jni_image;

static void *handle(JNIEnv *env, jobject obj) {
    jlong h = (*env)->GetLongField(env, obj, (*env)->GetFieldID(env, (*env)->GetObjectClass(env, obj), "handle", "J"));
    return (void*)h;
}

JNIEXPORT jlong JNICALL Java_org_pngquant_PngQuant_liq_1attr_1create(JNIEnv *env, jclass class) {
    return (jlong)liq_attr_create();
}

JNIEXPORT jlong JNICALL Java_org_pngquant_PngQuant_liq_1attr_1copy(JNIEnv *env, jclass class, jlong attr) {
    return (jlong)liq_attr_copy((liq_attr*)attr);
}

JNIEXPORT void JNICALL Java_org_pngquant_PngQuant_liq_1attr_1destroy(JNIEnv *env, jclass class, jlong attr) {
    return liq_attr_destroy((liq_attr*)attr);
}

JNIEXPORT jboolean JNICALL Java_org_pngquant_PngQuant_setMaxColors(JNIEnv *env, jobject obj, jint colors) {
    return LIQ_OK == liq_set_max_colors(handle(env, obj), colors);
}

JNIEXPORT jboolean JNICALL Java_org_pngquant_PngQuant_setSpeed(JNIEnv *env, jobject obj, jint speed) {
    return LIQ_OK == liq_set_speed(handle(env, obj), speed);
}

JNIEXPORT jboolean JNICALL Java_org_pngquant_PngQuant_setMinPosterization(JNIEnv *env, jobject obj, jint p) {
    return LIQ_OK == liq_set_min_posterization(handle(env, obj), p);
}

JNIEXPORT jboolean JNICALL Java_org_pngquant_PngQuant_setQuality__I(JNIEnv *env, jobject obj, jint q) {
    return LIQ_OK == liq_set_quality(handle(env, obj), q/2, q);
}

JNIEXPORT jboolean JNICALL Java_org_pngquant_PngQuant_setQuality__II(JNIEnv *env, jobject obj, jint qmin, jint qmax) {
    return LIQ_OK == liq_set_quality(handle(env, obj), qmin, qmax);
}

static void convert_abgr(liq_color row_out[], int row_index, int width, void* user_info) {
    liq_jni_image *jniimg = user_info;
    int column_index;
    for(column_index=0; column_index < width; column_index++) {
        row_out[column_index].r = jniimg->data[4*(width*row_index + column_index) + 3];
        row_out[column_index].g = jniimg->data[4*(width*row_index + column_index) + 2];
        row_out[column_index].b = jniimg->data[4*(width*row_index + column_index) + 1];
        row_out[column_index].a = jniimg->data[4*(width*row_index + column_index) + 0];
    }
}

static void convert_bgr(liq_color row_out[], int row_index, int width, void* user_info) {
    liq_jni_image *jniimg = user_info;
    int column_index;
    for(column_index=0; column_index < width; column_index++) {
        row_out[column_index].r = jniimg->data[3*(width*row_index + column_index) + 2];
        row_out[column_index].g = jniimg->data[3*(width*row_index + column_index) + 1];
        row_out[column_index].b = jniimg->data[3*(width*row_index + column_index) + 0];
        row_out[column_index].a = 255;
    }
}

JNIEXPORT jlong JNICALL Java_org_pngquant_Image_liq_1image_1create(JNIEnv *env, jclass class, jlong attr, jbyteArray bytearray, jint w, jint h, jint components) {
    /* liq_image needs to be wrapped to keep track of allocated buffer */
    liq_jni_image *jniimg = malloc(sizeof(liq_jni_image));

    /* copying buffer, since ReleaseByteArrayElements was crashing when called from finalize() */
    jsize size = (*env)->GetArrayLength(env, bytearray);
    jniimg->data = malloc(size);
    (*env)->GetByteArrayRegion(env, bytearray, 0, size, jniimg->data);

    jniimg->image = liq_image_create_custom((liq_attr*)attr, components == 4 ? convert_abgr : convert_bgr, jniimg, w, h, 0);

    if (!jniimg->image) {
        free(jniimg->data);
        free(jniimg);
        return 0;
    }
    return (jlong)jniimg;
}

JNIEXPORT jboolean JNICALL Java_org_pngquant_Image_addFixedColor(JNIEnv *env, jobject obj, jint r, jint g, jint b, jint a) {
    liq_color c = {r,g,b,a};
    return LIQ_OK == liq_image_add_fixed_color(((liq_jni_image*)handle(env,obj))->image, c);
}

JNIEXPORT jint JNICALL Java_org_pngquant_Image_getWidth(JNIEnv *env, jobject obj) {
    return liq_image_get_width(((liq_jni_image*)handle(env,obj))->image);
}

JNIEXPORT jint JNICALL Java_org_pngquant_Image_getHeight(JNIEnv *env, jobject obj) {
    return liq_image_get_height(((liq_jni_image*)handle(env,obj))->image);
}

JNIEXPORT void JNICALL Java_org_pngquant_Image_liq_1image_1destroy(JNIEnv *env, jclass class, jlong handle) {
    liq_jni_image *jniimg = (liq_jni_image*)handle;
    liq_image_destroy(jniimg->image);
    free(jniimg->data);
    free(jniimg);
}

JNIEXPORT jlong JNICALL Java_org_pngquant_Result_liq_1quantize_1image(JNIEnv *env, jclass class, jlong attr, jlong handle) {
    return (jlong)liq_quantize_image((liq_attr*)attr, ((liq_jni_image*)handle)->image);
}

JNIEXPORT jboolean JNICALL Java_org_pngquant_Result_setDitheringLevel(JNIEnv *env, jobject obj, jfloat l) {
    return LIQ_OK == liq_set_dithering_level(handle(env, obj), l);
}

JNIEXPORT jboolean JNICALL Java_org_pngquant_Result_setGamma(JNIEnv *env, jobject obj, jdouble gamma) {
    return LIQ_OK == liq_set_output_gamma(handle(env, obj), gamma);
}

JNIEXPORT jdouble JNICALL Java_org_pngquant_Result_getGamma(JNIEnv *env, jobject obj) {
    return liq_get_output_gamma(handle(env, obj));
}

JNIEXPORT jboolean JNICALL Java_org_pngquant_Result_liq_1write_1remapped_1image(JNIEnv *env, jclass class, jlong result, jlong image_handle, jbyteArray bytearray) {
    jsize size = (*env)->GetArrayLength(env, bytearray);

    jbyte *bitmap = (*env)->GetByteArrayElements(env, bytearray, 0);
    liq_error err = liq_write_remapped_image((liq_result*)result, ((liq_jni_image*)image_handle)->image, bitmap, size);
    (*env)->ReleaseByteArrayElements(env, bytearray, bitmap, 0);

    return LIQ_OK == err;
}

JNIEXPORT jdouble JNICALL Java_org_pngquant_Result_getMeanSquareError(JNIEnv *env, jobject obj) {
    return liq_get_quantization_error(handle(env, obj));
}

JNIEXPORT jint JNICALL Java_org_pngquant_Result_getQuality(JNIEnv *env, jobject obj) {
    return liq_get_quantization_quality(handle(env, obj));
}

JNIEXPORT void JNICALL Java_org_pngquant_Result_liq_1result_1destroy(JNIEnv *env, jclass class, jlong result) {
    return liq_result_destroy((liq_result*)result);
}

JNIEXPORT jbyteArray JNICALL Java_org_pngquant_Result_liq_1get_1palette(JNIEnv *env, jclass class, jlong result) {
    const liq_palette *pal = liq_get_palette((liq_result*)result);
    jbyteArray arr = (*env)->NewByteArray(env, pal->count * 4);
    int i;
    for(i=0; i < pal->count; i++) {
        (*env)->SetByteArrayRegion(env, arr, i*4, 4, ((jbyte*)&pal->entries[i]));
    }
    return arr;
}
