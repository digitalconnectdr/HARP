#!/usr/bin/env python3
import hashlib
import hmac


def l16(n: int) -> bytes:
    return n.to_bytes(2, "big")


def hx(b: bytes) -> str:
    return b.hex()


def main() -> None:
    ck = bytes(range(0x00, 0x10))
    ik = bytes(range(0x10, 0x20))
    rand = bytes(range(0x20, 0x30))
    sqn = bytes(range(0x30, 0x36))
    res_input = bytes(range(0x40, 0x48))
    snn = b"5G:mnc015.mcc234.3gppnetwork.org"

    key = ck + ik

    s_res = (
        bytes([0x6B])
        + snn
        + l16(len(snn))
        + rand
        + l16(len(rand))
        + res_input
        + l16(len(res_input))
    )
    res_full = hmac.new(key, s_res, hashlib.sha256).digest()
    res_star = res_full[16:]

    s_kausf = (
        bytes([0x6A])
        + snn
        + l16(len(snn))
        + sqn
        + l16(len(sqn))
    )
    kausf = hmac.new(key, s_kausf, hashlib.sha256).digest()

    s_kseaf = bytes([0x6C]) + snn + l16(len(snn))
    kseaf = hmac.new(kausf, s_kseaf, hashlib.sha256).digest()

    assert res_star.hex() == "e5c9b031ea670bc494e4db45fb1cf267"
    assert kausf.hex() == "1789cd7d88b07b803330574544da1bfcb52c67ec14b4075b4b36d262d773dc83"
    assert kseaf.hex() == "e40038b02ad5457c40f27f92e92bdd735c7720287ecbd7ff304f7751d7bf2191"

    print(f"SNN={snn.decode()}")
    print(f"S_RES={hx(s_res)}")
    print(f"RES_STAR={hx(res_star)}")
    print(f"S_KAUSF={hx(s_kausf)}")
    print(f"KAUSF={hx(kausf)}")
    print(f"S_KSEAF={hx(s_kseaf)}")
    print(f"KSEAF={hx(kseaf)}")

    def derive_for_snn(snn_text: str):
        snn_bytes = snn_text.encode()
        s_res_local = (
            bytes([0x6B])
            + snn_bytes
            + l16(len(snn_bytes))
            + rand
            + l16(len(rand))
            + res_input
            + l16(len(res_input))
        )
        res_full_local = hmac.new(key, s_res_local, hashlib.sha256).digest()
        res_star_local = res_full_local[16:]

        s_kausf_local = (
            bytes([0x6A])
            + snn_bytes
            + l16(len(snn_bytes))
            + sqn
            + l16(len(sqn))
        )
        kausf_local = hmac.new(key, s_kausf_local, hashlib.sha256).digest()
        s_kseaf_local = bytes([0x6C]) + snn_bytes + l16(len(snn_bytes))
        kseaf_local = hmac.new(
            kausf_local, s_kseaf_local, hashlib.sha256
        ).digest()
        return res_star_local, kausf_local, kseaf_local

    snpn_1 = "5G:mnc099.mcc999.3gppnetwork.org:10000000001"
    snpn_2 = "5G:mnc099.mcc999.3gppnetwork.org:10000000002"
    r1, a1, s1 = derive_for_snn(snpn_1)
    r2, a2, s2 = derive_for_snn(snpn_2)

    assert r1.hex() == "4a880d868e07cb3ad0a3ef39b21eebe5"
    assert a1.hex() == "742c95dd9003e1c6c148236f5f8c9f9f2b89b02b2d898d989d4de00189ff5626"
    assert s1.hex() == "a19ff0f63a0093d859f72233688e472a3283493bc2e852f030d9de7aaf9e93b4"
    assert r1 != r2
    assert a1 != a2
    assert s1 != s2

    print(f"SNPN1_RES_STAR={hx(r1)}")
    print(f"SNPN1_KAUSF={hx(a1)}")
    print(f"SNPN1_KSEAF={hx(s1)}")
    print(f"SNPN2_RES_STAR={hx(r2)}")
    print(f"SNPN2_KAUSF={hx(a2)}")
    print(f"SNPN2_KSEAF={hx(s2)}")
    print("PASS_KDF_BASELINE_VECTOR_GENERATOR")
    print("PASS_SNPN_KDF_VECTOR_GENERATOR")


if __name__ == "__main__":
    main()
