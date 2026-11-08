# W23 — Economics and capacity gate for authorized community Wi-Fi (2026-11-08)

## Evidence and scope
This is an **offline scenario model**, not a provider quote, field measurement or proof of Internet on phone A. W22 JDK gate remains a reference only. No Android production change in W23.

Internet Society's [Community Networks DIY technology guide](https://www.internetsociety.org/resources/community-network-diy-toolkit/determine-technology-requirements/) distinguishes last-mile access from upstream backhaul and identifies fiber, wireless and satellite options; its [backhaul study](https://www.internetsociety.org/resources/doc/2019/solutions-for-traffic-backhaul-in-community-networks/) explicitly requires upstream Internet for a community network. [ITU-D 2025 cost report](https://www.itu.int/pub/D-STG-SG01.04_REV_ED-2025) discusses infrastructure-sharing and service-cost economics. These sources **do not validate any Dominican Republic vendor price**.

## Falsifiable pilot model
Inputs: monthly upstream RD$4,500; power RD$500; maintenance RD$700; access-point/router CAPEX RD$12,000 amortized over 24 months; 600 nominal successful user-hours/month; 90% availability; 0.25 GB per successful user-hour; usable upstream 25 Mbps; 10 concurrent users targeting 2 Mbps each. **Every number is hypothetical, not an offer or observation.**

Calculations (verified offline with Python stdlib unit tests, 4/4 PASS):
- Monthly allocated cost = 4500 + 500 + 700 + 12000/24 = **RD$6,200**.
- Availability-adjusted successful user-hours = 600 × 0.90 = **540 h**.
- Cost per delivered successful user-hour = 6200/540 = **RD$11.48/h**.
- Assumed monthly transfer = 540 × 0.25 = **135 GB**; allocated cost **RD$45.93/GB** (not ISP billing).
- Peak offered load = 10 × 2 = **20 Mbps**; 25 Mbps usable upstream gives **5 Mbps nominal headroom**. This is an arithmetic admission check, **not** a throughput/latency guarantee.
- To reach RD$10 per delivered hour at 90% availability requires at least **688.89 nominal user-hours/month**.
- **Dimensional warning:** GB per user-hour and target Mbps per concurrent user are separate workload assumptions. They are not interchangeable; a 2 Mbps stream for one hour transfers ~0.9 GB decimal. Do not claim both assumptions describe continuous streaming.
- No packet overhead, taxes, uplink contention, upstream data caps, burstiness, power outages or vendor terms have been measured; include them in real quotations.

## Go/no-go experiments
1. Obtain written venue permission and ISP authorization for redistribution; record plan, monthly bill, data cap/FUP, tax, setup fees, upload/download SLA, allowed number of guests, and cancellation.
2. A with mobile data OFF and an approved guest AP: log DNS A/AAAA, HTTPS IPv4/IPv6, externally observed egress IP, captive portal behavior, isolation, disconnect/reconnect and session success. **Not yet executed.**
3. Collect hourly simultaneous clients, throughput p50/p95, successful user-hours, availability, traffic GB, power consumption and incidents for >=7 days. Derive costs using *observed* hours and quotes.
4. Reject a site if authorization is missing, effective backhaul fails target at measured peak, isolation fails, or cost per successful hour exceeds the pilot's documented threshold. No thresholds have yet been commercially approved.

## Architecture decision
- Authorized community/venue Wi-Fi: first physical experiment; A needs no cellular subscription, but needs compatible Wi-Fi coverage and funded backhaul.
- Voluntary B relay: W13 quota, W17 close, W22 callback gate are not integrated; public relay NO-GO.
- Unknown stock B without HARP/cooperation: no general authorization or standard feature to force Internet sharing.
- 5G ProSe/SNPN: provisioned radio/core and compatible UE are not substitutes for a funded upstream.
- eSIM: credential/profile provisioning, not free backhaul.

## Execution classification
Local offline Python standard-library scenario checks: **4/4 PASS** (known scenario, over-capacity, zero user-hours, invalid inputs). This run did **not** run Java/Android builds, use a physical AP/phone, obtain ISP quotes, perform RFsim/PC5, or measure Internet. No GitHub Actions or CI used.
