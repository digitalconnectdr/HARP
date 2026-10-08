#include "snn.h"

#include <assert.h>
#include <stdio.h>
#include <string.h>

static void expect(
    const harp_serving_network_id_t *sn,
    const char *expected)
{
  char out[128] = {0};
  assert(harp_format_serving_network_name(sn, out, sizeof(out)));
  assert(strcmp(out, expected) == 0);
}

int main(void)
{
  const harp_serving_network_id_t plmn = {
      .plmn = {.mcc = 234, .mnc = 15, .mnc_digit_length = 2},
      .has_nid = false,
      .nid = 0,
  };
  expect(&plmn, "5G:mnc015.mcc234.3gppnetwork.org");

  const harp_serving_network_id_t lab = {
      .plmn = {.mcc = 999, .mnc = 99, .mnc_digit_length = 2},
      .has_nid = true,
      .nid = 0x10000000001ULL,
  };
  expect(&lab, "5G:mnc099.mcc999.3gppnetwork.org:10000000001");

  const harp_serving_network_id_t low = {
      .plmn = {.mcc = 234, .mnc = 15, .mnc_digit_length = 2},
      .has_nid = true,
      .nid = 1,
  };
  expect(&low, "5G:mnc015.mcc234.3gppnetwork.org:00000000001");

  const harp_serving_network_id_t max = {
      .plmn = {.mcc = 234, .mnc = 15, .mnc_digit_length = 2},
      .has_nid = true,
      .nid = HARP_NID44_MAX,
  };
  expect(&max, "5G:mnc015.mcc234.3gppnetwork.org:FFFFFFFFFFF");

  const harp_serving_network_id_t overflow = {
      .plmn = {.mcc = 234, .mnc = 15, .mnc_digit_length = 2},
      .has_nid = true,
      .nid = 0x100000000000ULL,
  };
  char out[128];
  assert(!harp_format_serving_network_name(
      &overflow, out, sizeof(out)));

  printf("PASS_SNN_VECTORS\n");
  return 0;
}
