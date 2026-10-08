#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "nid44.h"

typedef struct {
  uint16_t mcc;
  uint16_t mnc;
  uint8_t mnc_digit_length;
} harp_plmn_id_t;

typedef struct {
  harp_plmn_id_t plmn;
  bool has_nid;
  uint64_t nid;
} harp_serving_network_id_t;

bool harp_format_serving_network_name(
    const harp_serving_network_id_t *sn,
    char *out,
    size_t out_size);
