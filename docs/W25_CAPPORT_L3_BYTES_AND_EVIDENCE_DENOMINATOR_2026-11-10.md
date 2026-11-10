# W25 — CAPPORT byte accounting and evidence denominator (2026-11-10)

## New standards-derived correction
RFC 8908 §5 defines `bytes-remaining` as **the sum of layer-3 IP packet bytes sent AND received**, including IP headers. It is **not** a one-direction application payload quota. RFC 8908 also permits the captive portal to omit accounting for whitelisted servers, but a client must not rely on this. Accordingly, W24's illustrative 500 MB / 0.25 GB per hour = 2 hours is only valid if the workload estimate is also measured as combined L3 bytes. An application-download-only 0.25 GB/h estimate is insufficient to predict expiry. The API's reported budget may also differ from ISP upstream metering. No portal/ISP implementation has been tested.

RFC 8910 specifies DHCPv4 option 114, DHCPv6 option 103, and RA option 37 for discovery of the API URI; it does not certify public Internet service. The RFC 8908 API can report captive status and remaining session limits, but **a non-captive response is not a substitute for independent DNS, authenticated HTTPS, and egress verification**.

Official standards: https://www.rfc-editor.org/rfc/rfc8908.html (sections 4–5, September 2020); https://www.rfc-editor.org/rfc/rfc8910.html (section 2, September 2020).

## Falsifiable field hypotheses
1. A with mobile data disabled can complete independent DNS + TLS HTTPS to two authorized origins after normal portal login. Failure of either origin is not counted as a successful interval.
2. A site reporting `bytes-remaining` should show a broadly monotonic reduction during a controlled authorized transfer when measured on the same client/session. Differences are recorded rather than interpreted as fraud or bypass; reset/renewal/whitelisting can change the value.
3. The site can sustain 7 days of sampled client intervals with a known observation denominator and without double-discounting availability. **Missing intervals are unknown, not successes.**
4. Client L3 bidirectional bytes, gateway/WAN bytes, and ISP-billed bytes must be labeled as different accounting scopes; do not substitute one for another.
5. Quotes must separately state bandwidth, transfer cap/FUP, redistribution permission, fixed monthly fees, installation/CAPEX, support, power and applicable taxes.

## Offline auditor experiment
A standard-library-only CSV auditor was written and executed locally, without phones or network probes. It requires UTC-aligned, ordered, unique per-client 5-minute records and seven binary evidence flags: `associated`, `portal_authorized`, `dns_ok`, `https1_ok`, `https2_ok`, `egress_verified`, `cellular_off`. It rejects duplicate/malformed/ambiguous records; successful user-hours are successful client intervals / 12. Six synthetic local test cases passed, including negative tests for missing evidence, duplicate timestamps, unaligned timestamps, out-of-order rows and zero-success cost. **The auditor source was not committed in W25; the local execution is not a field result, and its coverage of missing whole intervals remains unverified.** Never infer 7-day observation completeness from an incomplete CSV.

## Architecture and evidence status
- Highest-priority near-term path: venue/ISP-approved guest Wi-Fi with funded fixed backhaul; zero mobile subscription on A does not mean zero upstream cost.
- Voluntary B: needs informed consent, authorized upstream redistribution, W13 quotas + W17 closure + W22 callback integration and independent pairing authentication; public relay NO-GO.
- Unknown B without HARP or a compatible pre-existing OEM/network service: no general relay capability.
- 5G ProSe/SNPN: needs supported, provisioned network and UE features; no PC5 physical demonstration.
- eSIM: credentials/provisioning only, not free backhaul.
- Fixed wireless / PtP / community fiber: candidate low-cost backhaul topologies, not prices or permissions verified.

## Next experiment
Obtain written venue/ISP authorization and two dated comparable quotes. Run a permissioned A cellular-OFF 7-day pilot and capture interval coverage, bidirectional L3 traffic, WAN traffic, portal state, HTTPS/DNS, IP egress and observed successful user-hours. Only then compute real cost per successful user-hour with cost and measurement periods matched.

**No Android build, phone installation, GitHub Actions/CI, physical Internet, RFsim, PC5 or carrier billing measurement in W25.**
