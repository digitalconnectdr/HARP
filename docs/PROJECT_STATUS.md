# HARP — Project Status

**Baseline date:** 2026-10-07  
**Repository:** `digitalconnectdr/HARP`  
**Baseline commit reviewed:** `7cca26bae69fb648a060ba954523374c54d798ba`

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
- local verified download scripts for the official HEV Android AAR

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
