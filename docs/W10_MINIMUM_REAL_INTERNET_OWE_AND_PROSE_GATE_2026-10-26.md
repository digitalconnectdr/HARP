# HARP W10 — Simplify the first real Internet proof; protect guest Wi-Fi with OWE

**Research date:** 2026-10-26  
**Evidence class:** current official Android/AOSP, IETF, OpenWrt, Samsung and 3GPP documentation. **No hardware, operator agreement, live Android, gateway, PC5 or RFsim test was performed.**

## Executive decision: stop making CAPPORT a prerequisite

W8/W9 were valuable but the first field experiment was overconstrained. **CAPPORT is optional for a network that can grant guest access directly.** To prove the actual HARP product requirement "Phone A has useful Internet without an active cellular data plan or HARP app", the shortest falsifiable route is:

```
A: cellular data disabled, HARP not installed
  -> join one authorized guest SSID (manually or conventional Wi-Fi QR)
  -> DHCP/DNS/IP -> real HTTPS in a normal browser
  -> traffic confirmed at consenting gateway
  -> ISP-approved, funded backhaul -> Internet
```

This proves only **Internet within that participating Wi-Fi footprint**. It is not operator-independent global coverage. It is not a discovery mechanism for arbitrary networks and does not make B an unconsenting relay.

If a sponsor/venue later requires login, quotas, or consent pages, add W8/W9 CAPPORT as a **second** gate. Do not delay the first IP demonstration for a portal server, DPP, Passpoint federation, or 5G SDR.

## 1. Newly verified security alternative: Enhanced Open / OWE

- Android's AOSP documentation (last updated **2026-10-09**) says Android 10 introduced **Wi-Fi Enhanced Open**, based on Opportunistic Wireless Encryption (OWE). It provides **per-association link encryption on passwordless public Wi-Fi**, **not authentication**.
- Android feature availability depends on chipset, firmware, driver, supplicant and vendor implementation. The public Android API `WifiManager.isEnhancedOpenSupported()` can check capability **from an authorized local app**, but Android-version support is **not** proof that the HARP Galaxy S22 Ultra actually supports OWE.
- RFC 8110 confirms that OWE does not authenticate the AP and remains susceptible to active evil-twin/relay attacks. HTTPS and trustworthy portal URLs remain important. OWE is not a VPN and cannot authorize use of someone else's upstream.
- OpenWrt documents OWE-only and OWE transition mode; it specifies that full `wpad` variants are required. Actual AP radio/firmware/driver compatibility must be checked before buying hardware.
- **Transition mode is not an encrypted guarantee**: legacy devices connect to an unencrypted network. Never count an OWE-transition SSID as encrypted for all users. The pilot should distinguish the actual client negotiation mode, not just the SSID name.

Primary references:
- AOSP (2026-10-09): https://source.android.com/docs/core/connect/wifi-wpa3-owe
- RFC 8110: https://www.rfc-editor.org/rfc/rfc8110.html
- OpenWrt OWE: https://openwrt.org/docs/guide-user/network/wifi/owe_encryption
- OpenWrt Wi-Fi encryption: https://openwrt.org/docs/guide-user/network/wifi/encryption

## 2. Simpler zero-install onboarding: ordinary Wi-Fi QR versus DPP

Samsung documents that Galaxy phones can join a **known, consenting Wi-Fi network** by scanning a QR code containing its connection details. This is distinct from the DPP `DPP:...` mechanism investigated in W9 and **does not require DPP-capable AP hardware**. A Wi-Fi QR is a convenience for a known SSID/credential, not authorization to reuse unknown networks.

For the very first test, manual SSID join is even simpler. If an authorized venue offers a WPA2/WPA3 guest SSID and a QR, A can attempt HTTPS without any HARP software or CAPPORT at all.

Samsung sources:
- https://www.samsung.com/sg/support/mobile-devices/share-your-wi-fi-password-without-typing-it/
- https://www.samsung.com/ca/support/mobile-devices/connect-your-samsung-galaxy-to-the-internet/
- https://www.samsung.com/ca/support/mobile-devices/scan-a-qr-code-with-your-samsung-galaxy/

## 3. A practical three-mode pilot, with clear security properties

| Mode | What it proves | Tradeoff |
|---|---|---|
| P0: isolated WPA2/WPA3 guest SSID, consented credential/QR | First normal-app IP/DNS/HTTPS with cellular data OFF, no HARP app | Existing password/credential and manual join; not automatic discovery |
| P1: isolated **OWE-only** guest SSID, if both A and AP support it | Passwordless *encrypted* 802.11 link plus IP/HTTPS | No AP authentication; may exclude older devices |
| P2: optional CAPPORT over P0/P1 when policy requires it | Native Android captive login and session enforcement | More software, TLS, per-client identity, negative security tests |

**Not recommended as the default:** OWE transition mode. If used for compatibility, measure separately how many associations negotiate OWE vs legacy open, and segregate/label the insecure fallback. Do not weaken the venue's internal network.

The first pilot can use an existing managed guest SSID if the venue and ISP explicitly permit it. **No hardware purchases are justified before rights and site capability are confirmed.**

## 4. W10 minimum physical experiment and evidence rules

No physical steps have been executed. Proposed acceptance criteria:

1. **G0 rights/security** — Written permission from venue and ISP for the exact guest-redistribution scope; independent guest VLAN/firewall, no private LAN access, and consented observation. If not, STOP.
2. **G1 baseline association** — A connects manually or with conventional Wi-Fi QR to the approved SSID. A has cellular data OFF; no HARP app. Record OS/device version and actual AP security mode. Do not infer OWE solely from Android version.
3. **G2 useful Internet** — Show DHCP lease, DNS resolution, an ordinary HTTPS request to a non-captive Internet endpoint and matching egress counters/logs at the consenting gateway. Browser splash/portal alone is NOT a PASS. A's mobile route must not be used.
4. **G3 negative security** — A cannot access venue private LAN; A cannot reach other guest clients; on P2, B remains blocked while A is authorized, and session expiry blocks A again.
5. **G4 utility and economics** — Record busy-hour throughput, p95 latency, actual connected-user hours, user/session success ratio, energy, amortization, maintenance and **ISP-authorized** monthly upstream cost. Measure at least a useful observation window before making service claims.

Only G0-G4 **with physical evidence** can justify a pilot claim. Passing static Python validators from W8/W9 does not. If P0 succeeds, P1/OWE and P2/CAPPORT are improvements, not new sources of Internet.

**Suggested experiment order:** G0 -> P0/G1/G2/G3 -> P1 OWE compatibility/encryption -> P2 CAPPORT only if required -> W3 Passpoint for returning visitors -> W4 mesh only if coverage justifies the added hop cost.

## 5. Unit economics without fabricated tariffs

For each actual candidate venue collect:
- `C_month = permitted_upstream + venue_fee + AP_gateway_amortization + energy + operations + authentication + settlement - contracted_sponsorship`.
- `successful_user_hours = sum(actual successful connected hours with usable DNS/HTTPS)`.
- `C_per_successful_user_hour = C_month / successful_user_hours` (undefined if zero).
- `C_per_active_user = C_month / verified distinct users` (undefined if zero).
- `busy_hour_headroom = measured_usable_backhaul - measured_simultaneous_demand`.

Do not treat historical INDOTEL public-hotspot counts as live coverage or residential ISP plans as necessarily permitting resale. The 25/60 Mbps cases in W7 are planning inputs, not measured usable throughput.

## 6. ProSe/SNPN, stock-phone B, eSIM and satellite: refreshed boundary

The 3GPP Rel-19 SNPN ProSe change request TS 24.554 CR 0792 rev 1 is **approved** and explicitly deals with provisioned/configured participating devices in subscribed SNPNs. This does not create a public Android API for arbitrary nearby phones B.

Another 3GPP change request, TS 31.102 CR 1075, discusses new UICC configuration files for multi-hop ProSe U2N/U2U, but its portal status is **Revised**, not an approved/deployed feature claim. This is further evidence that PC5 relay requires provisioning, device support and authorization; it does **not** prove a retail Galaxy's modem can relay HARP packets.

Android's official satellite framework supports specialized NTN services, but requires OEM modem/HAL integration and network/carrier entitlement. It does not turn generic satellite visibility into free arbitrary Internet for HARP.

Sources:
- https://portal.3gpp.org/DesktopModules/CRs/CrDetails.aspx?CrId=583024
- https://portal.3gpp.org/DesktopModules/CRs/CrDetails.aspx?CrId=574643
- https://source.android.com/docs/core/connect/satellite

Status of other paths:
- Cooperative Phone B hotspot: possible with consent, limited spatial availability; not a substitute for fixed APs.
- Unknown unprepared Phone B: no verified forwarding mechanism; do not claim a solution.
- Managed ProSe/SNPN: standards pathway but no HARP RAN/AMF RFsim runtime PASS, no PC5 user-plane IP, no phone relay PASS.
- eSIM: profile/identity provisioning; not upstream radio or Internet itself.
- Community mesh: may extend participating AP coverage; backhaul and channel airtime still required.

## 7. Research decisions for the next cycle

**Stop expanding architecture count until at least one hard gate changes.** Prioritize one of:
- documented written consent and a current ISP-permitted venue;
- actual Android A/guest AP association and Internet egress;
- actual local HARP L2 APK build and cooperative A/B transport;
- real OAI/AMF positive/negative RFsim authentication evidence;
- verified OEM capability for retail PC5 U2N relay.

The W10 matrix is at `data/w10_access_options_2026-10-26.csv`. All physical deployment entries remain `NOT_TESTED`. No GitHub Actions, phone installation, scanning, operator outreach, hardware purchase or real network testing was performed during this desk review.
