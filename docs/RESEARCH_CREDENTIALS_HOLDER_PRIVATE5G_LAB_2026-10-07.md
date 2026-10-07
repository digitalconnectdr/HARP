# HARP Research — Credentials Holder and Private 5G Lab

**Research date:** 2026-10-07  
**Status:** architecture and feasibility analysis  
**Scope:** define a practical path to validate HARP-controlled identity/provisioning with a standards-based 5G access network.

## 1. Key conclusion

The strongest standards-based architecture identified for HARP is not “clone an eSIM”.

It is:

```
HARP-managed device identity
        |
        | Network-Specific Identifier / EAP credentials
        v
HARP Credentials Holder / AAA
        |
        | authenticated trust relationship
        v
Partner or HARP-controlled SNPN
        |
        | 5G NR access
        v
Phone / UE
```

3GPP Release 17 explicitly supports access to an SNPN using credentials owned by a Credentials Holder that is separate from the SNPN.

The Credentials Holder can use:

1. an external AAA Server; or
2. AUSF + UDM.

For HARP, the AAA-based option is especially interesting because 3GPP permits non-AKA key-generating EAP methods and Network-Specific-Identifier-based SUPIs.

Sources:
- 3GPP TS 23.501, clause 5.30.2.9.2
- 3GPP TS 33.501, Annex I.2.2.2
- ETSI TS 123 501 V18.11.0
- ETSI TS 133 501 V18.9.0

## 2. What this changes

A conventional cellular subscription normally couples:

- identity;
- subscription database;
- authentication;
- access network;
- commercial billing relationship.

The Credentials Holder architecture allows these to be split.

Conceptually:

```
Identity owner: HARP
Radio/access owner: SNPN partner
Internet breakout: SNPN/partner
```

Phone A therefore does not necessarily need a conventional retail subscription sold by the access-network owner.

This does **not** make access free. It changes who controls identity and how the access relationship can be commercialized.

## 3. Critical credential detail

For access to an SNPN using credentials from a Credentials Holder with an external AAA Server:

- the UE can use a SUPI based on a Network-Specific Identifier (NAI);
- 3GPP says only Network-Specific-Identifier-based SUPI is supported for the AAA-server Credentials Holder case;
- the realm identifies/routs toward the Credentials Holder;
- the UE uses a key-generating EAP method;
- the resulting MSK is used by the AUSF to derive the 5G key hierarchy.

Illustrative HARP identity:

```
harp-device-7f3a@id.harp.example
```

This string is illustrative only; final realm/NAI design must follow TS 23.003 and the actual SNPN integration.

## 4. Credentials may live in the ME

3GPP explicitly discusses SNPN credentials stored in either:

- USIM; or
- ME.

When credentials are stored in the ME, the corresponding routing indicator can also be provisioned in the ME.

This matters because HARP should not assume that every future identity must be implemented as a traditional SIM profile.

However, whether a commercial Android phone allows an application to configure those ME-resident SNPN credentials is a separate implementation/OEM question.

No public Android API was found that allows a normal application to configure arbitrary SNPN identity/NID/default UE credentials.

## 5. External AAA authentication flow

Simplified 3GPP flow:

```
UE
 |
 | Registration Request / SUCI
 v
AMF
 |
 | Nausf_UEAuthentication
 v
AUSF
 |
 | select NSSAAF
 v
NSSAAF / AIWF
 |
 | EAP relay / protocol conversion
 v
HARP AAA
```

The NSSAAF selects the AAA server using the realm portion of the SUPI/NAI.

The AAA server acts as the EAP server.

The interface/protocol between NSSAAF and AAA is outside the strict 3GPP definition; existing AAA protocols such as RADIUS or Diameter may be used.

This is favorable for HARP because it gives us freedom to implement the external identity server without inventing a proprietary radio protocol.

## 6. SNPN onboarding flow

Release 17 also provides a bootstrap path for a device that does not yet possess its final SNPN credentials.

Simplified sequence:

```
UE with Default UE Credentials
      |
      | selects network broadcasting onboarding support
      v
ON-SNPN
      |
      | SNPN Onboarding Registration
      v
AMF / AUSF
      |
      | authenticate Default UE Credentials
      | locally or through DCS
      v
restricted onboarding PDU session
      |
      v
PVS — Provisioning Server
      |
      | provision final SNPN credentials/config
      v
UE re-registers with final identity
```

Important details:

- UE must already contain Default UE credentials.
- NG-RAN broadcasts an onboarding indication.
- Registration type is specifically “SNPN Onboarding”.
- The onboarding PDU session can be restricted so it can reach only provisioning infrastructure.
- UE configuration can include PVS IP/FQDN.
- DCS and PVS can be owned by an entity different from both the onboarding SNPN and the subscription-owning SNPN.

This last property is especially useful for HARP.

## 7. Minimum network functions for the HARP target

### Access-network side

For a full Credentials Holder / SNPN experiment we should expect at least:

- gNB;
- AMF;
- AUSF;
- NSSAAF / AIWF functionality for external AAA;
- SMF;
- UPF;
- UDM/UDR where required;
- NRF for SBA discovery in a normal implementation;
- DNS/routing/Internet breakout.

Depending on implementation:

- PCF;
- NSSF;
- SEPP;
- DCS;
- PVS.

are added as required by the exact architecture.

### HARP side

The minimum HARP identity-plane components are:

```
HARP Credential Issuer
        |
        +--> device credential lifecycle
        |
        +--> HARP AAA / EAP server
        |
        +--> optional DCS for onboarding
        |
        +--> optional PVS for final credential provisioning
```

A production deployment must also solve:

- secure device enrollment;
- credential revocation;
- replay resistance;
- device replacement;
- privacy-preserving identifiers;
- abuse controls;
- roaming/partner trust;
- billing/settlement with access-network partner.

## 8. Open-source 5GC reality

### Open5GS

Open5GS is an excellent candidate for **Phase 1: ordinary 5G SA control**.

Current project documentation describes:

- AMF;
- AUSF;
- UDM/UDR;
- SMF;
- UPF;
- NRF;
- NSSF;
- PCF;
- standard 5G SA registration and Internet breakout.

Open5GS currently advertises Release-19 compliance.

The source tree also contains generated Rel-17/Rel-19 OpenAPI and ASN.1 structures for:

- NID;
- SNPN;
- onboarding capability;
- PVS information;
- NGAP OnboardingSupport.

However, source review did not find a documented end-to-end runtime configuration for:

- SNPN access mode with NID;
- SNPN Onboarding Registration;
- Default UE Credentials;
- DCS;
- NSSAAF-to-external-AAA primary authentication.

Therefore those generated structures must **not** be interpreted as proof that full SNPN onboarding works today.

A further warning: current Open5GS development still has work in progress around EAP-AKA' support. The repository currently shows an active contribution titled “Add EAP-AKA' authentication support (AUSF/UDM/AMF)”.

**Conclusion:** Open5GS is suitable as the base 5GC for HARP lab Phase 1 and potentially as a patchable base for later phases, but full Release-17 SNPN Credentials Holder functionality should be treated as missing until demonstrated.

### free5GC

free5GC is another usable baseline 5GC.

Its own documentation explicitly stated that Network Slice-Specific and SNPN Authentication and Authorization Function (NSSAAF) functionality was not supported in the documented implementation.

That is directly relevant because the external-AAA Credentials Holder path depends on NSSAAF/AIWF behavior.

**Conclusion:** useful for ordinary 5G SA research, but not currently the shortest path to HARP external Credentials Holder validation.

## 9. RAN / UE software reality

### srsRAN Project

Current code search did not find SNPN/onboarding/NID implementation entries sufficient to claim Release-17 SNPN onboarding support.

Therefore srsRAN remains an excellent ordinary 5G SA gNB candidate, but we should not currently assume it can broadcast the complete SNPN onboarding information required by HARP.

### OpenAirInterface

OAI is currently more interesting for the advanced branch.

Current source contains:

- Release-17 RRC structures such as `SNPN-AccessInfo-r17`;
- SNPN-related F1AP/XnAP/E1AP definitions;
- NR sidelink implementation work;
- active 2026 integration work toward sidelink.

But the same codebase contains explicit paths where some SNPN IEs are marked unsupported, and September 2026 integration notes still describe “cleanup towards sidelink integration”.

Therefore OAI is promising as a research platform, but neither full SNPN onboarding nor ProSe U2N Relay should be considered turnkey.

## 10. Proposed HARP private-5G lab plan

### Phase 1 — Baseline 5G SA

Goal:

```
controlled UE -> controlled gNB -> HARP lab 5GC -> Internet
```

No SNPN onboarding. No ProSe. No external Credentials Holder.

Candidate components:

- Open5GS;
- srsRAN Project or OAI gNB;
- SDR or commercial small-cell/gNB;
- programmable test USIM or compatible test UE;
- Linux host for 5GC + Internet NAT.

PASS:

- UE completes 5G SA registration;
- PDU session established;
- UE receives IP;
- DNS works;
- HTTPS/iperf reaches Internet through lab UPF.

This proves we control the complete user-plane chain.

### Phase 2 — SNPN identity / NID

Goal:

```
UE selects PLMN+NID as an SNPN
```

Add:

- SNPN broadcast information;
- NID;
- SNPN access mode;
- UE selection/configuration.

First target should be a **software UE or controllable modem**, not a locked retail smartphone.

PASS:

- UE sees/selects correct SNPN;
- registration carries SNPN identity correctly;
- core and RAN preserve NID.

### Phase 3 — HARP Credentials Holder

Goal:

```
UE identity is authenticated by HARP, not by the SNPN owner's subscriber database
```

Preferred method:

- Network-Specific-Identifier SUPI;
- key-generating EAP method, initially EAP-TLS;
- HARP AAA server;
- NSSAAF/AIWF integration or a controlled experimental equivalent in the lab.

PASS:

- UE sends HARP realm identity;
- SNPN routes authentication to HARP AAA;
- HARP AAA authenticates device;
- AUSF derives required 5G keys;
- UE completes registration and obtains a PDU session.

This is the most important standards-level HARP identity milestone.

### Phase 4 — HARP onboarding

Goal:

A device with only Default UE Credentials receives final HARP/SNPN credentials over restricted onboarding connectivity.

Add:

- OnboardingSupport broadcast;
- SNPN Onboarding Registration;
- Default UE Credentials;
- DCS;
- onboarding DNN/S-NSSAI;
- PVS.

PASS:

- fresh UE has no final SNPN credentials;
- it authenticates using Default UE Credentials;
- it reaches only PVS;
- final credentials are provisioned;
- UE re-registers using final HARP identity.

### Phase 5 — ProSe UE-to-Network Relay

Only after Phase 3/4:

```
Remote UE A -> PC5 -> Relay UE B -> HARP-authorized 5G access
```

Requires:

- Release-17/18 UE/modem with real ProSe U2N support;
- network-side ProSe authorization/policy;
- PC5 configuration;
- Relay Service Code;
- Remote UE and Relay UE credentials/policy;
- OEM/modem interface or a software UE supporting the relevant stack.

PASS:

- A has no direct Uu connectivity;
- A establishes PC5 relay path through B;
- A receives generic IP connectivity;
- traffic exits via B's authorized PDU session / relay path.

## 11. Hardware strategy

Do not buy hardware merely because the datasheet says “Release 17”.

Require explicit confirmation for the exact feature.

For SNPN candidate hardware ask:

1. Does the UE support SNPN access mode?
2. Can PLMN + NID be configured?
3. Can credentials be stored in the ME?
4. Does it support Network-Specific-Identifier SUPI?
5. Which EAP methods are supported for SNPN access?
6. Does it support SNPN onboarding?
7. Can Default UE Credentials be provisioned?
8. Can PVS/DCS parameters be configured?
9. Is the interface exposed through AT commands, QMI/MBIM, SDK or OEM tooling?

For ProSe candidate hardware additionally ask:

10. Does it support 5G ProSe Layer-3 UE-to-Network Relay?
11. Remote UE role?
12. Relay UE role?
13. PC5 frequency/resource-pool configuration?
14. Relay Service Codes?
15. Commercial firmware enablement?

Candidate Release-17 module families identified during research include Qualcomm X75/X72-based industrial modules such as Quectel RG650E/RG650V and Telit FE990B34/40, but public product pages do **not** prove SNPN onboarding or ProSe U2N support.

Therefore vendor confirmation is mandatory before purchase.

## 12. Android retail phone constraint

Current public Android APIs provide rich support for ordinary telephony, eSIM and network state.

No public API was found for a normal app to:

- put the modem into SNPN access mode;
- configure arbitrary PLMN+NID subscription data;
- write Default UE Credentials;
- configure a Network-Specific-Identifier SUPI for SNPN;
- enable ProSe U2N Remote/Relay roles.

The standards allow these functions at the UE level, but retail Android implementation appears to remain OEM/baseband controlled.

This means the laboratory should start with:

- software UE; or
- industrial modem/module with documented low-level management;

and only later move the proven configuration to a retail smartphone through OEM cooperation.

## 13. Project decision

HARP now has two separate proof tracks:

### Track A — application user-plane proof

Existing:

```
Android A -> Wi-Fi Aware -> Android B -> Internet
```

Continue Stage 0/1/2.

### Track B — cellular identity/access proof

New:

```
HARP identity -> Credentials Holder -> SNPN -> UE
```

Build in ordered phases:

```
5G SA
 -> SNPN/NID
 -> external HARP AAA
 -> onboarding
 -> ProSe relay
```

Do not merge the tracks until each independently passes.

## 14. Immediate next technical task

Before purchasing hardware, create a lab bill-of-materials decision matrix comparing:

- Open5GS + srsRAN;
- Open5GS + OAI;
- OAI 5GC + OAI RAN/UE;
- commercial private-5G lab systems.

Score each against:

- standard 5G SA;
- NID/SNPN;
- onboarding indication;
- external AAA/NSSAAF;
- software UE availability;
- NR sidelink;
- ProSe U2N;
- required SDR;
- expected implementation effort;
- ability to work without GitHub Actions/cloud CI.

The default recommendation at this research stage is:

> Start Phase 1 with Open5GS + an open RAN/UE stack, but treat Phase 2–5 as patch/research work rather than pre-existing product features.


## 15. Release boundary correction for Phase 5

The Phase-5 wording above that refers to a “Release-17/18 UE/modem” is **not sufficient for the combined SNPN + ProSe target**.

A standards re-check shows:

- basic 5G ProSe work and UE-to-Network Relay precede Release 19;
- explicit 5G ProSe support in NPN/SNPN is a Release-19 enhancement (`TEI19_ProSe_NPN`);
- Release-19 RAN/CT changes add SNPN-specific NID handling and remove earlier SNPN ProSe limitations.

Therefore the hardware rule for Phase 5 is now:

> A modem/UE release number alone is insufficient. For HARP's final SNPN + ProSe architecture, require explicit vendor confirmation of ProSe-in-SNPN support and both Remote UE / Relay UE roles.

The software lab should still develop the features independently:

```
SNPN identity/access
        +
PC5/sidelink
        +
ProSe relay semantics
```

and merge them only after each path is measurable.
