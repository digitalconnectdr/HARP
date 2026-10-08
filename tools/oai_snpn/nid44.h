#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define HARP_NID44_MAX 0xFFFFFFFFFFFULL

typedef struct {
  uint8_t *buf;
  size_t size;
  int bits_unused;
} harp_bit_string_t;

bool harp_nid44_encode(uint64_t nid, harp_bit_string_t *out);
bool harp_nid44_decode(const harp_bit_string_t *in, uint64_t *nid);
