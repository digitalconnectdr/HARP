# HARP — Research decision record: ProSe/SNPN, relays and operator independence

**Evidence cutoff:** 2026-10-17
**Repository:** digitalconnectdr/HARP
**Type:** documentary research and experimental decision; **not** a measured RFsim or phone PASS.

## 1. Requirements must not be conflated

HARP's original end goal is useful Internet for Phone A without A contracting its own conventional mobile-data plan. Investigate the stronger constraint of an unknown, unprepared Phone B separately.

Acceptance dimensions:

- R1: A has no active paid data plan and no native Internet route.
- R2: A exchanges real application data with a remote endpoint, with request and response observed at A.
- R3: B is not required to install HARP (stronger, independent hypothesis).
- R4: any transit through B is authorized and does not assume that an unknown person's data plan can be used without permission.
- R5: costs (backhaul, spectrum, radio network, energy, device hardware) are explicitly accounted for.

A successful two-phone Wi-Fi Aware relay can validate R1/R2 **only when B is participating and has authorized upstream Internet**; it does not validate R3/R5.

## 2. Newly checked standards evidence

**Rel-19 ProSe has an explicit SNPN/multi-hop standards path.**

- 3GPP TS 23.304 CR 0568 rev. 2, listed in the 3GPP portal as approved into version 19.6.0, addresses *Multi-hop ProSe support for SNPNs*.
- 3GPP TS 24.554 CR 0792 rev. 1, approved into 19.3.0, adds configuration parameters for multi-hop 5G ProSe operation in SNPN.
- The justification for TS 24.554 CR 0792 states that in the multi-hop SNPN scenario **all UE nodes in all hops need to be in the subscribed SNPN**.
- Earlier approved 3GPP TS 24.554 CR 0632 covers ProSe announce procedures within SNPN.

**Interpretation:** this improves the feasibility of a *managed* 5G device-to-network relay. It does NOT establish a public Android API or a way to enlist arbitrary stock phones as Internet relays. HARP must still obtain device/baseband/OS support, provisioning, consent/authorization and access-network support. A standardized procedure is not evidence of deployment on the Samsung S22 Ultra or any other retail device.

Sources:
- https://portal.3gpp.org/ChangeRequests.aspx?q=1&specnumber=23.304
- https://portal.3gpp.org/DesktopModules/CRs/CrDetails.aspx?CrId=583024
- https://portal.3gpp.org/DesktopModules/CRs/CrDetails.aspx?CrId=553974

## 3. Status boundary for the OAI lab

The OAI RFsim walkthrough documents an SA architecture using nrUE + gNB + 5GC, including example UICC credentials and user-plane connectivity. The OAI RF simulator is a software radio-channel model, not a physical mobile-network or ProSe product.

OAI's published roadmap lists *Basic Sidelink Procedures (PSSCH, PSCCH)* as a **Q2 2026 roadmap item**. A roadmap entry is not a test result. Do not promote that item to implemented/verified solely from its roadmap date. An OAI NR sidelink synchronization or PSBCH test is not a PSSCH user-data proof.

Accordingly, HARP's test ladder remains explicitly separated:

| Gate | Evidence required | Currently evidenced? |
|---|---|---|
| G-SNPN-REF | NID / SNN / KDF deterministic reference vectors | Earlier local self-test records; repeat when environment permits |
| G-SNPN-BUILD | RAN and AMF patched sources actually compile | No |
| G-SNPN-AUTH+ | Full positive RFsim registration/authentication with matching NID | No |
| G-SNPN-AUTH- | Negative NID-only mismatch rejects while controls are equal | No |
| G-PC5-SYNC | Physical/simulated sidelink synchronization/PSBCH | Not demonstrated by HARP |
| G-PC5-DATA | PSSCH/PSCCH real payload from UE A to UE B | No |
| G-PC5-IP | End-to-end IP packet A↔B over PC5 | No |
| G-PROSE-U2N | Authorized UE-to-network relay with end-to-end request/response | No |
| G-STOCK-B | Independent unmodified/unaffiliated B relays HARP traffic | No |

HARP's latest local orchestration is `tools/oai_snpn/run_rfsim_auth_lab.sh`: preflight -> clean-worktree fixture generation -> RAN gates -> AMF gates -> matched positive/negative auth pair. Its structural self-test and dry-run are **not** the RFsim real PASS. It must be run in a Linux environment with compatible OAI toolchains; GitHub Actions remain disabled.

Sources:
- https://github.com/openairinterface/openairinterface5g/blob/develop/ROADMAP.md
- https://github.com/openairinterface/openairinterface5g/blob/develop/doc/NR_SA_Tutorial_OAI_nrUE.md
- https://github.com/openairinterface/openairinterface5g/blob/develop/radio/rfsimulator/README.md

## 4. What a Credentials Holder and eSIM can and cannot do

HARP's Credentials Holder branch can decouple who issues/holds subscriber credentials from who operates the private access network. It does **not** remove the need for a functioning radio access network, a 5GC, Internet breakout/backhaul, device firmware compatibility and admission policy. eSIM/RSP likewise manages credential/profile provisioning, not free radio access or a perpetual Internet uplink.

The product must not be described as "free Internet created from radio signals." A no-plan-for-A product is possible in principle only with an upstream whose cost is borne by some authorized participant/infrastructure/community. That is a commercial/network design challenge in addition to software.

Standards anchor:
- https://portal.3gpp.org/DesktopModules/CRs/CrDetails.aspx?CrId=482840

## 5. Reusing third-party smartphones as infrastructure

**Hubble terrestrial:** the official integration guide documents up to **13 bytes of custom application data** in BLE advertisements addressed to its network. Useful experimental candidate for tiny uplink telemetry, but not evidence for low-latency, arbitrary IP, bidirectional browser sessions. Its satellite documentation describes asynchronous satellite-to-ground delivery, not interactive satellite-to-device Internet.

**Nodle:** the vendor describes opted-in smartphones with Nodle SDK embedded in a host app as BLE edge nodes that collect and forward device observations. The smartphone software is a pre-existing participant; this is not generic forwarding by unprepared Android phones. No published general-purpose SOCKS/IP downlink service is established by those pages.

**Wi-Fi Aware:** Android's documented APIs require participating discovery and network-request logic; availability is device/firmware dependent. The HARP A↔B laboratory does not satisfy R3.

Sources:
- https://hubble.com/docs/guides/device-integration
- https://hubble.com/docs/network/satellite/how-it-works
- https://docs.nodle.com/nodle-sdk
- https://developer.android.com/reference/android/net/wifi/aware/WifiAwareManager

## 6. Dominican Republic deployment cost/authorization

INDOTEL's official portal distinguishes licenses for use of frequencies tied to concessions/registrations, and registration/licensing for private radio services. Do not infer that a non-public 5G base station may operate on an arbitrary cellular band without authorization. Exact bands, exemptions, type approvals, operational restrictions and current fees must be checked with INDOTEL for a real deployment, not guessed from a lab RFsim experiment.

This increases the commercial relevance of a cooperative/unlicensed Wi-Fi-based access network as an **alternative physical infrastructure hypothesis**, even though it would require compatible APs, backhaul and coverage; it would not convert all nearby stock phones into relays.

Sources:
- https://www.gob.do/servicios/licencia-para-usar-frecuencias-del-espectro-radioelectrico-que-se-encuentra-vinculada-a-una-autorizacion
- https://www.gob.do/servicios/inscripcion-y-licencia-para-operar-servicios-privados-de-radiocomunicaciones-o-servicios-moviles-aeronauticos

## 7. Priorities and decision gates after this research

**Parallel track A — Android transport (practical near-term):** obtain a locally built APK, install the same version on A and B only after build gate succeeds, then observe Stage-0/1/2 and Stage-2A. Report no real PASS before logs exist. Keep B cooperative and online during this controlled proof.

**Parallel track B — 5G identity proof (software-only):** execute the pinned end-to-end OAI/AMF RFsim positive/negative auth pair. Preserve run manifests and logs and verify the NID-only causal difference. A success shows SNPN/KDF operation only, not PC5 or stock-phone relaying.

**Track C — feasibility of the final architecture:** request concrete equipment/OEM/operator feature-level capability evidence: 3GPP release and exact PC5/U2N/NPN features; host APIs; permission/provisioning; use in DR; whether an independently deployed B can discover and forward HARP traffic. Alternatively scope a consent-based participating network or a self-operated unlicensed access/backhaul model. Do not build an open proxy on unsuspecting phones.

**Go/no-go rule:** do not count (a) an RFsim authentication PASS as Internet, (b) Aware SOCKS relay as stock-B relaying, (c) advertising BLE telemetry as bidirectional IP, or (d) credentials/eSIM issuance as upstream access.

## 8. Status at conclusion

- No new Android build/device PASS claimed by this desk review.
- No real RAN/AMF/RFsim PASS claimed by this desk review.
- No new hardware or service purchases justified yet.
- The strongest standards-aligned route is managed ProSe U2N in a cooperative SNPN; the strongest near-term experimental route is the existing Android Wi-Fi Aware proof.
- Product-level R3/R5 remain unresolved research decisions.
- No GitHub Actions were run or enabled.
