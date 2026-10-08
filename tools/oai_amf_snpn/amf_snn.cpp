#include "amf_snn.hpp"

#include <iomanip>
#include <sstream>
#include <stdexcept>

std::string harp_amf_snn(
    const std::string& mnc,
    const std::string& mcc)
{
  if (mcc.size() != 3 || (mnc.size() != 2 && mnc.size() != 3))
    throw std::invalid_argument("invalid PLMN digits");

  const std::string padded_mnc = mnc.size() == 2 ? "0" + mnc : mnc;
  return "5G:mnc" + padded_mnc + ".mcc" + mcc + ".3gppnetwork.org";
}

std::string harp_amf_snn(
    const std::string& mnc,
    const std::string& mcc,
    std::uint64_t nid)
{
  if (nid > 0xFFFFFFFFFFFULL)
    throw std::invalid_argument("SNPN NID exceeds 44 bits");

  std::ostringstream suffix;
  suffix << ':' << std::uppercase << std::hex
         << std::setw(11) << std::setfill('0') << nid;
  return harp_amf_snn(mnc, mcc) + suffix.str();
}
