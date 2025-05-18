/*
** © 2011-2016 by Kornel Lesiński.
** See COPYRIGHT file for license.
*/

#include "libimagequant.h"
#include "pam.h"
#include "kmeans.h"
#include "nearest.h"
#include <stdlib.h>
#include <string.h>

#ifdef _OPENMP
#include <omp.h>
#else
#define omp_get_max_threads() 1
#define omp_get_thread_num() 0
#endif

/*
 * K-Means iteration: new palette color is computed from weighted average of colors that map to that palette entry.
 */
LIQ_PRIVATE void kmeans_init(const colormap *map, const unsigned int max_threads, kmeans_state average_color[])
{
    memset(average_color, 0, sizeof(average_color[0])*(KMEANS_CACHE_LINE_GAP+map->colors)*max_threads);
}

LIQ_PRIVATE void kmeans_update_color(const f_pixel acolor, const float value, const colormap *map, unsigned int match, const unsigned int thread, kmeans_state average_color[])
{
    match += thread * (KMEANS_CACHE_LINE_GAP+map->colors);
    average_color[match].a += acolor.a * value;
    average_color[match].r += acolor.r * value;
    average_color[match].g += acolor.g * value;
    average_color[match].b += acolor.b * value;
    average_color[match].total += value;
}

LIQ_PRIVATE void kmeans_finalize(colormap *map, const unsigned int max_threads, const kmeans_state average_color[])
{
    for (unsigned int i=0; i < map->colors; i++) {
        double a=0, r=0, g=0, b=0, total=0;

        // Aggregate results from all threads
        for(unsigned int t=0; t < max_threads; t++) {
            const unsigned int offset = (KMEANS_CACHE_LINE_GAP+map->colors) * t + i;

            a += average_color[offset].a;
            r += average_color[offset].r;
            g += average_color[offset].g;
            b += average_color[offset].b;
            total += average_color[offset].total;
        }

        if (!map->palette[i].fixed) {
            map->palette[i].popularity = total;
            if (total) {
                map->palette[i].acolor = (f_pixel){
                    .a = a / total,
                    .r = r / total,
                    .g = g / total,
                    .b = b / total,
                };
            } else {
                // if a color is useless, make a new one
                // (it was supposed to be random, but Android NDK has problematic stdlib headers)
                map->palette[i].acolor.a = map->palette[(i+1)%map->colors].acolor.a;
                map->palette[i].acolor.r = map->palette[(i+2)%map->colors].acolor.r;
                map->palette[i].acolor.g = map->palette[(i+3)%map->colors].acolor.g;
                map->palette[i].acolor.b = map->palette[(i+4)%map->colors].acolor.b;
            }
        }
    }
}

LIQ_PRIVATE double kmeans_do_iteration(histogram *hist, colormap *const map, kmeans_callback callback)
{
    const unsigned int max_threads = omp_get_max_threads();
    LIQ_ARRAY(kmeans_state, average_color, (KMEANS_CACHE_LINE_GAP+map->colors) * max_threads);
    kmeans_init(map, max_threads, average_color);
    struct nearest_map *const n = nearest_init(map);
    hist_item *const achv = hist->achv;
    const int hist_size = hist->size;

    double total_diff=0;
#if __GNUC__ >= 9 || __clang__
    #pragma omp parallel for if (hist_size > 2000) \
        schedule(static) default(none) shared(achv,average_color,callback,hist_size,map,n) reduction(+:total_diff)
#else
    #pragma omp parallel for if (hist_size > 2000) \
        schedule(static) default(none) shared(average_color,callback) reduction(+:total_diff)
#endif
    for(int j=0; j < hist_size; j++) {
        float diff;
        const f_pixel px = achv[j].acolor;
        const unsigned int match = nearest_search(n, &px, achv[j].tmp.likely_colormap_index, &diff);
        achv[j].tmp.likely_colormap_index = match;

        if (callback) {
            // Check how average diff would look like if there was dithering
            const f_pixel remapped = map->palette[match].acolor;
            nearest_search(n, &(f_pixel){
                .a = px.a + px.a - remapped.a,
                .r = px.r + px.r - remapped.r,
                .g = px.g + px.g - remapped.g,
                .b = px.b + px.b - remapped.b,
            }, match, &diff);

            callback(&achv[j], diff);
        }

        total_diff += diff * achv[j].perceptual_weight;

        kmeans_update_color(px, achv[j].adjusted_weight, map, match, omp_get_thread_num(), average_color);
    }

    nearest_free(n);
    kmeans_finalize(map, max_threads, average_color);

    return total_diff / hist->total_perceptual_weight;
}
