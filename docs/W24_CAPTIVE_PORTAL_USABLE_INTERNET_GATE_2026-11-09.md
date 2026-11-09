# W24 — Captive-portal versus usable Internet acceptance gate (2026-11-09)

## Scope and new distinction
W23 modeled capacity and delivered user-hours, but did not specify a deterministic classification of **association / local gateway / captive portal / usable public Internet**. This W24 gate prevents counting an associated phone A or a successful portal login as an Internet-success hour. **Documentation and offline arithmetic only; no field measurements, vendor quote, Android build or production code.**

Standards checked: [RFC 8910](https://www.rfc-editor.org/rfc/rfc8910.html) advertises the captive-portal API URI via DHCPv4 option 114, DHCPv6 option 103 and IPv6 RA option 37; [RFC 8908](https://www.rfc-editor.org/rfc/rfc8908.html) defines the HTTPS captive-portal API including `captive`, `user-portal-url` and optional session/byte limits. An RFC-compliant portal API is **not** itself proof of Internet reachability. Android [NetworkCapabilities](https://developer.android.com/reference/android/net/NetworkCapabilities) distinguishes `NET_CAPABILITY_INTERNET`, `NET_CAPABILITY_VALIDATED` and `NET_CAPABILITY_CAPTIVE_PORTAL`; an Internet capability is not a validated end-to-end result. Do not bypass captive-portal authorization or assume all guest APs implement RFC 8908/8910.

## Falsifiable P0 test protocol (authorized venue + ISP only)
1. Record signed venue/ISP redistribution permissions, network ID, SSID, router model, tariff, cap/FUP, and observation timestamp. A: mobile data OFF, record SIM/data state, Wi-Fi association and obtained gateway/DNS addresses. Never record portal passwords, MAC addresses or personal browsing history.
2. Before portal acceptance, record DHCP/RA captive-portal URI **if present**, Android validation/captive status, DNS A/AAAA to controlled public hostname, HTTP portal redirect, and HTTPS certificate validity. Do not treat a redirected HTTP response as public HTTPS success.
3. Accept portal terms through normal UI only if authorized. Afterwards verify: independent DNS resolution, TLS-authenticated HTTPS to two distinct authorized public origins, response body nonce, observed external egress IP, and no cellular fallback. Repeat after AP reconnect and portal expiry. Where available, compare IPv4 and IPv6.
4. Record samples at fixed intervals, e.g. 5 minutes, including associated, portal-only, DNS_OK, HTTPS1_OK, HTTPS2_OK, gateway/egress proof, throughput and latency. Define `internet_success = associated AND portal_authorized AND DNS_OK AND HTTPS1_OK AND HTTPS2_OK AND egress_verified AND cellular_off`. This is a **proposed acceptance definition**, not a standards requirement. Any missing evidence => not successful.
5. Compute delivered user-hours as `sum(successful 5-minute client intervals)/12`. Count simultaneous users separately (one clock-hour with 10 successful clients = 10 user-hours). Record session failures and the number of devices; report denominator and observation coverage. For billing economics use measured successful hours and quoted costs, not W23's availability multiplier a second time.
6. Isolation negative test: from consenting client devices only, attempt access to a deliberately provisioned test host on a separate guest client; reject if guest-to-guest communication succeeds against site policy. Do not scan unrelated clients.

## Economic correction to W23 assumptions
W23's 540 hours were 600 **nominal** user-hours multiplied by a hypothetical 90% success factor. If the pilot actually measures 540 **successful** user-hours, the cost is RD$6,200/540 = RD$11.48 per successful user-hour; do **not** multiply by 0.9 again. If portal expiry reduces successful intervals to 360, cost becomes RD$17.22/h at the same hypothetical fixed monthly cost. If the portal enforces 500 MB per session, a hypothetical 0.25 GB/h workload reaches that quota after 2 hours of transfer per session, before overhead; this is a scenario, **not an ISP or portal quota quote**. Count portal waiting time as unsuccessful. All values illustrative.

## Architecture boundaries and status
- Authorized guest/community Wi-Fi: highest-priority practical path for A without a cellular subscription, conditional on funded upstream and explicit redistribution rights.
- B voluntary relay: W13 quotas, W17 close and W22 reference gate still need Android integration, independent authentication, consent and legal upstream rights. **NO-GO public relay**.
- Unknown B without HARP: no general mechanism to force hotspot/relay or share billing; discovery does not grant permission.
- 5G ProSe/SNPN: requires compatible provisioned radio/core/UE; not a demonstrated stock handset bypass.
- eSIM: provisions service profiles, does not create free upstream.
- Low-cost infrastructure: authorized AP + fixed broadband, point-to-point backhaul, or community fiber are candidate topologies; each requires dated quotes, power, mounting rights, capacity and permitted redistribution.

## Evidence level and next experiment
This document is a **test specification** derived from official standards, not an executed test. Next obtain one written ISP/venue authorization and two dated upstream quotes; execute the A data-OFF 7-day portal-aware test; publish anonymized interval counts and successful user-hour cost. No GitHub Actions/CI, phone installs, field Internet measurements, PC5/RFsim or carrier billing used in W24.
