#ifndef BLUR_H
#define BLUR_H

LIQ_PRIVATE void liq_blur(unsigned char *src, unsigned char *tmp, unsigned char *dst, unsigned int width, unsigned int height, unsigned int size);
LIQ_PRIVATE void liq_max3(unsigned char *src, unsigned char *dst, unsigned int width, unsigned int height);
LIQ_PRIVATE void liq_min3(unsigned char *src, unsigned char *dst, unsigned int width, unsigned int height);

#endif
