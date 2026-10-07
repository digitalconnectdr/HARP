# HARP Research — eSIM, 5G ProSe and SNPN Onboarding

**Research date:** 2026-10-07  
**Purpose:** identify standards-based paths that can move HARP from an application-layer A↔B relay toward the real product goal: useful Internet connectivity for Phone A without requiring Phone A to maintain a conventional retail mobile-data subscription.

## Executive conclusion

Three technologies must be separated:

1. **eSIM/eUICC RSP** solves *secure provisioning of subscription credentials*. It does not create radio access or Internet by itself.
2. **5G ProSe UE-to-Network Relay** solves *indirect network access through another UE* at the 3GPP layer and is architecturally very close to HARP.
3. **SNPN onboarding** solves *bootstrap access and remote provisioning into a private 5G network*, including a model where the Credentials Holder can be separate from the access network.

The most important result is therefore:

> Replicating an LPA or SM-DP+ is not sufficient. The promising standards-level HARP path is a combination of identity/provisioning control plus a network that explicitly authorizes indirect or onboarding access.

---

## 1. eSIM: what it really provides

Current GSMA consumer RSP is described by SGP.21/SGP.22. As of this research date, SGP.21 v2.7 and SGP.22 v2.7 are active 2026 versions. GSMA IoT eSIM uses SGP.31/SGP.32; v1.3 was published in May 2026.

Sources:

- https://www.gsma.com/solutions-and-impact/technologies/esim/esim-specification/
- https://www.gsma.com/solutions-and-impact/technologies/esim/gsma_resources/sgp-22-v2-7/
- https://www.gsma.com/solutions-and-impact/technologies/esim/gsma_resources/sgp-32-v1-3/

### Functional chain

For consumer eSIM, the simplified chain is:

```
Activation code / discovery
        |
        v
LPA on device
        |
        v
SM-DP+
        |
        v
authenticated + bound profile package
        |
        v
production eUICC
        |
        v
USIM/ISIM subscription credentials
```

The eUICC is a secure execution environment, not generic storage.

### Production certificate chain

SGP.22 defines certificate chains rooted in a GSMA Certificate Issuer. The SM-DP+ uses separate authentication/profile-binding certificates and the eUICC has its own certificate chain.

Source:

- https://www.gsma.com/solutions-and-impact/technologies/esim/wp-content/uploads/2024/09/SGP.22-v2.6.pdf
- https://www.gsma.com/solutions-and-impact/technologies/esim/compliance/
- https://www.gsma.com/solutions-and-impact/technologies/esim/gsma-root-ci/

Operational production SM-DP+ service requires GSMA compliance/SAS processes and production certificates.

### Android access

Android exposes `EuiccManager` and `DownloadableSubscription`. A normal application can initiate supported profile-download flows, but privileged profile management is restricted by carrier privileges or privileged permissions; otherwise Android returns a resolvable operation requiring user involvement.

Sources:

- https://developer.android.com/reference/android/telephony/euicc/EuiccManager
- https://developer.android.com/reference/android/telephony/euicc/DownloadableSubscription

### Decisive limitation

Even if HARP reproduced the user-facing LPA workflow, that does **not** create usable cellular service.

5G primary authentication verifies subscription credentials held on the UE/USIM side against corresponding subscription/authentication state in the home network core (UDM/UDR/AUSF).

Therefore:

```
self-made activation code
+ self-made LPA
+ arbitrary local profile
!= public mobile Internet
```

unless a network core recognizes and authorizes those credentials.

### What we *can* replicate in a lab

Android documents downloadable **test** eSIM profiles for radio/eSIM testing. The target device must use a test certificate issued by a GSMA Certificate Issuer.

Source:

- https://source.android.com/docs/core/connect/esim-test-profiles

This is useful for learning RSP and implementing an experimental LPA/SM-DP+ flow, but a normal production eUICC in a retail phone should not be treated as an unrestricted test eUICC.

---

## 2. SGP.32: useful, but not the missing Internet primitive

SGP.32 changes management for IoT:

- IPA (IoT Profile Assistant) can reside on device or eUICC.
- eIM remotely triggers downloads and profile enable/disable/delete operations.
- It is designed for constrained/fleet devices.

Source:

- https://www.gsma.com/solutions-and-impact/technologies/esim/about/
- https://www.gsma.com/solutions-and-impact/technologies/esim/gsma_resources/sgp-32-v1-3/

For HARP this is strategically useful because it demonstrates a standards-supported model where device connectivity identities can be managed remotely without a person scanning a QR code each time.

But it still manages **profiles supplied by connectivity/network entities**. It does not eliminate the need for an access network that accepts those credentials.

**Decision:** study SGP.32 for control-plane/product architecture, not as the direct answer to zero-operator Internet.

---

## 3. 5G ProSe UE-to-Network Relay: closest standards match to HARP

3GPP TS 23.304 defines 5G Proximity-based Services.

A 5G ProSe Layer-3 UE-to-Network Relay can relay generic:

- IP traffic;
- Ethernet traffic;
- unstructured traffic.

For IPv4, the standard explicitly describes the relay performing NAT between a Remote UE and the relay's PDU session.

A Remote UE can be in NG-RAN coverage or **outside NG-RAN coverage**.

Sources:

- https://www.etsi.org/deliver/etsi_TS/123300_123399/123304/18.08.00_60/ts_123304v180800p.pdf
- https://itecspec.com/3gpp/23.304/s/5.4.1.1
- https://itecspec.com/3gpp/23.304/s/6.5.1.1

Conceptually:

```
Phone A: 5G ProSe Remote UE
       |
       | PC5 / sidelink
       v
Phone B: 5G ProSe UE-to-Network Relay
       |
       | Uu / PDU session
       v
5G network -> Internet
```

This is materially closer to the HARP objective than Wi-Fi tethering because the relay function is part of the standardized cellular architecture.

### Commercial use is in scope

3GPP work describes UE-to-Network Relay for public-safety **and commercial services**. Release 19 also defines intermediate/multi-hop UE-to-Network Relay functionality.

Source:

- https://itecspec.com/3gpp/23.304/s/4.3.13

### But authorization is mandatory

TS 23.304 requires policy/provisioning for:

- a UE acting as Relay;
- a UE acting as Remote UE;
- Relay Service Codes;
- PLMNs where relaying is authorized;
- security parameters/policies.

Source:

- https://itecspec.com/3gpp/23.304/s/5.1.4.1

TS 33.503 is even more explicit: the Remote UE is authenticated and authorized by the network. If it has no valid ProSe Remote User Key, it identifies through SUCI and the network checks UDM subscription data to determine whether that UE is authorized for the relay service.

Sources:

- https://itecspec.com/3gpp/33.503/s/6.3.3.3.1
- https://itecspec.com/3gpp/33.503/s/6.3.3.3.2
- https://www.etsi.org/deliver/etsi_ts/133500_133599/133503/17.08.00_60/ts_133503v170800p.pdf

Therefore standard 5G ProSe is **not** an unpermissioned free ride through an arbitrary nearby subscriber.

It can, however, support a commercial model where the network deliberately authorizes HARP Remote UEs and Relay UEs.

---

## 4. Important hardware implication for the current S22 target

The Snapdragon 8 Gen 1 used in the Snapdragon Galaxy S22 family integrates the Snapdragon X65.

Qualcomm publicly describes X65 as a **3GPP Release 16** modem.

Source:

- https://www.qualcomm.com/modems/products/snapdragon-x65-5g-modem-rf-system
- https://www.qualcomm.com/smartphones/products/8-series/snapdragon-8-gen-1-mobile-platform

The 5G ProSe UE-to-Network Relay architecture being evaluated here is a Release-17-era feature set.

No public evidence was found that the SM-S908U/X65 exposes the standardized 5G ProSe UE-to-Network Relay capability needed by HARP. The X65 architecture is described as software-upgradeable, but that is not evidence that Samsung/Qualcomm enabled this specific feature.

**Implication:** do not make the S22 the only hardware target for the ProSe branch.

For a future controlled ProSe investigation, hardware based on Snapdragon X75 or newer is more appropriate because Qualcomm explicitly documents Release 17/18 support.

Sources:

- https://www.qualcomm.com/modems/products/snapdragon-x75-5g-modem-rf-system
- https://docs.qualcomm.com/doc/87-71408-1/87-71408-1_REV_G_Snapdragon_8_gen_3_Mobile_Platform_Product_Brief.pdf

Even with X75-class hardware, modem support does not imply that Android exposes a public application API or that an operator has enabled the network-side feature.

---

## 5. Android application API gap

A review of current public Android developer/AOSP telephony documentation found public APIs for:

- eSIM management;
- subscription/carrier privilege operations;
- network slicing;
- tethering/VPN;
- Wi-Fi Aware.

No public application-level Android API was found that allows an ordinary app to create/configure a 5G NR PC5 ProSe UE-to-Network Relay session.

This should be treated as:

> **No public API found**, not proof that no vendor/private modem interface exists.

This moves ProSe from a normal-app implementation route to an **OEM/modem/operator integration route** unless future Android APIs expose it.

---

## 6. SNPN onboarding: the strongest new direction from this research

3GPP Release 17 added onboarding for Stand-alone Non-Public Networks (SNPN).

The architecture allows a UE with **Default UE credentials** to register to an **Onboarding SNPN (ON-SNPN)** for the limited purpose of obtaining final SNPN credentials and configuration.

Sources:

- https://www.etsi.org/deliver/etsi_TS/123500_123599/123501/17.14.00_60/ts_123501v171400p.pdf
- https://www.etsi.org/deliver/etsi_ts/123500_123599/123502/17.13.00_60/ts_123502v171300p.pdf
- https://itecspec.com/3gpp/23.501/s/5.30.2.10.2.6
- https://itecspec.com/3gpp/33.501/s/i.9.1

The onboarding PDU session may be restricted so that it is usable **only for remote provisioning**.

Source:

- https://itecspec.com/3gpp/rel-20/23.501/s/5.30.2.10.4.1

### Credentials Holder separation

Release-17 enhanced NPN architecture also allows a **Credentials Holder** to be separate from the SNPN that provides access.

3GPP defines:

- Credentials Holder;
- Default UE credentials;
- Default Credentials Server.

This is a major conceptual fit for HARP.

Possible future architecture:

```
HARP identity / Credentials Holder
          |
          | authentication / provisioning
          v
Partner or neutral-host SNPN
          |
          | 5G radio access
          v
Phone A
```

This separates:

- who owns/manages the user/device identity;
- who owns the physical radio access network.

That is much closer to the project's economic goal than reselling a conventional consumer eSIM plan.

### Limitation

SNPN onboarding still requires:

- an SNPN/ON-SNPN radio network;
- compatible UE modem/firmware;
- configured default credentials;
- network-side 5GC support;
- spectrum/deployment rights.

It is not an app-only solution.

But it proves that 3GPP already standardized a form of **bootstrap access whose purpose is to provision final connectivity credentials**.

---

## 7. New HARP architecture candidates

### Candidate A — Application relay

```
A app -> Wi-Fi Aware -> B app -> Internet
```

Status: current PoC.

Advantages:
- implementable now on stock Android;
- independent of cellular-core integration.

Limitation:
- B must run HARP;
- B pays/owns upstream connectivity.

Use: laboratory and early product prototype.

### Candidate B — 5G ProSe HARP

```
A Remote UE -> PC5 -> B Relay UE -> 5GC -> Internet
```

Advantages:
- standardized cellular-native relay;
- generic IP traffic;
- supports out-of-coverage Remote UE;
- commercial services are within architecture.

Limitations:
- both sides/network require authorization;
- modem/OEM/operator integration;
- current S22/X65 is a weak target for Rel-17 research.

Use: strategic OEM/operator/private-network branch.

### Candidate C — HARP Credentials Holder + SNPN

```
HARP credentials
     |
ON-SNPN bootstrap/provisioning
     |
partner/private SNPN -> Internet
```

Advantages:
- separates identity owner from radio network;
- remote provisioning is a standard feature;
- could avoid the retail MNO/eSIM resale model.

Limitations:
- still requires a compatible access network and commercial/spectrum arrangement.

Use: strongest new business/standards research direction.

### Candidate D — HARP-owned RSP / SM-DP+

```
HARP LPA/eIM -> HARP/partner SM-DP+ -> eUICC profile
```

Advantages:
- control of profile lifecycle;
- useful for enterprise/IoT/private-network provisioning.

Limitations:
- GSMA production compliance/certificates;
- still no radio access without a network recognizing the profile.

Use: supporting infrastructure, not standalone connectivity.

---

## 8. Recommended next research experiments

### R1 — eSIM protocol laboratory

Goal: reproduce RSP *legitimately* in a test environment.

Need:

- test-capable eUICC/device;
- GSMA test certificates/profile infrastructure or compatible lab components;
- local/controlled LPA and SM-DP+ experiments.

Success criterion:

- HARP-controlled test workflow downloads/enables a test profile.

What it proves:

- understanding/control of RSP.

What it does not prove:

- public network access.

### R2 — verify SNPN capabilities on candidate modern hardware

Target a Release-17/18 modem platform, preferably X75-class or newer.

Collect:

- modem/baseband version;
- OEM support for SNPN access mode/onboarding;
- possibility of configuring NID/SNPN selection;
- possibility of provisioning Default UE credentials;
- whether APIs are vendor-private.

### R3 — ProSe vendor feasibility request

The decisive questions for Samsung/Qualcomm/operator/private-network vendors are:

1. Does the exact UE/modem support **5G ProSe Layer-3 UE-to-Network Relay**?
2. Does it support the **Remote UE** role?
3. Does it support the **Relay UE** role?
4. Which PC5 bands/resource pools are implemented?
5. Is the feature enabled in commercial firmware?
6. Is there an Android/vendor API or modem interface?
7. Can Relay Service Codes and ProSe authorization policies be provisioned for a private/commercial service?
8. Can the network support a Remote UE without ordinary Uu coverage?
9. Can authorization use credentials managed by an external/private service?
10. What 5GC functions are required (PCF, UDM, AUSF, PKMF/PAnF)?

Do not accept a generic answer that the chipset “supports sidelink”; require answers for the two U2N roles and software exposure.

### R4 — private 5G lab

If suitable hardware and spectrum/lab access can be obtained:

```
Open/private 5GC
+ compatible gNB
+ controlled UE credentials
+ Release-17 UE
```

First target SNPN onboarding. Only after that attempt ProSe.

---

## 9. Decision update

The research changes the project map:

- **Do not pursue “clone eSIM = free Internet.”** That model is technically incomplete.
- **Keep Stage-0/1/2 application relay**, because it is the fastest way to validate user-plane behavior.
- **Promote SNPN onboarding + Credentials Holder to a primary research branch.**
- **Promote 5G ProSe U2N Relay to a primary strategic branch**, but move testing away from the S22-only assumption.
- **Treat SM-DP+/SGP.32 as enabling infrastructure**, not the missing radio-access layer.

The long-term objective becomes:

> HARP should control identity/provisioning and the user experience, while obtaining radio/user-plane access from a standards-based access layer that does not require Phone A to buy a conventional retail mobile-data subscription.


## 10. Dominican Republic deployment note

For a local SNPN/private-5G path, spectrum cannot be assumed to be freely available.

INDOTEL's 2021 5G licensing process covered the 3300–3600 MHz band for public carrier/Internet services and awarded spectrum in that process to incumbent concessionaires. The 2025 PNAF review also confirms a regulated spectrum framework while allowing the 5925–7125 MHz band under generic low/very-low-power licensing conditions.

Sources:

- https://indotel.gob.do/wp-content/uploads/2022/10/resolucion_116_reordenamiento_de_frecuencias_banda_3300___3600_mhz_conforme__licitacion_publica_internacional-1.pdf
- https://indotel.gob.do/wp-content/uploads/2025/05/0_Res._032-2025_PNAF2025rev.pdf

Implication:

- a production SNPN in conventional 3.5 GHz should be treated as a regulator/license/partner path, not an unlicensed app deployment;
- generic-license 6 GHz is useful for Wi-Fi and experimental local connectivity, but it does not by itself prove NR-U/PC5 support on retail smartphones;
- HARP should keep the **access-network partner / neutral-host** option explicit in the business architecture.


## 11. Release-19 correction — ProSe inside SNPN

A later standards pass found an important release boundary.

5G ProSe and UE-to-Network Relay are still relevant as earlier 5G ProSe work, but **explicit support for ProSe in NPN/SNPN was added by the Release-19 work item `TEI19_ProSe_NPN`**.

Current Release-19 TS 23.304 contains:

```
4.2.9 Support for 5G ProSe in NPNs
4.2.9.1 Support for 5G ProSe in SNPN
```

The corresponding 3GPP change work removed prior statements that 5G Proximity Services were not supported for SNPN.

Sources:

- https://www.etsi.org/deliver/etsi_ts/123300_123399/123304/19.05.00_60/ts_123304v190500p.pdf
- https://portal.3gpp.org/DesktopModules/CRs/CrDetails.aspx?CrId=531963
- https://portal.3gpp.org/DesktopModules/CRs/CrDetails.aspx?CrId=584430

### Consequence for HARP

The combined target:

```
HARP Credentials Holder
+ SNPN
+ ProSe Remote/Relay
```

must be treated as a **Release-19-class feature set**.

Earlier references in this document to Release-17-era ProSe should be read as referring to the origin/baseline of the ProSe relay feature, **not** as evidence that a Release-17 modem supports ProSe within an SNPN.

Do not buy hardware for the combined architecture unless the vendor explicitly confirms ProSe-in-SNPN support, Remote UE role, Relay UE role, and software exposure.
