# HARP W7 — Santiago: authorized access partners, public Wi-Fi reality check and measurable pilot

**Research date:** 2026-10-23
**Status:** DOCUMENTARY PARTNER LEADS / NO PARTNERSHIP / NO LIVE AP OR PHONE TEST / NO RFsim PASS.
**Scope:** first Santiago-area feasibility gate for Phone A to obtain useful Internet without its own conventional mobile-data subscription. This does not imply free upstream Internet or automatic forwarding by strangers.

## 1. Important correction to W6: historic public Wi-Fi count != 2026 usable inventory

W6 cited historic 1,080/1,130 national public Wi-Fi points. Newer **official INDOTEL planning and execution documents** show that the network was subject to a rescue/transformation program, so those historic headline totals must NOT be used as today's working access-point denominator:

- **2024 execution report:** the 2024 POA execution report describes an adjudicated subset of 18 sites to Gold Data, S.A. (7 at 25 Mbps and 11 at 60 Mbps) and **8 localities installed/received at the Q3 reporting checkpoint** (3 at 25 Mbps, 5 at 60 Mbps); other lots were unsuccessful. This is a **dated subset and checkpoint**, not the nationwide working count or the final 2024 outcome.
- **2025-2026 approved development plan:** INDOTEL Resolution 009-2025 describes a **proposal for 180 public Wi-Fi access points**, approximately 15 communities, 25 or 60 Mbps dedicated fibre, Wi-Fi 6 and remote monitoring, with RD$250,515,034.56 total estimated project budget. **Planned**, not proven installed, available or priced per AP.
- **2025 operational plan:** INDOTEL's POA 2025 includes a **target of 70 functioning Wi-Fi points** for rescue/transformation, with RD$32,392,866.97 program budget. **Target**, not measured 2025 or 2026 delivered count.
- As of this desk review, no authoritative current 2026 per-site live inventory or operator/Passpoint capability table has been confirmed.

Primary evidence:
- https://indotel.gob.do/wp-content/uploads/2025/01/Macro-POA-2024-INDOTEL-2.pdf
- https://indotel.gob.do/wp-content/uploads/2025/01/res._009_2025_pb_2025_2026_140124_1__signed.pdf
- https://indotel.gob.do/wp-content/uploads/2025/03/POA-2025.pdf

**Decision:** do not extrapolate national coverage from 2024 press counts; use a dated candidate ledger and require W6-INV/W6-LIVE.

## 2. New, specific Santiago-area legal-market evidence

The public INDOTEL decisions below identify **real-world organizations authorized to resell Internet within specified territories as of their resolution dates**. They are potential *commercial conversations*, not verified current HARP partners, accessible APs, Passpoint nodes or offers of free Internet. Every entry needs current-registration and upstream-contract confirmation.

| Documentary lead | Resolution and decision date | Authorized geography in document | Upstream named in document | Important boundary |
|---|---|---|---|---|
| FREFELIX WIRELESS, S.R.L. | DE-054-2025, 2025-06-10 | Santiago province | Dominican Telecom Prime DTP | Approved renewal; 36-month term measured from prior authorization's expiry, **not** assumed to start on resolution date |
| CESAR ALEX COMPUTER WIFI, S.R.L. | DE-016-2025, 2025-02-07 | Bella Vista, La Herradura, Reparto Peralta, La Barranquita, La Yagüita de Pastor, Santiago | Mundo1 Telecom | Approved renewal; 24-month term from notification; no evidence of public Wi-Fi |
| GF WIRELESS SOLUTIONS, S.R.L. | DE-101-2024, 2024-11-05 | Pontezuela, Santiago | Mundo1 Telecom | Approved 2-year renewal from notification; check current validity |
| SERVIMAST JPM, S.R.L. | DE-083-2025, 2025-08-04 | Jánico, Juncalito, San José de las Matas and enumerated communities, Santiago | Dominican Telecom Prime DTP | Approved renewal; outside Santiago city in the enumerated authorization |
| DIOSNAEL TELECOMUNICACIONES, S.R.L. | DE-083-2024, 2024-08-19 | Las Tres Cruces, San Francisco de Jacagua, Santiago | BW Telecom | Approved 2-year renewal from prior expiry; check current validity |
| SENDAS ENTERPRISES, S.R.L. | 109-2024, 2024-09-19 | Santiago de los Caballeros | Concession expansion for Internet | Different concession category; do not label as an IRE reseller |

Regulatory decision sources:
- https://indotel.gob.do/wp-content/uploads/2025/06/Res._DE-054-2025_Frefelix_Wireless_SRL.pdf
- https://indotel.gob.do/wp-content/uploads/2025/02/res._de_016_2025_cesar_alex.pdf
- https://indotel.gob.do/wp-content/uploads/2024/11/res._de_101_2024_wireless_solutions.pdf
- https://indotel.gob.do/wp-content/uploads/2025/08/Res._DE-083-2025_de_renovacion_y_consolidacion_de_IRE_-_Servimast_JPM_S.R.L-1.pdf
- https://indotel.gob.do/wp-content/uploads/2024/08/res._de_083_2024_diosnael_telecomunicaciones_s.r.l.pdf
- https://indotel.gob.do/wp-content/uploads/2024/09/res._109_2024_sendas_enterprises_signed.pdf

**New hypothesis W7-A:** an already-authorized fixed Internet provider/venue in Santiago will voluntarily host a sponsor-funded, isolated guest AP (or compatible Passpoint SSID) with contractual permission for the intended use. Phone A can then authenticate and browse HTTPS without A's mobile plan and without an HARP-installed Phone B.

**Falsification:** no consenting partner in the selected pilot geography; contract prohibits guest/public access; no isolated guest-network capability; or measured service-hour economics are not competitive.

This is a **partnered access** model, not a solution for arbitrary geographic locations with no participating infrastructure.

## 3. Capacity gate: compare the official 25/60 Mbps project profiles with W6 demand

Assume **eight concurrent users × 5 Mbps application throughput** with **10% planning overhead**. Required backhaul is 8×5/(1−0.10) = **44.44 Mbps**.

At that assumed overhead, a **25 Mbps** backhaul has 22.5 Mbps left for payload (at most **4 simultaneous 5 Mbps users** by floor arithmetic); **60 Mbps** has 54 Mbps left (at most **10** such users). Thus 25 Mbps **fails** this illustrative eight-user criterion; 60 Mbps **passes the backhaul-only arithmetic**, not the actual RF/Wi-Fi congestion, asymmetric upload, contention, policy limits, service quality or sustained throughput.

The RD$250.5m project budget includes multiple capital/service components and is **not** a price quote to HARP or a measured per-site monthly fee. The 2025 RD$32.39m budget is a program line item, not HARP's available subsidy.

A separate official 2026 announcement discusses a subsidized **RD$299/month** fixed-broadband offer in specified southern underserved localities. It is a market comparator, **not a Santiago offer or permission to resell or redistribute the connection**:
https://presidencia.gob.do/noticias/indotel-marca-un-antes-y-un-despues-en-la-transformacion-digital-del-pais

## 4. Explicit operating model

**Candidate ledger:** `data/w7_santiago_operator_leads_2026-10-23.csv` contains the six documented regulatory leads with official decision links. `current_authorization_verified`, `partner_consent`, `upstream_guest_redistribution_approved`, `site_live_measured` and `passpoint_supported` all begin as `unknown` (not `yes`).

**Partner prequalification (no outreach claimed):**
1. Confirm registration, scope, provider and contractual authorization *currently in force* with INDOTEL/provider; written answer required.
2. Ask whether a guest SSID is permissible and whether they control AP/controller, VLAN isolation, bandwidth and captive portal/AAA.
3. If they agree, obtain a dated written pilot permission, location, data-protection boundaries and commercial/sponsorship terms.
4. With permission, measure mobile-data-OFF Phone A HTTPS/DNS, network validation, p95 latency, packet loss, peak concurrent users, 30-day successful user-hours and downtime.
5. For W3, only then evaluate Passpoint EAP-TLS with correct AAA server trust and certificate revocation; never claim OpenRoaming membership from private Passpoint.
6. Keep costs explicit: `net_monthly_cost = partner_fee + equipment_amortization + power + operations + AAA - sponsor_funding`; `cost_per_successful_user_hour = net_monthly_cost / measured_successful_user_hours`. Zero observed hours means **undefined**, not zero cost.
7. Isolate guests and avoid open proxies, unauthorized scanning, captive-portal bypass, retention of MAC/location trails or use of third-party connectivity without permission.

**Pilot go/no-go (proposed targets, not current results):** secure one written-authorized site; confirm its current status and commercial terms; test at least 30 days with a pre-registered schedule; obtain ≥90% HTTPS success during measured on-site coverage windows; establish backhaul busy-hour headroom for declared concurrent demand; show positive measured useful hours and a funded monthly cost. A failed legal/permission gate is an immediate **NO-GO** regardless of radio speed.

## 5. Other HARP branches, unchanged by W7

- **L2 Wi-Fi Aware + SOCKS + VPN:** cooperative B and locally built APK remain necessary for the physical test; no APK/phone PASS.
- **Stock B without HARP:** user-enabled tethering only with consent and a legitimate upstream; no covert/automatic forwarding.
- **W4 mesh:** may extend a participating fixed AP into a coverage gap; mesh links still consume capacity and backhaul.
- **ProSe/SNPN:** authorized 3GPP cellular relay in compatible networks and devices; RFsim auth pair not yet run; no PC5 IP/ProSe PASS.
- **eSIM:** operator credential provisioning, not Internet created independently. Venue-sponsored mobile data is a funding comparator, not the final HARP radio mechanism.

## 6. Next research cycle: verifiable outcomes

1. Look for **2025/2026 execution evidence** of the 70-point target/180-point plan, rather than treating a target as achieved.
2. Check whether any of the six providers publishes **official enterprise guest Wi-Fi / hotspot partnership** conditions and verify present operating area. Website product claims alone are not authorization for HARP use.
3. Build a consent-first three-site pilot matrix in Santiago: operator, venue, geography, authorized guest SSID, measured service-hours, commercial sponsor and 30-day costs.
4. Continue separate software-only RFsim and local APK build gates when compatible environments become available; never promote a documentary result to a lab PASS.

**Evidence grade:** Official legal decisions and government project plans are documentary evidence; current operating status, partnership, RF performance, AP compatibility, site cost and actual user Internet remain **unverified**. No GitHub Actions or phone installs.
