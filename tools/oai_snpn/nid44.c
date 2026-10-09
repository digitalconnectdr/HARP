#include "nid44.h"

#include <stdint.h>
#include <stdlib.h>

bool harp_nid44_encode(uint64_t nid, harp_bit_string_t *out)
{
  if (!out || nid > HARP_NID44_MAX)
    return false;

  out->size = 6;
  out->bits_unused = 4;
  out->buf = calloc(out->size, 1);
  if (!out->buf)
    return false;

  const uint64_t shifted = nid << 4;
  out->buf[0] = (shifted >> 40) & 0xff;
  out->buf[1] = (shifted >> 32) & 0xff;
  out->buf[2] = (shifted >> 24) & 0xff;
  out->buf[3] = (shifted >> 16) & 0xff;
  out->buf[4] = (shifted >> 8) & 0xff;
  out->buf[5] = shifted & 0xff;
  return true;
}

bool harp_nid44_decode(const harp_bit_string_t *in, uint64_t *nid)
{
  if (!in || !nid || !in->buf || in->size != 6 || in->bits_unused != 4)
    return false;

  uint64_t value = 0;
  for (size_t i = 0; i < 6; ++i)
    value = (value << 8) | in->buf[i];

  if ((value & 0x0f) != 0)
    return false;

  *nid = value >> 4;
  return *nid <= HARP_NID44_MAX;
}
