#ifndef MEDIANCUT_H
#define MEDIANCUT_H

LIQ_PRIVATE colormap *mediancut(histogram *hist, unsigned int newcolors, const double target_mse, const double max_mse, void* (*malloc)(size_t), void (*free)(void*));

#endif
