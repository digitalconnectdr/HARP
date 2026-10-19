# HARP — 2026-10-19: OpenRoaming broker/settlement-free and Dominican regulatory gates

**Research-only checkpoint.** No AP, phone, RFsim, AAA or commercial agreement was tested or deployed in this iteration. GitHub Actions remain disabled.

## Executive finding: a narrower but commercially meaningful path

HARP can investigate **no mobile subscription for Phone A** using voluntary, pre-existing Wi-Fi infrastructure in two stages:

1. **Private Passpoint domain:** A installs HARP-issued credentials; a consenting venue's Passpoint AP authenticates A through an authorized AAA and offers IP service through legitimate upstream connectivity. No federation needed for this gate.
2. **OpenRoaming federation:** after private Passpoint is demonstrated, a legitimate OpenRoaming broker can connect an HARP identity provider (IDP) and/or consenting access-network providers (ANPs) to a wider federation. This adds *participating* APs, not every Wi-Fi router or smartphone.

**New detail:** the 12 June 2026 IETF individual Internet-Draft `draft-tomas-openroaming-08` explicitly says ANPs and IDPs **do not need to be WBA members** to participate; brokers are WBA members that assign federation identities and issue/arrange certificates. This suggests a **broker-assisted route** that may be less burdensome than HARP becoming a direct WBA member, but **broker fees, eligibility, liability and contracts are unverified**. The Internet-Draft is *not* an RFC or binding WBA certification document. Validate against current WBA terms and broker offers before claiming eligibility.

Source: https://datatracker.ietf.org/doc/draft-tomas-openroaming/ (sections 1.2, 3 and Appendix C; last updated 2026-06-12).

## New commercial distinction: settlement-free does not mean free backhaul

The same draft distinguishes:

- **Settlement-free:** base RCOI `5A-03-BA`. The ANP does not charge the IDP for the end user's roaming session.
- **Settled:** base RCOI `BA-A2-D0`. The ANP expects compensation for connectivity.

These are federation *accounting policies*, not cryptographic access keys. Actual RCOIs are extended with policy bits and must match valid Passpoint profiles; simply broadcasting the identifiers does not authorize a device or join a federation.

**HARP's candidate:** venue-, municipality- or sponsor-funded *settlement-free* Wi-Fi with opt-in AP owners. HARP would focus on credential issuance, connection quality and service discovery, not paying a cellular operator per phone A. The ANP still pays for or otherwise provides its access point, electricity, maintenance and legitimate upstream. A venue's existing Internet contract may prohibit third-party redistribution; obtain explicit ISP approval.

WBA corroborates that OpenRoaming supports both settled and settlement-free models:
https://wballiance.com/openroaming/faq/

**Falsification:** obtain actual broker and ANP terms. If no feasible settlement-free agreement/coverage exists in the target Dominican venues, this is **not** a viable local distribution channel regardless of standards support.

## Newly discovered regulatory gate: Dominican internet resale rules

The 3 October 2024 **INDOTEL Resolution 116-2024**, *Reglamento de Reventa de Servicios de Telecomunicaciones*, is materially relevant to any plan that takes an ISP uplink and redistributes public Internet as a business:

- Art. 4.3 permits authorized resellers to provide access media, subject to the scope of the authorized service.
- Art. 4.5 distinguishes resale to the public from selling onward to other resellers.
- Art. 4.6 ties resale and own access infrastructure to authorized geographic areas and registered contracts.
- Art. 5.1 requires special-registry registration **before** starting public-service resale operations.
- The regulator's February 2025 Resolution DE-022-2025 describes a reseller's explicit contract with an authorized national concessionaire and notes the 2024 regulation's transitional applicability to older filings.

**Critical qualification:** a venue offering guest Wi-Fi at no charge, a sponsored hotspot, a membership service, and a public paid Internet reseller may be classified differently. This documentary review does **not** establish which regulatory category HARP would occupy. Do not assume either that every guest AP needs resale registration or that an HARP-branded public access network is exempt. Ask INDOTEL and obtain ISP contract confirmation before a public deployment.

Official sources:
- https://indotel.gob.do/wp-content/uploads/2024/10/Reglamento-de-Reventa-Res.-Num.-116-2024.pdf
- https://indotel.gob.do/wp-content/uploads/2025/02/res._de_022_2025_coitnet_e.i.r.l.pdf
- https://indotel.gob.do/wp-content/uploads/2022/09/guia-de-inscripcion-el-registro-especial-para-reventa-de-acceso-a-internet.pdf

**Do not apply the 2022 procedural guide without checking the newer 2024 regulation.** No fees or approval timelines have been assumed.

## Technical implementation anchors

- Android Passpoint R1 Chrome HTTPS provisioning is an Android platform mechanism but requires an initial Internet/bootstrap path and explicit user approval; verify on the real firmware. See https://source.android.com/docs/core/connect/wifi-passpoint
- OpenWrt's full hostapd build configuration contains `CONFIG_HS20=y`, and historical OpenWrt hostapd integration exposes Interworking/802.11u settings. This is **source-level capability**, not proof that an arbitrary router/firmware has Passpoint working. Verify `hostapd -v`, build options, driver support, ANQP response and EAP success on the exact AP before purchasing. See https://git.openwrt.org/openwrt/openwrt/tree/package/network/services/hostapd/files/hostapd-full.config and https://lists.openwrt.org/pipermail/openwrt-devel/2020-October/031812.html
- WBA lists certified AP/controller combinations, but a worldwide certification geography is **not** evidence of an AP deployed in Santiago or elsewhere in DR. See https://wballiance.com/openroaming/certified-partners/

## New operational evidence requirement: accounting without invasive tracking

The June 2026 OpenRoaming draft describes RADIUS Accounting, `Acct-Session-Id`, `Event-Timestamp` and optional connection-quality information (`Connect-Info`). These can support a **consented pilot's** measured session success, session duration, traffic volume (if provided by the NAS), reconnect failure and throughput; not every metric is mandatory or supported by all APs.

Use anonymous outer EAP identities where supported. Aggregate metrics by venue and time window; do not log EAP passwords, raw permanent identifiers, MAC-address histories or detailed location traces. Apply retention limits and a documented consent policy.

Source: https://datatracker.ietf.org/doc/draft-tomas-openroaming/ (sections 8.2.3, 9, 10.6).

## Four gates before spending on APs or promising a commercial launch

| Gate | Exact evidence required | Failure means |
|---|---|---|
| W3-BROKER | At least one broker provides written IDP/ANP onboarding terms, supported RCOI/policy, fees and DR eligibility | Federated route is not yet commercially actionable |
| W3-LEGAL | Written ISP authorization for guest/public redistribution; local regulatory classification of the specific model documented | No public pilot; private controlled lab only |
| W3-HARDWARE | Candidate existing AP's actual firmware supports 802.11u/ANQP/HS2.0 + EAP/RADIUS; evidence from its admin/firmware, not a chipset brochure | Do not purchase or promise Passpoint coverage |
| W3-ACCESS | A with mobile data off authenticates using an approved profile, receives IP/DNS and completes HTTPS request/response; record radio/AAA/IP logs | No product-level Internet claim |

Later, measure opt-in user-hour coverage and busy-hour capacity before estimating cost per useful served hour. The existing `tools/access_feasibility.py` measures monthly averages, not coverage or concurrency. A separate `tools/w3_access_quality.py` (when committed) can calculate a *planning-only* busy-hour/availability sensitivity, never measured coverage.

## Reprioritization of the whole HARP program

| Architecture | A no mobile plan | Unprepared stock B | Requires cooperating infrastructure | Principal unknown |
|---|---|---|---|---|
| L2 Wi-Fi Aware + SOCKS/VPN | Yes in controlled lab | **No** | B with HARP and upstream | Actual APK/phone validation |
| W3 private Passpoint | Yes | B **not involved** | Participating AP, AAA, upstream | AP/Android compatibility + legal permission |
| W3 broker-assisted settlement-free OpenRoaming | Yes, where covered | B **not involved** | Federated ANP, broker/IDP, upstream | Local coverage, partner terms, sponsorship |
| 5G ProSe/SNPN | Potentially | **No generic stock-B proof** | Authorized compatible UE and 5G network | Feature-level hardware and live 5G tests |
| eSIM/RSP | Not by itself | No | Provisioned network/identity | eSIM is provisioning, not free Internet |

**Decision:** prioritize W3's *partner/legality/coverage* gates and L2's *physical build* gate in parallel. Keep ProSe/SNPN as long-horizon standards research. Do not confuse identity federation with universal radio coverage.

**No GitHub Actions, no device installation, no live RFsim/phone/AAA PASS claimed.**
