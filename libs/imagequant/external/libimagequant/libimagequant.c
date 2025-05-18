/*
** © 2009-2018 by Kornel Lesiński.
** © 1989, 1991 by Jef Poskanzer.
** © 1997, 2000, 2002 by Greg Roelofs; based on an idea by Stefan Schneider.
**
** See COPYRIGHT file for license.
*/

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <limits.h>

#if !(defined(__STDC_VERSION__) && __STDC_VERSION__ >= 199900L) && !(defined(_MSC_VER) && _MSC_VER >= 1800)
#error "This program requires C99, e.g. -std=c99 switch in GCC or it requires MSVC 18.0 or higher."
#error "Ignore torrent of syntax errors that may follow. It's only because compiler is set to use too old C version."
#endif

#ifdef _OPENMP
#include <omp.h>
#define LIQ_TEMP_ROW_WIDTH(img_width) (((img_width) | 15) + 1) /* keep alignment & leave space between rows to avoid cache line contention */
#else
#define LIQ_TEMP_ROW_WIDTH(img_width) (img_width)
#define omp_get_max_threads() 1
#define omp_get_thread_num() 0
#endif

#include "libimagequant.h"

#include "pam.h"
#include "mediancut.h"
#include "nearest.h"
#include "blur.h"
#include "kmeans.h"

#define LIQ_HIGH_MEMORY_LIMIT (1<<26)  /* avoid allocating buffers larger than 64MB */

// each structure has a pointer as a unique identifier that allows type checking at run time
static const char liq_attr_magic[] = "liq_attr";
static const char liq_image_magic[] = "liq_image";
static const char liq_result_magic[] = "liq_result";
static const char liq_histogram_magic[] = "liq_histogram";
static const char liq_remapping_result_magic[] = "liq_remapping_result";
static const char liq_freed_magic[] = "free";
#define CHECK_STRUCT_TYPE(attr, kind) liq_crash_if_invalid_handle_pointer_given((const liq_attr*)attr, kind ## _magic)
#define CHECK_USER_POINTER(ptr) liq_crash_if_invalid_pointer_given(ptr)

struct liq_attr {
    const char *magic_header;
    void* (*malloc)(size_t);
    void (*free)(void*);

    double target_mse, max_mse, kmeans_iteration_limit;
    unsigned int max_colors, max_histogram_entries;
    unsigned int min_posterization_output /* user setting */, min_posterization_input /* speed setting */;
    unsigned int kmeans_iterations, feedback_loop_trials;
    bool last_index_transparent, use_contrast_maps;
    unsigned char use_dither_map;
    unsigned char speed;

    unsigned char progress_stage1, progress_stage2, progress_stage3;
    liq_progress_callback_function *progress_callback;
    void *progress_callback_user_info;

    liq_log_callback_function *log_callback;
    void *log_callback_user_info;
    liq_log_flush_callback_function *log_flush_callback;
    void *log_flush_callback_user_info;
};

struct liq_image {
    const char *magic_header;
    void* (*malloc)(size_t);
    void (*free)(void*);

    f_pixel *f_pixels;
    rgba_pixel **rows;
    double gamma;
    unsigned int width, height;
    unsigned char *importance_map, *edges, *dither_map;
    rgba_pixel *pixels, *temp_row;
    f_pixel *temp_f_row;
    liq_image_get_rgba_row_callback *row_callback;
    void *row_callback_user_info;
    liq_image *background;
    f_pixel fixed_colors[256];
    unsigned short fixed_colors_count;
    bool free_pixels, free_rows, free_rows_internal;
};

typedef struct liq_remapping_result {
    const char *magic_header;
    void* (*malloc)(size_t);
    void (*free)(void*);

    unsigned char *pixels;
    colormap *palette;
    liq_progress_callback_function *progress_callback;
    void *progress_callback_user_info;

    liq_palette int_palette;
    double gamma, palette_error;
    float dither_level;
    unsigned char use_dither_map;
    unsigned char progress_stage1;
} liq_remapping_result;

struct liq_result {
    const char *magic_header;
    void* (*malloc)(size_t);
    void (*free)(void*);

    liq_remapping_result *remapping;
    colormap *palette;
    liq_progress_callback_function *progress_callback;
    void *progress_callback_user_info;

    liq_palette int_palette;
    float dither_level;
    double gamma, palette_error;
    int min_posterization_output;
    unsigned char use_dither_map;
};

struct liq_histogram {
    const char *magic_header;
    void* (*malloc)(size_t);
    void (*free)(void*);

    struct acolorhash_table *acht;
    double gamma;
    f_pixel fixed_colors[256];
    unsigned short fixed_colors_count;
    unsigned short ignorebits;
    bool had_image_added;
};

static void contrast_maps(liq_image *image) LIQ_NONNULL;
static liq_error finalize_histogram(liq_histogram *input_hist, liq_attr *options, histogram **hist_output) LIQ_NONNULL;
static const rgba_pixel *liq_image_get_row_rgba(liq_image *input_image, unsigned int row) LIQ_NONNULL;
static bool liq_image_get_row_f_init(liq_image *img) LIQ_NONNULL;
static const f_pixel *liq_image_get_row_f(liq_image *input_image, unsigned int row) LIQ_NONNULL;
static void liq_remapping_result_destroy(liq_remapping_result *result) LIQ_NONNULL;
static liq_error pngquant_quantize(histogram *hist, const liq_attr *options, const int fixed_colors_count, const f_pixel fixed_colors[], const double gamma, bool fixed_result_colors, liq_result **) LIQ_NONNULL;
static liq_error liq_histogram_quantize_internal(liq_histogram *input_hist, liq_attr *attr, bool fixed_result_colors, liq_result **result_output) LIQ_NONNULL;

LIQ_NONNULL static void liq_verbose_printf(const liq_attr *context, const char *fmt, ...)
{
    if (context->log_callback) {
        va_list va;
        va_start(va, fmt);
        int required_space = vsnprintf(NULL, 0, fmt, va)+1; // +\0
        va_end(va);

        LIQ_ARRAY(char, buf, required_space);
        va_start(va, fmt);
        vsnprintf(buf, required_space, fmt, va);
        va_end(va);

        context->log_callback(context, buf, context->log_callback_user_info);
    }
}

LIQ_NONNULL inline static void verbose_print(const liq_attr *attr, const char *msg)
{
    if (attr->log_callback) {
        attr->log_callback(attr, msg, attr->log_callback_user_info);
    }
}

LIQ_NONNULL static void liq_verbose_printf_flush(liq_attr *attr)
{
    if (attr->log_flush_callback) {
        attr->log_flush_callback(attr, attr->log_flush_callback_user_info);
    }
}

LIQ_NONNULL static bool liq_progress(const liq_attr *attr, const float percent)
{
    return attr->progress_callback && !attr->progress_callback(percent, attr->progress_callback_user_info);
}

LIQ_NONNULL static bool liq_remap_progress(const liq_remapping_result *quant, const float percent)
{
    return quant->progress_callback && !quant->progress_callback(percent, quant->progress_callback_user_info);
}

#if USE_SSE
inline static bool is_sse_available()
{
#if (defined(__x86_64__) || defined(__amd64) || defined(_WIN64))
    return true;
#elif _MSC_VER
    int info[4];
    __cpuid(info, 1);
    /* bool is implemented as a built-in type of size 1 in MSVC */
    return info[3] & (1<<26) ? true : false;
#else
    int a,b,c,d;
        cpuid(1, a, b, c, d);
    return d & (1<<25); // edx bit 25 is set when SSE is present
#endif
}
#endif

/* make it clear in backtrace when user-supplied handle points to invalid memory */
NEVER_INLINE LIQ_EXPORT bool liq_crash_if_invalid_handle_pointer_given(const liq_attr *user_supplied_pointer, const char *const expected_magic_header);
LIQ_EXPORT bool liq_crash_if_invalid_handle_pointer_given(const liq_attr *user_supplied_pointer, const char *const expected_magic_header)
{
    if (!user_supplied_pointer) {
        return false;
    }

    if (user_supplied_pointer->magic_header == liq_freed_magic) {
        fprintf(stderr, "%s used after being freed", expected_magic_header);
        // this is not normal error handling, this is programmer error that should crash the program.
        // program cannot safely continue if memory has been used after it's been freed.
        // abort() is nasty, but security vulnerability may be worse.
        abort();
    }

    return user_supplied_pointer->magic_header == expected_magic_header;
}

NEVER_INLINE LIQ_EXPORT bool liq_crash_if_invalid_pointer_given(const void *pointer);
LIQ_EXPORT bool liq_crash_if_invalid_pointer_given(const void *pointer)
{
    if (!pointer) {
        return false;
    }
    // Force a read from the given (potentially invalid) memory location in order to check early whether this crashes the program or not.
    // It doesn't matter what value is read, the code here is just to shut the compiler up about unused read.
    char test_access = *((volatile char *)pointer);
    return test_access || true;
}

LIQ_NONNULL static void liq_log_error(const liq_attr *attr, const char *msg)
{
    if (!CHECK_STRUCT_TYPE(attr, liq_attr)) return;
    liq_verbose_printf(attr, "  error: %s", msg);
}

static double quality_to_mse(long quality)
{
    if (quality == 0) {
        return MAX_DIFF;
    }
    if (quality == 100) {
        return 0;
    }

    // curve fudged to be roughly similar to quality of libjpeg
    // except lowest 10 for really low number of colors
    const double extra_low_quality_fudge = MAX(0,0.016/(0.001+quality) - 0.001);
    return extra_low_quality_fudge + 2.5/pow(210.0 + quality, 1.2) * (100.1-quality)/100.0;
}

static unsigned int mse_to_quality(double mse)
{
    for(int i=100; i > 0; i--) {
        if (mse <= quality_to_mse(i) + 0.000001) { // + epsilon for floating point errors
            return i;
        }
    }
    return 0;
}

/** internally MSE is a sum of all channels with pixels 0..1 range,
 but other software gives per-RGB-channel MSE for 0..255 range */
static double mse_to_standard_mse(double mse) {
    return mse * 65536.0/6.0;
}

LIQ_EXPORT LIQ_NONNULL liq_error liq_set_quality(liq_attr* attr, int minimum, int target)
{
    if (!CHECK_STRUCT_TYPE(attr, liq_attr)) return LIQ_INVALID_POINTER;
    if (target < 0 || target > 100 || target < minimum || minimum < 0) return LIQ_VALUE_OUT_OF_RANGE;

    attr->target_mse = quality_to_mse(target);
    attr->max_mse = quality_to_mse(minimum);
    return LIQ_OK;
}

LIQ_EXPORT LIQ_NONNULL int liq_get_min_quality(const liq_attr *attr)
{
    if (!CHECK_STRUCT_TYPE(attr, liq_attr)) return -1;
    return mse_to_quality(attr->max_mse);
}

LIQ_EXPORT LIQ_NONNULL int liq_get_max_quality(const liq_attr *attr)
{
    if (!CHECK_STRUCT_TYPE(attr, liq_attr)) return -1;
    return mse_to_quality(attr->target_mse);
}


LIQ_EXPORT LIQ_NONNULL liq_error liq_set_max_colors(liq_attr* attr, int colors)
{
    if (!CHECK_STRUCT_TYPE(attr, liq_attr)) return LIQ_INVALID_POINTER;
    if (colors < 2 || colors > 256) return LIQ_VALUE_OUT_OF_RANGE;

    attr->max_colors = colors;
    return LIQ_OK;
}

LIQ_EXPORT LIQ_NONNULL int liq_get_max_colors(const liq_attr *attr)
{
    if (!CHECK_STRUCT_TYPE(attr, liq_attr)) return -1;

    return attr->max_colors;
}

LIQ_EXPORT LIQ_NONNULL liq_error liq_set_min_posterization(liq_attr *attr, int bits)
{
    if (!CHECK_STRUCT_TYPE(attr, liq_attr)) return LIQ_INVALID_POINTER;
    if (bits < 0 || bits > 4) return LIQ_VALUE_OUT_OF_RANGE;

    attr->min_posterization_output = bits;
    return LIQ_OK;
}

LIQ_EXPORT LIQ_NONNULL int liq_get_min_posterization(const liq_attr *attr)
{
    if (!CHECK_STRUCT_TYPE(attr, liq_attr)) return -1;

    return attr->min_posterization_output;
}

LIQ_EXPORT LIQ_NONNULL liq_error liq_set_speed(liq_attr* attr, int speed)
{
    if (!CHECK_STRUCT_TYPE(attr, liq_attr)) return LIQ_INVALID_POINTER;
    if (speed < 1 || speed > 10) return LIQ_VALUE_OUT_OF_RANGE;

    unsigned int iterations = MAX(8-speed, 0);
    iterations += iterations * iterations/2;
    attr->kmeans_iterations = iterations;
    attr->kmeans_iteration_limit = 1.0/(double)(1<<(23-speed));
    attr->feedback_loop_trials = MAX(56-9*speed, 0);

    attr->max_histogram_entries = (1<<17) + (1<<18)*(10-speed);
    attr->min_posterization_input = (speed >= 8) ? 1 : 0;
    attr->use_dither_map = (speed <= (omp_get_max_threads() > 1 ? 7 : 5)); // parallelized dither map might speed up floyd remapping
    if (attr->use_dither_map && speed < 3) {
        attr->use_dither_map = 2; // always
    }
    attr->use_contrast_maps = (speed <= 7) || attr->use_dither_map;
    attr->speed = speed;

    attr->progress_stage1 = attr->use_contrast_maps ? 20 : 8;
    if (attr->feedback_loop_trials < 2) {
        attr->progress_stage1 += 30;
    }
    attr->progress_stage3 = 50 / (1+speed);
    attr->progress_stage2 = 100 - attr->progress_stage1 - attr->progress_stage3;
    return LIQ_OK;
}

LIQ_EXPORT LIQ_NONNULL int liq_get_speed(const liq_attr *attr)
{
    if (!CHECK_STRUCT_TYPE(attr, liq_attr)) return -1;

    return attr->speed;
}

LIQ_EXPORT LIQ_NONNULL liq_error liq_set_output_gamma(liq_result* res, double gamma)
{
    if (!CHECK_STRUCT_TYPE(res, liq_result)) return LIQ_INVALID_POINTER;
    if (gamma <= 0 || gamma >= 1.0) return LIQ_VALUE_OUT_OF_RANGE;

    if (res->remapping) {
        liq_remapping_result_destroy(res->remapping);
        res->remapping = NULL;
    }

    res->gamma = gamma;
    return LIQ_OK;
}

LIQ_EXPORT LIQ_NONNULL liq_error liq_set_min_opacity(liq_attr* attr, int min)
{
    return LIQ_OK;
}

LIQ_EXPORT LIQ_NONNULL int liq_get_min_opacity(const liq_attr *attr)
{
    return 0;
}

LIQ_EXPORT LIQ_NONNULL void liq_set_last_index_transparent(liq_attr* attr, int is_last)
{
    if (!CHECK_STRUCT_TYPE(attr, liq_attr)) return;

    attr->last_index_transparent = !!is_last;
}

LIQ_EXPORT void liq_attr_set_progress_callback(liq_attr *attr, liq_progress_callback_function *callback, void *user_info)
{
    if (!CHECK_STRUCT_TYPE(attr, liq_attr)) return;

    attr->progress_callback = callback;
    attr->progress_callback_user_info = user_info;
}

LIQ_EXPORT void liq_result_set_progress_callback(liq_result *result, liq_progress_callback_function *callback, void *user_info)
{
    if (!CHECK_STRUCT_TYPE(result, liq_result)) return;

    result->progress_callback = callback;
    result->progress_callback_user_info = user_info;
}

LIQ_EXPORT void liq_set_log_callback(liq_attr *attr, liq_log_callback_function *callback, void* user_info)
{
    if (!CHECK_STRUCT_TYPE(attr, liq_attr)) return;

    liq_verbose_printf_flush(attr);
    attr->log_callback = callback;
    attr->log_callback_user_info = user_info;
}

LIQ_EXPORT void liq_set_log_flush_callback(liq_attr *attr, liq_log_flush_callback_function *callback, void* user_info)
{
    if (!CHECK_STRUCT_TYPE(attr, liq_attr)) return;

    attr->log_flush_callback = callback;
    attr->log_flush_callback_user_info = user_info;
}

LIQ_EXPORT liq_attr* liq_attr_create()
{
    return liq_attr_create_with_allocator(NULL, NULL);
}

LIQ_EXPORT LIQ_NONNULL void liq_attr_destroy(liq_attr *attr)
{
    if (!CHECK_STRUCT_TYPE(attr, liq_attr)) {
        return;
    }

    liq_verbose_printf_flush(attr);

    attr->magic_header = liq_freed_magic;
    attr->free(attr);
}

LIQ_EXPORT LIQ_NONNULL liq_attr* liq_attr_copy(const liq_attr *orig)
{
    if (!CHECK_STRUCT_TYPE(orig, liq_attr)) {
        return NULL;
    }

    liq_attr *attr = orig->malloc(sizeof(liq_attr));
    if (!attr) return NULL;
    *attr = *orig;
    return attr;
}

static void *liq_aligned_malloc(size_t size)
{
    unsigned char *ptr = malloc(size + 16);
    if (!ptr) {
        return NULL;
    }

    uintptr_t offset = 16 - ((uintptr_t)ptr & 15); // also reserves 1 byte for ptr[-1]
    ptr += offset;
    assert(0 == (((uintptr_t)ptr) & 15));
    ptr[-1] = offset ^ 0x59; // store how much pointer was shifted to get the original for free()
    return ptr;
}

LIQ_NONNULL static void liq_aligned_free(void *inptr)
{
    unsigned char *ptr = inptr;
    size_t offset = ptr[-1] ^ 0x59;
    assert(offset > 0 && offset <= 16);
    free(ptr - offset);
}

LIQ_EXPORT liq_attr* liq_attr_create_with_allocator(void* (*custom_malloc)(size_t), void (*custom_free)(void*))
{
#if USE_SSE
    if (!is_sse_available()) {
        return NULL;
    }
#endif
    if (!custom_malloc && !custom_free) {
        custom_malloc = liq_aligned_malloc;
        custom_free = liq_aligned_free;
    } else if (!custom_malloc != !custom_free) {
        return NULL; // either specify both or none
    }

    liq_attr *attr = custom_malloc(sizeof(liq_attr));
    if (!attr) return NULL;
    *attr = (liq_attr) {
        .magic_header = liq_attr_magic,
        .malloc = custom_malloc,
        .free = custom_free,
        .max_colors = 256,
        .last_index_transparent = false, // puts transparent color at last index. This is workaround for blu-ray subtitles.
        .target_mse = 0,
        .max_mse = MAX_DIFF,
    };
    liq_set_speed(attr, 4);
    return attr;
}

LIQ_EXPORT LIQ_NONNULL liq_error liq_image_add_fixed_color(liq_image *img, liq_color color)
{
    if (!CHECK_STRUCT_TYPE(img, liq_image)) return LIQ_INVALID_POINTER;
    if (img->fixed_colors_count > 255) return LIQ_UNSUPPORTED;

    float gamma_lut[256];
    to_f_set_gamma(gamma_lut, img->gamma);
    img->fixed_colors[img->fixed_colors_count++] = rgba_to_f(gamma_lut, (rgba_pixel){
        .r = color.r,
        .g = color.g,
        .b = color.b,
        .a = color.a,
    });
    return LIQ_OK;
}

LIQ_NONNULL static liq_error liq_histogram_add_fixed_color_f(liq_histogram *hist, f_pixel color)
{
    if (hist->fixed_colors_count > 255) return LIQ_UNSUPPORTED;

    hist->fixed_colors[hist->fixed_colors_count++] = color;
    return LIQ_OK;
}

LIQ_EXPORT LIQ_NONNULL liq_error liq_histogram_add_fixed_color(liq_histogram *hist, liq_color color, double gamma)
{
    if (!CHECK_STRUCT_TYPE(hist, liq_histogram)) return LIQ_INVALID_POINTER;

    float gamma_lut[256];
    to_f_set_gamma(gamma_lut, gamma ? gamma : 0.45455);
    const f_pixel px = rgba_to_f(gamma_lut, (rgba_pixel){
        .r = color.r,
        .g = color.g,
        .b = color.b,
        .a = color.a,
    });
    return liq_histogram_add_fixed_color_f(hist, px);
}

LIQ_NONNULL static bool liq_image_use_low_memory(liq_image *img)
{
    img->temp_f_row = img->malloc(sizeof(img->f_pixels[0]) * LIQ_TEMP_ROW_WIDTH(img->width) * omp_get_max_threads());
    return img->temp_f_row != NULL;
}

LIQ_NONNULL static bool liq_image_should_use_low_memory(liq_image *img, const bool low_memory_hint)
{
    return (size_t)img->width * (size_t)img->height > (low_memory_hint ? LIQ_HIGH_MEMORY_LIMIT/8 : LIQ_HIGH_MEMORY_LIMIT) / sizeof(f_pixel); // Watch out for integer overflow
}

static liq_image *liq_image_create_internal(const liq_attr *attr, rgba_pixel* rows[], liq_image_get_rgba_row_callback *row_callback, void *row_callback_user_info, int width, int height, double gamma)
{
    if (gamma < 0 || gamma > 1.0) {
        liq_log_error(attr, "gamma must be >= 0 and <= 1 (try 1/gamma instead)");
        return NULL;
    }

    if (!rows && !row_callback) {
        liq_log_error(attr, "missing row data");
        return NULL;
    }

    liq_image *img = attr->malloc(sizeof(liq_image));
    if (!img) return NULL;
    *img = (liq_image){
        .magic_header = liq_image_magic,
        .malloc = attr->malloc,
        .free = attr->free,
        .width = width, .height = height,
        .gamma = gamma ? gamma : 0.45455,
        .rows = rows,
        .row_callback = row_callback,
        .row_callback_user_info = row_callback_user_info,
    };

    if (!rows) {
        img->temp_row = attr->malloc(sizeof(img->temp_row[0]) * LIQ_TEMP_ROW_WIDTH(width) * omp_get_max_threads());
        if (!img->temp_row) return NULL;
    }

    // if image is huge or converted pixels are not likely to be reused then don't cache converted pixels
    if (liq_image_should_use_low_memory(img, !img->temp_row && !attr->use_contrast_maps && !attr->use_dither_map)) {
        verbose_print(attr, "  conserving memory");
        if (!liq_image_use_low_memory(img)) return NULL;
    }

    return img;
}

LIQ_EXPORT LIQ_NONNULL liq_error liq_image_set_memory_ownership(liq_image *img, int ownership_flags)
{
    if (!CHECK_STRUCT_TYPE(img, liq_image)) return LIQ_INVALID_POINTER;
    if (!img->rows || !ownership_flags || (ownership_flags & ~(LIQ_OWN_ROWS|LIQ_OWN_PIXELS))) {
        return LIQ_VALUE_OUT_OF_RANGE;
    }

    if (ownership_flags & LIQ_OWN_ROWS) {
        if (img->free_rows_internal) return LIQ_VALUE_OUT_OF_RANGE;
        img->free_rows = true;
    }

    if (ownership_flags & LIQ_OWN_PIXELS) {
        img->free_pixels = true;
        if (!img->pixels) {
            // for simplicity of this API there's no explicit bitmap argument,
            // so the row with the lowest address is assumed to be at the start of the bitmap
            img->pixels = img->rows[0];
            for(unsigned int i=1; i < img->height; i++) {
                img->pixels = MIN(img->pixels, img->rows[i]);
            }
        }
    }

    return LIQ_OK;
}

LIQ_NONNULL static void liq_image_free_maps(liq_image *input_image);
LIQ_NONNULL static void liq_image_free_dither_map(liq_image *input_image);
LIQ_NONNULL static void liq_image_free_importance_map(liq_image *input_image);

LIQ_EXPORT LIQ_NONNULL liq_error liq_image_set_importance_map(liq_image *img, unsigned char importance_map[], size_t buffer_size, enum liq_ownership ownership) {
    if (!CHECK_STRUCT_TYPE(img, liq_image)) return LIQ_INVALID_POINTER;
    if (!CHECK_USER_POINTER(importance_map)) return LIQ_INVALID_POINTER;

    const size_t required_size = (size_t)img->width * (size_t)img->height;
    if (buffer_size < required_size) {
        return LIQ_BUFFER_TOO_SMALL;
    }

    if (ownership == LIQ_COPY_PIXELS) {
        unsigned char *tmp = img->malloc(required_size);
        if (!tmp) {
            return LIQ_OUT_OF_MEMORY;
        }
        memcpy(tmp, importance_map, required_size);
        importance_map = tmp;
    } else if (ownership != LIQ_OWN_PIXELS) {
        return LIQ_UNSUPPORTED;
    }

    liq_image_free_importance_map(img);
    img->importance_map = importance_map;

    return LIQ_OK;
}

LIQ_EXPORT LIQ_NONNULL liq_error liq_image_set_background(liq_image *img, liq_image *background)
{
    if (!CHECK_STRUCT_TYPE(img, liq_image)) return LIQ_INVALID_POINTER;
    if (!CHECK_STRUCT_TYPE(background, liq_image)) return LIQ_INVALID_POINTER;

    if (background->background) {
        return LIQ_UNSUPPORTED;
    }
    if (img->width != background->width || img->height != background->height) {
        return LIQ_BUFFER_TOO_SMALL;
    }

    if (img->background) {
        liq_image_destroy(img->background);
    }

    img->background = background;
    liq_image_free_dither_map(img); // Force it to be re-analyzed with the background

    return LIQ_OK;
}

LIQ_NONNULL static bool check_image_size(const liq_attr *attr, const int width, const int height)
{
    if (!CHECK_STRUCT_TYPE(attr, liq_attr)) {
        return false;
    }

    if (width <= 0 || height <= 0) {
        liq_log_error(attr, "width and height must be > 0");
        return false;
    }

    if (width > INT_MAX/sizeof(rgba_pixel)/height || width > INT_MAX/16/sizeof(f_pixel) || height > INT_MAX/sizeof(size_t)) {
        liq_log_error(attr, "image too large");
        return false;
    }
    return true;
}

LIQ_EXPORT liq_image *liq_image_create_custom(const liq_attr *attr, liq_image_get_rgba_row_callback *row_callback, void* user_info, int width, int height, double gamma)
{
    if (!check_image_size(attr, width, height)) {
        return NULL;
    }
    return liq_image_create_internal(attr, NULL, row_callback, user_info, width, height, gamma);
}

LIQ_EXPORT liq_image *liq_image_create_rgba_rows(const liq_attr *attr, void *const rows[], int width, int height, double gamma)
{
    if (!check_image_size(attr, width, height)) {
        return NULL;
    }

    for(int i=0; i < height; i++) {
        if (!CHECK_USER_POINTER(rows+i) || !CHECK_USER_POINTER(rows[i])) {
            liq_log_error(attr, "invalid row pointers");
            return NULL;
        }
    }
    return liq_image_create_internal(attr, (rgba_pixel**)rows, NULL, NULL, width, height, gamma);
}

LIQ_EXPORT LIQ_NONNULL liq_image *liq_image_create_rgba(const liq_attr *attr, const void* bitmap, int width, int height, double gamma)
{
    if (!check_image_size(attr, width, height)) {
        return NULL;
    }
    if (!CHECK_USER_POINTER(bitmap)) {
        liq_log_error(attr, "invalid bitmap pointer");
        return NULL;
    }

    rgba_pixel *const pixels = (rgba_pixel *const)bitmap;
    rgba_pixel **rows = attr->malloc(sizeof(rows[0])*height);
    if (!rows) return NULL;

    for(int i=0; i < height; i++) {
        rows[i] = pixels + width * i;
    }

    liq_image *image = liq_image_create_internal(attr, rows, NULL, NULL, width, height, gamma);
    if (!image) {
        attr->free(rows);
        return NULL;
    }
    image->free_rows = true;
    image->free_rows_internal = true;
    return image;
}

NEVER_INLINE LIQ_EXPORT void liq_executing_user_callback(liq_image_get_rgba_row_callback *callback, liq_color *temp_row, int row, int width, void *user_info);
LIQ_EXPORT void liq_executing_user_callback(liq_image_get_rgba_row_callback *callback, liq_color *temp_row, int row, int width, void *user_info)
{
    assert(callback);
    assert(temp_row);
    callback(temp_row, row, width, user_info);
}

LIQ_NONNULL inline static bool liq_image_has_rgba_pixels(const liq_image *img)
{
    if (!CHECK_STRUCT_TYPE(img, liq_image)) {
        return false;
    }
    return img->rows || (img->temp_row && img->row_callback);
}

LIQ_NONNULL inline static bool liq_image_can_use_rgba_rows(const liq_image *img)
{
    assert(liq_image_has_rgba_pixels(img));
    return img->rows;
}

LIQ_NONNULL static const rgba_pixel *liq_image_get_row_rgba(liq_image *img, unsigned int row)
{
    if (liq_image_can_use_rgba_rows(img)) {
        return img->rows[row];
    }

    assert(img->temp_row);
    rgba_pixel *temp_row = img->temp_row + LIQ_TEMP_ROW_WIDTH(img->width) * omp_get_thread_num();
    if (img->rows) {
        memcpy(temp_row, img->rows[row], img->width * sizeof(temp_row[0]));
    } else {
        liq_executing_user_callback(img->row_callback, (liq_color*)temp_row, row, img->width, img->row_callback_user_info);
    }

    return temp_row;
}

LIQ_NONNULL static void convert_row_to_f(liq_image *img, f_pixel *row_f_pixels, const unsigned int row, const float gamma_lut[])
{
    assert(row_f_pixels);
    assert(!USE_SSE || 0 == ((uintptr_t)row_f_pixels & 15));

    const rgba_pixel *const row_pixels = liq_image_get_row_rgba(img, row);

    for(unsigned int col=0; col < img->width; col++) {
        row_f_pixels[col] = rgba_to_f(gamma_lut, row_pixels[col]);
    }
}

LIQ_NONNULL static bool liq_image_get_row_f_init(liq_image *img)
{
    assert(omp_get_thread_num() == 0);
    if (img->f_pixels) {
        return true;
    }
    if (!liq_image_should_use_low_memory(img, false)) {
        img->f_pixels = img->malloc(sizeof(img->f_pixels[0]) * img->width * img->height);
    }
    if (!img->f_pixels) {
        return liq_image_use_low_memory(img);
    }

    if (!liq_image_has_rgba_pixels(img)) {
        return false;
    }

    float gamma_lut[256];
    to_f_set_gamma(gamma_lut, img->gamma);
    for(unsigned int i=0; i < img->height; i++) {
        convert_row_to_f(img, &img->f_pixels[i*img->width], i, gamma_lut);
    }
    return true;
}

LIQ_NONNULL static const f_pixel *liq_image_get_row_f(liq_image *img, unsigned int row)
{
    if (!img->f_pixels) {
        assert(img->temp_f_row); // init should have done that
        float gamma_lut[256];
        to_f_set_gamma(gamma_lut, img->gamma);
        f_pixel *row_for_thread = img->temp_f_row + LIQ_TEMP_ROW_WIDTH(img->width) * omp_get_thread_num();
        convert_row_to_f(img, row_for_thread, row, gamma_lut);
        return row_for_thread;
    }
    return img->f_pixels + img->width * row;
}

LIQ_EXPORT LIQ_NONNULL int liq_image_get_width(const liq_image *input_image)
{
    if (!CHECK_STRUCT_TYPE(input_image, liq_image)) return -1;
    return input_image->width;
}

LIQ_EXPORT LIQ_NONNULL int liq_image_get_height(const liq_image *input_image)
{
    if (!CHECK_STRUCT_TYPE(input_image, liq_image)) return -1;
    return input_image->height;
}

typedef void free_func(void*);

LIQ_NONNULL static free_func *get_default_image_free_func(liq_image *img)
{
    // When default allocator is used then user-supplied pointers must be freed with free()
    if (img->free != liq_aligned_free) {
        return img->free;
    }
    return free;
}

LIQ_NONNULL static free_func *get_default_rows_free_func(liq_image *img)
{
    // When default allocator is used then user-supplied pointers must be freed with free()
    if (img->free_rows_internal || img->free != liq_aligned_free) {
        return img->free;
    }
    return free;
}

LIQ_NONNULL static void liq_image_free_rgba_source(liq_image *input_image)
{
    if (input_image->free_pixels && input_image->pixels) {
        get_default_image_free_func(input_image)(input_image->pixels);
        input_image->pixels = NULL;
    }

    if (input_image->free_rows && input_image->rows) {
        get_default_rows_free_func(input_image)(input_image->rows);
        input_image->rows = NULL;
    }
}

LIQ_NONNULL static void liq_image_free_importance_map(liq_image *input_image) {
    if (input_image->importance_map) {
        input_image->free(input_image->importance_map);
        input_image->importance_map = NULL;
    }
}

LIQ_NONNULL static void liq_image_free_maps(liq_image *input_image) {
    liq_image_free_importance_map(input_image);

    if (input_image->edges) {
        input_image->free(input_image->edges);
        input_image->edges = NULL;
    }
    liq_image_free_dither_map(input_image);
}

LIQ_NONNULL static void liq_image_free_dither_map(liq_image *input_image) {
    if (input_image->dither_map) {
        input_image->free(input_image->dither_map);
        input_image->dither_map = NULL;
    }
}

LIQ_EXPORT LIQ_NONNULL void liq_image_destroy(liq_image *input_image)
{
    if (!CHECK_STRUCT_TYPE(input_image, liq_image)) return;

    liq_image_free_rgba_source(input_image);

    liq_image_free_maps(input_image);

    if (input_image->f_pixels) {
        input_image->free(input_image->f_pixels);
    }

    if (input_image->temp_row) {
        input_image->free(input_image->temp_row);
    }

    if (input_image->temp_f_row) {
        input_image->free(input_image->temp_f_row);
    }

    if (input_image->background) {
        liq_image_destroy(input_image->background);
    }

    input_image->magic_header = liq_freed_magic;
    input_image->free(input_image);
}

LIQ_EXPORT liq_histogram* liq_histogram_create(const liq_attr* attr)
{
    if (!CHECK_STRUCT_TYPE(attr, liq_attr)) {
        return NULL;
    }

    liq_histogram *hist = attr->malloc(sizeof(liq_histogram));
    if (!hist) return NULL;
    *hist = (liq_histogram) {
        .magic_header = liq_histogram_magic,
        .malloc = attr->malloc,
        .free = attr->free,

        .ignorebits = MAX(attr->min_posterization_output, attr->min_posterization_input),
    };
    return hist;
}

LIQ_EXPORT LIQ_NONNULL void liq_histogram_destroy(liq_histogram *hist)
{
    if (!CHECK_STRUCT_TYPE(hist, liq_histogram)) return;
    hist->magic_header = liq_freed_magic;

    pam_freeacolorhash(hist->acht);
    hist->free(hist);
}

LIQ_EXPORT LIQ_NONNULL liq_result *liq_quantize_image(liq_attr *attr, liq_image *img)
{
    liq_result *res;
    if (LIQ_OK != liq_image_quantize(img, attr, &res)) {
        return NULL;
    }
    return res;
}

LIQ_EXPORT LIQ_NONNULL liq_error liq_image_quantize(liq_image *const img, liq_attr *const attr, liq_result **result_output)
{
    if (!CHECK_STRUCT_TYPE(attr, liq_attr)) return LIQ_INVALID_POINTER;
    if (!liq_image_has_rgba_pixels(img)) {
        return LIQ_UNSUPPORTED;
    }

    liq_histogram *hist = liq_histogram_create(attr);
    if (!hist) {
        return LIQ_OUT_OF_MEMORY;
    }
    liq_error err = liq_histogram_add_image(hist, attr, img);
    if (LIQ_OK != err) {
        liq_histogram_destroy(hist);
        return err;
    }

    err = liq_histogram_quantize_internal(hist, attr, false, result_output);
    liq_histogram_destroy(hist);

    return err;
}

LIQ_EXPORT LIQ_NONNULL liq_error liq_histogram_quantize(liq_histogram *input_hist, liq_attr *attr, liq_result **result_output) {
    return liq_histogram_quantize_internal(input_hist, attr, true, result_output);
}

LIQ_NONNULL static liq_error liq_histogram_quantize_internal(liq_histogram *input_hist, liq_attr *attr, bool fixed_result_colors, liq_result **result_output)
{
    if (!CHECK_USER_POINTER(result_output)) return LIQ_INVALID_POINTER;
    *result_output = NULL;

    if (!CHECK_STRUCT_TYPE(attr, liq_attr)) return LIQ_INVALID_POINTER;
    if (!CHECK_STRUCT_TYPE(input_hist, liq_histogram)) return LIQ_INVALID_POINTER;

    if (liq_progress(attr, 0)) return LIQ_ABORTED;

    histogram *hist;
    liq_error err = finalize_histogram(input_hist, attr, &hist);
    if (err != LIQ_OK) {
        return err;
    }

    err = pngquant_quantize(hist, attr, input_hist->fixed_colors_count, input_hist->fixed_colors, input_hist->gamma, fixed_result_colors, result_output);
    pam_freeacolorhist(hist);

    return err;
}

LIQ_EXPORT LIQ_NONNULL liq_error liq_set_dithering_level(liq_result *res, float dither_level)
{
    if (!CHECK_STRUCT_TYPE(res, liq_result)) return LIQ_INVALID_POINTER;

    if (res->remapping) {
        liq_remapping_result_destroy(res->remapping);
        res->remapping = NULL;
    }

    if (dither_level < 0 || dither_level > 1.0f) return LIQ_VALUE_OUT_OF_RANGE;
    res->dither_level = dither_level;
    return LIQ_OK;
}

LIQ_NONNULL static liq_remapping_result *liq_remapping_result_create(liq_result *result)
{
    if (!CHECK_STRUCT_TYPE(result, liq_result)) {
        return NULL;
    }

    liq_remapping_result *res = result->malloc(sizeof(liq_remapping_result));
    if (!res) return NULL;
    *res = (liq_remapping_result) {
        .magic_header = liq_remapping_result_magic,
        .malloc = result->malloc,
        .free = result->free,
        .dither_level = result->dither_level,
        .use_dither_map = result->use_dither_map,
        .palette_error = result->palette_error,
        .gamma = result->gamma,
        .palette = pam_duplicate_colormap(result->palette),
        .progress_callback = result->progress_callback,
        .progress_callback_user_info = result->progress_callback_user_info,
        .progress_stage1 = result->use_dither_map ? 20 : 0,
    };
    return res;
}

LIQ_EXPORT LIQ_NONNULL double liq_get_output_gamma(const liq_result *result)
{
    if (!CHECK_STRUCT_TYPE(result, liq_result)) return -1;

    return result->gamma;
}

LIQ_NONNULL static void liq_remapping_result_destroy(liq_remapping_result *result)
{
    if (!CHECK_STRUCT_TYPE(result, liq_remapping_result)) return;

    if (result->palette) pam_freecolormap(result->palette);
    if (result->pixels) result->free(result->pixels);

    result->magic_header = liq_freed_magic;
    result->free(result);
}

LIQ_EXPORT LIQ_NONNULL void liq_result_destroy(liq_result *res)
{
    if (!CHECK_STRUCT_TYPE(res, liq_result)) return;

    memset(&res->int_palette, 0, sizeof(liq_palette));

    if (res->remapping) {
        memset(&res->remapping->int_palette, 0, sizeof(liq_palette));
        liq_remapping_result_destroy(res->remapping);
    }

    pam_freecolormap(res->palette);

    res->magic_header = liq_freed_magic;
    res->free(res);
}


LIQ_EXPORT LIQ_NONNULL double liq_get_quantization_error(const liq_result *result) {
    if (!CHECK_STRUCT_TYPE(result, liq_result)) return -1;

    if (result->palette_error >= 0) {
        return mse_to_standard_mse(result->palette_error);
    }

    return -1;
}

LIQ_EXPORT LIQ_NONNULL double liq_get_remapping_error(const liq_result *result) {
    if (!CHECK_STRUCT_TYPE(result, liq_result)) return -1;

    if (result->remapping && result->remapping->palette_error >= 0) {
        return mse_to_standard_mse(result->remapping->palette_error);
    }

    return -1;
}

LIQ_EXPORT LIQ_NONNULL int liq_get_quantization_quality(const liq_result *result) {
    if (!CHECK_STRUCT_TYPE(result, liq_result)) return -1;

    if (result->palette_error >= 0) {
        return mse_to_quality(result->palette_error);
    }

    return -1;
}

LIQ_EXPORT LIQ_NONNULL int liq_get_remapping_quality(const liq_result *result) {
    if (!CHECK_STRUCT_TYPE(result, liq_result)) return -1;

    if (result->remapping && result->remapping->palette_error >= 0) {
        return mse_to_quality(result->remapping->palette_error);
    }

    return -1;
}

LIQ_NONNULL static int compare_popularity(const void *ch1, const void *ch2)
{
    const float v1 = ((const colormap_item*)ch1)->popularity;
    const float v2 = ((const colormap_item*)ch2)->popularity;
    return v1 > v2 ? -1 : 1;
}

LIQ_NONNULL static void sort_palette_qsort(colormap *map, int start, int nelem)
{
    if (!nelem) return;
    qsort(map->palette + start, nelem, sizeof(map->palette[0]), compare_popularity);
}

#define SWAP_PALETTE(map, a,b) { \
    const colormap_item tmp = (map)->palette[(a)]; \
    (map)->palette[(a)] = (map)->palette[(b)]; \
    (map)->palette[(b)] = tmp; }

LIQ_NONNULL static void sort_palette(colormap *map, const liq_attr *options)
{
    /*
    ** Step 3.5 [GRR]: remap the palette colors so that all entries with
    ** the maximal alpha value (i.e., fully opaque) are at the end and can
    ** therefore be omitted from the tRNS chunk.
    */
    if (options->last_index_transparent) {
        for(unsigned int i=0; i < map->colors; i++) {
            if (map->palette[i].acolor.a < 1.f/256.f) {
                const unsigned int old = i, transparent_dest = map->colors-1;

                SWAP_PALETTE(map, transparent_dest, old);

                /* colors sorted by popularity make pngs slightly more compressible */
                sort_palette_qsort(map, 0, map->colors-1);
                return;
            }
        }
    }

    unsigned int non_fixed_colors = 0;
    for(unsigned int i = 0; i < map->colors; i++) {
        if (map->palette[i].fixed) {
            break;
        }
        non_fixed_colors++;
    }

    /* move transparent colors to the beginning to shrink trns chunk */
    unsigned int num_transparent = 0;
    for(unsigned int i = 0; i < non_fixed_colors; i++) {
        if (map->palette[i].acolor.a < 255.f/256.f) {
            // current transparent color is swapped with earlier opaque one
            if (i != num_transparent) {
                SWAP_PALETTE(map, num_transparent, i);
                i--;
            }
            num_transparent++;
        }
    }

    liq_verbose_printf(options, "  eliminated opaque tRNS-chunk entries...%d entr%s transparent", num_transparent, (num_transparent == 1)? "y" : "ies");

    /* colors sorted by popularity make pngs slightly more compressible
     * opaque and transparent are sorted separately
     */
    sort_palette_qsort(map, 0, num_transparent);
    sort_palette_qsort(map, num_transparent, non_fixed_colors - num_transparent);

    if (non_fixed_colors > 9 && map->colors > 16) {
        SWAP_PALETTE(map, 7, 1); // slightly improves compression
        SWAP_PALETTE(map, 8, 2);
        SWAP_PALETTE(map, 9, 3);
    }
}

inline static unsigned int posterize_channel(unsigned int color, unsigned int bits)
{
    return (color & ~((1<<bits)-1)) | (color >> (8-bits));
}

LIQ_NONNULL static void set_rounded_palette(liq_palette *const dest, colormap *const map, const double gamma, unsigned int posterize)
{
    float gamma_lut[256];
    to_f_set_gamma(gamma_lut, gamma);

    dest->count = map->colors;
    for(unsigned int x = 0; x < map->colors; ++x) {
        rgba_pixel px = f_to_rgb(gamma, map->palette[x].acolor);

        px.r = posterize_channel(px.r, posterize);
        px.g = posterize_channel(px.g, posterize);
        px.b = posterize_channel(px.b, posterize);
        px.a = posterize_channel(px.a, posterize);

        map->palette[x].acolor = rgba_to_f(gamma_lut, px); /* saves rounding error introduced by to_rgb, which makes remapping & dithering more accurate */

        if (!px.a && !map->palette[x].fixed) {
            px.r = 71; px.g = 112; px.b = 76;
        }

        dest->entries[x] = (liq_color){.r=px.r,.g=px.g,.b=px.b,.a=px.a};
    }
}

LIQ_EXPORT LIQ_NONNULL const liq_palette *liq_get_palette(liq_result *result)
{
    if (!CHECK_STRUCT_TYPE(result, liq_result)) return NULL;

    if (result->remapping && result->remapping->int_palette.count) {
        return &result->remapping->int_palette;
    }

    if (!result->int_palette.count) {
        set_rounded_palette(&result->int_palette, result->palette, result->gamma, result->min_posterization_output);
    }
    return &result->int_palette;
}

LIQ_NONNULL static float remap_to_palette(liq_image *const input_image, unsigned char *const *const output_pixels, colormap *const map)
{
    const int rows = input_image->height;
    const unsigned int cols = input_image->width;
    double remapping_error=0;

    if (!liq_image_get_row_f_init(input_image)) {
        return -1;
    }
    if (input_image->background && !liq_image_get_row_f_init(input_image->background)) {
        return -1;
    }

    const colormap_item *acolormap = map->palette;

    struct nearest_map *const n = nearest_init(map);
    liq_image *background = input_image->background;
    const int transparent_index = background ? nearest_search(n, &(f_pixel){0,0,0,0}, 0, NULL) : -1;
    if (background && acolormap[transparent_index].acolor.a > 1.f/256.f) {
        // palette unsuitable for using the bg
        background = NULL;
    }


    const unsigned int max_threads = omp_get_max_threads();
    LIQ_ARRAY(kmeans_state, average_color, (KMEANS_CACHE_LINE_GAP+map->colors) * max_threads);
    kmeans_init(map, max_threads, average_color);

#if __GNUC__ >= 9 || __clang__
    #pragma omp parallel for if (rows*cols > 3000) \
        schedule(static) default(none) shared(background,acolormap,average_color,cols,input_image,map,n,output_pixels,rows,transparent_index) reduction(+:remapping_error)
#endif
    for(int row = 0; row < rows; ++row) {
        const f_pixel *const row_pixels = liq_image_get_row_f(input_image, row);
        const f_pixel *const bg_pixels = background && acolormap[transparent_index].acolor.a < 1.f/256.f ? liq_image_get_row_f(background, row) : NULL;

        unsigned int last_match=0;
        for(unsigned int col = 0; col < cols; ++col) {
            float diff;
            last_match = nearest_search(n, &row_pixels[col], last_match, &diff);
            if (bg_pixels) {
                float bg_diff = colordifference(bg_pixels[col], acolormap[last_match].acolor);
                if (bg_diff <= diff) {
                    diff = bg_diff;
                    last_match = transparent_index;
                }
            }
            output_pixels[row][col] = last_match;

            remapping_error += diff;
            if (last_match != transparent_index) {
                kmeans_update_color(row_pixels[col], 1.0, map, last_match, omp_get_thread_num(), average_color);
            }
        }
    }

    kmeans_finalize(map, max_threads, average_color);

    nearest_free(n);

    return remapping_error / (input_image->width * input_image->height);
}

inline static f_pixel get_dithered_pixel(const float dither_level, const float max_dither_error, const f_pixel thiserr, const f_pixel px)
{
    /* Use Floyd-Steinberg errors to adjust actual color. */
    const float sr = thiserr.r * dither_level,
                sg = thiserr.g * dither_level,
                sb = thiserr.b * dither_level,
                sa = thiserr.a * dither_level;

    float ratio = 1.0;
    const float max_overflow = 1.1f;
    const float max_underflow = -0.1f;

    // allowing some overflow prevents undithered bands caused by clamping of all channels
           if (px.r + sr > max_overflow)  ratio = MIN(ratio, (max_overflow -px.r)/sr);
    else { if (px.r + sr < max_underflow) ratio = MIN(ratio, (max_underflow-px.r)/sr); }
           if (px.g + sg > max_overflow)  ratio = MIN(ratio, (max_overflow -px.g)/sg);
    else { if (px.g + sg < max_underflow) ratio = MIN(ratio, (max_underflow-px.g)/sg); }
           if (px.b + sb > max_overflow)  ratio = MIN(ratio, (max_overflow -px.b)/sb);
    else { if (px.b + sb < max_underflow) ratio = MIN(ratio, (max_underflow-px.b)/sb); }

    float a = px.a + sa;
         if (a > 1.f) { a = 1.f; }
    else if (a < 0)   { a = 0; }

     // If dithering error is crazy high, don't propagate it that much
     // This prevents crazy geen pixels popping out of the blue (or red or black! ;)
     const float dither_error = sr*sr + sg*sg + sb*sb + sa*sa;
     if (dither_error > max_dither_error) {
         ratio *= 0.8f;
     } else if (dither_error < 2.f/256.f/256.f) {
        // don't dither areas that don't have noticeable error — makes file smaller
        return px;
    }

    return (f_pixel) {
        .r=px.r + sr * ratio,
        .g=px.g + sg * ratio,
        .b=px.b + sb * ratio,
        .a=a,
    };
}

/**
  Uses edge/noise map to apply dithering only to flat areas. Dithering on edges creates jagged lines, and noisy areas are "naturally" dithered.

  If output_image_is_remapped is true, only pixels noticeably changed by error diffusion will be written to output image.
 */
LIQ_NONNULL static bool remap_to_palette_floyd(liq_image *input_image, unsigned char *const output_pixels[], liq_remapping_result *quant, const float max_dither_error, const bool output_image_is_remapped)
{
    const int rows = input_image->height, cols = input_image->width;
    const unsigned char *dither_map = quant->use_dither_map ? (input_image->dither_map ? input_image->dither_map : input_image->edges) : NULL;

    const colormap *map = quant->palette;
    const colormap_item *acolormap = map->palette;

    if (!liq_image_get_row_f_init(input_image)) {
        return false;
    }
    if (input_image->background && !liq_image_get_row_f_init(input_image->background)) {
        return false;
    }

    /* Initialize Floyd-Steinberg error vectors. */
    const size_t errwidth = cols+2;
    f_pixel *restrict thiserr = input_image->malloc(errwidth * sizeof(thiserr[0]) * 2); // +2 saves from checking out of bounds access
    if (!thiserr) return false;
    f_pixel *restrict nexterr = thiserr + errwidth;
    memset(thiserr, 0, errwidth * sizeof(thiserr[0]));

    bool ok = true;
    struct nearest_map *const n = nearest_init(map);
    liq_image *background = input_image->background;
    const int transparent_index = background ? nearest_search(n, &(f_pixel){0,0,0,0}, 0, NULL) : -1;
    if (background && acolormap[transparent_index].acolor.a > 1.f/256.f) {
        // palette unsuitable for using the bg
        background = NULL;
    }

    // response to this value is non-linear and without it any value < 0.8 would give almost no dithering
    float base_dithering_level = quant->dither_level;
    base_dithering_level = 1.f - (1.f-base_dithering_level)*(1.f-base_dithering_level);

    if (dither_map) {
        base_dithering_level *= 1.f/255.f; // convert byte to float
    }
    base_dithering_level *= 15.f/16.f; // prevent small errors from accumulating

    int fs_direction = 1;
    unsigned int last_match=0;
    for (int row = 0; row < rows; ++row) {
        if (liq_remap_progress(quant, quant->progress_stage1 + row * (100.f - quant->progress_stage1) / rows)) {
            ok = false;
            break;
        }

        memset(nexterr, 0, errwidth * sizeof(nexterr[0]));

        int col = (fs_direction > 0) ? 0 : (cols - 1);
        const f_pixel *const row_pixels = liq_image_get_row_f(input_image, row);
        const f_pixel *const bg_pixels = background && acolormap[transparent_index].acolor.a < 1.f/256.f ? liq_image_get_row_f(background, row) : NULL;
        int undithered_bg_used = 0;

        do {
            float dither_level = base_dithering_level;
            if (dither_map) {
                dither_level *= dither_map[row*cols + col];
            }

            const f_pixel spx = get_dithered_pixel(dither_level, max_dither_error, thiserr[col + 1], row_pixels[col]);

            const unsigned int guessed_match = output_image_is_remapped ? output_pixels[row][col] : last_match;
            float dither_diff;
            last_match = nearest_search(n, &spx, guessed_match, &dither_diff);
            f_pixel output_px = acolormap[last_match].acolor;
            // this is for animgifs
            if (bg_pixels) {
                // if the background makes better match *with* dithering, it's a definitive win
                float bg_for_dither_diff = colordifference(spx, bg_pixels[col]);
                if (bg_for_dither_diff <= dither_diff) {
                    output_px = bg_pixels[col];
                    last_match = transparent_index;
                } else if (undithered_bg_used > 1) {
                    // the undithered fallback can cause artifacts when too many undithered pixels accumulate a big dithering error
                    // so periodically ignore undithered fallback to prevent that
                    undithered_bg_used = 0;
                } else {
                    // if dithering is not applied, there's a high risk of creating artifacts (flat areas, error accumulating badly),
                    // OTOH poor dithering disturbs static backgrounds and creates oscilalting frames that break backgrounds
                    // back and forth in two differently bad ways
                    float max_diff = colordifference(row_pixels[col], bg_pixels[col]);
                    float dithered_diff = colordifference(row_pixels[col], output_px);
                    // if dithering is worse than natural difference between frames
                    // (this rule dithers moving areas, but does not dither static areas)
                    if (dithered_diff > max_diff) {
                        // then see if an undithered color is closer to the ideal
                        float undithered_diff = colordifference(row_pixels[col], acolormap[guessed_match].acolor);
                        if (undithered_diff < max_diff) {
                            undithered_bg_used++;
                            output_px = acolormap[guessed_match].acolor;
                            last_match = guessed_match;
                        }
                    }
                }
            }

            output_pixels[row][col] = last_match;

            f_pixel err = {
                .r = (spx.r - output_px.r),
                .g = (spx.g - output_px.g),
                .b = (spx.b - output_px.b),
                .a = (spx.a - output_px.a),
            };

            // If dithering error is crazy high, don't propagate it that much
            // This prevents crazy geen pixels popping out of the blue (or red or black! ;)
            if (err.r*err.r + err.g*err.g + err.b*err.b + err.a*err.a > max_dither_error) {
                err.r *= 0.75f;
                err.g *= 0.75f;
                err.b *= 0.75f;
                err.a *= 0.75f;
            }

            /* Propagate Floyd-Steinberg error terms. */
            if (fs_direction > 0) {
                thiserr[col + 2].a += err.a * (7.f/16.f);
                thiserr[col + 2].r += err.r * (7.f/16.f);
                thiserr[col + 2].g += err.g * (7.f/16.f);
                thiserr[col + 2].b += err.b * (7.f/16.f);

                nexterr[col + 2].a  = err.a * (1.f/16.f);
                nexterr[col + 2].r  = err.r * (1.f/16.f);
                nexterr[col + 2].g  = err.g * (1.f/16.f);
                nexterr[col + 2].b  = err.b * (1.f/16.f);

                nexterr[col + 1].a += err.a * (5.f/16.f);
                nexterr[col + 1].r += err.r * (5.f/16.f);
                nexterr[col + 1].g += err.g * (5.f/16.f);
                nexterr[col + 1].b += err.b * (5.f/16.f);

                nexterr[col    ].a += err.a * (3.f/16.f);
                nexterr[col    ].r += err.r * (3.f/16.f);
                nexterr[col    ].g += err.g * (3.f/16.f);
                nexterr[col    ].b += err.b * (3.f/16.f);

            } else {
                thiserr[col    ].a += err.a * (7.f/16.f);
                thiserr[col    ].r += err.r * (7.f/16.f);
                thiserr[col    ].g += err.g * (7.f/16.f);
                thiserr[col    ].b += err.b * (7.f/16.f);

                nexterr[col    ].a  = err.a * (1.f/16.f);
                nexterr[col    ].r  = err.r * (1.f/16.f);
                nexterr[col    ].g  = err.g * (1.f/16.f);
                nexterr[col    ].b  = err.b * (1.f/16.f);

                nexterr[col + 1].a += err.a * (5.f/16.f);
                nexterr[col + 1].r += err.r * (5.f/16.f);
                nexterr[col + 1].g += err.g * (5.f/16.f);
                nexterr[col + 1].b += err.b * (5.f/16.f);

                nexterr[col + 2].a += err.a * (3.f/16.f);
                nexterr[col + 2].r += err.r * (3.f/16.f);
                nexterr[col + 2].g += err.g * (3.f/16.f);
                nexterr[col + 2].b += err.b * (3.f/16.f);
            }

            // remapping is done in zig-zag
            col += fs_direction;
            if (fs_direction > 0) {
                if (col >= cols) break;
            } else {
                if (col < 0) break;
            }
        } while(1);

        f_pixel *const temperr = thiserr;
        thiserr = nexterr;
        nexterr = temperr;
        fs_direction = -fs_direction;
    }

    input_image->free(MIN(thiserr, nexterr)); // MIN because pointers were swapped
    nearest_free(n);

    return ok;
}

/* fixed colors are always included in the palette, so it would be wasteful to duplicate them in palette from histogram */
LIQ_NONNULL static void remove_fixed_colors_from_histogram(histogram *hist, const int fixed_colors_count, const f_pixel fixed_colors[], const float target_mse)
{
    const float max_difference = MAX(target_mse/2.f, 2.f/256.f/256.f);
    if (fixed_colors_count) {
        for(int j=0; j < hist->size; j++) {
            for(unsigned int i=0; i < fixed_colors_count; i++) {
                if (colordifference(hist->achv[j].acolor, fixed_colors[i]) < max_difference) {
                    hist->achv[j] = hist->achv[--hist->size]; // remove color from histogram by overwriting with the last entry
                    j--; break; // continue searching histogram
                }
            }
        }
    }
}

LIQ_EXPORT LIQ_NONNULL liq_error liq_histogram_add_colors(liq_histogram *input_hist, const liq_attr *options, const liq_histogram_entry entries[], int num_entries, double gamma)
{
    if (!CHECK_STRUCT_TYPE(options, liq_attr)) return LIQ_INVALID_POINTER;
    if (!CHECK_STRUCT_TYPE(input_hist, liq_histogram)) return LIQ_INVALID_POINTER;
    if (!CHECK_USER_POINTER(entries)) return LIQ_INVALID_POINTER;
    if (gamma < 0 || gamma >= 1.0) return LIQ_VALUE_OUT_OF_RANGE;
    if (num_entries <= 0 || num_entries > 1<<30) return LIQ_VALUE_OUT_OF_RANGE;

    if (input_hist->ignorebits > 0 && input_hist->had_image_added) {
        return LIQ_UNSUPPORTED;
    }
    input_hist->ignorebits = 0;

    input_hist->had_image_added = true;
    input_hist->gamma = gamma ? gamma : 0.45455;

    if (!input_hist->acht) {
        input_hist->acht = pam_allocacolorhash(~0, num_entries*num_entries, 0, options->malloc, options->free);
        if (!input_hist->acht) {
            return LIQ_OUT_OF_MEMORY;
        }
    }
    // Fake image size. It's only for hash size estimates.
    if (!input_hist->acht->cols) {
        input_hist->acht->cols = num_entries;
    }
    input_hist->acht->rows += num_entries;

    const unsigned int hash_size = input_hist->acht->hash_size;
    for(int i=0; i < num_entries; i++) {
        const rgba_pixel rgba = {
            .r = entries[i].color.r,
            .g = entries[i].color.g,
            .b = entries[i].color.b,
            .a = entries[i].color.a,
        };
        union rgba_as_int px = {rgba};
        unsigned int hash;
        if (px.rgba.a) {
            hash = px.l % hash_size;
        } else {
            hash=0; px.l=0;
        }
        if (!pam_add_to_hash(input_hist->acht, hash, entries[i].count, px, i, num_entries)) {
            return LIQ_OUT_OF_MEMORY;
        }
    }

    return LIQ_OK;
}

LIQ_EXPORT LIQ_NONNULL liq_error liq_histogram_add_image(liq_histogram *input_hist, const liq_attr *options, liq_image *input_image)
{
    if (!CHECK_STRUCT_TYPE(options, liq_attr)) return LIQ_INVALID_POINTER;
    if (!CHECK_STRUCT_TYPE(input_hist, liq_histogram)) return LIQ_INVALID_POINTER;
    if (!CHECK_STRUCT_TYPE(input_image, liq_image)) return LIQ_INVALID_POINTER;

    const unsigned int cols = input_image->width, rows = input_image->height;

    if (!input_image->importance_map && options->use_contrast_maps) {
        contrast_maps(input_image);
    }

    input_hist->gamma = input_image->gamma;

    for(int i = 0; i < input_image->fixed_colors_count; i++) {
        liq_error res = liq_histogram_add_fixed_color_f(input_hist, input_image->fixed_colors[i]);
        if (res != LIQ_OK) {
            return res;
        }
    }

    /*
     ** Step 2: attempt to make a histogram of the colors, unclustered.
     ** If at first we don't succeed, increase ignorebits to increase color
     ** coherence and try again.
     */

    if (liq_progress(options, options->progress_stage1 * 0.4f)) {
        return LIQ_ABORTED;
    }

    const bool all_rows_at_once = liq_image_can_use_rgba_rows(input_image);

    // Usual solution is to start from scratch when limit is exceeded, but that's not possible if it's not
    // the first image added
    const unsigned int max_histogram_entries = input_hist->had_image_added ? ~0 : options->max_histogram_entries;
    do {
        if (!input_hist->acht) {
            input_hist->acht = pam_allocacolorhash(max_histogram_entries, rows*cols, input_hist->ignorebits, options->malloc, options->free);
        }
        if (!input_hist->acht) return LIQ_OUT_OF_MEMORY;

        // histogram uses noise contrast map for importance. Color accuracy in noisy areas is not very important.
        // noise map does not include edges to avoid ruining anti-aliasing
        for(unsigned int row=0; row < rows; row++) {
            bool added_ok;
            if (all_rows_at_once) {
                added_ok = pam_computeacolorhash(input_hist->acht, (const rgba_pixel *const *)input_image->rows, cols, rows, input_image->importance_map);
                if (added_ok) break;
            } else {
                const rgba_pixel* rows_p[1] = { liq_image_get_row_rgba(input_image, row) };
                added_ok = pam_computeacolorhash(input_hist->acht, rows_p, cols, 1, input_image->importance_map ? &input_image->importance_map[row * cols] : NULL);
            }
            if (!added_ok) {
                input_hist->ignorebits++;
                liq_verbose_printf(options, "  too many colors! Scaling colors to improve clustering... %d", input_hist->ignorebits);
                pam_freeacolorhash(input_hist->acht);
                input_hist->acht = NULL;
                if (liq_progress(options, options->progress_stage1 * 0.6f)) return LIQ_ABORTED;
                break;
            }
        }
    } while(!input_hist->acht);

    input_hist->had_image_added = true;

    liq_image_free_importance_map(input_image);

    if (input_image->free_pixels && input_image->f_pixels) {
        liq_image_free_rgba_source(input_image); // bow can free the RGBA source if copy has been made in f_pixels
    }

    return LIQ_OK;
}

LIQ_NONNULL static liq_error finalize_histogram(liq_histogram *input_hist, liq_attr *options, histogram **hist_output)
{
    if (liq_progress(options, options->progress_stage1 * 0.9f)) {
        return LIQ_ABORTED;
    }

    if (!input_hist->acht) {
        return LIQ_BITMAP_NOT_AVAILABLE;
    }

    histogram *hist = pam_acolorhashtoacolorhist(input_hist->acht, input_hist->gamma, options->malloc, options->free);
    pam_freeacolorhash(input_hist->acht);
    input_hist->acht = NULL;

    if (!hist) {
        return LIQ_OUT_OF_MEMORY;
    }
    liq_verbose_printf(options, "  made histogram...%d colors found", hist->size);
    remove_fixed_colors_from_histogram(hist, input_hist->fixed_colors_count, input_hist->fixed_colors, options->target_mse);

    *hist_output = hist;
    return LIQ_OK;
}

/**
 Builds two maps:
    importance_map - approximation of areas with high-frequency noise, except straight edges. 1=flat, 0=noisy.
    edges - noise map including all edges
 */
LIQ_NONNULL static void contrast_maps(liq_image *image)
{
    const unsigned int cols = image->width, rows = image->height;
    if (cols < 4 || rows < 4 || (3*cols*rows) > LIQ_HIGH_MEMORY_LIMIT) {
        return;
    }

    unsigned char *restrict noise = image->importance_map ? image->importance_map : image->malloc(cols*rows);
    image->importance_map = NULL;
    unsigned char *restrict edges = image->edges ? image->edges : image->malloc(cols*rows);
    image->edges = NULL;

    unsigned char *restrict tmp = image->malloc(cols*rows);

    if (!noise || !edges || !tmp || !liq_image_get_row_f_init(image)) {
        image->free(noise);
        image->free(edges);
        image->free(tmp);
        return;
    }

    const f_pixel *curr_row, *prev_row, *next_row;
    curr_row = prev_row = next_row = liq_image_get_row_f(image, 0);

    for (unsigned int j=0; j < rows; j++) {
        prev_row = curr_row;
        curr_row = next_row;
        next_row = liq_image_get_row_f(image, MIN(rows-1,j+1));

        f_pixel prev, curr = curr_row[0], next=curr;
        for (unsigned int i=0; i < cols; i++) {
            prev=curr;
            curr=next;
            next = curr_row[MIN(cols-1,i+1)];

            // contrast is difference between pixels neighbouring horizontally and vertically
            const float a = fabsf(prev.a+next.a - curr.a*2.f),
                        r = fabsf(prev.r+next.r - curr.r*2.f),
                        g = fabsf(prev.g+next.g - curr.g*2.f),
                        b = fabsf(prev.b+next.b - curr.b*2.f);

            const f_pixel prevl = prev_row[i];
            const f_pixel nextl = next_row[i];

            const float a1 = fabsf(prevl.a+nextl.a - curr.a*2.f),
                        r1 = fabsf(prevl.r+nextl.r - curr.r*2.f),
                        g1 = fabsf(prevl.g+nextl.g - curr.g*2.f),
                        b1 = fabsf(prevl.b+nextl.b - curr.b*2.f);

            const float horiz = MAX(MAX(a,r),MAX(g,b));
            const float vert = MAX(MAX(a1,r1),MAX(g1,b1));
            const float edge = MAX(horiz,vert);
            float z = edge - fabsf(horiz-vert)*.5f;
            z = 1.f - MAX(z,MIN(horiz,vert));
            z *= z; // noise is amplified
            z *= z;
            // 85 is about 1/3rd of weight (not 0, because noisy pixels still need to be included, just not as precisely).
            const unsigned int z_int = 80 + (unsigned int)(z * 176.f);
            noise[j*cols+i] = MIN(z_int, 255);
            const int e_int = 255 - (int)(edge * 256.f);
            edges[j*cols+i] = e_int > 0 ? MIN(e_int, 255) : 0;
        }
    }

    // noise areas are shrunk and then expanded to remove thin edges from the map
    liq_max3(noise, tmp, cols, rows);
    liq_max3(tmp, noise, cols, rows);

    liq_blur(noise, tmp, noise, cols, rows, 3);

    liq_max3(noise, tmp, cols, rows);

    liq_min3(tmp, noise, cols, rows);
    liq_min3(noise, tmp, cols, rows);
    liq_min3(tmp, noise, cols, rows);

    liq_min3(edges, tmp, cols, rows);
    liq_max3(tmp, edges, cols, rows);
    for(unsigned int i=0; i < cols*rows; i++) edges[i] = MIN(noise[i], edges[i]);

    image->free(tmp);

    image->importance_map = noise;
    image->edges = edges;
}

/**
 * Builds map of neighbor pixels mapped to the same palette entry
 *
 * For efficiency/simplicity it mainly looks for same consecutive pixels horizontally
 * and peeks 1 pixel above/below. Full 2d algorithm doesn't improve it significantly.
 * Correct flood fill doesn't have visually good properties.
 */
LIQ_NONNULL static void update_dither_map(liq_image *input_image, unsigned char *const *const row_pointers, colormap *map)
{
    const unsigned int width = input_image->width;
    const unsigned int height = input_image->height;
    unsigned char *const edges = input_image->edges;

    for(unsigned int row=0; row < height; row++) {
        unsigned char lastpixel = row_pointers[row][0];
        unsigned int lastcol=0;

        for(unsigned int col=1; col < width; col++) {
            const unsigned char px = row_pointers[row][col];
            if (input_image->background && map->palette[px].acolor.a < 1.f/256.f) {
                // Transparency may or may not create an edge. When there's an explicit background set, assume no edge.
                continue;
            }

            if (px != lastpixel || col == width-1) {
                int neighbor_count = 10 * (col-lastcol);

                unsigned int i=lastcol;
                while(i < col) {
                    if (row > 0) {
                        unsigned char pixelabove = row_pointers[row-1][i];
                        if (pixelabove == lastpixel) neighbor_count += 15;
                    }
                    if (row < height-1) {
                        unsigned char pixelbelow = row_pointers[row+1][i];
                        if (pixelbelow == lastpixel) neighbor_count += 15;
                    }
                    i++;
                }

                while(lastcol <= col) {
                    int e = edges[row*width + lastcol];
                    edges[row*width + lastcol++] = (e+128) * (255.f/(255+128)) * (1.f - 20.f / (20 + neighbor_count));
                }
                lastpixel = px;
            }
        }
    }
    input_image->dither_map = input_image->edges;
    input_image->edges = NULL;
}

/**
 * Palette can be NULL, in which case it creates a new palette from scratch.
 */
static colormap *add_fixed_colors_to_palette(colormap *palette, const int max_colors, const f_pixel fixed_colors[], const int fixed_colors_count, void* (*malloc)(size_t), void (*free)(void*))
{
    if (!fixed_colors_count) return palette;

    colormap *newpal = pam_colormap(MIN(max_colors, (palette ? palette->colors : 0) + fixed_colors_count), malloc, free);
    unsigned int i=0;
    if (palette && fixed_colors_count < max_colors) {
        unsigned int palette_max = MIN(palette->colors, max_colors - fixed_colors_count);
        for(; i < palette_max; i++) {
            newpal->palette[i] = palette->palette[i];
        }
    }
    for(int j=0; j < MIN(max_colors, fixed_colors_count); j++) {
        newpal->palette[i++] = (colormap_item){
            .acolor = fixed_colors[j],
            .fixed = true,
        };
    }
    if (palette) pam_freecolormap(palette);
    return newpal;
}

LIQ_NONNULL static void adjust_histogram_callback(hist_item *item, float diff)
{
    item->adjusted_weight = (item->perceptual_weight + 2.0 * item->adjusted_weight) * (0.5 + diff);
}

/**
 Repeats mediancut with different histogram weights to find palette with minimum error.

 feedback_loop_trials controls how long the search will take. < 0 skips the iteration.
 */
static colormap *find_best_palette(histogram *hist, const liq_attr *options, const double max_mse, const f_pixel fixed_colors[], const unsigned int fixed_colors_count, double *palette_error_p)
{
    unsigned int max_colors = options->max_colors;

    // if output is posterized it doesn't make sense to aim for perfrect colors, so increase target_mse
    // at this point actual gamma is not set, so very conservative posterization estimate is used
    const double target_mse = MIN(max_mse, MAX(options->target_mse, pow((1<<options->min_posterization_output)/1024.0, 2)));
    int feedback_loop_trials = options->feedback_loop_trials;
    if (hist->size > 5000) {feedback_loop_trials = (feedback_loop_trials*3 + 3)/4;}
    if (hist->size > 25000) {feedback_loop_trials = (feedback_loop_trials*3 + 3)/4;}
    if (hist->size > 50000) {feedback_loop_trials = (feedback_loop_trials*3 + 3)/4;}
    if (hist->size > 100000) {feedback_loop_trials = (feedback_loop_trials*3 + 3)/4;}
    colormap *acolormap = NULL;
    double least_error = MAX_DIFF;
    double target_mse_overshoot = feedback_loop_trials>0 ? 1.05 : 1.0;
    const float total_trials = (float)(feedback_loop_trials>0?feedback_loop_trials:1);
    int fails_in_a_row=0;

    do {
        colormap *newmap;
        if (hist->size && fixed_colors_count < max_colors) {
            newmap = mediancut(hist, max_colors-fixed_colors_count, target_mse * target_mse_overshoot, MAX(MAX(45.0/65536.0, target_mse), least_error)*1.2,
                               options->malloc, options->free);
        } else {
            feedback_loop_trials = 0;
            newmap = NULL;
        }
        newmap = add_fixed_colors_to_palette(newmap, max_colors, fixed_colors, fixed_colors_count, options->malloc, options->free);
        if (!newmap) {
            return NULL;
        }

        if (feedback_loop_trials <= 0) {
            return newmap;
        }

        // after palette has been created, total error (MSE) is calculated to keep the best palette
        // at the same time K-Means iteration is done to improve the palette
        // and histogram weights are adjusted based on remapping error to give more weight to poorly matched colors

        const bool first_run_of_target_mse = !acolormap && target_mse > 0;
        double total_error = kmeans_do_iteration(hist, newmap, first_run_of_target_mse ? NULL : adjust_histogram_callback);

        // goal is to increase quality or to reduce number of colors used if quality is good enough
        if (!acolormap || total_error < least_error || (total_error <= target_mse && newmap->colors < max_colors)) {
            if (acolormap) pam_freecolormap(acolormap);
            acolormap = newmap;

            if (total_error < target_mse && total_error > 0) {
                // K-Means iteration improves quality above what mediancut aims for
                // this compensates for it, making mediancut aim for worse
                target_mse_overshoot = MIN(target_mse_overshoot*1.25, target_mse/total_error);
            }

            least_error = total_error;

            // if number of colors could be reduced, try to keep it that way
            // but allow extra color as a bit of wiggle room in case quality can be improved too
            max_colors = MIN(newmap->colors+1, max_colors);

            feedback_loop_trials -= 1; // asymptotic improvement could make it go on forever
            fails_in_a_row = 0;
        } else {
            fails_in_a_row++;
            target_mse_overshoot = 1.0;

            // if error is really bad, it's unlikely to improve, so end sooner
            feedback_loop_trials -= 5 + fails_in_a_row;
            pam_freecolormap(newmap);
        }

        float fraction_done = 1.f-MAX(0.f, feedback_loop_trials/total_trials);
        if (liq_progress(options, options->progress_stage1 + fraction_done * options->progress_stage2)) break;
        liq_verbose_printf(options, "  selecting colors...%d%%", (int)(100.f * fraction_done));
    }
    while(feedback_loop_trials > 0);

    *palette_error_p = least_error;
    return acolormap;
}

static colormap *histogram_to_palette(const histogram *hist, const liq_attr *options) {
    if (!hist->size) {
        return NULL;
    }
    colormap *acolormap = pam_colormap(hist->size, options->malloc, options->free);
    for(unsigned int i=0; i < hist->size; i++) {
        acolormap->palette[i].acolor = hist->achv[i].acolor;
        acolormap->palette[i].popularity = hist->achv[i].perceptual_weight;
    }
    return acolormap;
}

LIQ_NONNULL static liq_error pngquant_quantize(histogram *hist, const liq_attr *options, const int fixed_colors_count, const f_pixel fixed_colors[], const double gamma, bool fixed_result_colors, liq_result **result_output)
{
    colormap *acolormap;
    double palette_error = -1;

    assert((verbose_print(options, "SLOW debug checks enabled. Recompile with NDEBUG for normal operation."),1));

    const bool few_input_colors = hist->size+fixed_colors_count <= options->max_colors;

    if (liq_progress(options, options->progress_stage1)) return LIQ_ABORTED;

    // If image has few colors to begin with (and no quality degradation is required)
    // then it's possible to skip quantization entirely
    if (few_input_colors && options->target_mse == 0) {
        acolormap = add_fixed_colors_to_palette(histogram_to_palette(hist, options), options->max_colors, fixed_colors, fixed_colors_count, options->malloc, options->free);
        palette_error = 0;
    } else {
        const double max_mse = options->max_mse * (few_input_colors ? 0.33 : 1.0); // when degrading image that's already paletted, require much higher improvement, since pal2pal often looks bad and there's little gain
        acolormap = find_best_palette(hist, options, max_mse, fixed_colors, fixed_colors_count, &palette_error);
        if (!acolormap) {
            return LIQ_VALUE_OUT_OF_RANGE;
        }

        // K-Means iteration approaches local minimum for the palette
        double iteration_limit = options->kmeans_iteration_limit;
        unsigned int iterations = options->kmeans_iterations;

        if (!iterations && palette_error < 0 && max_mse < MAX_DIFF) iterations = 1; // otherwise total error is never calculated and MSE limit won't work

        if (iterations) {
            // likely_colormap_index (used and set in kmeans_do_iteration) can't point to index outside colormap
            if (acolormap->colors < 256) for(unsigned int j=0; j < hist->size; j++) {
                if (hist->achv[j].tmp.likely_colormap_index >= acolormap->colors) {
                    hist->achv[j].tmp.likely_colormap_index = 0; // actual value doesn't matter, as the guess is out of date anyway
                }
            }

            if (hist->size > 5000) {iterations = (iterations*3 + 3)/4;}
            if (hist->size > 25000) {iterations = (iterations*3 + 3)/4;}
            if (hist->size > 50000) {iterations = (iterations*3 + 3)/4;}
            if (hist->size > 100000) {iterations = (iterations*3 + 3)/4; iteration_limit *= 2;}

            verbose_print(options, "  moving colormap towards local minimum");

            double previous_palette_error = MAX_DIFF;

            for(unsigned int i=0; i < iterations; i++) {
                palette_error = kmeans_do_iteration(hist, acolormap, NULL);

                if (liq_progress(options, options->progress_stage1 + options->progress_stage2 + (i * options->progress_stage3 * 0.9f) / iterations)) {
                    break;
                }

                if (fabs(previous_palette_error-palette_error) < iteration_limit) {
                    break;
                }

                if (palette_error > max_mse*1.5) { // probably hopeless
                    if (palette_error > max_mse*3.0) break; // definitely hopeless
                    i++;
                }

                previous_palette_error = palette_error;
            }
        }

        if (palette_error > max_mse) {
            liq_verbose_printf(options, "  image degradation MSE=%.3f (Q=%d) exceeded limit of %.3f (%d)",
                               mse_to_standard_mse(palette_error), mse_to_quality(palette_error),
                               mse_to_standard_mse(max_mse), mse_to_quality(max_mse));
            pam_freecolormap(acolormap);
            return LIQ_QUALITY_TOO_LOW;
        }
    }

    if (liq_progress(options, options->progress_stage1 + options->progress_stage2 + options->progress_stage3 * 0.95f)) {
        pam_freecolormap(acolormap);
        return LIQ_ABORTED;
    }

    sort_palette(acolormap, options);

    // If palette was created from a multi-image histogram,
    // then it shouldn't be optimized for one image during remapping
    if (fixed_result_colors) {
        for(unsigned int i=0; i < acolormap->colors; i++) {
            acolormap->palette[i].fixed = true;
        }
    }

    liq_result *result = options->malloc(sizeof(liq_result));
    if (!result) return LIQ_OUT_OF_MEMORY;
    *result = (liq_result){
        .magic_header = liq_result_magic,
        .malloc = options->malloc,
        .free = options->free,
        .palette = acolormap,
        .palette_error = palette_error,
        .use_dither_map = options->use_dither_map,
        .gamma = gamma,
        .min_posterization_output = options->min_posterization_output,
    };
    *result_output = result;
    return LIQ_OK;
}

LIQ_EXPORT LIQ_NONNULL liq_error liq_write_remapped_image(liq_result *result, liq_image *input_image, void *buffer, size_t buffer_size)
{
    if (!CHECK_STRUCT_TYPE(result, liq_result)) {
        return LIQ_INVALID_POINTER;
    }
    if (!CHECK_STRUCT_TYPE(input_image, liq_image)) {
        return LIQ_INVALID_POINTER;
    }
    if (!CHECK_USER_POINTER(buffer)) {
        return LIQ_INVALID_POINTER;
    }

    const size_t required_size = (size_t)input_image->width * (size_t)input_image->height;
    if (buffer_size < required_size) {
        return LIQ_BUFFER_TOO_SMALL;
    }

    unsigned char **rows = input_image->malloc(input_image->height * sizeof(unsigned char *));
    unsigned char *buffer_bytes = buffer;
    for(unsigned int i=0; i < input_image->height; i++) {
        rows[i] = &buffer_bytes[input_image->width * i];
    }
    
    liq_error err = liq_write_remapped_image_rows(result, input_image, rows);
    input_image->free(rows);
    return err;
}

LIQ_EXPORT LIQ_NONNULL liq_error liq_write_remapped_image_rows(liq_result *quant, liq_image *input_image, unsigned char **row_pointers)
{
    if (!CHECK_STRUCT_TYPE(quant, liq_result)) return LIQ_INVALID_POINTER;
    if (!CHECK_STRUCT_TYPE(input_image, liq_image)) return LIQ_INVALID_POINTER;
    for(unsigned int i=0; i < input_image->height; i++) {
        if (!CHECK_USER_POINTER(row_pointers+i) || !CHECK_USER_POINTER(row_pointers[i])) return LIQ_INVALID_POINTER;
    }

    if (quant->remapping) {
        liq_remapping_result_destroy(quant->remapping);
    }
    liq_remapping_result *const result = quant->remapping = liq_remapping_result_create(quant);
    if (!result) return LIQ_OUT_OF_MEMORY;

    if (!input_image->edges && !input_image->dither_map && quant->use_dither_map) {
        contrast_maps(input_image);
    }

    if (liq_remap_progress(result, result->progress_stage1 * 0.25f)) {
        return LIQ_ABORTED;
    }

    /*
     ** Step 4: map the colors in the image to their closest match in the
     ** new colormap, and write 'em out.
     */

    float remapping_error = result->palette_error;
    if (result->dither_level == 0) {
        set_rounded_palette(&result->int_palette, result->palette, result->gamma, quant->min_posterization_output);
        remapping_error = remap_to_palette(input_image, row_pointers, result->palette);
    } else {
        const bool is_image_huge = (input_image->width * input_image->height) > 2000 * 2000;
        const bool allow_dither_map = result->use_dither_map == 2 || (!is_image_huge && result->use_dither_map);
        const bool generate_dither_map = allow_dither_map && (input_image->edges && !input_image->dither_map);
        if (generate_dither_map) {
            // If dithering (with dither map) is required, this image is used to find areas that require dithering
            remapping_error = remap_to_palette(input_image, row_pointers, result->palette);
            update_dither_map(input_image, row_pointers, result->palette);
        }

        if (liq_remap_progress(result, result->progress_stage1 * 0.5f)) {
            return LIQ_ABORTED;
        }

        // remapping above was the last chance to do K-Means iteration, hence the final palette is set after remapping
        set_rounded_palette(&result->int_palette, result->palette, result->gamma, quant->min_posterization_output);

        if (!remap_to_palette_floyd(input_image, row_pointers, result, MAX(remapping_error*2.4, 8.f/256.f), generate_dither_map)) {
            return LIQ_ABORTED;
        }
    }

    // remapping error from dithered image is absurd, so always non-dithered value is used
    // palette_error includes some perceptual weighting from histogram which is closer correlated with dssim
    // so that should be used when possible.
    if (result->palette_error < 0) {
        result->palette_error = remapping_error;
    }

    return LIQ_OK;
}

LIQ_EXPORT int liq_version() {
    return LIQ_VERSION;
}
