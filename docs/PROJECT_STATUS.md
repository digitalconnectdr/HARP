# HARP — Project Status

**Baseline date:** 2026-10-07  
**Repository:** `digitalconnectdr/HARP`  
**Baseline commit reviewed through:** `d8d0e001e8a88d2fd47c0f7f6c76cec0a1af5e0e`

## 1. Product objective

HARP is investigating how **Phone A can obtain useful Internet connectivity without requiring Phone A to maintain a conventional paid mobile-data subscription**.

The eSIM line of research is used to understand provisioning, identity, radio/network attachment and control-plane mechanisms. It is **not** a plan to buy an eSIM/data package from an operator and resell that connectivity through HARP.

The current A↔B relay is a laboratory mechanism to prove transport and routing primitives. It must not be confused with the final commercial architecture.

## 2. Current technical baseline

### Implemented in Android source

- Wi-Fi Aware discovery between two HARP phones.
- Wi-Fi Aware data path (NDP) establishment.
- Stage-0 authenticated PING/PONG with a random 128-bit nonce.
- Stage-1 restricted SOCKS5 path:
  - Phone A connects through Wi-Fi Aware.
  - Phone B selects an Internet network with `VALIDATED`.
  - B prefers `NOT_METERED` when available.
  - DNS resolution is performed through the selected B network.
  - outbound TCP sockets are created through the same selected network.
  - TLS remains end-to-end between A and the destination.
- Destination/peer admission policies.
- Logging and a phone-test runbook.

### Stage-2 relay preflight wired into the Android runtime

After Stage-1 succeeds, the current controller now automatically performs a stronger preflight before any VPN is introduced:

- B generates `Stage2SessionCredentials`.
- Credentials are delivered over a control TCP connection inside the encrypted Wi-Fi Aware NDP.
- A parses the session frame and acknowledges it.
- B converts the same advertised `ServerSocket` into a persistent authenticated `Stage2RelayServer`.
- The relay is limited to public web destinations on ports 80/443 and requires an IPv6 link-local Aware peer.
- A opens a new SOCKS session using the ephemeral credentials and repeats the HTTPS proof.
- Expected result: `PASS_STAGE2_RELAY_READY` on B and `PASS_STAGE2_RELAY` on A.

The repository also contains the **Stage-2A VPN preparation**:

- `Stage2TunnelConfig`
- `ProtectedAwareBridge`
- `HevTunnelAdapter`
- local checksum-verifying download scripts for the official HEV Android AAR

Target architecture:

```
Apps / Chrome on A
  -> Android VpnService / TUN
  -> HEV tun2socks
  -> local protected bridge
  -> Wi-Fi Aware
  -> persistent Stage2RelayServer on B
  -> Internet selected by B
```

The Android Stage-2A VPN source is now wired but remains **uncompiled and unvalidated**:

- `HarpVpnService` establishes the IPv4 TUN, excludes the HARP package, declares Aware as the underlying network, starts `ProtectedAwareBridge`, and invokes HEV through `HevTunnelAdapter`.
- `MainActivity` exposes an explicit user-driven **A — ACTIVAR VPN (Stage2A)** flow using `VpnService.prepare()`.
- the manifest declares the VPN service, disables always-on for this PoC, and uses foreground-service type `connectedDevice`.
- `HarpRuntime` owns the Aware session at process scope so Activity recreation does not tear down the NDP.
- the VPN handoff is published only after `PASS_STAGE2_RELAY`, and is invalidated if A's NDP is lost.

The remaining gap is now build/device validation of that source, not the basic lifecycle design.

## 3. Validation status

### Source/self-test level

The repository contains pure-Java Stage-0/1 and Stage-2 self-tests and the latest commits document successful preparation/self-test work.

This review did **not** independently rerun those tests in the current execution container because outbound network/DNS access to GitHub is unavailable there. Repository contents were reviewed through the connected GitHub integration.

### Android build

A local Android build is still required. GitHub Actions must remain disabled.

Expected APK:

```
app/build/outputs/apk/debug/app-debug.apk
```

### Physical phone validation

Still pending.

No claim should yet be made that any of these has been physically demonstrated:

- `PASS_STAGE0`
- `PASS_STAGE1`
- `PASS_STAGE2_RELAY_READY`
- `PASS_STAGE2_RELAY`

Therefore the current status is **source-complete for the Stage-0/1/2 relay-preflight experiment, but not device-validated**.

## 4. GitHub Actions policy

GitHub Actions is intentionally disabled because the available Actions quota/capacity is constrained.

- No active workflow should be created under `.github/workflows/`.
- The archived workflow under `docs/ci/*.disabled` is reference only.
- Builds and tests for this phase must be local/manual.
- Do not re-enable Actions unless explicitly authorized.

## 5. What the current PoC proves — and what it does not

If Stage-0/1 passes on two real phones:

**It will prove**

- A and B can discover each other with Wi-Fi Aware.
- A and B can form a protected local IP data path.
- A can send application traffic over that path.
- B can deliberately egress the request through a separate validated Internet network.

**It will not yet prove**

- transparent Internet for arbitrary apps on A;
- operation without HARP installed on B;
- use of unknown/unprepared nearby phones;
- Internet without some upstream network existing somewhere;
- an operator-free cellular attachment mechanism;
- a commercially scalable zero-cost connectivity model.

Those are later architectural questions and must remain separate from this lab gate.

## 6. Immediate next gate

### Gate G1 — build

Build the current Stage-0/1/2 relay-preflight APK locally without GitHub Actions.

The HEV AAR remains optional at this gate because the relay preflight does not need the VPN. Do not activate `VpnService` until this build succeeds.

### Gate G2 — install on both phones

Once the debug APK exists, install **the same APK on Phone A and Phone B**.

This is the next point where both phones are required.

### Gate G3 — physical Stage-0/1/2 relay-preflight test

- B: HARP RELAY + normal validated Internet.
- A: HARP CLIENT + mobile data OFF + no other Internet path.
- Require `PASS_STAGE0`.
- Then require `PASS_STAGE1`.
- Then require `PASS_STAGE2_RELAY_READY` on B and `PASS_STAGE2_RELAY` on A.
- Preserve the complete logs from both devices.

If any stage fails, diagnose that layer before introducing the VPN.

### Gate G4 — Stage-2A VPN

Only after G3 passes:

- fetch and verify the HEV 2.18.0 Android AAR locally;
- build the existing Stage-2A VPN source without GitHub Actions;
- after G3 passes, press **A — ACTIVAR VPN (Stage2A)** and approve the system VPN prompt;
- require `PASS_STAGE2_VPN_STARTED`;
- route TCP-first traffic from normal apps/Chrome on A;
- test HTTPS with A having no native Internet.

Stage-2A PASS criterion: a normal app/Chrome on A loads HTTPS through B while A has no direct Internet.

### Gate G5 — broader architecture research

After the transport stack is physically proven, continue the harder research track without confusing it with the lab relay:

- eSIM/eUICC architecture as a source of design principles, not purchased connectivity;
- direct device-to-network options;
- D2D/sidelink/relay capabilities actually exposed on commodity phones;
- pre-existing system/OEM services that could supply relay functionality without requiring HARP on every B;
- cost model and geographic availability.

## 7. Current blocker

The blocker is **not protocol design**.

The immediate blocker is:

> no locally built APK and no two-phone Stage-0/1/2 relay-preflight measurement yet.

The long-term blocker remains different:

> finding or creating a scalable upstream mechanism that satisfies the final product requirement without turning HARP into another paid mobile-data reseller.

## 8. Decision rule

Do not spend time optimizing multi-hop, FEC, battery, UI polish or commercial coverage until the Stage-0/1/2 relay preflight passes physically.

Do not claim Stage-2 until normal Android app traffic on A crosses the VPN/TUN path.

Do not claim the final HARP objective from an A↔B lab relay; the relay exists to validate networking primitives only.


## 9. Research update — 2026-10-07

A dedicated standards review of eSIM RSP, 5G ProSe UE-to-Network Relay and SNPN onboarding is documented in:

- [RESEARCH_ESIM_PROSE_SNPN_2026-10-07.md](RESEARCH_ESIM_PROSE_SNPN_2026-10-07.md)

Key change to the research map:

- eSIM RSP is provisioning infrastructure, not independent Internet access;
- 5G ProSe U2N Relay is the closest standardized cellular-native equivalent of the HARP relay; basic ProSe relay work is earlier, but explicit ProSe operation in SNPN is a Release-19 feature set and requires network authorization;
- SNPN onboarding plus an external Credentials Holder is now a primary HARP research branch because it separates connectivity identity/provisioning from the owner of the access network;
- the current S22/X65 remains valid for the Wi-Fi Aware PoC, but should not be the sole hardware target for ProSe research.


## 10. Credentials Holder / private 5G lab update — 2026-10-07

Detailed architecture and phased lab plan:

- [RESEARCH_CREDENTIALS_HOLDER_PRIVATE5G_LAB_2026-10-07.md](RESEARCH_CREDENTIALS_HOLDER_PRIVATE5G_LAB_2026-10-07.md)

Key decisions:

- treat HARP identity/control and radio access as separable layers;
- target Network-Specific-Identifier SUPI + external AAA for the HARP Credentials Holder branch;
- start with ordinary 5G SA before SNPN, onboarding or ProSe;
- do not assume Open5GS/free5GC/srsRAN currently provide turnkey Release-17 SNPN onboarding or ProSe U2N;
- OAI is the more promising open research platform for advanced SNPN/sidelink work, but still contains unsupported paths and active integration work;
- retail Android remains OEM/baseband constrained for SNPN credential/configuration and ProSe roles.


## 11. Virtual 5G lab update — 2026-10-07

A zero-RF-hardware path is now documented in:

- [LAB_VIRTUAL_5G_PATH_2026-10-07.md](LAB_VIRTUAL_5G_PATH_2026-10-07.md)

New project decisions:

- reproduce OAI's official full-stack 5G SA RFSimulator scenario before buying SDR/modem/private-5G hardware;
- first virtual gate is `PASS_V5G_0A`: software nrUE -> RFsim -> gNB -> 5GC -> user-plane/Internet;
- second gate is HARP-controlled virtual identity issue/revoke;
- only then patch SNPN/NID behavior;
- OAI currently has a concrete two-nrUE RFsim sidelink scenario with no gNB;
- its current upstream automated evidence checks `PSBCH RX:OK`, which proves sidelink synchronization/broadcast reception, not PSSCH user data or ProSe U2N;
- HARP now distinguishes `PASS_PC5_SYNC`, `PASS_PC5_DATA`, `PASS_PC5_IP` and `PASS_PROSE_U2N`;
- no GitHub Actions are required for this work.


## 12. SNPN encoding / Release-19 correction — 2026-10-07

The concrete implementation plan is:

- [OAI_SNPN_PATCH_PLAN_2026-10-07.md](OAI_SNPN_PATCH_PLAN_2026-10-07.md)

Confirmed standards map:

```
SIB1 npn-IdentityInfoList-r16
  -> PLMN + 44-bit NID = SNPN identity

SIB1 snpn-AccessInfoList-r17
  -> external-Credentials-Holder / onboarding / emergency capability flags

SIB18
  -> GIN information for Credentials Holder / onboarding network selection
```

Lab identity selected for the software-only experiment:

```
MCC 999 / MNC 99
NID 10000000001
```

The NID is assignment-mode 1 and is lab-only. The final external-AAA Credentials Holder architecture must move to an appropriate coordinated identity model.

Release boundary correction:

- do not equate “Release 17 modem” with the final HARP feature set;
- explicit ProSe support in SNPN is standardized through Release-19 work;
- future hardware procurement for the combined architecture requires feature-level confirmation, not chipset release-number marketing.


## 13. Stock-phone relay boundary / B without HARP — 2026-10-07

Detailed review:

- [RESEARCH_STOCK_PHONE_RELAY_BOUNDARY_2026-10-07.md](RESEARCH_STOCK_PHONE_RELAY_BOUNDARY_2026-10-07.md)

New conclusions:

- Android 16/API 36 exposes `TetheringManager`, but the Wi-Fi tethering configuration path for a non-system caller requires privileged tethering permission and provisioning may still be required;
- `LocalOnlyHotspot` is explicitly local-only and has no Internet access;
- Wi-Fi Direct lets a device maintain its own uplink while participating in P2P, but the public P2P API is peer connectivity, not a generic NAT/Internet-forwarding service for the peer;
- Android 17/API 37 improves application-level Wi-Fi Aware data-path negotiation, but both peer applications still participate in the NDP;
- therefore, sending an “instruction” inside A's packets cannot make an arbitrary stock B originate/forward Internet traffic unless B already has a compatible authorized service interpreting that protocol;
- the realistic “B without HARP” paths are an already-enabled stock/OEM relay such as tethering, or an OEM/carrier/network-integrated mechanism such as 5G ProSe;
- 3GPP Release-19 continues to add multi-hop UE-to-Network relay procedures and authorization, strengthening ProSe as the standards-native research branch rather than removing its authorization requirements.

### Static Stage-2A build audit

A fresh static review of the current Stage-2A source found no new conceptual blocker in:

- AGP 9.2.1 / Gradle 9.4.1 compatibility;
- Java 17 source level;
- `VpnService` foreground lifecycle;
- `connectedDevice` foreground-service permission prerequisites;
- protected + Aware-bound transport socket ordering;
- current HEV JNI method contract documented by the upstream Android AAR.

This remains a **static review only**. The current execution environment has JDK 21 but no installed Gradle/Android SDK/ADB toolchain, so no Android APK was built here and no device-validation claim is made.

### Current installation decision

Do **not** install on both phones yet.

The two-phone gate remains:

```
local APK exists
    -> install same debug APK on A and B
    -> PASS_STAGE0
    -> PASS_STAGE1
    -> PASS_STAGE2_RELAY_READY / PASS_STAGE2_RELAY
```

Until the local APK exists, continue research/source hardening and the virtual 5G branch without consuming GitHub Actions.


## 14. Stage-2A lifecycle hardening — 2026-10-07

Static source review found and corrected a transport-loss lifecycle defect.

Before the correction:

```
A NDP lost
  -> vpnSession = null
  -> already-running HarpVpnService could remain active
  -> TUN could continue capturing traffic with no valid relay path
```

Current behavior after commit `9a709cf92e4f0b6cc9753e749814ce56cd983f62`:

```
A NDP lost/unavailable
  -> invalidate Stage2VpnSession
  -> stop HarpVpnService
  -> shutdown HEV / bridge / TUN

B NDP lost/unavailable
  -> close Stage2 relay/listener
```

This makes transport failure fail-closed rather than leaving a stale VPN/relay.

HEV configuration was also checked against current upstream documentation:

- `mapdns`, `tcp-read-write-timeout` and the Android AAR JNI contract match upstream;
- `udp: 'tcp'` is a HEV UDP-over-TCP extension, while HARP's current `MiniSocks5` supports only SOCKS5 CONNECT;
- therefore Stage-2A's PASS remains TCP/HTTPS only; UDP/QUIC is explicitly deferred to Stage-2B.

No Android build or device claim is added by this review.


## 15. Pre-phone hardening checkpoint — 2026-10-07

Additional source hardening completed before any APK/device installation:

- commit `c66e0441f14a82b25252abc36eb3cc8e64adee4f`: changing A/B role now stops an active Stage-2A VPN before resetting the Aware controller;
- commit `fb12c0d90a682238b23ee6ee8ba5c904d8d847c9`: a late Stage-2 worker cannot republish a stale VPN handoff after the NDP was lost/reset;
- commit `b26dd93520b868e341de02f6da981218961d11b6`: phone runbook now requires disabling SoftAP/tethering and Wi-Fi Direct/P2P during the first Aware test, because those modes can make Aware unavailable on some hardware;
- commit `ed9471c4801e57c66f5332a6e296989566f62c3e`: preflight now logs manufacturer, model, SDK, `FEATURE_WIFI_AWARE`, current availability and advertised cipher suites;
- commits `c13354f6dc8cc786d6b6b81730dd2757a0908020` and `285b9a31e7866c42939cfd2f2c09a6f2ba33bdc8`: next APK identifies itself as `0.5-stage2a-preflight-hardened` / versionCode 5.

The installation decision is unchanged: **do not install on both phones yet**. First produce the local debug APK without GitHub Actions.


## 16. OAI SNPN source anchor + Aware cipher hardening — 2026-10-07

Current OAI source was re-checked against the official GitHub mirror:

```
openairinterface/openairinterface5g
develop
f8f769592a7030be88ede4bb5ca66fa1ca6a80e0
integration 2026.w40
```

New concrete SNPN findings:

- `get_SIB1_NR()` in `openair2/LAYER2/NR_MAC_gNB/nr_radio_config.c` is the first practical patch anchor for HARP's PLMN+NID broadcast experiment;
- current OAI builds ordinary `plmn_IdentityInfoList` there, but does not populate the HARP-required NPN/NID identity path;
- generated Rel-17 ASN.1 support exists for `NR_NID-r16` and `NR_NPN-IdentityInfoList-r16`;
- `AvailableSNPN_ID_List` remains explicitly unsupported in `openair2/F1AP/lib/f1ap_interface_management.c`;
- therefore the first SNPN proof should use monolithic/full-stack RFsim and split the gate into:
  - `PASS_V5G_SNPN_BROADCAST`;
  - `PASS_V5G_SNPN_SELECT`;
- F1AP/NGAP/core propagation remains a later gate rather than a prerequisite for the first NID broadcast experiment.

The detailed patch plan was updated in commit `8339f2b98cacf917d2c8bc2824961d972c9a74f9`.

Android Aware hardening also changed:

- API 33+ HARP now prefers `NCS_SK_256` when supported;
- it falls back to `NCS_SK_128`;
- it fails closed if no shared-key Aware cipher is available;
- commit: `8810ab4144a95aad281e4f6cc665d0d75bb7b415`.

No installation is required yet.


## 17. SNPN executable blueprint + NID44 self-test — 2026-10-08

The SNPN research branch now has an implementation-oriented blueprint rather than only an architecture plan:

- `docs/OAI_SNPN_PATCH_BLUEPRINT_0_1.md`
- initial commit: `cf5f4c5b239ef374a8b3fc1e2f09c8882d6b1a2e`
- configuration/test refinement: `e49297022ff63b3f2e97401cec0d10e444402974`

The blueprint is anchored to OAI `develop` commit:

```
f8f769592a7030be88ede4bb5ca66fa1ca6a80e0
```

Confirmed implementation anchors:

```
gNB SIB1 build:
openair2/LAYER2/NR_MAC_gNB/nr_radio_config.c
get_SIB1_NR()

nrUE SIB1 decode:
openair2/RRC/NR_UE/rrc_UE.c
nr_rrc_process_sib1()
```

Standards structure used by the blueprint:

```
CellAccessRelatedInfo
  -> npn-IdentityInfoList-r16
  -> NPN-IdentityInfo-r16
  -> NPN-Identity-r16 / snpn-r16
  -> PLMN Identity + nid-List-r16
  -> NID-r16 BIT STRING SIZE(44)
```

A reusable standalone NID helper was added:

```
tools/oai_snpn/nid44.h
tools/oai_snpn/nid44.c
tools/oai_snpn/nid44_selftest.c
tools/oai_snpn/run_nid44_selftest.sh
```

The helper was compiled locally as C11 with `-Wall -Wextra -Werror` and produced:

```
PASS_NID44_VECTORS
```

Validated values include zero, one, the HARP lab NID `0x10000000001`, maximum 44-bit NID, overflow rejection and invalid padding rejection.

New gate ladder:

```
PASS_NID44_VECTORS
        |
PASS_V5G_SNPN_SIB1_CODEC
        |
PASS_V5G_SNPN_BROADCAST
        |
PASS_V5G_SNPN_SELECT
```

Configuration decision:

- do not extend OAI `plmn_id_t`;
- do not add NID to the shared `plmn_list`;
- add a separate optional gNB-level SNPN configuration block for the laboratory;
- keep SNPN disabled behavior equivalent to upstream ordinary PLMN behavior.

No phone installation is required for this work.


## 18. Stage2 upstream fail-closed + SNPN codec/KDF anchors — 2026-10-08

Android relay hardening:

- `ca83547fa179071c9605a614464d69ff7d994521`: B now watches the exact Stage2 Internet upstream and closes the relay if it loses `INTERNET`/`VALIDATED` or disappears;
- `cd0c5da4545a5fdfd7182b6da978977eabdec943`: Stage2 startup failure also closes the transport instead of leaving a stale listener;
- `75c05400a922b2a7368d44da115ae76c4cbd2b13`: B records the Wi-Fi Aware interface and rejects an accepted IPv6 link-local peer when an explicit IPv6 scope ID points to a different interface; zero scope remains diagnostic until real-device behavior is measured;
- `ff84d9b3a185c39f19bfc844004de3fb8d0edaf3`: phone runbook now includes upstream-loss and interface-scope negative tests.

OAI SNPN test design:

- `b6fb856d2835918e20e2074b586c8fc0412241b6`: added `docs/OAI_SNPN_SIB1_CODEC_TEST_PLAN_2026-10-08.md`;
- the first deterministic OAI gate is now an encode/decode SIB1 round trip linked around OAI's `L2_NR` + NR RRC ASN.1 targets before RFsim;
- pass order remains:
  `PASS_NID44_VECTORS -> PASS_V5G_SNPN_SIB1_CODEC -> PASS_V5G_SNPN_BROADCAST -> PASS_V5G_SNPN_SELECT`.

nrUE authentication boundary:

- `1a7f25f5bc91190cdde57fe9bc0a689be8f5b34e` maps the exact current OAI call chain;
- `servingNetworkName()` still consumes only `plmn_id_t`;
- `transferRES()`, `derive_kausf()` and `derive_kseaf()` all depend on that PLMN-only SNN;
- therefore a decoded NID cannot influence 5G-AKA until the serving-network context becomes PLMN+optional NID;
- new planned gates:
  `PASS_V5G_SNN_REFACTOR_BASELINE -> PASS_V5G_SNPN_SNN -> PASS_V5G_SNPN_KDF_LAB`.

No GitHub Actions were used.
No phone installation is required yet.


## 19. Stronger Aware peer binding + canonical SNPN SNN — 2026-10-08

Android hardening:

- commit `4b3d8198dc9aae61dc021d4af1c324680da847b6`;
- B now records the IPv6 link-local addresses assigned to the actual Wi-Fi Aware `LinkProperties`;
- an accepted socket must use one of those local addresses when that information is available;
- IPv6 scope/interface index remains a second validation layer;
- this is stronger than the previous “remote peer is any IPv6 link-local address” policy while preserving a diagnostic fallback if Android does not expose enough interface metadata.

SNPN SNN research:

- TS 24.501 SNN format was verified for SNPN;
- canonical SNPN SNN is the ordinary PLMN SNN followed by `:` and exactly 11 uppercase hexadecimal NID digits;
- HARP lab SNN is:
  `5G:mnc099.mcc999.3gppnetwork.org:10000000001`.

Standalone reference code added:

```
tools/oai_snpn/snn.h
tools/oai_snpn/snn.c
tools/oai_snpn/snn_selftest.c
tools/oai_snpn/run_snn_selftest.sh
```

Local C11 validation produced:

```
PASS_SNN_VECTORS
```

Validated cases include:

- ordinary PLMN baseline;
- HARP lab NID;
- leading-zero NID formatting;
- maximum 44-bit NID;
- overflow rejection.

New implementation document:

- `docs/OAI_SNPN_SNN_KDF_REFACTOR_2026-10-08.md`
- commit `b63d61470d2777dc950a902c6bc704e4854f071d`.

Current OAI AMF source anchor:

```
openairinterface/oai-cn5g-amf
develop
5eedea557a3745b13ed9ec4bf29e6a28bd912574
```

The AMF current `get_serving_network_name(mnc, mcc)` is also PLMN-only, so `PASS_V5G_SNPN_KDF_LAB` must coordinate UE and AMF changes.

Updated gate ladder:

```
PASS_NID44_VECTORS
        |
PASS_SNN_VECTORS
        |
PASS_V5G_SNPN_SIB1_CODEC
        |
PASS_V5G_SNPN_BROADCAST
        |
PASS_V5G_SNPN_SELECT
        |
PASS_V5G_SNN_REFACTOR_BASELINE
        |
PASS_V5G_SNPN_SNN
        |
PASS_V5G_SNPN_KDF_LAB
```

No GitHub Actions were used.
No phone installation is required yet.


## 20. Staged OAI / AMF patches — 2026-10-08

OAI nrUE patch staging now exists under:

```
patches/oai/
  0001-nr-ue-serving-network-baseline-refactor.patch
  0002-nr-ue-snpn-serving-network-name.patch
  README.md
```

Commits:

- `1a4399a50da6f9d6055b3f19484c603d98287df2` — baseline serving-network identity refactor;
- `c725a735b3c448686f145687b2c415950ed1cb05` — SNPN SNN formatter patch;
- `e98842f5b2fa9b2fe0beaf5a1ee92ccd568869ac` — application order and validation policy.

The first patch is intentionally PLMN-behavior-preserving. The second patch must not be applied until `PASS_V5G_SNN_REFACTOR_BASELINE` demonstrates no regression in ordinary PLMN RES*/K_AUSF/K_SEAF behavior.

OAI AMF patch staging now exists under:

```
patches/oai-amf/
  0001-amf-optional-snpn-snn-formatter.patch
  README.md
```

AMF anchor:

```
openairinterface/oai-cn5g-amf
develop
5eedea557a3745b13ed9ec4bf29e6a28bd912574
```

Commits:

- `bbfee71c745c694c4670b6f8842c633df3c82205` — initial formatter patch;
- `c31cf69d3405122d1e1b28e05165f12c233cb76b` — required C++ includes;
- `4b8d3fa3dc4478aa11d4ae5bb3ce9150a90b7ac5` — AMF staging notes.

Important source finding:

- current OAI AMF has only two known call sites for `get_serving_network_name()` in `amf_n1.cpp`;
- the staged AMF patch does not change those call sites, so existing behavior remains PLMN-only until an explicit lab-NID source is added;
- this allows formatter validation to remain separate from authentication behavior.

ASN.1 caution:

- the OAI tree contains generated NR RRC artifacts for `NR_NID-r16` and `NR_NPN-Identity*`;
- the versioned ASN.1 source files inspected in the pinned tree do not expose those definitions literally under those names;
- therefore HARP will not fabricate the SIB1 C field layout from assumptions;
- the final SIB1 patch must be generated/verified against the actual asn1c output of a pinned OAI build environment.

No GitHub Actions were used.
No phone installation is required yet.


## 21. Patch-anchor validation + relay preflight fail-closed — 2026-10-08

Static validation of the staged nrUE patch against the pinned OAI commit succeeded at all nine critical textual anchors:

```
PASS_OAI_PATCH_ANCHORS_9_OF_9
```

AMF SNN reference code was compiled locally as C++17 with `-Wall -Wextra -Werror` and produced:

```
PASS_AMF_SNN_VECTORS
```

The independent nrUE C formatter and AMF C++ formatter were cross-checked for the HARP lab identity and produced the same canonical string:

```
5G:mnc099.mcc999.3gppnetwork.org:10000000001
PASS_UE_AMF_SNN_MATCH
```

Cross-check runner:

```
tools/oai_amf_snpn/run_ue_amf_snn_crosscheck.sh
```

Android relay lifecycle was hardened further:

- `8febedd44cfe01323931a5cfa904499d562e91ae`: Stage0/Stage1/Stage2-control failures now close B's relay transport instead of returning with a live listener/NDP callback;
- `25de98cc0d48833f5bf7a2cd6a9a7a05e06e0cae`: NDP setup exceptions also close transport, and the server reference is cleared in a `finally` path;
- `closeRelayTransport()` now unregisters the Aware network callback, which prevents late `LinkProperties` callbacks from repopulating metadata after closure.

Detailed validation record:

- `docs/OAI_PATCH_STATIC_VALIDATION_2026-10-08.md`
- commit `e7548f10a7f01d8e3edd5ccf3924c9868f95335e`.

These are still static/source-level PASS markers, not OAI build/RFsim or phone-validation claims.

No GitHub Actions were used.
No phone installation is required yet.


## 22. Cryptographic baseline gates + AMF lab NID source — 2026-10-08

nrUE test integration:

- OAI's existing `openair3/NAS/NR_UE/5GS/tests/nas_lib_test.c` links directly against `nr_nas`, so HARP can validate SNN/KDF behavior inside the upstream test framework without RFsim;
- `acd8c3c9d210b7ffedfc2ac08aa134905f6cacb8`: staged `0001b-nr-ue-snn-formatter-baseline-test.patch`;
- new sub-gate:
  `PASS_V5G_SNN_FORMAT_BASELINE`;
- `22bc9e42b21c42e1d3ab508a8206553a72ec92b4`: staged `0001c-nr-ue-kdf-baseline-test.patch`;
- this adds deterministic ordinary-PLMN RES*, K_AUSF and K_SEAF regression vectors;
- gate:
  `PASS_V5G_SNN_REFACTOR_BASELINE`.

Reference ordinary-PLMN vector for `5G:mnc015.mcc234.3gppnetwork.org`:

```
RES*   = e5c9b031ea670bc494e4db45fb1cf267
KAUSF  = 1789cd7d88b07b803330574544da1bfcb52c67ec14b4075b4b36d262d773dc83
KSEAF  = e40038b02ad5457c40f27f92e92bdd735c7720287ecbd7ff304f7751d7bf2191
```

The vectors are reproducible with:

```
tools/oai_snpn/kdf_baseline_vectors.py
```

SNPN nrUE cryptographic binding:

- `1a0a8b631adfa74e15d1acc9ad0aaf291078a1f8`: staged SNPN patch now also checks RES*, K_AUSF and K_SEAF with the HARP lab NID;
- changing only NID from `10000000001` to `10000000002` must change all three derived values;
- new UE-only marker:
  `PASS_V5G_SNPN_KDF_UE`;
- `adda3063562b2f4d008a964329fa6761448dd560`: vector generator extended with positive and mismatched-NID SNPN cases.

HARP lab SNPN nrUE vector:

```
SNN     = 5G:mnc099.mcc999.3gppnetwork.org:10000000001
RES*    = 4a880d868e07cb3ad0a3ef39b21eebe5
KAUSF   = 742c95dd9003e1c6c148236f5f8c9f9f2b89b02b2d898d989d4de00189ff5626
KSEAF   = a19ff0f63a0093d859f72233688e472a3283493bc2e852f030d9de7aaf9e93b4
```

AMF/AUSF integration:

- `db5b4150f3de94b3c281d30c7edea748742146e3`: documented exact SNN flow through AMF;
- external-AUSF mode sends `nc->serving_network` as `AuthenticationInfo.servingNetworkName`;
- simple-scenario mode consumes the same string in local UDM/AUSF emulation and `derive_kseaf()`;
- therefore NID injection must happen before `nc->serving_network` is stored.

AMF lab bridge:

- `64622f4175ae2a062eae06ed7625087e3ccd68b2` + `61b96cd220807ae13a520ffdfdc6b1dc234c750d`: staged `0002-amf-lab-snpn-nid-config.patch`;
- optional YAML:
  `amf.snpn_nid: "10000000001"`;
- absent -> ordinary PLMN behavior;
- present -> exactly 11 hex digits / <=44 bits;
- both current AMF SNN construction sites use it;
- explicitly lab-only until NID arrives through standards-compliant serving-network/NGAP context;
- `c384a5460f58abc244cd7cc07bc0093ed4a45703`: AMF patch staging documentation updated.

Updated gate sequence:

```
PASS_NID44_VECTORS
        |
PASS_SNN_VECTORS
        |
PASS_AMF_SNN_VECTORS
        |
PASS_UE_AMF_SNN_MATCH
        |
PASS_V5G_SNN_FORMAT_BASELINE
        |
PASS_V5G_SNN_REFACTOR_BASELINE
        |
PASS_V5G_SNPN_SNN
        |
PASS_V5G_SNPN_KDF_UE
        |
PASS_V5G_SNPN_SIB1_CODEC
        |
PASS_V5G_SNPN_BROADCAST
        |
PASS_V5G_SNPN_SELECT
        |
PASS_V5G_SNPN_KDF_LAB
        |
PASS_V5G_SNPN_KDF_NEGATIVE
```

No GitHub Actions were used.
No phone installation is required yet.


## 23. Deterministic blueprint application + nrUE SNPN selection/SIB1 gates — 2026-10-08

Context-blueprint execution:

- `8e17a59755f284d179c41c39b124f23db2fdc21a`: added strict `tools/apply_context_blueprints.py`;
- `d7002371f18c09b72fce0262fe4d9b07f0806fdf`: ordered OAI wrapper;
- `b34711e5e2f11afd7b19a714dfb014751f78b6cc`: ordered AMF wrapper;
- every hunk must match exactly once; ambiguous or missing context fails closed;
- after writing, the tool runs `git diff --check`;
- context-blueprint semantics are now documented explicitly; the research `*.patch` files are not claimed to be direct `git apply` unified diffs.

Static anchor validation against pinned sources:

- OAI RAN blueprints through `0003c`: 48 sequential hunks matched exactly once;
- `0004`: 7/7 hunks matched on its dependency-equivalent state;
- `0004b`: 10/10 hunks matched after `0001 + 0003c + 0004`;
- `0004c`: 4/4 hunks matched on the accumulated gNB/RRC state;
- AMF blueprints: 17/17 hunks across six files matched exactly once.
- detailed record: `docs/OAI_CONTEXT_BLUEPRINT_VALIDATION_2026-10-08.md`, commit `2db8dd54fc81bc3181995c2d2306d0516791a113`.

nrUE selection:

- `1c681c2d352cd05106ba3c52fa5f1b3bcdb58075` + `7f2cd373259a9a599803b0192103616f8baa6467`: staged `0004-nr-ue-snpn-selection.patch`;
- software UICC gains optional lab `snpn_nid`;
- expected MCC/MNC is derived from UICC IMSI + `nmc_size`;
- SIB1 must contain an exact SNPN PLMN+NID match;
- mismatch returns before SIB1 validity / Random Access is published;
- runtime marker remains `PASS_V5G_SNPN_SELECT`, not yet claimed.

Shared production selector:

- `1d1535a685aa933b68f6d28e8431664068e9f057` + `8352a729014b3ef1dc855aaa607f25b9e60e6b61`: staged `0004b-nr-ue-snpn-selection-unit-test.patch`;
- ASN.1-specific NID decode and PLMN+NID search move to `asn1_msg.c/.h`;
- the same production lookup becomes testable through existing `test_asn1_msg`;
- unit cases: exact match, NID mismatch, PLMN mismatch;
- staged marker: `PASS_V5G_SNPN_SELECT_UNIT`.

Full SIB1 codec gate:

- `fad2ced41b95ccf7258a5224f34a4b115690e71a`: staged `0004c-snpn-full-sib1-codec-test.patch`;
- reuses OAI simulator fixture path:
  `prepare_scc -> fill_scc_sim -> fix_scc`;
- uses valid timer values instead of zero-initialized invalid defaults;
- calls real `get_SIB1_NR()`;
- encodes with `encode_SIB_NR()`;
- UPER-decodes complete BCCH-DL-SCH/SIB1;
- verifies exact HARP PLMN+NID using the shared selector;
- repeats with SNPN disabled and requires the NPN identity list to be absent;
- staged marker: `PASS_V5G_SNPN_SIB1_CODEC`;
- the current design links `test_asn1_msg` to `L2_NR`; context is valid, but a real CMake/link pass is still required to determine whether a smaller dedicated target is preferable.

Current pre-RFsim gate order:

```
PASS_V5G_SNPN_NPN_CODEC
        |
PASS_V5G_SNPN_SELECT_UNIT
        |
PASS_V5G_SNPN_SIB1_CODEC
        |
PASS_V5G_SNPN_BROADCAST
        |
PASS_V5G_SNPN_SELECT
```

No build/RFsim marker above is claimed yet.

No GitHub Actions were used.
No phone installation is required yet.


## 24. ASN.1 extension hierarchy verified + layered codec gates — 2026-10-08

Exact OAI NR RRC grammar recovery:

- GitHub Contents API returned the large `nr-rrc-17.3.0.asn1` file as empty, but fetching its Git blob by SHA recovered the full ~1.2 MB grammar;
- the pinned OAI grammar confirms:
  `CellAccessRelatedInfo -> npn-IdentityInfoList-r16 -> NPN-IdentityInfo-r16 -> NPN-Identity-r16 -> snpn-r16 -> PLMN + nid-List-r16`;
- `NID-r16 ::= BIT STRING (SIZE(44))`.

Generated-layout verification:

- public OAI/asn1c generated artifacts confirm:
  - `NR_NPN_Identity_r16_PR_snpn_r16`;
  - `choice.snpn_r16` is a pointer;
  - `npn_IdentityInfoList_r16` resides under `CellAccessRelatedInfo.ext1`;
  - `cellReservedForOperatorUse-r16` exposes the expected `notReserved` enum;
- an independent Rel-17 generated artifact confirms the hierarchy remains:
  - `ext1 -> npn_IdentityInfoList_r16`;
  - `ext2 -> snpn_AccessInfoList_r17`.

gNB SIB1 staging:

- `bfee36f05f87aabb90b9e108c489ec0158ab119e` + `d32c5f7b7b6b44868c28ae8829c2146c0f004051`: `0003a-gnb-snpn-config-plumbing.patch`;
- `16baac2d36a0e571e74ab71b5db392e837d9dad6` + `bd0db7415c9c46c0b683e327e3eabad44c8115b6`: `0003b-gnb-snpn-sib1-encoding.patch`, corrected to the verified generated extension/CHOICE layout.

Layered ASN.1 gates:

```
0003c  NPN-IdentityInfoList-r16 codec
       -> PASS_V5G_SNPN_NPN_CODEC

0003d  CellAccessRelatedInfo ext1 codec
       -> PASS_V5G_SNPN_CELL_ACCESS_CODEC

0004c  full BCCH-DL-SCH/SIB1 round-trip
       -> PASS_V5G_SNPN_SIB1_CODEC
```

Repository cleanup:

- temporary duplicate name `0003c-snpn-cell-access-codec-test.patch` was renamed to `0003d-snpn-cell-access-codec-test.patch`;
- `913d34481fecb29169f42a9183fadbc0090c14c3`: ordered applicator updated;
- `bb4c7a55692936a43c9e637376879548c7defeb5`: patch README updated with the new gate;
- `c34bd3feef8001a5c341ea530650ebf857a108d8`: obsolete note saying full-SIB1 testing was deferred removed.

nrUE selector hardening:

- existing design correctly binds the lab target NID to `uiccN.snpn_nid`, alongside IMSI/`nmc_size`, rather than a process-global CLI flag;
- OAI source confirms `nr_ue_nas_t` owns `uicc_t *uicc` and the UICC supplies IMSI/security identity used by NAS;
- `7e876db70d3e3c959396ee8e1bc3464a633ca0ad`: staged selector NID buffer validation hardened;
- `88b6157db5c9e10ee5fe75eba11f5366b0109621`: shared production decoder now rejects null NID buffers and its unit test exercises malformed padding and missing-buffer negatives.

Full-SIB1 test static dependency audit:

- `tests/nrdlbench` already uses the same OAI link group and framework stubs adopted by `0004c`;
- `prepare_scc()`, `fill_scc_sim()` and `fix_scc()` are declared in `nr_unitary_defs.h` and already used by `nr_dlbench`;
- timer values used by `0004c` were checked against the exact `get_NR_UE_TimersAndConstants_*` switch tables and are valid.

No build-dependent PASS marker is claimed yet.
No GitHub Actions were used.
No phone installation is required yet.


## 25. AMF runtime SNN evidence marker — 2026-10-08

AMF patch audit confirmed:

- `amf_cfg` is `std::unique_ptr<oai::config::amf_config>`;
- public config fields such as `default_dnn` are already consumed directly through `amf_cfg->...`;
- therefore the staged public `std::optional<uint64_t> snpn_nid` is accessible from both SNN construction sites in `amf_n1.cpp`;
- OAI AMF logging uses printf-style format strings, so the existing `%011llX` diagnostic format is compatible.

Commit `1a1303d1d703426efd7504491f1dbdcf66ac0408` adds runtime evidence at both AMF SNN construction sites:

```
HARP_SNPN_AMF_SNN 5G:mnc099.mcc999.3gppnetwork.org:10000000001
```

Expected future RFsim correlation:

```
nrUE:
PASS_V5G_SNPN_SELECT mcc=999 mnc=99 nid=10000000001

nrUE KDF:
SNN=5G:mnc099.mcc999.3gppnetwork.org:10000000001

AMF:
HARP_SNPN_AMF_SNN 5G:mnc099.mcc999.3gppnetwork.org:10000000001
```

Only after those identities match should the positive authentication gate be evaluated.

The negative gate remains mandatory:

```
UE  NID = 10000000001
AMF NID = 10000000002
-> authentication must fail
-> PASS_V5G_SNPN_KDF_NEGATIVE
```

No authentication PASS marker is claimed yet.
No GitHub Actions were used.
No phone installation is required yet.


## 26. Current blueprint chains revalidated in memory — 2026-10-08

A fresh sequential context validation was run directly against the pinned upstream GitHub source files after the latest ASN.1 and selector edits.

OAI critical chain:

```
PASS_OAI_CRITICAL_BLUEPRINT_CHAIN_41_OF_41
```

Sequence:

```
0001
 -> 0003c
 -> 0003d
 -> 0004
 -> 0004b
 -> 0004c
```

One real dependency drift was detected and corrected during this pass:

- `0004` had gained the explicit `snpn` pointer helper;
- `0004b` still matched the older function body;
- commit `0bdcee8603053fab81030941ce36c6593f53c79c` realigned the hunk.

Additional cleanup:

- `f3fd6f16d56725b834c199b156469bc01c877efa`: `0004b` removal context realigned to the hardened null-buffer decoder;
- `822681a4bafc2ab083cbf0a73eb4740f8c253b48`: `0003d` no longer duplicates the ASN.1 includes already introduced by `0003c`, and no longer relies on `sizeofArray`.

AMF chain:

```
PASS_AMF_BLUEPRINT_CHAIN_17_OF_17
```

Sequence:

```
0001-amf-optional-snpn-snn-formatter.patch
 -> 0002-amf-lab-snpn-nid-config.patch
```

Detailed record:

```
docs/OAI_BLUEPRINT_CHAIN_VALIDATION_2026-10-08.md
```

These are context-applicability PASS markers only. Compilation, CMake linkage, generated-header compile, unit test execution, RFsim, and authentication remain pending.

No GitHub Actions were used.
No phone installation is required yet.


## 27. Minimal local OAI build/test runner — 2026-10-08

Commit `d28d66eae46f6093830c39cb42d097a411ea584a` adds:

```
tools/oai_snpn/run_pre_rfsim_gates.sh
```

The runner is intentionally local/manual and does not use GitHub Actions.

It requires a clean disposable OAI checkout pinned to:

```
f8f769592a7030be88ede4bb5ca66fa1ca6a80e0
```

It then:

```
blueprint --check
    |
apply blueprints
    |
cmake -DENABLE_TESTS=ON -DSANITIZE_ADDRESS=OFF
    |
build only:
  nas_lib_test
  test_asn1_msg
  test_snpn_sib1_codec
    |
focused ctest
    |
verify HARP PASS markers
```

Required markers:

```
PASS_V5G_SNN_FORMAT_BASELINE
PASS_V5G_SNN_REFACTOR_BASELINE
PASS_V5G_SNPN_SNN
PASS_V5G_SNPN_KDF_UE
PASS_V5G_SNPN_NPN_CODEC
PASS_V5G_SNPN_NPN_CODEC_NEGATIVE
PASS_V5G_SNPN_CELL_ACCESS_CODEC
PASS_V5G_SNPN_SELECT_UNIT
PASS_V5G_SNPN_SIB1_CODEC
```

Final local pre-RFsim marker:

```
PASS_HARP_OAI_SNPN_PRE_RFSIM_GATES
```

CMake audit confirms:

- top-level OAI includes `tests/` when `ENABLE_TESTS=ON`;
- `tests/CMakeLists.txt` includes `nrdlbench`;
- therefore the staged `test_snpn_sib1_codec` target is reachable in the intended configuration.

This runner has not yet been executed in the current environment because no OAI checkout/build dependency set is available locally.

No GitHub Actions were used.
No phone installation is required yet.


## 28. AMF build gate + RFsim log gate automation — 2026-10-08

AMF build runner:

- `451450a544c9848c2c8304bbaf04da9878052e75`
- file: `tools/oai_amf_snpn/run_amf_build_gate.sh`
- requires clean AMF checkout pinned to `5eedea557a3745b13ed9ec4bf29e6a28bd912574`;
- requires initialized `src/common-src` and `build/common-build`;
- validates/applies HARP AMF blueprints;
- configures upstream CMake in Release mode;
- builds only target `amf`;
- success marker:
  `PASS_HARP_AMF_SNPN_BUILD`.

RFsim correlation checker:

- `adc65c37c44b2699a77c6908109f4f832f29844a`
- file: `tools/oai_snpn/check_rfsim_snpn_logs.py`;
- positive mode requires:
  - nrUE `PASS_V5G_SNPN_SELECT`;
  - canonical AMF `HARP_SNPN_AMF_SNN`;
  - identical UE/AMF NID;
  - a registration/authentication success marker;
- emits:
  `PASS_V5G_SNPN_KDF_LAB`.

Negative mode requires:

```
UE NID != AMF NID
and
no authentication/registration success marker
```

and emits:

```
PASS_V5G_SNPN_KDF_NEGATIVE
```

If the negative case contains a success marker, the checker fails closed.

Self-test:

- `e4f7d9f16aa7df9b72d3e898fcc8c484e9cbf554`
- file: `tools/oai_snpn/run_rfsim_log_checker_selftest.sh`.

Synthetic validation performed in the current environment produced:

```
PASS_V5G_SNPN_KDF_LAB
PASS_V5G_SNPN_KDF_NEGATIVE
PASS_NEGATIVE_FALSE_SUCCESS_REJECTED
```

These synthetic markers validate the checker logic only; they are not RFsim or 5G authentication claims.

Current execution frontier:

```
OAI blueprint chain      PASS 41/41
AMF blueprint chain      PASS 17/17
OAI pre-RFsim runner     READY
AMF build runner         READY
RFsim log checker        SELF-TESTED
real OAI compile         PENDING
real AMF compile         PENDING
RFsim positive           PENDING
RFsim mismatched NID     PENDING
```

No GitHub Actions were used.
No phone installation is required yet.
