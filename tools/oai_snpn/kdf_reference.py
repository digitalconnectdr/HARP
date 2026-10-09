#!/usr/bin/env python3
import hashlib
import hmac

CK = bytes(range(0x00, 0x10))
IK = bytes(range(0x10, 0x20))
RAND = bytes(range(0x20, 0x30))
SQN = bytes(range(0x30, 0x36))
RES_INPUT = bytes(range(0x40, 0x48))
KEY = CK + IK

EXPECTED = {
    "baseline": {
        "snn": "5G:mnc015.mcc234.3gppnetwork.org",
        "res_star": "e5c9b031ea670bc494e4db45fb1cf267",
        "kausf": "1789cd7d88b07b803330574544da1bfcb52c67ec14b4075b4b36d262d773dc83",
        "kseaf": "e40038b02ad5457c40f27f92e92bdd735c7720287ecbd7ff304f7751d7bf2191",
    },
    "snpn_001": {
        "snn": "5G:mnc099.mcc999.3gppnetwork.org:10000000001",
        "res_star": "4a880d868e07cb3ad0a3ef39b21eebe5",
        "kausf": "742c95dd9003e1c6c148236f5f8c9f9f2b89b02b2d898d989d4de00189ff5626",
        "kseaf": "a19ff0f63a0093d859f72233688e472a3283493bc2e852f030d9de7aaf9e93b4",
    },
    "snpn_002": {
        "snn": "5G:mnc099.mcc999.3gppnetwork.org:10000000002",
        "res_star": "b941a7b510507edd235d2fbad30fde03",
        "kausf": "aaf0234ed414af2f91be9a736f163af77451e6f2bf5b7cb5a34bd85d10674d0b",
        "kseaf": "18082e6da076b999a51713098bf9ee0bc66ad6e6163683aff70608cd334fb46b",
    },
}


def kdf(key: bytes, s: bytes) -> bytes:
    return hmac.new(key, s, hashlib.sha256).digest()


def derive_res_star(snn: bytes) -> bytes:
    s = (
        b"\x6b"
        + snn
        + len(snn).to_bytes(2, "big")
        + RAND
        + (16).to_bytes(2, "big")
        + RES_INPUT
        + (8).to_bytes(2, "big")
    )
    return kdf(KEY, s)[16:]


def derive_kausf(snn: bytes) -> bytes:
    s = (
        b"\x6a"
        + snn
        + len(snn).to_bytes(2, "big")
        + SQN
        + (6).to_bytes(2, "big")
    )
    return kdf(KEY, s)


def derive_kseaf(snn: bytes, kausf: bytes) -> bytes:
    s = b"\x6c" + snn + len(snn).to_bytes(2, "big")
    return kdf(kausf, s)


def check_case(name: str) -> tuple[bytes, bytes, bytes]:
    case = EXPECTED[name]
    snn = case["snn"].encode("ascii")
    res_star = derive_res_star(snn)
    kausf = derive_kausf(snn)
    kseaf = derive_kseaf(snn, kausf)

    assert res_star.hex() == case["res_star"], f"{name}: RES* mismatch"
    assert kausf.hex() == case["kausf"], f"{name}: KAUSF mismatch"
    assert kseaf.hex() == case["kseaf"], f"{name}: KSEAF mismatch"

    print(f"{name}: SNN_LEN={len(snn)}")
    print(f"{name}: RES*={res_star.hex()}")
    print(f"{name}: KAUSF={kausf.hex()}")
    print(f"{name}: KSEAF={kseaf.hex()}")
    return res_star, kausf, kseaf


def main() -> None:
    baseline = check_case("baseline")
    snpn_001 = check_case("snpn_001")
    snpn_002 = check_case("snpn_002")

    assert snpn_001[0] != snpn_002[0], "NID change did not change RES*"
    assert snpn_001[1] != snpn_002[1], "NID change did not change KAUSF"
    assert snpn_001[2] != snpn_002[2], "NID change did not change KSEAF"

    # Buffer-size audit for the exact OAI input layouts.
    snn_len = len(EXPECTED["snpn_001"]["snn"])
    res_s_len = 1 + snn_len + 2 + 16 + 2 + 8 + 2
    kausf_s_len = 1 + snn_len + 2 + 6 + 2
    kseaf_s_len = 1 + snn_len + 2
    assert res_s_len <= 100
    assert kausf_s_len <= 100
    assert kseaf_s_len <= 100

    print(f"SNPN_SNN_LEN={snn_len}")
    print(f"RES_STAR_KDF_INPUT_LEN={res_s_len}")
    print(f"KAUSF_KDF_INPUT_LEN={kausf_s_len}")
    print(f"KSEAF_KDF_INPUT_LEN={kseaf_s_len}")
    print("PASS_V5G_SNPN_KDF_REFERENCE")


if __name__ == "__main__":
    main()
