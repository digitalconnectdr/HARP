#!/usr/bin/env python3
import argparse
import re
import sys
from pathlib import Path

SNN_RE = re.compile(
    r"5G:mnc(?P<mnc>\d{3})\.mcc(?P<mcc>\d{3})\.3gppnetwork\.org:(?P<nid>[0-9A-F]{11})"
)
SELECT_RE = re.compile(
    r"PASS_V5G_SNPN_SELECT\s+mcc=(?P<mcc>\d+)\s+mnc=(?P<mnc>\d+)\s+nid=(?P<nid>[0-9A-F]{11})"
)
AMF_SNN_RE = re.compile(r"HARP_SNPN_AMF_SNN\s+(?P<snn>\S+)")


def read(path: str) -> str:
    return Path(path).read_text(errors="replace")


def fail(msg: str) -> None:
    print(f"FAIL: {msg}", file=sys.stderr)
    raise SystemExit(1)


def first(pattern, text, label):
    m = pattern.search(text)
    if not m:
        fail(f"missing {label}")
    return m


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--mode", choices=["positive", "negative"], required=True)
    ap.add_argument("--ue-log", required=True)
    ap.add_argument("--amf-log", required=True)
    ap.add_argument("--expected-ue-nid", default="10000000001")
    ap.add_argument("--expected-amf-nid")
    args = ap.parse_args()

    ue = read(args.ue_log)
    amf = read(args.amf_log)

    sel = first(SELECT_RE, ue, "UE PASS_V5G_SNPN_SELECT")
    ue_nid = sel.group("nid").upper()
    if ue_nid != args.expected_ue_nid.upper():
        fail(f"UE selected NID {ue_nid}, expected {args.expected_ue_nid.upper()}")

    amf_line = first(AMF_SNN_RE, amf, "AMF HARP_SNPN_AMF_SNN")
    amf_snn = amf_line.group("snn")
    sm = SNN_RE.fullmatch(amf_snn)
    if not sm:
        fail(f"AMF SNN is not canonical SNPN form: {amf_snn}")

    amf_nid = sm.group("nid").upper()
    expected_amf = (args.expected_amf_nid or args.expected_ue_nid).upper()
    if amf_nid != expected_amf:
        fail(f"AMF NID {amf_nid}, expected {expected_amf}")

    auth_marker = f"PASS_V5G_SNPN_AUTH_AMF nid={amf_nid}"
    registered_marker = f"PASS_V5G_SNPN_REGISTERED_AMF nid={amf_nid}"
    auth_seen = auth_marker in amf
    registered_seen = registered_marker in amf

    print(f"UE_NID={ue_nid}")
    print(f"AMF_NID={amf_nid}")
    print(f"AMF_SNN={amf_snn}")
    print(f"AUTH_MARKER_SEEN={int(auth_seen)}")
    print(f"REGISTERED_MARKER_SEEN={int(registered_seen)}")

    if args.mode == "positive":
        if ue_nid != amf_nid:
            fail("positive mode requires identical UE and AMF NID")
        if not auth_seen:
            fail(f"missing exact AMF authentication marker: {auth_marker}")
        print("PASS_V5G_SNPN_AUTH")
        if not registered_seen:
            fail(f"missing exact AMF registration marker: {registered_marker}")
        print("PASS_V5G_SNPN_KDF_LAB")
        return

    if ue_nid == amf_nid:
        fail("negative mode requires mismatched UE and AMF NID")
    if auth_seen or registered_seen:
        fail("negative mode unexpectedly reached SNPN authentication/registration success")
    print("PASS_V5G_SNPN_KDF_NEGATIVE")


if __name__ == "__main__":
    main()
