#!/usr/bin/env python3
import argparse
import shutil
import subprocess
from pathlib import Path

OAI_RAN_ANCHOR = "f8f769592a7030be88ede4bb5ca66fa1ca6a80e0"
AMF_ANCHOR = "5eedea557a3745b13ed9ec4bf29e6a28bd912574"

BASE_GNB = Path("ci-scripts/conf_files/gnb.sa.band78.106prb.rfsim.conf")
BASE_AMF = Path("etc/config.yaml")

LAB_MCC = "999"
LAB_MNC = "99"
LAB_NID = "10000000001"
NEGATIVE_AMF_NID = "10000000002"


def git_head(path: Path) -> str:
    return subprocess.check_output(
        ["git", "-C", str(path), "rev-parse", "HEAD"], text=True
    ).strip()


def require_anchor(path: Path, expected: str, label: str) -> None:
    repo = subprocess.run(
        ["git", "-C", str(path), "rev-parse", "--is-inside-work-tree"],
        text=True,
        capture_output=True,
    )
    if repo.returncode != 0 or repo.stdout.strip() != "true":
        raise SystemExit(f"{label}: not a git worktree/checkout: {path}")

    actual = git_head(path)
    if actual != expected:
        raise SystemExit(
            f"{label}: wrong commit\nexpected {expected}\nactual   {actual}"
        )

    dirty = subprocess.check_output(
        ["git", "-C", str(path), "status", "--porcelain"], text=True
    ).strip()
    if dirty:
        raise SystemExit(f"{label}: checkout/worktree is not clean")


def exactly_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


def make_gnb(base: str) -> str:
    old_plmn = (
        "plmn_list = ({ mcc = 208; mnc = 99; mnc_length = 2; "
        "snssaiList = ({ sst = 1, sd = 0xffffff }) });"
    )
    new_plmn = (
        "plmn_list = ({ mcc = 999; mnc = 99; mnc_length = 2; "
        "snssaiList = ({ sst = 1, sd = 0xffffff }) });\n\n"
        "    snpn = {\n"
        '      enabled = "yes";\n'
        '      nid = "10000000001";\n'
        "    };"
    )
    return exactly_once(base, old_plmn, new_plmn, "gNB PLMN/SNPN insertion")


def make_amf(base: str, nid: str) -> str:
    out = exactly_once(
        base,
        '  amf_name: "OAI-AMF"\n',
        f'  amf_name: "OAI-AMF"\n  snpn_nid: "{nid}"\n',
        "AMF NID insertion",
    )
    out = exactly_once(
        out,
        "    enable_simple_scenario: no",
        "    enable_simple_scenario: yes",
        "AMF simple-scenario switch",
    )

    # Keep the rest of the stock config intact; only convert the first GUAMI
    # and the first supported PLMN to the HARP laboratory PLMN.
    out = exactly_once(
        out,
        "    - mcc: 208\n      mnc: 95\n      amf_region_id: 01",
        "    - mcc: 999\n      mnc: 99\n      amf_region_id: 01",
        "AMF served GUAMI",
    )
    out = exactly_once(
        out,
        "    - mcc: 208\n      mnc: 95\n      tac: 0xa000",
        "    - mcc: 999\n      mnc: 99\n      tac: 1",
        "AMF PLMN support",
    )
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--ran", required=True, help="Pinned openairinterface5g checkout")
    ap.add_argument("--amf", required=True, help="Pinned oai-cn5g-amf checkout")
    ap.add_argument("--out", required=True, help="Output directory")
    args = ap.parse_args()

    ran = Path(args.ran).resolve()
    amf = Path(args.amf).resolve()
    out = Path(args.out).resolve()

    require_anchor(ran, OAI_RAN_ANCHOR, "OAI RAN")
    require_anchor(amf, AMF_ANCHOR, "OAI AMF")

    gnb_base_path = ran / BASE_GNB
    amf_base_path = amf / BASE_AMF
    if not gnb_base_path.is_file():
        raise SystemExit(f"missing gNB base config: {gnb_base_path}")
    if not amf_base_path.is_file():
        raise SystemExit(f"missing AMF base config: {amf_base_path}")

    root = Path(__file__).resolve().parents[2]
    ue_fixture = root / "lab/rfsim_snpn/nrue.harp-snpn.conf"
    sql_fixture = root / "lab/rfsim_snpn/harp_subscriber.sql"
    if not ue_fixture.is_file() or not sql_fixture.is_file():
        raise SystemExit("HARP RFsim fixture files are missing from the repository")

    out.mkdir(parents=True, exist_ok=True)

    gnb = make_gnb(gnb_base_path.read_text())
    amf_base = amf_base_path.read_text()
    amf_positive = make_amf(amf_base, LAB_NID)
    amf_negative = make_amf(amf_base, NEGATIVE_AMF_NID)

    # Enforce the negative experiment invariant: only the AMF NID may differ.
    normalized_negative = amf_negative.replace(
        f'snpn_nid: "{NEGATIVE_AMF_NID}"',
        f'snpn_nid: "{LAB_NID}"',
        1,
    )
    if normalized_negative != amf_positive:
        raise SystemExit("positive/negative AMF configs differ beyond snpn_nid")

    (out / "gnb.harp-snpn.conf").write_text(gnb)
    (out / "amf.harp-snpn-positive.yaml").write_text(amf_positive)
    (out / "amf.harp-snpn-negative.yaml").write_text(amf_negative)
    shutil.copyfile(ue_fixture, out / "nrue.harp-snpn.conf")
    shutil.copyfile(sql_fixture, out / "harp_subscriber.sql")

    print(f"OAI_RAN_ANCHOR={OAI_RAN_ANCHOR}")
    print(f"AMF_ANCHOR={AMF_ANCHOR}")
    print(f"LAB_PLMN={LAB_MCC}/{LAB_MNC}")
    print(f"UE_GNB_NID={LAB_NID}")
    print(f"AMF_POSITIVE_NID={LAB_NID}")
    print(f"AMF_NEGATIVE_NID={NEGATIVE_AMF_NID}")
    checker = root / "tools/oai_snpn/check_rfsim_fixture_pair.py"
    subprocess.run(["python3", str(checker), str(out)], check=True)

    print("PASS_HARP_RFSIM_FIXTURE_GENERATION")


if __name__ == "__main__":
    main()
