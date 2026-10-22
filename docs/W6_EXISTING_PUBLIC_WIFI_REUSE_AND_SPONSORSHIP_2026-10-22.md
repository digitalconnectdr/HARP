# HARP W6 — Reuse of Dominican public Wi-Fi, verification-first partnerships, and sponsored connectivity
**Research date:** 2026-10-22
**Evidence:** official documentary sources; NO live Wi-Fi, AP, phone, AAA, RFsim or commercial validation.

## 1. New opportunity: do not build APs before inventorying existing public access

The ITU WSIS 2023 entry for INDOTEL's **Banda Ancha para Todos** reports **1,080 public-access Wi-Fi networks** deployed nationwide, in public universities, hospitals, public transportation, parks, plazas, government service offices and libraries. It says the rollout involved Claro, Altice and Wind, and describes **three years** of operation/maintenance responsibility per ISP; the entry describes a potential advertising-based sustainability model, not a verified 2026 contract.

The Dominican Presidency's 2024 retrospective reports that the **Dominicana Conectada** program had expanded to **1,130 free public Wi-Fi access points** nationwide. These are historical program counts, **NOT** a count of working sites as of October 2026. The 1,080 and 1,130 figures should not be added together or treated as separate inventories.

- ITU program entry: https://www.itu.int/net4/wsis/stocktaking/Prizes/Prizes/Details/16704947467405423
- Dominican Presidency retrospective: https://www.presidencia.gob.do/noticias/presidente-de-indotel-en-estos-cuatro-anos-el-pais-se-ha-colocado-como-referente-de-la
- INDOTEL legacy site inventory, including Santiago OMSA corridor entries (May 2020; archival, not current): https://indotel.gob.do/wp-content/uploads/2022/11/0_redes_wifi_-_descripcion_mayo_2020.pdf

**Hypothesis W6-A:** a subset of already-funded, currently operating public/partner Wi-Fi sites can provide HARP Phone A with real IP Internet without A's mobile subscription, without an HARP relay B, and with materially lower incremental AP/backhaul CAPEX than a new W4 mesh build.

**Falsification:** zero authorized, working candidate sites in a defined pilot zone; incompatible captive portal/AAA access rules; backhaul contracts prohibit the intended integration; or measured coverage/service-hour economics are worse than new installations.

**What is not proved:** active sites, access policy, Passpoint support, IP quality, automatic roaming, ability to brand/integrate, and permission to use existing government/ISP infrastructure.

## 2. Separate an immediately usable public hotspot from a HARP-integrated network

- **Access discovery only:** A can connect to a genuinely public/authorized Wi-Fi hotspot via the phone's existing OS UI. HARP adds little unless it can independently verify uptime/quality and help users navigate reliably.
- **Controlled Passpoint overlay (W3):** a venue/ISP/municipality **explicitly agrees** to configure ANQP/802.11u + EAP/AAA or an existing compatible controller; HARP issues authorized credentials. This is a higher-value service but cannot be assumed for current public APs.
- **Community mesh (W4):** only for coverage gaps not economically served by existing APs. 802.11s mesh routers do not make stock Android phones into mesh routers.
- **Volunteer hotspot (W5):** only when B consents and actively enables native tethering; unknown unprepared B is not a general forwarding service.
- **ProSe/SNPN:** separate managed 5G research with network authorization and supported baseband; not a shortcut to enlist unknown phones.
- **eSIM:** provisions a mobile operator network profile, not Internet independently. Do not convert HARP into a purchased-eSIM/data resale product.

## 3. Real Dominican precedent for third-party-funded connectivity, with a crucial limitation

AERODOM's **2026-08-21 official announcement** says international arrivals at AILA can activate **24 hours of free-to-user eSIM Internet** under a partnership with SimLimites, with an option to buy further data. This establishes a real **venue-sponsored connectivity offer** in the Dominican market. It does **NOT** establish that HARP can use that offer, nor disclose the commercial costs, partner funding, traffic limits, subscriber eligibility beyond arrivals, or the underlying mobile operator.

This is a **business-model comparator**, not HARP's final radio solution: A uses an operator-backed temporary mobile profile. HARP's primary technical route remains authorized Wi-Fi without A buying mobile data.

Official release: https://prensa.aerodom.com/news/aerodom-anuncia-alianza-con-simlimites-el-aila-es-el-primer-aeropuerto-de-latinoamerica-en-ofrecer-24-horas-de-internet-gratuito-a-sus-viajeros-mediante-esim-3c1af-d7826.html

## 4. Negative comparator: carrier-offload marketplaces are not general free Internet

Google's Orion Wi-Fi documentation describes a **carrier-offload** marketplace, and its FAQ says venue availability is US-only and users connect when a **participating carrier** authorizes them. The current Orion FAQ redirects to Google Fi's Wi-Fi Auto Connect+ page; that product specifies US availability and eligible Pixel phones. These do not satisfy the no-carrier-subscription, Samsung/RD HARP baseline. Do not add Orion as an RD partner without fresh explicit evidence.

- https://www.orion.google/
- https://orion.google/faq/
- https://fi.google.com/about/wi-fi-auto-connect-plus

## 5. HARP W6 decision gates (all PENDING)

| Gate | Minimum auditable evidence | If it fails |
|---|---|---|
| W6-INV | Current dated list of candidate locations, actual operator/owner, public policy and contact; distinguish archived from active | No claim of 2026 coverage |
| W6-AUTH | Written permission for access/integration and documented upstream/ISP redistribution terms | No HARP-integrated pilot |
| W6-LIVE | Authorized site test: SSID/network discovery, captive portal outcome, IP/DNS, TLS/HTTPS response and timestamps, with A mobile data off | Site excluded from viable coverage |
| W6-QUALITY | Repeat observations of successful user-hours, session continuity, peak throughput, busy-hour concurrency, uptime and user consent | No cost-per-useful-hour claims |
| W6-PASSPOINT | Only for willing operator: actual AP/controller 802.11u/ANQP + AAA support, correct server trust, profile install and authenticated traffic | Use manual public Wi-Fi or reject integration |
| W6-ECON | Written incremental costs/terms, observed served user-hours, comparison to new AP/mesh and sponsor budget | No scale-up |
| W6-LEGAL | Model-specific regulatory and ISP-contract classification confirmed with competent parties | No public commercial launch |

Never equate a historical count of APs with accessible user-hours. Do not conduct unauthorized active scans or attempt bypassing captive portals. Do not store Wi-Fi passwords, per-device MAC histories, or detailed movement trails.

## 6. Parameterized economic test

A small pure-Python planning model is in `tools/w6_wifi_reuse_model.py` (if committed). It estimates:

- eligible_user_hours = users × eligible_hours_per_user
- estimated_served_user_hours = eligible_user_hours × in_coverage × auth_success × upstream_uptime
- cost_per_estimated_served_hour = monthly_incremental_cost / estimated_served_user_hours
- required_busy_hour_Mbps = simultaneous_clients × Mbps_per_client / (1 − overhead)

The model requires an explicit JSON scenario; it **ships no prices, site coverage or measurements**. A synthetic test proves arithmetic only. An operational gate additionally requires `permission_confirmed`, `measurements_confirmed` and sufficient measured usable throughput. The flags are claims from input, not independently verified evidence.

## 7. Next work, without hardware purchases

1. Obtain a dated **2026** official INDOTEL/ISP list of public Wi-Fi sites and current operator/contract/maintenance status; prioritize a small pilot area rather than nationwide headline counts.
2. Select 3–5 candidate venues with owners willing to share technical configuration and explicit permission; record firmware/ANQP capability before proposing Passpoint.
3. Evaluate existing access and measured user-hours before considering W4 mesh expansion.
4. Separately execute existing HARP L2 local APK build and RFsim RAN/AMF auth pair in suitable local environments; this research does not promote any lab PASS.
5. Request sponsor/venue budget and permitted backhaul terms; do not infer prices from the AERODOM eSIM offer.

**Status:** DOCUMENTARY LEAD; all live/permission/quality/commercial gates PENDING. No GitHub Actions, device installs, or new lab PASS.
