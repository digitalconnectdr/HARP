#!/usr/bin/env python3
"""Offline HARP W8 RFC 8908/8910 CAPPORT fixture preflight (not a live network test)."""
import argparse
import ipaddress
import json
import sys
from pathlib import Path
from urllib.parse import urlsplit


def check_https_uri(value, label):
    if not isinstance(value, str):
        return [f"{label}: expected URL string"]
    try:
        u = urlsplit(value)
        if u.scheme.lower() != "https" or not u.hostname or u.username or u.password or u.fragment:
            return [f"{label}: must be HTTPS with hostname, no credentials/fragment"]
        if any(c.isspace() for c in value):
            return [f"{label}: whitespace is invalid"]
        try:
            ipaddress.ip_address(u.hostname)
            return [f"{label}: use DNS hostname, not IP literal (pilot policy)"]
        except ValueError:
            pass
        if u.port is not None and not (1 <= u.port <= 65535):
            return [f"{label}: invalid port"]
        return []
    except (ValueError, TypeError):
        return [f"{label}: malformed URL"]


def validate(doc, api_uri, dhcp_uri=None, content_type="application/captive+json"):
    errors = []
    if not isinstance(doc, dict):
        return ["API response must be a JSON object"]
    if content_type.split(";", 1)[0].strip().lower() != "application/captive+json":
        errors.append("Content-Type must be application/captive+json")
    errors += check_https_uri(api_uri, "CAPPORT API URI")
    if len(api_uri.encode("utf-8")) > 255:
        errors.append("DHCPv4 option 114 URI exceeds 255 UTF-8 bytes")
    if dhcp_uri is not None:
        errors += check_https_uri(dhcp_uri, "DHCP option 114 URI")
        if dhcp_uri != api_uri:
            errors.append("DHCP option 114 must point to the CAPPORT API URI, not a portal page")
    if type(doc.get("captive")) is not bool:
        errors.append("RFC 8908 requires top-level boolean captive")
    for name in ("user-portal-url", "venue-info-url"):
        if name in doc:
            errors += check_https_uri(doc[name], name)
    if doc.get("captive") is True and "user-portal-url" not in doc:
        errors.append("HARP pilot requires user-portal-url when captive=true")
    if "can-extend-session" in doc and type(doc["can-extend-session"]) is not bool:
        errors.append("can-extend-session must be boolean")
    if doc.get("can-extend-session") is True and "user-portal-url" not in doc:
        errors.append("can-extend-session requires user-portal-url for pilot")
    for name in ("seconds-remaining", "bytes-remaining"):
        if name in doc:
            val = doc[name]
            if type(val) is not int or val < 0:
                errors.append(f"{name} must be a nonnegative integer")
            if doc.get("captive") is True:
                errors.append(f"{name} should be omitted while captive=true (strict pilot)")
    return errors


def self_test():
    api = "https://capport.example.test/api/session1"
    good = {"captive": True, "user-portal-url": "https://portal.example.test/login"}
    checks = [
        (not validate(good, api, api), "valid captive fixture"),
        (not validate({"captive": False, "seconds-remaining": 120}, api, api), "valid noncaptive fixture"),
        (bool(validate({"captive": "true"}, api)), "reject nonboolean captive"),
        (bool(validate(good, "http://capport.example.test/api")), "reject HTTP API"),
        (bool(validate(good, api, "https://portal.example.test/login")), "reject DHCP portal instead of API"),
        (bool(validate(good, api, api, "text/html")), "reject wrong MIME"),
        (bool(validate({"captive": True}, api)), "reject no portal in HARP pilot"),
        (bool(validate({"captive": False, "bytes-remaining": True}, api)), "reject bool quota"),
        (bool(validate(good, "https://192.0.2.1/api")), "reject literal IP"),
        (bool(validate(good, "https://capport.example.test/" + "a" * 300)), "reject overlong DHCP URI"),
    ]
    for result, label in checks:
        if not result:
            raise AssertionError(label)
    print("PASS_HARP_W8_CAPPORT_VALIDATOR_SELFTEST")


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--self-test", action="store_true")
    p.add_argument("--json", type=Path, help="offline JSON fixture")
    p.add_argument("--api-uri", help="CAPPORT HTTPS API URI")
    p.add_argument("--dhcp-uri", help="URI advertised by DHCP option 114")
    p.add_argument("--content-type", default="application/captive+json")
    a = p.parse_args()
    if a.self_test:
        self_test()
        return 0
    if not a.json or not a.api_uri:
        p.error("--json and --api-uri required unless --self-test")
    try:
        doc = json.loads(a.json.read_text(encoding="utf-8"))
        errors = validate(doc, a.api_uri, a.dhcp_uri, a.content_type)
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        print(f"FAIL_HARP_W8_CAPPORT_FIXTURE: {exc}", file=sys.stderr)
        return 1
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        print("FAIL_HARP_W8_CAPPORT_FIXTURE", file=sys.stderr)
        return 1
    print("PASS_HARP_W8_CAPPORT_FIXTURE_STATIC_ONLY")
    return 0


if __name__ == "__main__":
    sys.exit(main())
