
#ifndef KMEANS_H
#define KMEANS_H

// Spread memory touched by different threads at least 64B apart which I assume is the cache line size. This should avoid memory write contention.
#define KMEANS_CACHE_LINE_GAP ((64+sizeof(kmeans_state)-1)/sizeof(kmeans_state))

typedef struct {
    double a, r, g, b, total;
} kmeans_state;

typedef void (*kmeans_callback)(hist_item *item, float diff);

LIQ_PRIVATE void kmeans_init(const colormap *map, const unsigned int max_threads, kmeans_state state[]);
LIQ_PRIVATE void kmeans_update_color(const f_pixel acolor, const float value, const colormap *map, unsigned int match, const unsigned int thread, kmeans_state average_color[]);
LIQ_PRIVATE void kmeans_finalize(colormap *map, const unsigned int max_threads, const kmeans_state state[]);
LIQ_PRIVATE double kmeans_do_iteration(histogram *hist, colormap *const map, kmeans_callback callback);

#endif
