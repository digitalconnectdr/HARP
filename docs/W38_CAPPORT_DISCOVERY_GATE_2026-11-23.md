# W38 — CAPPORT discovery versus actual Internet entitlement (2026-11-23)

## Scope and evidence
Read current docs/PROJECT_STATUS.md and tools/w27/{audit.py,test_audit.py} via authenticated GitHub connector. Existing W37 already covered RFC 8908 quota accounting; **this iteration adds RFC 8910 discovery/interoperability**, not another quota model. No physical measurement, no exact-source test execution, no vendor quote, no Android build.

## Official normative reference
- IETF RFC 8910 (September 2020): https://www.rfc-editor.org/rfc/rfc8910.html — DHCPv4 captive-portal option **114**, DHCPv6 option **103**, IPv6 Router Advertisement option **37** convey a URI for the **RFC 8908 API endpoint**, not a generic login URL. The URI across provisioned methods SHOULD be identical. IPv4 DHCP URI length <=255 bytes. RFC 8910 replaces RFC 7710's conflicting DHCPv4 option 160.
- IETF RFC 8908: https://www.rfc-editor.org/rfc/rfc8908.html — CAPPORT API describes captive status/quota; neither API availability nor captive=false proves independently observed Internet access.

## Falsifiable zero-install experiment (NOT executed)
In a venue/ISP-authorized isolated SSID with an owned AP/gateway, A has cellular data OFF, no VPN, and no installed HARP software. Configure RFC 8910 DHCPv4 option 114 (and DHCPv6 103/RA 37 only where supported) pointing to the same HTTPS CAPPORT API URI. Record AP DHCP/RA config, A association, portal presentation, two distinct TLS-valid HTTPS origin requests, WAN egress counters and timestamps. Test a deliberately incorrect URI in a controlled isolated configuration and restore; compare portal discoverability while keeping the same upstream. **Expected:** CAPPORT signaling may improve portal discovery; it cannot generate Internet upstream. Browser/OS support varies; a negative portal popup is not proof of failure to connect. Avoid HTTPS interception/MITM. Negative control: Wi-Fi OFF and cellular OFF should remove new Internet connectivity after existing sessions are cleared. Separate API reachability, portal authorization, IP egress, DNS provenance, measured WAN bytes and billed ISP bytes.

## Economic falsifier
At F=RD$6,200/month hypothetical and 100 active sponsored clients, fixed cost RD$62/client/month excluding CAPEX, power, maintenance, taxes, metered transit and permissions. If a provider quotes per-GB fee p and usage 1,000 GB, cost/client=(6200+1000p)/100; e.g. p=RD$3/GB => RD$92/client/month. This is a sensitivity calculation **not** a quoted tariff. 15 concurrent clients x 2 Mbps x 1.3 headroom = 39 Mbps busy-hour usable target; 50 Mbps nominal is insufficient if efficiency is <78% (39/50). Seek two dated quotes explicitly allowing resale/sponsorship, stating contention, caps and SLA.

## Architecture decisions
1. Sponsored/community Wi-Fi with licensed/authorized upstream: candidate for zero-install A physical test.
2. Voluntary B: requires consent, local radio/link, actual paid upstream, W13 quota + W17 socket close + W22 revocation; not production-ready.
3. Unknown B without HARP/consent: no general relay entitlement or device-control path demonstrated.
4. 5G ProSe/SNPN: compatible provisioned UEs and operator/private-network authorization; no universal bypass.
5. eSIM: profile provisioning, not free data/backhaul.
6. Mesh: access transport only unless an authorized node provides upstream.

## Exit criteria
- Implement and locally validate versioned auditor states (SUCCESS/FAIL/UNKNOWN), distinguish point-sample equivalent hours from observed continuous time; execute exact committed test code offline.
- Confirm written venue and ISP rights, two real quotes, a gateway with IPv4/IPv6 enforcement and counter evidence.
- Run zero-install A pilot, then seven days; only then consider GO for *that authorized Wi-Fi deployment*. Public volunteer relay remains **NO-GO**.

No GitHub Actions/CI, no phone installs, no Android/PC5/RFsim or physical tests performed.
