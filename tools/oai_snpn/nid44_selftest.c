#include "nid44.h"

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static void check(uint64_t value, const uint8_t expected[6])
{
  harp_bit_string_t encoded = {0};
  assert(harp_nid44_encode(value, &encoded));
  assert(encoded.size == 6);
  assert(encoded.bits_unused == 4);
  assert(memcmp(encoded.buf, expected, 6) == 0);

  uint64_t decoded = 0;
  assert(harp_nid44_decode(&encoded, &decoded));
  assert(decoded == value);
  free(encoded.buf);
}

int main(void)
{
  const uint8_t zero[6] = {0, 0, 0, 0, 0, 0};
  const uint8_t one[6] = {0, 0, 0, 0, 0, 0x10};
  const uint8_t lab[6] = {0x10, 0, 0, 0, 0, 0x10};
  const uint8_t max[6] = {0xff, 0xff, 0xff, 0xff, 0xff, 0xf0};

  check(0, zero);
  check(1, one);
  check(0x10000000001ULL, lab);
  check(HARP_NID44_MAX, max);

  harp_bit_string_t overflow = {0};
  assert(!harp_nid44_encode(0x100000000000ULL, &overflow));

  uint8_t bad_padding[6] = {0, 0, 0, 0, 0, 1};
  harp_bit_string_t malformed = {
      .buf = bad_padding,
      .size = 6,
      .bits_unused = 4,
  };
  uint64_t decoded = 0;
  assert(!harp_nid44_decode(&malformed, &decoded));

  printf("PASS_NID44_VECTORS\n");
  return 0;
}
