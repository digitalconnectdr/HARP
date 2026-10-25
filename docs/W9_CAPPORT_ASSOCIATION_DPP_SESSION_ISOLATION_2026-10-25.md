# HARP W9 — The missing association and session-security gates (2026-10-25)

**Evidence grade:** official Android/AOSP and IETF specifications, plus offline deterministic self-test. **No authorized AP, physical phone, CAPPORT server, Android association, IP egress or 5G RFsim has been tested in this iteration.**

## 1. New decisive finding: CAPPORT does not join Wi-Fi

W8 proved only that Android can *detect a captive portal after joining an AP*. CAPPORT RFC 8910/8908 is a DHCP/RA plus HTTPS API protocol. It cannot:
- discover an unknown compatible access network as an Internet subscription;
- associate to an SSID without the user/system doing so;
- authenticate an unwilling phone B as a relay;
- fund upstream Internet or override the access provider's policy.

Android's published Android 11 support explicitly identifies DHCPv4 **option 114**, not a guarantee that every Android device consumes the IPv6 RA **option 37**. RFC 8910 standardizes DHCPv4 114, DHCPv6 103, and IPv6 RA 37, but implementation support must be checked per device. Use DHCPv4 114 in the first Android test; IPv6-only CAPPORT is a separate gate.

Sources:
- https://developer.android.com/about/versions/11/features/captive-portal
- https://www.rfc-editor.org/rfc/rfc8910.html
- https://www.rfc-editor.org/rfc/rfc8908.html

## 2. A credible zero-HARP-app association option: Wi-Fi Easy Connect (DPP)

Android 10 introduced Wi-Fi Easy Connect / Device Provisioning Protocol (DPP), which can join a network after the user scans a QR code (initiator-enrollee). AOSP says Android 10 supports initiator mode, **not responder mode**, and requires DPP-capable supplicant, driver, firmware and peer infrastructure. DPP is a secure provisioning/onboarding protocol; it is **not** an Internet provider or an automatic public Wi-Fi federation.

A possible **consent-first** flow:
1. Authorized venue displays a DPP QR code associated with a compatible participating AP/configurator.
2. Phone A scans the code and explicitly approves the system's Wi-Fi join flow, subject to actual firmware support.
3. Phone A joins the authorized network.
4. If the venue has a captive policy, CAPPORT may then present its portal; otherwise the AP can grant authorized access directly.
5. The ISP-approved upstream delivers real IP/DNS/HTTPS.

Do not mistake an ordinary `WIFI:T:WPA;...` QR for DPP `DPP:...`; they are distinct formats and capabilities. A DPP QR code cannot be promised to work on the S22 Ultra without a real compatibility check. DPP is **optional** for the first W8 manual-join pilot, not a prerequisite.

Sources:
- https://source.android.com/docs/core/connect/wifi-easy-connect
- https://developer.android.com/reference/android/net/wifi/WifiManager

## 3. Second critical issue: CAPPORT session identity and cross-client leakage

RFC 8908 requires that the API and enforcement gateway agree on which client is captive. If source IP is not consistently visible, it recommends **per-client API URIs**. A successful login for A must **not** grant Internet to unauthorized B. A `captive=false` JSON response alone is not proof that gateway traffic is permitted. Responses with per-client state should not be publicly cached; the first pilot policy requires `Cache-Control: no-store`.

Android 10+ defaults to randomized Wi-Fi MAC addresses, so a factory-MAC-based permanent entitlement is inappropriate. The pilot should use short-lived session bindings and should not log personal device identifiers unnecessarily.

Sources:
- https://www.rfc-editor.org/rfc/rfc8908.html
- https://www.rfc-editor.org/rfc/rfc8952.html
- https://source.android.com/docs/core/connect/wifi-mac-randomization-behavior

## 4. W9 experiment matrix — proposed, not executed

| Gate | Required proof | Stop/fail condition |
|---|---|---|
| W9-G0 | Written venue + ISP rights, isolated guest network, authorized test window | Any permission or isolation missing |
| W9-G1 | Phone A joins SSID manually; optionally test DPP QR on known-compatible AP | Claim of automatic join without credentials/approval |
| W9-G2 | Android receives DHCPv4 option 114 pointing to **HTTPS API**, valid TLS and `application/captive+json` | DHCP points to HTML portal or TLS invalid |
| W9-G3 | Before A login: `captive=true`, gateway blocks public Internet; after login: `captive=false`, DNS + HTTPS + gateway egress work | JSON says open but gateway does not pass traffic |
| W9-G4 | Phone B remains blocked while A is authorized; no guest-to-guest or guest-to-LAN access | Cross-client authorization leakage |
| W9-G5 | Expiry/logout returns A to captive and blocks public traffic | Stale session keeps forwarding |
| W9-G6 | Measured on-site useful hours, p95 latency, throughput, concurrent load, and authorized upstream monthly fee | No observed user-hours or unsustainable cost |

**No Android app installed on A for W8/W9.** If the project later uses Wi-Fi Network Suggestions, Android requires a participating app and user approval; that is a distinct product mode, not zero-install.

Sources:
- https://developer.android.com/develop/connectivity/wifi/wifi-suggest
- https://developer.android.com/develop/connectivity/wifi/wifi-infrastructure

## 5. New offline evidence validator

`tools/w9_capport_pilot_evidence_gate.py` is an offline **manifest-and-hash preflight**, not a CAPPORT server, router configuration tool or network probe. It checks:
- references to written permissions and isolated guest LAN;
- manual SSID join, per-client HTTPS API URIs, matching DHCPv4 114 URIs, MIME, `no-store`, TLS evidence flag;
- A data-off/no-HARP, before/after/expiry state consistency with enforcement, DNS/HTTPS and gateway egress;
- B remaining blocked during A's authorization;
- negative guest-to-guest/LAN and preauth Internet controls;
- presence and SHA-256 of at least four local evidence artifacts, rejecting path traversal.

`--self-test` uses **synthetic files** and rejects malformed/contradictory scenarios. Passing a field manifest produces only `READY_HARP_W9_EVIDENCE_FOR_MANUAL_REVIEW`; a human must inspect the actual evidence before declaring any real PASS. The tool does not prove that the evidence files are authentic.

Example future invocation (only after authorized physical measurements):
```bash
python3 tools/w9_capport_pilot_evidence_gate.py \
  --manifest /path/to/field-manifest.json \
  --evidence-root /path/to/consented/evidence
```

## 6. Priority decisions and economics

**Do not add DPP hardware purchases yet.** First secure one ISP-approved AP/venue and obtain an Android manual-join W8 result. DPP can reduce first-join friction if the AP and Galaxy firmware support it; Passpoint is the next choice for seamless *return visits*. Neither changes the need for authorized backhaul.

For a pilot, calculate:
- `monthly_total = ISP-approved_guest_backhaul + AP/gateway amortization + electricity + operations + AAA/portal + fees - confirmed sponsorship`;
- `cost_per_successful_user_hour = monthly_total / measured_successful_user_hours`;
- `capacity_headroom = measured_busy_hour_backhaul - measured_busy_hour_demand`.

No numeric price is asserted without a current offer. Do not treat INDOTEL historical public Wi-Fi site counts as 2026 working coverage. W5 voluntary phone relays remain consent-based and density-limited. ProSe/SNPN and eSIM remain separate: no 5G/PC5 runtime PASS and no free radio connectivity implied by credentials.

## 7. Current state

**NEW:** W9 source-level/standards boundary, DPP alternative for first association, cross-client isolation gates, deterministic offline validator.

**NOT DONE:** physical Wi-Fi join, DPP support check on actual S22 Ultra, Android CAPPORT prompt, live portal authorization, Chrome HTTPS, any operator agreement, real RAN/AMF RFsim positive/negative test, PC5 IP, any unknown-stock-B forwarding.

**Safety:** no network scanning, no installs, no GitHub Actions, no open proxy, no unconsented data access.
