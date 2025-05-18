#ifndef MEMPOOL_H
#define MEMPOOL_H

#include <stddef.h>

struct mempool;
typedef struct mempool *mempoolptr;

LIQ_PRIVATE void* mempool_create(mempoolptr *mptr, const unsigned int size, unsigned int capacity, void* (*malloc)(size_t), void (*free)(void*));
LIQ_PRIVATE void* mempool_alloc(mempoolptr *mptr, const unsigned int size, const unsigned int capacity);
LIQ_PRIVATE void mempool_destroy(mempoolptr m);

#endif
