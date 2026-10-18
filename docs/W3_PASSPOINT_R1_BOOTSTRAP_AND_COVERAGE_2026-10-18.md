# HARP W3 — 2026-10-18: zero-APK Passpoint provisioning and realistic coverage gate

**Status:** documentary research; no radio hardware, Android APK, or real network test has been executed.

## New finding 1 — Passpoint R1 can be provisioned via Chrome, before an HARP APK exists

Android AOSP documents an HTTPS-triggered Passpoint Release 1 profile installer (supported since Android 6). The user clicks a download link, sees the system installer, and accepts/rejects a network profile. In the documented Chrome path, the HTTP response is `application/x-wifi-config` with `Content-Transfer-Encoding: base64`, no `Content-Disposition`, and the multipart MIME contains an OMA-DM profile plus an AAA trust certificate. For EAP-TTLS the profile carries a unique username/password; EAP-TLS instead carries a PKCS#12 key/certificate, with sensitive provisioning consequences.

**This means HARP can test the core no-mobile-subscription direct Wi-Fi hypothesis WITHOUT writing or installing the HARP Android app**: provision A's credentials via a deliberate HTTPS action on Chrome, then authenticate against a cooperating 802.11u/Passpoint AP and its AAA service. It still requires a supported AP, reachable authorized backhaul, and a pre-provisioning Internet/bootstrap path (e.g. home Wi-Fi). An offline phone cannot fetch a profile from an Internet HTTPS endpoint without prior connectivity. Other browsers may not invoke the installer.

The AOSP docs explicitly state that **Wi-Fi Alliance deprecated Passpoint R2 Online Sign-Up (OSU) on June 30, 2023**. Do not base HARP's onboarding on a new OSU implementation. R1 click-to-install is documented but must be verified on the actual target Android firmware; the long-term path can use Android `WifiNetworkSuggestion.Builder.setPasspointConfig` after user approval.

AOSP states Passpoint is required for Wi-Fi devices running Android 11+, but the manufacturer must provide 802.11u firmware support. This is a platform requirement, **not a measurement of the user's specific S22 Ultra**.

Sources:
- https://source.android.com/docs/core/connect/wifi-passpoint
- https://developer.android.com/reference/android/net/wifi/WifiNetworkSuggestion.Builder
- https://developer.android.com/reference/android/net/wifi/WifiManager

## New finding 2 — the previous per-user economics were insufficient

The existing `tools/access_feasibility.py` calculates monthly GB, monthly cost and an assumed peak multiplier. This is a valid **arithmetic scenario**, not a serviceability or mobility forecast.

Consider a separate illustrative busy-hour hypothesis:
- 40 eligible users, but **8 simultaneous active clients** each seeking 5 Mbps of useful throughput.
- Assume 10% of link payload budget is overhead: required capacity = `8 * 5 / (1 - 0.10) = 44.44 Mbps`.
- A 20 Mbps uplink would fail that planning gate even though 80 GB/month averages only ~0.247 Mbps.
- A 50 Mbps uplink could pass the **backhaul-only** test, but AP airtime, channel interference, retry rates and coverage are still unknown.

Consider coverage:
- User spends 25% of user-hours in a cooperating AP's service area.
- Conditional association/auth success is 90%; conditional upstream availability is 95%.
- Accessible user-hour fraction = `0.25 * 0.90 * 0.95 = 21.375%`.
- With 40 eligible users and a hypothetical RD$4,500 monthly site cost, the site costs RD$112.50 **per eligible user**, but RD$526.32 **per accessible-user equivalent** (40 * 0.21375 = 8.55). This is **not** a subscription price or unique reachable-user count; it highlights how coverage changes the unit economics.

The percentages and prices above are synthetic, not observations, not local quotes and not a promise of 21% coverage in Santiago. User-hours and GB consumption are not interchangeable. To make this a product metric, instrument opt-in coverage samples and the actual fraction of user requests successfully served.

## W3 laboratory decision ladder

1. **W3-PROFILE:** On an initially connected test Android device, user deliberately downloads an R1 profile over HTTPS in Chrome and approves it. Confirm the installed profile is real Passpoint, not a WPA-PSK network. Record the Android build and consent outcome.
2. **W3-AAA:** AP must advertise 802.11u/ANQP/HS2.0 and forward 802.1X authentication to an authorized AAA server. Use EAP-TTLS or EAP-TLS with proper server trust. A failed or revoked credential must be rejected; avoid shared/demo credentials in production.
3. **W3-IP:** Disable A's mobile data and any alternative Wi-Fi route. Demonstrate AAA acceptance, assigned IP, DNS, HTTPS request and remote response. A must have no native cellular data uplink.
4. **W3-REJOIN:** Leave and re-enter AP coverage; record reconnection time, errors, device firmware, certificate behavior and real end-to-end traffic.
5. **W3-COVERAGE:** Measure consenting participants' user-hours and attempted requests in coverage across target routes/venues; never infer geographic coverage from a list of providers.
6. **W3-ECON:** Use simultaneous client measurements and observed payload throughput, airtime, coverage and authorized backhaul contract to estimate cost per actually served user/request.

**Security requirements:** TLS/HTTPS provisioning, per-device credentials, AAA server CA/name validation, revocation, short-lived pilot credentials, no public logging of passwords or client private keys. The Android R1 EAP-TLS installer documentation describes unencrypted PKCS#12 key material *inside the HTTPS-protected provisioning payload*; prefer avoiding private-key distribution through this route in the first pilot.

**FreeRADIUS official EAP-TLS documentation** explains the AP/AAA exchange and distinguishes authentication from obtaining IP connectivity. Certificates shipped for examples are not production trust anchors.
- https://www.freeradius.org/documentation/freeradius-server/3.2.9/tutorials/eap-tls.html
- https://www.freeradius.org/documentation/freeradius-server/4.0.0/troubleshooting/eap_certificates.html

## Decision

The fastest falsifiable direct-access proof is **AOSP Passpoint R1 browser provisioning + cooperating AP + AAA**, before any HARP app build. Do not interpret it as coverage outside participating APs, Internet without upstream cost, or proof that unknown phones relay data. The ProSe/SNPN and Wi-Fi Aware tracks remain independent. No GitHub Actions or phone installations occurred in this research cycle.
