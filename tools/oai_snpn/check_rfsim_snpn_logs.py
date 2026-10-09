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


def last(pattern, text, label):
    matches = list(pattern.finditer(text))
    if not matches:
        fail(f"missing {label}")
    return matches[-1]


def position_after(text: str, marker: str, offset: int) -> int:
    return text.find(marker, offset)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--mode", choices=["positive", "negative"], required=True)
    ap.add_argument("--ue-log", required=True)
    ap.add_argument("--amf-log", required=True)
    ap.add_argument("--expected-ue-nid", default="10000000001")
    ap.add_argument("--expected-amf-nid")
    ap.add_argument(
        "--require-registration",
        action="store_true",
        help="Also require the explicit AMF registration-complete marker",
    )
    args = ap.parse_args()

    ue = read(args.ue_log)
    amf = read(args.amf_log)

    sel = last(SELECT_RE, ue, "UE PASS_V5G_SNPN_SELECT")
    ue_mcc = sel.group("mcc")
    ue_mnc = sel.group("mnc")
    ue_nid = sel.group("nid").upper()
    if ue_nid != args.expected_ue_nid.upper():
        fail(f"UE selected NID {ue_nid}, expected {args.expected_ue_nid.upper()}")

    amf_line = last(AMF_SNN_RE, amf, "AMF HARP_SNPN_AMF_SNN")
    amf_snn = amf_line.group("snn")
    sm = SNN_RE.fullmatch(amf_snn)
    if not sm:
        fail(f"AMF SNN is not canonical SNPN form: {amf_snn}")

    amf_mcc = sm.group("mcc")
    amf_mnc = sm.group("mnc")
    amf_nid = sm.group("nid").upper()
    if int(amf_mcc) != int(ue_mcc) or int(amf_mnc) != int(ue_mnc):
        fail(
            f"PLMN mismatch: UE selected {ue_mcc}/{ue_mnc}, "
            f"AMF SNN carries {amf_mcc}/{amf_mnc}"
        )

    expected_amf = (args.expected_amf_nid or args.expected_ue_nid).upper()
    if amf_nid != expected_amf:
        fail(f"AMF NID {amf_nid}, expected {expected_amf}")

    auth_marker = f"PASS_V5G_SNPN_AUTH_AMF nid={amf_nid}"
    registered_marker = f"PASS_V5G_SNPN_REGISTERED_AMF nid={amf_nid}"
    reject_marker = f"HARP_SNPN_AUTH_REJECT_AMF nid={amf_nid}"
    session_offset = amf_line.end()
    auth_pos = position_after(amf, auth_marker, session_offset)
    registered_pos = position_after(amf, registered_marker, session_offset)
    reject_pos = position_after(amf, reject_marker, session_offset)
    auth_seen = auth_pos != -1
    registered_seen = registered_pos != -1
    reject_seen = reject_pos != -1

    print(f"UE_PLMN={ue_mcc}/{ue_mnc}")
    print(f"AMF_PLMN={amf_mcc}/{amf_mnc}")
    print(f"UE_NID={ue_nid}")
    print(f"AMF_NID={amf_nid}")
    print(f"AMF_SNN={amf_snn}")
    print(f"AUTH_MARKER_SEEN={int(auth_seen)}")
    print(f"REGISTERED_MARKER_SEEN={int(registered_seen)}")
    print(f"REJECT_MARKER_SEEN={int(reject_seen)}")

    if args.mode == "positive":
        if ue_nid != amf_nid:
            fail("positive mode requires identical UE and AMF NID")
        if not auth_seen:
            fail(f"missing exact AMF authentication marker: {auth_marker}")
        if reject_seen:
            fail("positive mode contains an authentication rejection after the current SNN")
        print("PASS_V5G_SNPN_AUTH")
        print("PASS_V5G_SNPN_KDF_LAB")
        if args.require_registration:
            if not registered_seen:
                fail(f"missing exact AMF registration marker: {registered_marker}")
            if registered_pos < auth_pos:
                fail("registration marker appears before authentication success")
            print("PASS_V5G_SNPN_REGISTERED")
        return

    if ue_nid == amf_nid:
        fail("negative mode requires mismatched UE and AMF NID")
    if auth_seen or registered_seen:
        fail("negative mode unexpectedly reached SNPN authentication/registration success")
    if not reject_seen:
        fail(f"negative mode missing explicit AMF authentication rejection marker: {reject_marker}")
    print("PASS_V5G_SNPN_KDF_NEGATIVE")


if __name__ == "__main__":
    main()
