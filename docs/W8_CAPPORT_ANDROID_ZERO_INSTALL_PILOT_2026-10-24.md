# HARP W8 — Zero-install Android guest Wi-Fi using CAPPORT (RFC 8908/8910)
**Research date:** 2026-10-24
**Evidence level:** standards + implementation documentation; **no live AP, partner consent, Android device, backhaul or user-Internet test**.

## Executive decision
Before deploying private Passpoint credentials/OpenRoaming federation (W3) or a mesh (W4), test the **simplest authorized network** that can give a stock Android Phone A useful Internet with mobile data OFF and **no HARP app installed**:

```
Phone A (stock Android 11+, no SIM data)
 -> manually joins authorized guest SSID
 -> DHCPv4 option 114 advertises HTTPS Captive Portal API endpoint
 -> Android reads CAPPORT JSON (RFC 8908)
 -> OS shows operator login/terms URL
 -> user explicitly accepts authorized access
 -> gateway permits user traffic
 -> DNS/HTTPS/Internet via operator-approved backhaul
```

**W8 is an access/onboarding method, not a novel source of Internet.** It requires a participating AP, an authorized gateway, a valid upstream contract, a permitted venue and a funded Internet connection. It does **not** work over arbitrary Wi-Fi networks or via unwilling/unknown stock phones B.

## New decisive finding
- Android's official **Android 11 captive portal API support** documentation says DHCP option **114** can announce the API URI and Android can fetch the API immediately to show the sign-in prompt. Older/fallback captive-portal probes still exist. OEM/firmware behavior on HARP's actual Samsung phones remains untested.
- **RFC 8910** replaces deprecated DHCP option **160** with **114**. Critically, option 114 points to the **RFC 8908 JSON API**, *not* directly to a login HTML page. Wrong target is a standards-level configuration error.
- **RFC 8908** requires the API be HTTPS, validated against its advertised hostname; content type `application/captive+json`; a top-level boolean `captive`. The `user-portal-url` is an optional HTTPS URL at the standards level, but the HARP pilot **requires it when captive=true** to provide a usable sign-in path. Per-client state must be consistent between the enforcement gateway and API server. Do not expose session secrets in public logs.
- **openNDS** is a concrete open-source gateway candidate: upstream documentation explicitly describes support for both traditional Captive Portal Detection (CPD) and RFC 8908/8910 Captive Portal Identification (CPI), plus a Forward Authentication Service (FAS). This is documented capability, **not** proof it has been installed, secured or tested in HARP.

Primary sources:
1. Android Developers: https://developer.android.com/about/versions/11/features/captive-portal
2. IETF RFC 8910: https://www.rfc-editor.org/rfc/rfc8910.html
3. IETF RFC 8908: https://www.rfc-editor.org/rfc/rfc8908.html
4. openNDS official project: https://github.com/openNDS/openNDS
5. openNDS configuration reference: https://github.com/openNDS/openNDS/blob/master/linux_openwrt/opennds/files/etc/config/opennds

## Why W8 is distinct from W3 and W7
- W3 Passpoint/802.1X: credential/profile provisioning and automatic future discovery/association to *configured* participating networks. Potentially seamless but requires compatible AP/AAA/profiles, possibly federation agreements.
- W8 CAPPORT: a **manually joined** participating guest SSID, then native Android sign-in prompt. No Passpoint profile, no HARP Android app, no OpenRoaming broker needed for the initial proof.
- W7: supplies documentary candidate Internet operators in Santiago. W8 is the **minimum viable network-access test** that can be offered only after a venue and an operator grant permission.
- W4 mesh: optional coverage extension *after* one W8 AP demonstrably works; not required for first proof.
- L2 cooperative B and 5G SNPN/ProSe: separate technical experiments, unaffected by W8. eSIM only provisions credentials; no free backhaul.

## Controlled implementation hypothesis (not a deployment instruction)
Proposed **isolated lab only**, after written venue/ISP authorization:
1. Confirm ISP contract permits guest/public access, obtain guest SSID/AP admin authorization, and separate guests from venue internal LAN using VLAN/firewall/client isolation. Record whether open SSID, OWE or WPA2/3 guest is supported; CAPPORT alone does not encrypt the Wi-Fi radio link.
2. Set up an openNDS-compatible gateway using a **supported OpenWrt/router build**, or another gateway that can implement CAPPORT correctly. Preserve a recovery path; never expose router admin HTTP/SSH on the public WAN as a shortcut.
3. Serve a properly certified **HTTPS** API endpoint with `Content-Type: application/captive+json`. Before authorization: `{"captive":true,"user-portal-url":"https://portal.example.invalid/login"}` is a **shape-only fixture** (the .invalid domain is deliberately not live). After authorization: `{"captive":false}`. Ensure state belongs to the connecting client.
4. Advertise the **API URI** (not the HTML login URL) in DHCPv4 option 114. Validate URL length/HTTPS/TLS/hostnames. Restrict preauth access to the API, login portal and necessary DNS/time/certificate validation endpoints; prevent unauthorized DNS tunneling and open proxy exposure.
5. On Phone A, manually join the consenting guest SSID with mobile data OFF. Measure time to system sign-in prompt, successful acceptance, IP/DNS, HTTPS in normal browser, failures/retries, expiry, logout and reconnection. Test a non-supporting client fallback separately.
6. Record guest-hour costs, 95th-percentile latency, successful HTTPS sessions, backhaul contention and coverage. Never count CAPPORT JSON responses as Internet success.

**Pilot acceptance gates (proposed, not executed):**
- G0 written authorization, permitted guest redistribution, site/ISP scope, security/isolation confirmed.
- G1 API HTTPS certificate valid; DHCP 114 points to JSON API; strict local fixture validator PASS.
- G2 Phone A OS detects portal without HARP app; actual screenshot/log; fallback documented.
- G3 A mobile data OFF: authenticated normal Chrome HTTPS, DNS and IP, observed through authorized gateway.
- G4 negative controls: captive remains blocked before login, logout/expiry blocks again, guests cannot reach private LAN or one another.
- G5 30-day availability, real user-hours, commercial fees and upstream throughput measured.

**Stop conditions:** no written upstream rights; inability to isolate guests; invalid HTTPS certificate; no usable Android sign-in; portal only provides splash but no normal-app Internet; economics fail measured useful-hour threshold.

## Security and privacy rules
- Do **not** intercept or forge TLS certificates to force a captive portal; prefer standards-based CAPPORT.
- No collection of user credentials beyond strictly needed and consented access; no storage of MAC/IP/location histories by default.
- Portal TLS protects web interactions, **not** necessarily 802.11 frames if the SSID is open.
- API identity and gateway enforcement must agree even with Android MAC randomization; do not assume a stable hardware MAC across networks.
- Portal/auth service must not create an open proxy, unauthenticated Internet bypass or public management interface.
- CAPPORT detection is not a substitute for contractual permission or cybersecurity review.

## Local deliverable
`tools/w8_capport_validate.py` is an offline stdlib-only fixture validator. It checks JSON structure, MIME, HTTPS API/portal URIs, DHCPv4 option 114 URI consistency and optional quotas. It does **not** contact networks or configure routers. A local self-test marker is meaningful only if the script is actually run.

## Research prioritization after W8
**First** demonstrate authorized stock Android Internet via one W8 guest AP; **then** compare W3 Passpoint for repeat automatic access. Keep OAI RFsim paired auth and L2 APK physical proof as separate independent gates. Do not expand to mesh hardware, federation membership or 5G SDR spending before those gates produce actual evidence.

**Current state:** research-only; no phone install; no live pilot; no GitHub Actions.
