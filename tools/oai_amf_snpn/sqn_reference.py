#!/usr/bin/env python3
from __future__ import annotations

SQN_MASK = (1 << 48) - 1


def mysql_decimal_to_sqn(value: int) -> bytes:
    if value < 0 or value > SQN_MASK:
        raise ValueError("SQN must fit in 48 bits")
    return bytes(
        [
            (value >> 40) & 0xFF,
            (value >> 32) & 0xFF,
            (value >> 24) & 0xFF,
            (value >> 16) & 0xFF,
            (value >> 8) & 0xFF,
            value & 0xFF,
        ]
    )


def sqn_to_mysql_decimal(sqn: bytes) -> int:
    if len(sqn) != 6:
        raise ValueError("SQN must be exactly 6 bytes")
    return (
        (sqn[0] << 40)
        | (sqn[1] << 32)
        | (sqn[2] << 24)
        | (sqn[3] << 16)
        | (sqn[4] << 8)
        | sqn[5]
    )


def main() -> None:
    vectors = {
        0: "000000000000",
        1: "000000000001",
        31: "00000000001f",
        32: "000000000020",
        255: "0000000000ff",
        256: "000000000100",
        0x010203040506: "010203040506",
        SQN_MASK: "ffffffffffff",
    }

    for value, expected_hex in vectors.items():
        encoded = mysql_decimal_to_sqn(value)
        assert encoded.hex() == expected_hex, (value, encoded.hex(), expected_hex)
        assert sqn_to_mysql_decimal(encoded) == value

    baseline = mysql_decimal_to_sqn(0)
    assert baseline == bytes(6)

    after_one_amf_increment = mysql_decimal_to_sqn(32)
    assert after_one_amf_increment.hex() == "000000000020"

    print("SQL_SQN_BASELINE=0")
    print(f"UE_SQN_BASELINE={baseline.hex()}")
    print(f"AMF_INCREMENTED_SQN={after_one_amf_increment.hex()}")
    print("PASS_HARP_AMF_SQN_ENDIANNESS")
    print("PASS_HARP_AMF_SQN_INCREMENT_MODEL")


if __name__ == "__main__":
    main()
