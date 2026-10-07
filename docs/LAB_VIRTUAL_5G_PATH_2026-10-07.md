# HARP — Virtual 5G Lab Path (No RF Hardware)

**Research date:** 2026-10-07  
**Goal:** validate the cellular-control-plane branch of HARP before purchasing SDRs, modems, SIM hardware or additional phones.

## Executive decision

The lowest-cost next step for the cellular branch is a **software-only 5G SA laboratory** using OpenAirInterface RFSimulator.

The official OAI repository currently contains a containerized full-stack scenario with:

```
OAI nrUE
   |
   | simulated I/Q samples (RFSimulator)
   v
OAI gNB
   |
   | N2 / N3
   v
OAI 5GC
  AMF + SMF + UPF + DB
   |
   v
external data network / Internet
```

No SDR or over-the-air transmission is required for this phase.

The official example creates a UE TUN interface (`oaitun_ue1`) and demonstrates Internet traffic through that interface.

This makes it the best **Phase 0** environment for HARP because we can modify the UE, RAN and core independently and observe NAS/RRC behavior without fighting commercial-phone firmware.

---

## 1. Why OAI RFSimulator is strategically useful to HARP

RFSimulator replaces the RF-board driver and exchanges baseband samples between gNB and UE processes.

From the point of view of OAI gNB/nrUE, it behaves like an RF device, while allowing execution without an SDR.

That allows HARP to test:

- 5G SA registration;
- NAS authentication;
- RRC signaling;
- PDU-session establishment;
- UE IP connectivity;
- multiple software UEs;
- custom UE identities;
- modifications to RRC/NAS related to SNPN;
- experimental NR sidelink code paths.

It does **not** prove:

- commercial handset compatibility;
- actual RF performance;
- modem firmware support;
- operator deployment compatibility;
- physical ProSe UE-to-Network Relay.

---

## 2. Current official OAI RFsim baseline

The current OAI `5g_rfsimulator` Docker scenario uses, at the time of this research:

- MySQL 9.6;
- OAI AMF v2.2.1;
- OAI SMF v2.2.1;
- OAI UPF v2.2.1;
- OAI gNB `develop`;
- OAI nrUE `develop`;
- external data-network container.

OAI documents Docker Compose v2.36.0 or newer for the current compose file because it uses `interface_name`.

The official deployment order is intentionally staged:

1. database + 5GC + external DN;
2. gNB;
3. nrUE.

The official documentation warns that simply starting everything blindly in one step is not the intended procedure.

---

## 3. Phase 0A — Reproduce unmodified OAI baseline

### Purpose

Before modifying anything for HARP, prove the upstream reference scenario works unchanged.

### Expected topology

```
nrUE 12.1.1.x
   |
RFsim
   |
gNB
   |
AMF / SMF / UPF
   |
external DN
   |
Internet
```

### Reference sequence

From the OAI `ci-scripts/yaml_files/5g_rfsimulator` directory:

```bash
docker compose up -d mysql oai-amf oai-smf oai-upf oai-ext-dn
docker compose ps -a

docker compose up -d oai-gnb
docker compose ps -a

docker compose up -d oai-nr-ue
docker compose ps -a
```

Expected checks:

- DB/core containers healthy;
- AMF reports gNB connected;
- nrUE creates `oaitun_ue1`;
- UE receives a 5G user-plane address;
- UE reaches the external DN;
- UE reaches Internet through `oaitun_ue1`.

### HARP pass condition

```
PASS_V5G_0A
```

Only claim it after all of these are observed:

1. gNB is registered to AMF;
2. nrUE is registered through gNB;
3. PDU session exists;
4. `oaitun_ue1` exists;
5. IP traffic reaches external DN;
6. Internet traffic exits through the UE tunnel.

---

## 4. Phase 0B — Make the virtual UE identity HARP-controlled

Before implementing SNPN, make identity changes in the software UE and core while staying in an ordinary PLMN-style test configuration.

Experiments:

1. change IMSI/SUPI;
2. change K/OPC and subscriber record;
3. run two nrUE instances with different identities;
4. remove one subscriber and confirm authentication fails;
5. rotate credentials and confirm re-registration succeeds.

### Pass condition

```
PASS_V5G_0B
```

HARP can deterministically issue/revoke a virtual device identity and the 5GC enforces the result.

This does not yet eliminate USIM-style AKA. It only proves control of the identity lifecycle.

---

## 5. Phase 0C — Instrument NAS/RRC before SNPN changes

Add logging/packet capture for:

- NGAP;
- NAS 5GMM;
- NAS 5GSM;
- RRC;
- SBI requests related to authentication/subscription;
- GTP-U user plane.

Record at least:

```
Registration Request
Authentication Request / Response
Security Mode
Registration Accept
PDU Session Establishment
UE IP allocation
```

This gives HARP a known-good trace against which SNPN modifications can be compared.

---

## 6. Phase 1 — SNPN/NID software experiment

### Current source finding

OAI's current source contains Release-17 ASN.1 structures such as:

```
NR_SNPN-AccessInfo-r17
```

and SNPN/NID definitions in F1AP/XnAP/E1AP.

However, presence of ASN.1 structures is **not** equivalent to functional SNPN support.

A current OAI F1AP code path explicitly treats:

```
AvailableSNPN_ID_List
```

as unsupported.

Code search also did not identify an implemented end-to-end **SNPN Onboarding Registration** procedure.

### Implication

The first SNPN work should be treated as a controlled source-code experiment.

Sub-goals:

1. identify SIB1/RRC path where SNPN/NID broadcast must be populated;
2. add/configure PLMN + NID;
3. teach nrUE selection logic to recognize the SNPN;
4. preserve NID through RAN/core interfaces;
5. extend core selection/subscription logic as necessary.

### Pass condition

```
PASS_V5G_SNPN_ID
```

A software nrUE sees/selects a configured PLMN+NID and the selected SNPN identity reaches the core correctly.

No onboarding requirement yet.

---

## 7. Phase 2 — SNPN onboarding

Only after ordinary SNPN identity works.

Required target behavior:

```
UE with Default UE Credentials
        |
        v
network broadcasting onboarding support
        |
        v
SNPN Onboarding Registration
        |
        v
restricted onboarding PDU session
        |
        v
DCS / PVS
```

### Current implementation risk

Neither current OAI RAN/core nor Open5GS should be assumed to provide this turnkey.

The standard data types exist in several open-source codebases, but our source review did not find a complete documented runtime path.

Therefore this phase may require implementing missing logic in:

- RRC/NAS;
- AMF;
- AUSF;
- subscriber/credential routing;
- provisioning server integration.

---

## 8. Phase 3 — HARP Credentials Holder / external AAA

Target identity model:

```
harp-device-id@harp-realm
```

using a standards-compliant Network-Specific-Identifier SUPI/NAI.

Target flow:

```
nrUE
 |
AMF
 |
AUSF
 |
NSSAAF / AIWF
 |
HARP AAA
```

Initial authentication candidate: **EAP-TLS**, because 3GPP allows key-generating EAP methods for the external Credentials Holder case.

### Major open-source gap

Current source searches found no turnkey NSSAAF/external-AAA implementation in the OAI 5GC components reviewed.

Open5GS contains modern generated Release-17/19 data models and onboarding-related structures but likewise should not be assumed to implement the entire Credentials Holder flow.

Therefore the likely HARP research options are:

1. add NSSAAF/AIWF functionality to an open 5GC;
2. implement a minimal lab-only equivalent to prove the authentication/key handoff;
3. obtain a 5GC implementation with existing Release-17 SNPN Credentials Holder support.

### Pass condition

```
PASS_HARP_CH
```

The access network accepts a UE whose subscription identity is authenticated by HARP's external credential domain rather than by an ordinary local subscriber record.

---

## 9. Parallel Phase SL0 — software sidelink experiment

OAI currently exposes NR sidelink mode controls.

Current source states:

- mode 0: no sidelink;
- mode 1: in-coverage/gNB;
- mode 2: out-of-coverage/no gNB.

The current nrUE code explicitly says **only sidelink mode 2 is supported; mode 1 is not yet supported**.

This is valuable but must not be confused with complete 5G ProSe UE-to-Network Relay.

### HARP use

Run two OAI nrUEs in the supported sidelink mode and prove:

1. synchronization;
2. direct PC5 physical/MAC path;
3. packet exchange supported by the current stack;
4. behavior with no gNB.

### Pass condition

```
PASS_PC5_SOFTWARE
```

This demonstrates a controllable PC5/sidelink substrate.

It still does not prove:

- ProSe discovery;
- Relay Service Codes;
- Remote UE authorization;
- UE-to-Network Relay;
- generic IP relay to 5GC.

Those become subsequent implementation targets.

---

## 10. Why OAI is better than srsUE for the advanced HARP branch

A second zero-hardware option exists with srsRAN/srsUE and ZeroMQ.

It is useful for a basic all-open 5G demonstration.

However, the legacy srsUE 5G path is not the preferred HARP research platform because advanced Release-17 work is the actual objective.

OAI has stronger strategic value for HARP because we currently see:

- active nrUE development;
- current RFSimulator Docker scenarios;
- Release-17 RRC ASN.1 structures;
- active sidelink implementation;
- source access across UE and gNB.

Therefore:

- use **OAI full-stack RFsim** as the default virtual laboratory;
- use srsRAN as a comparison/reference, not the primary advanced-UE branch.

---

## 11. Core choice

### First baseline

Use the **official OAI RFsim + OAI CN5G** topology unchanged.

Reason:

- it is an upstream tested integration;
- minimizes variables;
- gives a known-good baseline quickly.

### Second baseline

After `PASS_V5G_0A`, reproduce:

```
OAI nrUE -> OAI gNB -> Open5GS
```

Reason:

- Open5GS is easy to inspect and modify;
- it contains modern Release-17/19 data models;
- it may be a useful base for HARP-specific core experiments.

Do not start by mixing OAI RAN with Open5GS until the pure OAI scenario passes.

---

## 12. Machine requirements / planning assumption

OAI's current nrUE tutorial lists a reference development machine with approximately:

- Ubuntu Desktop 26.04.1 LTS, x86_64;
- 8 CPU cores around 3.5 GHz;
- 32 GB RAM.

Treat this as an upstream reference configuration, **not as a proven hard minimum for RFsim**.

RFSimulator is CPU-bound rather than bound to real-time RF sampling and may run faster or slower than real time.

For the HARP lab, prefer a Linux x86_64 machine with:

- Docker/Compose;
- at least 8 logical/high-performance cores if available;
- 16–32 GB RAM, with 32 GB preferred;
- substantial SSD free space for container images, builds and packet captures.

The exact minimum should be measured once the Phase 0 compose is run.

---

## 13. Cost-control decision

Before purchasing any of the following:

- USRP/B210/B205;
- industrial X75/X72 modem;
- programmable USIM hardware;
- Release-17 phone;
- small-cell/gNB;

HARP should first complete:

```
PASS_V5G_0A
PASS_V5G_0B
```

and make material progress toward:

```
PASS_V5G_SNPN_ID
PASS_PC5_SOFTWARE
```

This prevents buying hardware to discover later that the needed control-plane behavior is missing in software.

---

## 14. Relationship to the Android HARP PoC

This virtual lab does **not** replace the current phone work.

The project now has two independent laboratories:

### Lab A — Android user-plane relay

```
Phone A -> Wi-Fi Aware -> Phone B -> Internet
```

Goals:
- Stage 0;
- Stage 1;
- Stage 2 VPN/tun2socks.

### Lab B — cellular control-plane

```
software UE -> virtual/physical 5G access -> HARP identity/core
```

Goals:
- SA;
- SNPN/NID;
- Credentials Holder;
- onboarding;
- sidelink/ProSe.

The two tracks should only merge after both prove their core assumptions.

---

## 15. Immediate action order

1. Prepare a Linux host/VM capable of Docker Compose.
2. Reproduce OAI's unmodified `5g_rfsimulator` scenario.
3. Capture logs and network traces.
4. Record `PASS_V5G_0A` or exact failure.
5. Reproduce multiple nrUEs with independent credentials.
6. Add HARP-controlled issue/revoke test.
7. Begin SNPN/NID source changes.
8. In parallel, isolate OAI sidelink mode-2 RFsim test.
9. Only then evaluate physical RF hardware.

No GitHub Actions are required or planned for this laboratory.


## 16. Upstream sidelink test detail — important boundary

A deeper review found an upstream OAI container test dedicated to sidelink:

```
ci-scripts/yaml_files/5g_rfsimulator_sidelink/docker-compose.yaml
```

It launches:

```
nrUE-1:
  --sl-mode 2
  --sync-ref 4
  --rfsim
  acts as RFsim server

nrUE-2:
  --sl-mode 2
  --rfsim
  connects to nrUE-1
```

The associated CI test currently considers the receive-side evidence:

```
PSBCH RX:OK
```

on UE-2.

This is stronger evidence than merely finding sidelink data structures: OAI has a maintained executable two-UE RFsim sidelink scenario.

However, the current upstream automated assertion is **PSBCH synchronization/broadcast reception**, not end-to-end user data over PSSCH.

Source review also finds MAC scheduler branches for PSCCH/PSSCH marked `TBD` in current code.

Therefore split the software sidelink gate:

### SL0 — synchronization

```
PASS_PC5_SYNC
```

Requires:

- two nrUE containers;
- no gNB;
- UE-1 SyncRef;
- UE-2 decodes sidelink synchronization;
- `PSBCH RX:OK` observed.

### SL1 — sidelink user data

```
PASS_PC5_DATA
```

Requires:

- actual PSCCH/SCI scheduling;
- PSSCH/SLSCH transmit and receive;
- deterministic payload from UE-1 to UE-2;
- payload integrity assertion.

Current upstream OAI test does not by itself satisfy SL1.

### SL2 — IP bearer over PC5

```
PASS_PC5_IP
```

Requires an IP-facing adaptation/bearer on top of the proven sidelink data path.

### SL3 — UE-to-Network Relay

```
PASS_PROSE_U2N
```

Requires Remote UE + Relay UE semantics, authorization, relay service selection and Internet egress.

This staged definition prevents HARP from treating PHY synchronization as if it were already a ProSe data relay.
