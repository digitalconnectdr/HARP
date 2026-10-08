#include "snn.h"

#include <stdio.h>

bool harp_format_serving_network_name(
    const harp_serving_network_id_t *sn,
    char *out,
    size_t out_size)
{
  if (!sn || !out || out_size == 0)
    return false;

  if (sn->plmn.mcc > 999 || sn->plmn.mnc > 999)
    return false;

  if (sn->plmn.mnc_digit_length != 2 && sn->plmn.mnc_digit_length != 3)
    return false;

  if (sn->has_nid && sn->nid > HARP_NID44_MAX)
    return false;

  int n;
  if (sn->has_nid) {
    n = snprintf(out,
                 out_size,
                 "5G:mnc%03u.mcc%03u.3gppnetwork.org:%011llX",
                 sn->plmn.mnc,
                 sn->plmn.mcc,
                 (unsigned long long)sn->nid);
  } else {
    n = snprintf(out,
                 out_size,
                 "5G:mnc%03u.mcc%03u.3gppnetwork.org",
                 sn->plmn.mnc,
                 sn->plmn.mcc);
  }

  return n > 0 && (size_t)n < out_size;
}
