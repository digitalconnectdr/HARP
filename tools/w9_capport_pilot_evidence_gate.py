#!/usr/bin/env python3
"""HARP W9: offline CAPPORT two-client evidence preflight, NOT a live network test.

This tool checks internal consistency and SHA-256 evidence integrity. A READY
result requires independent human review of evidence before any physical PASS.
"""
import argparse
import copy
import hashlib
import json
import re
import sys
import tempfile
from pathlib import Path
from urllib.parse import urlsplit


def _require(condition, message, errors):
    if not condition:
        errors.append(message)


def _https_url(value):
    if not isinstance(value, str) or any(ch.isspace() for ch in value):
        return False
    try:
        url = urlsplit(value)
        return (url.scheme == "https" and bool(url.hostname)
                and url.username is None and url.password is None
                and not url.fragment and url.port in (None, 443))
    except ValueError:
        return False


def _state(state, captive, allowed, label, errors):
    _require(isinstance(state, dict), label + ": state missing", errors)
    if not isinstance(state, dict):
        return
    _require(state.get("captive") is captive, label + ": captive mismatch", errors)
    _require(state.get("internet_allowed") is allowed,
             label + ": enforcement mismatch", errors)


def validate(manifest, evidence_root):
    errors = []
    if not isinstance(manifest, dict):
        return ["manifest must be a JSON object"]
    _require(manifest.get("evidence_type") == "field",
             "evidence_type must be 'field' (synthetic data is never field proof)", errors)
    site = manifest.get("site")
    _require(isinstance(site, dict), "site missing", errors)
    if isinstance(site, dict):
        _require(bool(site.get("permission_document")),
                 "site: written venue permission reference required", errors)
        _require(bool(site.get("isp_rights_document")),
                 "site: ISP redistribution permission reference required", errors)
        _require(site.get("guest_network_isolated") is True,
                 "site: guest network isolation not established", errors)
    net = manifest.get("network")
    _require(isinstance(net, dict), "network missing", errors)
    if isinstance(net, dict):
        _require(net.get("join_method") == "manual",
                 "CAPPORT does not auto-join a new Wi-Fi network", errors)
        _require(net.get("identity_mode") == "per_client_uri",
                 "pilot requires per-client URI identity binding", errors)
        a_uri, b_uri = net.get("client_a_api_url"), net.get("client_b_api_url")
        _require(_https_url(a_uri) and _https_url(b_uri),
                 "both CAPPORT API URLs must use HTTPS DNS hostnames", errors)
        _require(a_uri != b_uri, "per-client CAPPORT URLs must differ", errors)
        _require(net.get("dhcp114_a_url") == a_uri,
                 "A DHCP option 114 must match A CAPPORT API", errors)
        _require(net.get("dhcp114_b_url") == b_uri,
                 "B DHCP option 114 must match B CAPPORT API", errors)
        _require(net.get("mime") == "application/captive+json",
                 "CAPPORT response MIME mismatch", errors)
        _require(net.get("cache_control") == "no-store",
                 "per-client state must use Cache-Control: no-store (pilot policy)", errors)
        _require(net.get("tls_verified") is True,
                 "API certificate and hostname must be independently verified", errors)
    a = manifest.get("client_a")
    _require(isinstance(a, dict), "client_a missing", errors)
    if isinstance(a, dict):
        _require(a.get("mobile_data_off") is True, "A mobile data must be OFF", errors)
        _require(a.get("harp_installed") is False, "A must not require HARP app", errors)
        _state(a.get("before"), True, False, "A before", errors)
        _state(a.get("after"), False, True, "A after", errors)
        _state(a.get("expired"), True, False, "A expiry", errors)
        after = a.get("after")
        if isinstance(after, dict):
            for key in ("dns_success", "https_success", "gateway_egress_seen"):
                _require(after.get(key) is True, "A after: missing " + key, errors)
    b = manifest.get("client_b")
    _require(isinstance(b, dict), "client_b missing", errors)
    if isinstance(b, dict):
        _require(b.get("unauthorized_during_a_auth") is True,
                 "B must remain unauthorized during A's login", errors)
        _require(b.get("internet_allowed_during_a_auth") is False,
                 "B must not inherit A's Internet authorization", errors)
    negative = manifest.get("negative_controls")
    _require(isinstance(negative, dict), "negative_controls missing", errors)
    if isinstance(negative, dict):
        for key in ("private_lan_blocked", "client_to_client_blocked",
                    "preauth_internet_blocked"):
            _require(negative.get(key) is True,
                     "negative_controls: missing " + key, errors)
    artifacts = manifest.get("artifacts")
    _require(isinstance(artifacts, list) and len(artifacts) >= 4,
             "at least four hashed evidence artifacts are required", errors)
    if isinstance(artifacts, list):
        root = Path(evidence_root).resolve()
        names = set()
        for index, artifact in enumerate(artifacts):
            label = "artifact[" + str(index) + "]"
            if not isinstance(artifact, dict):
                errors.append(label + ": invalid record")
                continue
            name, rel, digest = (artifact.get("name"), artifact.get("path"),
                                 artifact.get("sha256"))
            if not isinstance(name, str) or not name.strip() or name in names:
                errors.append(label + ": missing or repeated evidence name")
            names.add(name)
            if not isinstance(rel, str) or not rel or Path(rel).is_absolute():
                errors.append(label + ": invalid relative path")
                continue
            file = (root / rel).resolve()
            if not file.is_relative_to(root) or not file.is_file():
                errors.append(label + ": missing/outside evidence root")
                continue
            if not isinstance(digest, str) or not re.fullmatch(r"[0-9a-f]{64}", digest):
                errors.append(label + ": invalid SHA-256")
                continue
            if hashlib.sha256(file.read_bytes()).hexdigest() != digest:
                errors.append(label + ": evidence SHA-256 mismatch")
    return errors


def self_test():
    with tempfile.TemporaryDirectory() as folder:
        root = Path(folder)
        artifacts = []
        for name in ("venue_permission", "isp_rights", "client_a_gateway",
                     "client_b_negative", "https_dns_proof"):
            file = root / (name + ".txt")
            file.write_text("SYNTHETIC TEST ONLY: " + name, encoding="utf-8")
            artifacts.append({"name": name, "path": file.name,
                              "sha256": hashlib.sha256(file.read_bytes()).hexdigest()})
        doc = {
            "evidence_type": "field",
            "site": {"permission_document": "venue_permission.txt",
                     "isp_rights_document": "isp_rights.txt",
                     "guest_network_isolated": True},
            "network": {"join_method": "manual", "identity_mode": "per_client_uri",
                        "client_a_api_url": "https://capport.example.test/api/a",
                        "client_b_api_url": "https://capport.example.test/api/b",
                        "dhcp114_a_url": "https://capport.example.test/api/a",
                        "dhcp114_b_url": "https://capport.example.test/api/b",
                        "mime": "application/captive+json",
                        "cache_control": "no-store", "tls_verified": True},
            "client_a": {"mobile_data_off": True, "harp_installed": False,
                         "before": {"captive": True, "internet_allowed": False},
                         "after": {"captive": False, "internet_allowed": True,
                                   "dns_success": True, "https_success": True,
                                   "gateway_egress_seen": True},
                         "expired": {"captive": True, "internet_allowed": False}},
            "client_b": {"unauthorized_during_a_auth": True,
                         "internet_allowed_during_a_auth": False},
            "negative_controls": {"private_lan_blocked": True,
                                  "client_to_client_blocked": True,
                                  "preauth_internet_blocked": True},
            "artifacts": artifacts,
        }
        assert not validate(doc, root), validate(doc, root)
        mutations = [
            ("network", "join_method", "automatic"),
            ("network", "dhcp114_b_url", doc["network"]["dhcp114_a_url"]),
            ("network", "cache_control", "public"),
            ("client_b", "internet_allowed_during_a_auth", True),
            ("site", "guest_network_isolated", False),
            ("client_a", "mobile_data_off", False),
        ]
        for section, key, value in mutations:
            changed = copy.deepcopy(doc)
            changed[section][key] = value
            assert validate(changed, root), (section, key)
        changed = copy.deepcopy(doc)
        changed["client_a"]["expired"]["internet_allowed"] = True
        assert validate(changed, root)
        changed = copy.deepcopy(doc)
        changed["artifacts"][0]["sha256"] = "0" * 64
        assert validate(changed, root)
        changed = copy.deepcopy(doc)
        changed["artifacts"][0]["path"] = "../outside.txt"
        assert validate(changed, root)
        changed = copy.deepcopy(doc)
        changed["evidence_type"] = "synthetic"
        assert validate(changed, root)
    print("PASS_HARP_W9_STATIC_EVIDENCE_VALIDATOR_SELFTEST")
    print("NO_LIVE_CAPPORT_OR_ANDROID_INTERNET_TEST_WAS_PERFORMED")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--evidence-root", type=Path)
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return 0
    if not args.manifest or not args.evidence_root:
        parser.error("--manifest and --evidence-root are required")
    try:
        manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
        errors = validate(manifest, args.evidence_root)
    except (OSError, ValueError, UnicodeError) as exc:
        errors = [str(exc)]
    if errors:
        for error in errors:
            print("ERROR:", error, file=sys.stderr)
        print("FAIL_HARP_W9_EVIDENCE_PREFLIGHT", file=sys.stderr)
        return 1
    print("READY_HARP_W9_EVIDENCE_FOR_MANUAL_REVIEW")
    print("NOT_A_PHYSICAL_CONNECTIVITY_PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
