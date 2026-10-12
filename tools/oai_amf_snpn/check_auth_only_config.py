#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
from pathlib import Path


def fail(msg: str) -> None:
    raise SystemExit(f"FAIL: {msg}")


def exactly_one(pattern: str, text: str, label: str, flags: int = re.MULTILINE) -> re.Match[str]:
    hits = list(re.finditer(pattern, text, flags))
    if len(hits) != 1:
        fail(f"{label}: expected exactly one match, found {len(hits)}")
    return hits[0]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("amf_yaml")
    args = ap.parse_args()

    path = Path(args.amf_yaml)
    if not path.is_file():
        fail(f"missing AMF YAML: {path}")
    text = path.read_text()

    simple = exactly_one(
        r"^[ ]{4}enable_simple_scenario:[ \t]*(yes|no)[ \t]*(?:#.*)?$",
        text,
        "enable_simple_scenario",
    ).group(1)
    if simple != "yes":
        fail("enable_simple_scenario must be yes for auth-only lab")

    # The pinned AMF code forces these external-service features off whenever
    # simple scenario is enabled. The YAML may still retain their stock values;
    # runtime support_features are overridden by the super-option.
    db = exactly_one(
        r"^database:\s*$"
        r"(?P<body>(?:\n(?:  .*)?)+)",
        text,
        "database block",
    ).group("body")

    required = {
        "host": "mysql",
        "user": "test",
        "type": "mysql",
        "password": "test",
        "database_name": "oai_db",
    }
    for key, expected in required.items():
        m = exactly_one(
            rf"^[ ]{{2}}{re.escape(key)}:[ \t]*([^\s#]+)",
            db,
            f"database.{key}",
        )
        actual = m.group(1).strip('"\'')
        if actual != expected:
            fail(f"database.{key}={actual!r}, expected {expected!r}")

    exactly_one(
        r'^[ ]{2}snpn_nid:[ \t]*"[0-9A-Fa-f]{11}"[ \t]*,
        text,
        "amf.snpn_nid",
    )

    print("AUTH_ONLY_EXTERNAL_AUSF=0")
    print("AUTH_ONLY_EXTERNAL_UDM=0")
    print("AUTH_ONLY_EXTERNAL_UDR=0")
    print("AUTH_ONLY_EXTERNAL_NRF=0")
    print("AUTH_ONLY_EXTERNAL_PCF=0")
    print("AUTH_ONLY_EXTERNAL_NSSF=0")
    print("AUTH_ONLY_MYSQL=1")
    print("AUTH_ONLY_SMF_REQUIRED_FOR_AUTH=0")
    print("PASS_HARP_AMF_AUTH_ONLY_CONFIG")


if __name__ == "__main__":
    main()
,
        text,
        "amf.snpn_nid",
    )

    print("AUTH_ONLY_EXTERNAL_AUSF=0")
    print("AUTH_ONLY_EXTERNAL_UDM=0")
    print("AUTH_ONLY_EXTERNAL_UDR=0")
    print("AUTH_ONLY_EXTERNAL_NRF=0")
    print("AUTH_ONLY_EXTERNAL_PCF=0")
    print("AUTH_ONLY_EXTERNAL_NSSF=0")
    print("AUTH_ONLY_MYSQL=1")
    print("AUTH_ONLY_SMF_REQUIRED_FOR_AUTH=0")
    print("PASS_HARP_AMF_AUTH_ONLY_CONFIG")


if __name__ == "__main__":
    main()
