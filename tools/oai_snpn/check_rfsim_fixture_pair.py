#!/usr/bin/env python3
from __future__ import annotations

import argparse
import difflib
import re
from pathlib import Path

POS_NAME = "amf.harp-snpn-positive.yaml"
NEG_NAME = "amf.harp-snpn-negative.yaml"
UE_NAME = "nrue.harp-snpn.conf"
GNB_NAME = "gnb.harp-snpn.conf"
SQL_NAME = "harp_subscriber.sql"

EXPECTED_UE_NID = "10000000001"
EXPECTED_POS_NID = "10000000001"
EXPECTED_NEG_NID = "10000000002"
EXPECTED_IMSI = "999990000000001"
EXPECTED_MCC = "999"
EXPECTED_MNC = "99"
EXPECTED_KEY = "FEC86BA6EB707ED08905757B1BB44B8F"
EXPECTED_OPC = "C42449363BBAD02B66D16BC975D77CC1"
EXPECTED_RAND = "000102030405060708090A0B0C0D0E0F"
EXPECTED_AMF = "8000"


def fail(msg: str) -> None:
    raise SystemExit(f"FAIL: {msg}")


def read(root: Path, name: str) -> str:
    path = root / name
    if not path.is_file():
        fail(f"missing fixture: {path}")
    return path.read_text()


def one(pattern: str, text: str, label: str) -> str:
    hits = re.findall(pattern, text, re.MULTILINE)
    if len(hits) != 1:
        fail(f"{label}: expected exactly one match, found {len(hits)}")
    return hits[0]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("fixture_dir")
    args = ap.parse_args()

    root = Path(args.fixture_dir)
    pos = read(root, POS_NAME)
    neg = read(root, NEG_NAME)
    ue = read(root, UE_NAME)
    gnb = read(root, GNB_NAME)
    sql = read(root, SQL_NAME)

    pos_nid = one(r'^\s*snpn_nid:\s*"([0-9A-Fa-f]{11})"\s*$', pos, "positive AMF NID").upper()
    neg_nid = one(r'^\s*snpn_nid:\s*"([0-9A-Fa-f]{11})"\s*$', neg, "negative AMF NID").upper()
    if pos_nid != EXPECTED_POS_NID or neg_nid != EXPECTED_NEG_NID:
        fail(f"unexpected AMF NIDs: positive={pos_nid} negative={neg_nid}")

    normalized_neg = re.sub(
        r'(^\s*snpn_nid:\s*")([0-9A-Fa-f]{11})("\s*$)',
        rf'\g<1>{pos_nid}\g<3>',
        neg,
        count=1,
        flags=re.MULTILINE,
    )
    if normalized_neg != pos:
        diff = "".join(
            difflib.unified_diff(
                pos.splitlines(True),
                normalized_neg.splitlines(True),
                fromfile=POS_NAME,
                tofile=f"{NEG_NAME} normalized",
            )
        )
        fail("positive/negative AMF configs differ beyond snpn_nid\n" + diff)

    ue_nid = one(r'^\s*snpn_nid\s*=\s*"([0-9A-Fa-f]{11})";', ue, "UE NID").upper()
    imsi = one(r'^\s*imsi\s*=\s*"([0-9]+)";', ue, "UE IMSI")
    nmc_size = one(r'^\s*nmc_size\s*=\s*([0-9]+);', ue, "UE nmc_size")
    if ue_nid != EXPECTED_UE_NID:
        fail(f"UE NID {ue_nid} != {EXPECTED_UE_NID}")
    if imsi != EXPECTED_IMSI:
        fail(f"UE IMSI {imsi} != {EXPECTED_IMSI}")
    if nmc_size != "2":
        fail(f"UE nmc_size {nmc_size} != 2")

    gnb_mcc = one(r'plmn_list\s*=\s*\(\{\s*mcc\s*=\s*([0-9]+);', gnb, "gNB MCC")
    gnb_mnc = one(r'plmn_list\s*=\s*\(\{[^}]*?mnc\s*=\s*([0-9]+);', gnb, "gNB MNC")
    gnb_nid = one(r'^\s*nid\s*=\s*"([0-9A-Fa-f]{11})";', gnb, "gNB NID").upper()
    if (gnb_mcc, gnb_mnc, gnb_nid) != (EXPECTED_MCC, EXPECTED_MNC, EXPECTED_UE_NID):
        fail(f"unexpected gNB identity: {gnb_mcc}/{gnb_mnc}/{gnb_nid}")

    if EXPECTED_IMSI not in sql:
        fail("subscriber SQL does not contain expected IMSI")

    # The fixture reset must pin the AKA material used by the UE.
    all_hex = re.findall(r"UNHEX\('([0-9A-Fa-f]{32})'\)", sql)
    if len(all_hex) != 3:
        fail(f"subscriber SQL: expected exactly 3 128-bit UNHEX values, found {len(all_hex)}")
    key, rand, opc = (value.upper() for value in all_hex)

    ue_key = one(r'^\s*key\s*=\s*"([0-9A-Fa-f]{32})";', ue, "UE Ki").upper()
    ue_opc = one(r'^\s*opc\s*=\s*"([0-9A-Fa-f]{32})";', ue, "UE OPc").upper()
    ue_amf = one(r'^\s*amf\s*=\s*"([0-9A-Fa-f]{4})";', ue, "UE AMF").upper()
    ue_sqn = one(r'^\s*sqn\s*=\s*"([0-9A-Fa-f]{6})";', ue, "UE SQN").upper()
    if key != ue_key:
        fail(f"Ki mismatch UE/SQL: {ue_key} != {key}")
    if opc != ue_opc:
        fail(f"OPc mismatch UE/SQL: {ue_opc} != {opc}")
    if key != EXPECTED_KEY:
        fail(f"unexpected lab Ki: {key}")
    if opc != EXPECTED_OPC:
        fail(f"unexpected lab OPc: {opc}")
    if rand != EXPECTED_RAND:
        fail(f"unexpected lab RAND: {rand}")
    if ue_amf != EXPECTED_AMF:
        fail(f"unexpected UE AMF field: {ue_amf}")
    if ue_sqn != "000000":
        fail(f"unexpected UE SQN baseline: {ue_sqn}")

    insert = re.search(
        r"INSERT\s+INTO\s+users\s*\((?P<cols>.*?)\)\s*VALUES\s*\((?P<vals>.*?)\)\s*;",
        sql,
        re.IGNORECASE | re.DOTALL,
    )
    if not insert:
        fail("subscriber SQL INSERT statement not found")
    values = [v.strip() for v in insert.group("vals").split(",")]
    if len(values) != 17:
        fail(f"subscriber SQL: expected 17 INSERT values, found {len(values)}")
    if values[14] != "0":
        fail(f"subscriber SQL SQN baseline is not zero: {values[14]}")

    print(f"UE_IMSI={imsi}")
    print(f"UE_GNB_NID={ue_nid}")
    print(f"AMF_POSITIVE_NID={pos_nid}")
    print(f"AMF_NEGATIVE_NID={neg_nid}")
    print(f"PLMN={gnb_mcc}/{gnb_mnc}")
    print(f"NMC_SIZE={nmc_size}")
    print(f"AMF_FIELD={ue_amf}")
    print(f"SQN=000000")
    print(f"RAND={rand}")
    print("PASS_HARP_RFSIM_SINGLE_VARIABLE_FIXTURES")


if __name__ == "__main__":
    main()
