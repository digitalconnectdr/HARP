#!/usr/bin/env python3
"""HARP W14: static cross-file SOCKS5/HEV transport contract audit.

Read-only. Does not compile Android, open sockets, invoke CI or test Internet.
Exit 2 with --strict-release if known protocol gaps exist.
"""
import argparse
import json
import re
import sys
import tempfile
from pathlib import Path

SOURCE = Path('app/src/main/java/org/harp/l2')
FILES = ('Stage2TunnelConfig.java', 'MiniSocks5.java',
         'SocksPolicies.java', 'HarpVpnService.java', 'Stage2RelayServer.java')


def analyze(texts):
    conf = texts['Stage2TunnelConfig.java']
    socks = texts['MiniSocks5.java']
    policy = texts['SocksPolicies.java']
    vpn = texts['HarpVpnService.java']
    server = texts['Stage2RelayServer.java']
    hev_udp_tcp = bool(re.search(r"udp:\s*'tcp'", conf))
    socks_connect_only = (
        bool(re.search(r'CMD_CONNECT\s*=\s*1\s*;', socks))
        and bool(re.search(r'if\s*\(cmd\s*!=\s*CMD_CONNECT\)', socks))
        and 'CMD_UDP_ASSOCIATE' not in socks
    )
    mapped_dns = 'mapdns:' in conf and 'DNS_IPV4' in conf
    tcp_ports_restricted = bool(re.search(r'port\s*!=\s*80\s*&&\s*port\s*!=\s*443', policy))
    ipv4_default_route = 'addRoute("0.0.0.0", 0)' in vpn
    ipv6_default_route = bool(re.search(r'addRoute\("::",\s*0\)', vpn))
    quota_wired = 'RelayQuotaGate' in socks or 'RelayQuotaGate' in server
    gaps = []
    if hev_udp_tcp and socks_connect_only:
        gaps.append('HEV_UDP_OVER_TCP_WITH_CONNECT_ONLY_SOCKS_SERVER')
    if not quota_wired:
        gaps.append('W13_PAYLOAD_GATE_NOT_INTEGRATED')
    if ipv4_default_route and not ipv6_default_route:
        gaps.append('VPN_IPV4_ONLY')
    return {
        'evidence': 'STATIC_SOURCE_REVIEW_ONLY',
        'hev_udp_over_tcp_configured': hev_udp_tcp,
        'socks_connect_only': socks_connect_only,
        'mapped_dns_configured': mapped_dns,
        'tcp_public_web_ports_80_443_only': tcp_ports_restricted,
        'vpn_ipv4_default_route': ipv4_default_route,
        'vpn_ipv6_default_route': ipv6_default_route,
        'w13_quota_gate_wired_to_socks': quota_wired,
        'release_blockers': gaps,
        'physical_internet_proven': False,
        'carrier_billing_measured': False,
    }


def load(root):
    return {name: (root / SOURCE / name).read_text(encoding='utf-8')
            for name in FILES}


def self_test():
    # Synthetic snippets mirror the exact syntactic patterns found in main on 2026-10-30.
    synthetic = {
        'Stage2TunnelConfig.java': 'return "  udp: \'tcp\'\\n" + "mapdns:\\n" + DNS_IPV4;',
        'MiniSocks5.java': 'private static final int CMD_CONNECT = 1; if (cmd != CMD_CONNECT) sendReply(out, 0x07);',
        'SocksPolicies.java': 'if (port != 80 && port != 443) throw new IOException();',
        'HarpVpnService.java': 'builder.addRoute("0.0.0.0", 0);',
        'Stage2RelayServer.java': 'MiniSocks5.serveOne(accepted, io);',
    }
    got = analyze(synthetic)
    assert got['release_blockers'] == [
        'HEV_UDP_OVER_TCP_WITH_CONNECT_ONLY_SOCKS_SERVER',
        'W13_PAYLOAD_GATE_NOT_INTEGRATED', 'VPN_IPV4_ONLY']
    assert got['mapped_dns_configured'] and got['tcp_public_web_ports_80_443_only']
    changed = dict(synthetic)
    changed['Stage2TunnelConfig.java'] = changed['Stage2TunnelConfig.java'].replace("udp: 'tcp'", "udp: 'udp'")
    assert 'HEV_UDP_OVER_TCP_WITH_CONNECT_ONLY_SOCKS_SERVER' not in analyze(changed)['release_blockers']
    changed = dict(synthetic)
    changed['MiniSocks5.java'] = changed['MiniSocks5.java'].replace('if (cmd != CMD_CONNECT)', 'if (cmd != CMD_CONNECT && cmd != CMD_UDP_ASSOCIATE)')
    assert 'HEV_UDP_OVER_TCP_WITH_CONNECT_ONLY_SOCKS_SERVER' not in analyze(changed)['release_blockers']
    changed = dict(synthetic)
    changed['Stage2RelayServer.java'] += ' RelayQuotaGate gate;'
    assert 'W13_PAYLOAD_GATE_NOT_INTEGRATED' not in analyze(changed)['release_blockers']
    # Guard: this audit is not a protocol implementation test. Removing a pattern
    # only removes the static signal; it does not prove any actual UDP support.
    print('PASS_HARP_W14_STATIC_CONTRACT_AUDIT_SELFTEST')
    print('NO_ANDROID_BUILD_NO_LIVE_UDP_NO_INTERNET_TEST')


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--repo-root', type=Path, default=Path('.'))
    ap.add_argument('--self-test', action='store_true')
    ap.add_argument('--strict-release', action='store_true')
    args = ap.parse_args()
    if args.self_test:
        self_test()
        return 0
    try:
        result = analyze(load(args.repo_root))
    except (OSError, UnicodeError) as exc:
        print('ERROR_READ_ONLY_SOURCE_AUDIT:', exc, file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 2 if args.strict_release and result['release_blockers'] else 0


if __name__ == '__main__':
    sys.exit(main())
