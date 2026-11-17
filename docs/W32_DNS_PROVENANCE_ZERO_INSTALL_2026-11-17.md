# W32 — DNS provenance and zero-install pilot controls — 2026-11-17

## Finding (design/standards only)
W31's browser-based dual HTTPS checks do **not** independently prove that a conventional UDP/TCP port-53 DNS query originated from A or used the AP's DHCP resolver. Android 9+ Private DNS can use DNS-over-TLS (TCP 853); Chromium supports DNS-over-HTTPS and can use an independently configured resolver. Browser caches and existing connections may further prevent a fresh lookup. Therefore absence of port-53 packets at a gateway is **not** DNS failure, and successful HTTPS does not by itself establish DNS resolver provenance.

Official sources (reviewed 2026-11-17):
- Android Private DNS: https://developers.google.com/speed/public-dns/docs/using
- DNS-over-TLS and strict/opportunistic modes: https://developers.google.com/speed/public-dns/docs/dns-over-tls/
- Chromium DNS-over-HTTPS: https://www.chromium.org/developers/dns-over-https/
- Chrome Enterprise DoH modes: https://chromeenterprise.google/intl/en_ca/policies/dns-over-https-mode/

## Falsifiable experiment, no phone installations
1. With written venue and ISP approval, A mobile data OFF, connect to authorized AP; record Android Private DNS mode, browser Secure DNS setting, VPN/proxy state, OS/browser versions, DHCP lease, AP station identifier (account for MAC randomization), IPv4 and IPv6 routes.
2. Use two unrelated TLS-verified HTTPS origins and unique cache-busting URLs; note that query strings bypass HTTP object caches but do **not** force a new DNS lookup. Use a controlled, consented unique subdomain per trial if fresh DNS provenance is required; retain only minimal logs.
3. On gateway capture DNS egress categories UDP/TCP 53, TCP 853, HTTPS/443 (DoH indistinguishable from other HTTPS without endpoint attribution); collect per-client DHCP/AP session and WAN counters. Do not disable encrypted DNS as a condition of *usable Internet*; if changing it for a diagnostic A/B test, obtain user consent and restore it.
4. Negative controls: disconnect Wi-Fi with cellular OFF (HTTPS must fail absent another route); separately block only gateway DNS/53 in a controlled test and predict that cached/DoH/DoT lookups may continue, so this cannot establish a global DNS failure. Check IPv6 and VPN routes before claiming exclusivity.
5. Classify independently: (a) usable TLS Internet, (b) DNS resolution observed by a known resolver, (c) DNS transport inferred, (d) gateway-attributed WAN bytes. Unknown observations stay UNKNOWN. A browser cannot supply authenticated interface-level packet traces without instrumentation.

## Economics and architecture
W23's RD$6,200/month remains an **illustrative** budget, not a quote. 10 simultaneous users at 2 Mbps require 20 Mbps payload before overhead; 25 Mbps usable backhaul leaves 5 Mbps nominal headroom, not an SLA. At 540 verified user-hours RD$11.48/user-hour; at 270 verified user-hours RD$22.96/user-hour; seven-day samples cannot directly divide an entire monthly cost without period alignment. Collect two dated quotes including installation, upload, FUP, NAT/public IP, SLA, redistribution rights, power, taxes and maintenance.

Priority: authorized community/sponsored Wi-Fi with funded upstream. Voluntary B still requires consent and integrated W13 quotas/W17 socket closure/W22 revocation. Unknown noncooperative B cannot be assumed to relay. 5G ProSe/SNPN requires compatible provisioned UE and operator/private-network arrangements; eSIM is provisioning, not a free upstream. Low-cost AP/mesh/point-to-point radio cannot eliminate backhaul.

## Execution status
GitHub connector read the real PROJECT_STATUS.md and W27 auditor. A fresh container git clone failed DNS resolution for github.com on 2026-11-17; **no exact-source W27 test execution**. No physical A Internet, Android build, phone installs, PC5/RFsim, quotes, or lab tests. No GitHub Actions/CI triggered or used. **Public relay NO-GO.**
