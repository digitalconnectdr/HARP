#pragma once

#include <cstdint>
#include <string>

std::string harp_amf_snn(
    const std::string& mnc,
    const std::string& mcc);

std::string harp_amf_snn(
    const std::string& mnc,
    const std::string& mcc,
    std::uint64_t nid);
