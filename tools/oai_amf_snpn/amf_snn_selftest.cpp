#include "amf_snn.hpp"

#include <cassert>
#include <iostream>
#include <stdexcept>

int main()
{
  assert(harp_amf_snn("15", "234")
      == "5G:mnc015.mcc234.3gppnetwork.org");

  assert(harp_amf_snn("99", "999", 0x10000000001ULL)
      == "5G:mnc099.mcc999.3gppnetwork.org:10000000001");

  assert(harp_amf_snn("15", "234", 1)
      == "5G:mnc015.mcc234.3gppnetwork.org:00000000001");

  assert(harp_amf_snn("15", "234", 0xFFFFFFFFFFFULL)
      == "5G:mnc015.mcc234.3gppnetwork.org:FFFFFFFFFFF");

  bool overflow = false;
  try {
    (void)harp_amf_snn("15", "234", 0x100000000000ULL);
  } catch (const std::invalid_argument&) {
    overflow = true;
  }
  assert(overflow);

  std::cout << "PASS_AMF_SNN_VECTORS\n";
  return 0;
}
