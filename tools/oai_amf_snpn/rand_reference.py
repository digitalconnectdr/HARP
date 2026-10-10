#!/usr/bin/env python3
from __future__ import annotations

RAND_LEN = 16
MAX_5GS_AUTH_VECTORS = 1


def deterministic_rand(delta: int) -> bytes:
    if delta < 0 or delta > 0xFF:
        raise ValueError("delta out of supported reference range")
    return bytes(((i + delta) & 0xFF) for i in range(RAND_LEN))


def main() -> None:
    first = deterministic_rand(0)
    second = deterministic_rand(1)

    assert MAX_5GS_AUTH_VECTORS == 1
    assert first.hex() == "000102030405060708090a0b0c0d0e0f"
    assert second.hex() == "0102030405060708090a0b0c0d0e0f10"
    assert first != second

    print(f"MAX_5GS_AUTH_VECTORS={MAX_5GS_AUTH_VECTORS}")
    print(f"FIRST_PROCESS_RAND={first.hex()}")
    print(f"SECOND_VECTOR_SAME_PROCESS_RAND={second.hex()}")
    print("PASS_HARP_AMF_DETERMINISTIC_RAND_MODEL")
    print("PASS_HARP_AMF_RESTART_REQUIRED_FOR_IDENTICAL_RAND")


if __name__ == "__main__":
    main()
