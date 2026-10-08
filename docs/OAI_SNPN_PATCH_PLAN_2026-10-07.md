# HARP — OAI SNPN Patch Plan

**Research date:** 2026-10-07  
**Target:** OpenAirInterface `develop`, software-only RFSimulator first  
**Purpose:** implement the smallest standards-aligned SNPN identity experiment before onboarding, external Credentials Holder, or ProSe.

## 1. Standards correction

The SNPN identity itself is **not** carried in `SNPN-AccessInfo-r17`.

3GPP TS 38.331 separates the fields:

```
SIB1
└─ CellAccessRelatedInfo
   ├─ plmn-IdentityInfoList
   ├─ npn-IdentityInfoList-r16
   │  └─ NPN-Identity-r16
   │     └─ snpn-r16
   │        ├─ plmn-Identity-r16
   │        └─ nid-List-r16
   └─ snpn-AccessInfoList-r17
      ├─ extCH-Supported-r17
      ├─ extCH-WithoutConfigAllowed-r17
      ├─ onboardingEnabled-r17
      └─ imsEmergencySupportForSNPN-r17
```

Therefore:

- **Release 16 NPN structures carry PLMN + NID identity**.
- **Release 17 SNPN access-info structures carry per-SNPN capabilities**.

This distinction is mandatory for HARP's implementation.

Sources:

- ETSI/3GPP TS 38.331 V18.8.0, `CellAccessRelatedInfo`
  https://www.etsi.org/deliver/etsi_ts/138300_138399/138331/18.08.00_60/ts_138331v180800p.pdf
- 3GPP TS 38.331, `NPN-Identity`
  same specification, NPN-Identity information element.

## 2. Exact NPN identity encoding

TS 38.331 defines:

```
NPN-Identity-r16 ::= CHOICE {
    pni-npn-r16  SEQUENCE { ... },
    snpn-r16     SEQUENCE {
        plmn-Identity-r16  PLMN-Identity,
        nid-List-r16       SEQUENCE (...) OF NID-r16
    }
}

NID-r16 ::= BIT STRING (SIZE (44))
```

The standard states that:

```
PLMN ID + NID = SNPN identity
```

The NID is therefore exactly 44 bits.

TS 23.003 represents those 44 bits as:

```
11 hexadecimal digits
= 1 hex digit assignment mode
+ 10 hex digits NID value
```

Assignment modes:

- `0`: coordinated; NID globally unique;
- `1`: self-assigned;
- `2`: coordinated; PLMN+NID globally unique.

Important:

> Assignment mode 1 should not be used for the final external-Credentials-Holder/AAA architecture.

It is acceptable as a contained first laboratory SNPN identity experiment.

Source:

- 3GPP TS 23.003 clause 12.7.1
  https://itecspec.com/3gpp/23.003/s/12.7.1

## 3. Laboratory identity

For an isolated HARP RFSimulator laboratory use:

```
MCC = 999
MNC = 99
```

ITU-T E.212 reserves MCC 999 for internal use within a private network and recommends MNC 99 or 999 for testing/examples.

Source:

- ITU-T E.212 (06/2024), Appendix III
  https://www.itu.int/rec/T-REC-E.212

First lab NID:

```
10000000001
```

Interpretation:

```
1           = self-assignment mode
0000000001  = lab NID value
```

This value is **lab-only**, carries no global uniqueness claim, and must not be reused as the final HARP Credentials Holder identity.

## 4. Current OAI source gap

Current OAI source includes generated ASN.1 artifacts for:

- `NR_NID-r16`;
- `NR_NPN-IdentityInfoList-r16`;
- `NR_NPN-IdentityInfo-r16`;
- `NR_SNPN-AccessInfo-r17`;
- `NR_SIB18-r17`.

However, current repository searches found no runtime wiring of:

- `npn-IdentityInfoList-r16`;
- `snpn-AccessInfoList-r17`;
- `onboardingEnabled-r17`;

into the normal gNB SIB1 construction path.

This is a key distinction:

> OAI has the ASN.1 types but HARP still needs to wire the feature into runtime configuration and RRC behavior.

## 5. gNB patch point 1 — configuration

Current normal gNB configuration exposes roughly:

```
plmn_list = ({
    mcc = ...;
    mnc = ...;
    mnc_length = ...;
    snssaiList = (...);
});
```

HARP should initially add a separate lab SNPN block, conceptually:

```
harp_snpn = {
    enabled = 1;
    mcc = 999;
    mnc = 99;
    mnc_length = 2;
    nid = "10000000001";

    ext_ch_supported = 0;
    ext_ch_without_config = 0;
    onboarding_enabled = 0;
};
```

The exact field names are HARP design names and not 3GPP names.

Do **not** overload physical-cell `Nid_cell`; that symbol in OAI commonly means NR Physical Cell ID and is unrelated to the SNPN Network Identifier.

Required implementation tasks:

1. add configuration parser definitions;
2. validate NID is exactly 11 hex digits;
3. validate assignment-mode nibble;
4. convert 11 hex digits to 44-bit ASN.1 BIT STRING;
5. carry configuration into SIB1 builder.

## 6. gNB patch point 2 — SIB1 construction

Primary source target:

```
openair2/LAYER2/NR_MAC_gNB/nr_radio_config.c
```

Current code already constructs:

```
sib1->cellAccessRelatedInfo.plmn_IdentityInfoList
```

and currently assumes a single ordinary PLMN information block.

For HARP SNPN-0, add:

```
cellAccessRelatedInfo.npn-IdentityInfoList-r16
```

containing one `NPN-IdentityInfo-r16`:

```
npn-IdentityList-r16:
  [0]:
    choice = snpn-r16
    plmn = 999/99
    nid-List-r16:
      [0] = 0x10000000001 (44 bits)

trackingAreaCode-r16 = same controlled lab TAC
cellIdentity-r16 = controlled lab NR Cell Identity
cellReservedForOperatorUse-r16 = notReserved
```

For this first gate, do **not** advertise external Credentials Holder or onboarding.

That means `snpn-AccessInfoList-r17` can be omitted initially.

This isolates the fundamental question:

> Can OAI gNB broadcast a standards-shaped SNPN identity and can OAI nrUE select it?

## 7. nrUE patch point — SIB1 selection

Primary source target:

```
openair2/RRC/NR_UE/rrc_UE.c
```

Current code explicitly assumes only one normal PLMN information block and reads:

```
cellAccessRelatedInfo.plmn_IdentityInfoList
```

The SNPN path must instead:

1. detect `npn-IdentityInfoList-r16`;
2. iterate `NPN-IdentityInfo-r16`;
3. accept only `NPN-Identity-r16.snpn-r16` for the first experiment;
4. extract PLMN;
5. decode each 44-bit NID;
6. compare against configured HARP SNPN selection;
7. store selected PLMN + NID together;
8. emit an explicit log such as:

```
HARP_SNPN_SELECTED plmn=999-99 nid=10000000001
```

Do not collapse the NID into ordinary `selected_plmn_identity`; HARP needs a distinct SNPN identity object because PLMN alone is not unique.

Suggested internal structure:

```
typedef struct {
    bool is_snpn;
    plmn_t plmn;
    uint64_t nid44;
} harp_selected_network_t;
```

The final code should use OAI-native types where possible; this structure is a design sketch.

## 8. SNPN-only cell behavior

TS 38.331 still contains `plmn-IdentityInfoList` even for an NPN-only cell.

The specification states that in NPN-only cells:

- the ordinary PLMN identity list contains a single element;
- that ordinary element does not count toward the normal network-count limit;
- the cell identity relationship with the first NPN identity entry has defined behavior.

Therefore do not simply delete the existing PLMN list from SIB1.

For SNPN-0, preserve a standards-shaped SIB1 and add the NPN identity extension.

## 9. Release-17 capability list — second subgate

After basic identity selection works, add:

```
snpn-AccessInfoList-r17
```

TS 38.331 says its n-th entry corresponds to the n-th SNPN in `npn-IdentityInfoList`.

First capability tests:

### CH capability advertisement

```
extCH-Supported-r17 = true
```

PASS only means the UE decodes the flag.

It does **not** prove Credentials Holder authentication.

### Onboarding capability advertisement

```
onboardingEnabled-r17 = true
```

PASS only means the UE decodes the indication.

It does **not** prove SNPN Onboarding Registration, Default Credentials authentication or PVS access.

Create separate gates:

```
PASS_SNPN_ACCESSINFO_CH_FLAG
PASS_SNPN_ACCESSINFO_ONBOARD_FLAG
```

## 10. SIB18 boundary

Release 17 SIB18 is specifically relevant to HARP's future Credentials Holder/onboarding branch.

TS 38.331 defines:

```
SIB18-r17
├─ gin-ElementList-r17
│  └─ PLMN + NID list
└─ gins-PerSNPN-List-r17
```

These are **Group IDs for Network selection (GINs)**.

Their purpose is to increase the probability that a UE selects an SNPN capable of authenticating:

- credentials from a Credentials Holder; or
- Default Credentials for onboarding.

Important separation:

```
SIB1 npn-IdentityInfoList
    = identity of the access SNPN itself

SIB1 snpn-AccessInfoList
    = capability flags of each access SNPN

SIB18 GINs
    = groups of Credentials Holders / Default Credential Servers
      that can be used through the SNPN
```

For a single-SNPN first experiment, SIB18 is **not required** for `PASS_V5G_SNPN_ID`.

It becomes relevant when HARP implements actual external Credentials Holder selection/onboarding.

## 11. F1 split boundary

Current OAI source has ASN.1 support for SNPN-related F1AP structures but currently includes an explicit unsupported path for:

```
AvailableSNPN_ID_List
```

in:

```
openair2/F1AP/lib/f1ap_interface_management.c
```

Therefore use a **monolithic gNB** for SNPN-0.

Do not make CU/DU F1 SNPN support part of the first gate.

Later:

```
PASS_SNPN_MONOLITHIC
  -> implement/validate F1 SNPN propagation
  -> PASS_SNPN_F1_SPLIT
```

## 12. Core boundary

The first HARP SNPN patch should deliberately stop before full SNPN authentication semantics.

Initial target:

```
nrUE
  -> decodes PLMN+NID
  -> selects SNPN
  -> preserves identity toward NAS/RAN/core logs
```

Do not require external AAA yet.

Core work becomes a separate sequence:

```
C0 ordinary AKA subscriber
C1 NID-aware serving-network identity
C2 Network-Specific-Identifier SUPI
C3 NSSAAF/AIWF + external HARP AAA
C4 onboarding / DCS / PVS
```

## 13. ProSe + SNPN release correction

5G ProSe UE-to-Network Relay is a major HARP direction, but **ProSe support in SNPN is a Release-19 enhancement**.

3GPP work item:

```
TEI19_ProSe_NPN
```

added support/adaptations for ProSe in NPNs.

Current TS 23.304 Release 19 contains:

```
4.2.9 Support for 5G ProSe in NPNs
4.2.9.1 Support for 5G ProSe in SNPN
```

Consequences for HARP:

- do not infer final HARP capability from a modem that merely says “Release 17”;
- for **SNPN + ProSe** hardware, require explicit Release-19 ProSe-in-NPN support or vendor confirmation of the specific features;
- keep basic SNPN and basic ProSe experiments separable so each can be validated on earlier stacks.

Sources:

- 3GPP TS 23.304 Release 19
  https://www.etsi.org/deliver/etsi_ts/123300_123399/123304/19.05.00_60/ts_123304v190500p.pdf
- 3GPP CR / WI TEI19_ProSe_NPN
  https://portal.3gpp.org/DesktopModules/CRs/CrDetails.aspx?CrId=531963

## 14. Revised software gates

### `PASS_V5G_SNPN_BROADCAST`

Requires:

- gNB SIB1 contains NPN identity;
- PLMN = 999/99;
- NID decodes exactly to configured 44-bit value.

### `PASS_V5G_SNPN_SELECT`

Requires:

- nrUE identifies SNPN branch rather than ordinary PLMN only;
- nrUE selects exact PLMN+NID;
- HARP log contains exact selected NID.

### `PASS_V5G_SNPN_ID`

Requires:

- broadcast + selection pass;
- selected identity survives through the registration path far enough to be observable at the RAN/core boundary;
- mismatch NID test fails selection/registration as intended.

### `PASS_SNPN_ACCESSINFO_CH_FLAG`

Requires:

- `extCH-Supported-r17` is broadcast;
- nrUE decodes and associates it with the correct SNPN.

### `PASS_SNPN_ACCESSINFO_ONBOARD_FLAG`

Requires:

- `onboardingEnabled-r17` is broadcast;
- nrUE decodes and associates it with the correct SNPN.

None of these gates claims external AAA or onboarding success.

## 15. Negative tests

The first implementation must include negative cases:

1. malformed NID length;
2. non-hex NID;
3. NID mismatch;
4. PLMN matches but NID differs;
5. two NIDs under the same PLMN;
6. ordinary PLMN SIB1 without NPN extension;
7. unsupported PNI-NPN CHOICE received when HARP expects SNPN;
8. malformed/asymmetric `snpn-AccessInfoList` count;
9. capability flag mapped to wrong SNPN index.

This is important because HARP must prove that NID participates in network identity, rather than merely being printed in a log.

## 16. Implementation order

```
P0  reproduce upstream OAI RFsim baseline
P1  add NID parser/validator unit tests
P2  add gNB lab SNPN configuration
P3  encode npn-IdentityInfoList-r16 in SIB1
P4  decode/list SNPNs in nrUE
P5  implement exact PLMN+NID selection
P6  add mismatch negative tests
P7  preserve NID toward RAN/core boundary
P8  add snpn-AccessInfoList-r17 capability flags
P9  add SIB18 GIN support when Credentials Holder work begins
```

No physical RF hardware is needed for P0-P9.

No GitHub Actions are required.

## 17. Purchase decision impact

Do not purchase a “Release-17” phone/modem based solely on release number for the final combined architecture.

Require feature-level confirmation for:

- SNPN access mode;
- NID configuration;
- Credentials Holder/NSI SUPI;
- SNPN onboarding;
- 5G ProSe Remote UE;
- 5G ProSe Relay UE;
- **ProSe in SNPN / Rel-19 adaptations**.

Until that confirmation exists, software RFsim remains the lowest-risk HARP research path.


## 18. NAS and authentication gap — source-confirmed

A source review of the current OAI `nrUE` found that a true SNPN registration requires more than the RRC work described above.

### 18.1 Serving-network context is PLMN-only

Current UE NAS context:

```
openair3/NAS/NR_UE/nr_nas_msg.h

plmn_id_t *sn_id;
```

RRC currently assigns it in:

```
openair2/RRC/NR_UE/rrc_UE.c

nas->sn_id = plmn_id;
```

This means NAS receives only MCC/MNC.

For SNPN, HARP needs a serving-network context that can represent:

```
PLMN + NID
```

Suggested design shape:

```
typedef struct {
    plmn_id_t plmn;
    bool is_snpn;
    uint64_t nid44;
} serving_network_id_t;
```

Use OAI-native naming/types in the actual patch.

### 18.2 UE Serving Network Name is PLMN-only

Current nrUE helper:

```
static void servingNetworkName(uint8_t *msg, plmn_id_t *plmn_id)
{
    snprintf(...,
      "5G:mnc%03d.mcc%03d.3gppnetwork.org",
      plmn_id->mnc, plmn_id->mcc);
}
```

That helper is consumed by:

- RES* derivation;
- K_AUSF derivation;
- K_SEAF derivation.

Therefore a visually correct SNPN broadcast is **not sufficient**.

For SNPN the SNN must include the NID.

For the HARP lab identity:

```
MCC = 999
MNC = 99
NID = 10000000001
```

the expected SNPN SNN is conceptually:

```
5G:mnc099.mcc999.3gppnetwork.org:10000000001
```

The exact formatting must continue to follow TS 24.501/TS 33.501.

### 18.3 KDF impact

Current UE functions:

```
transferRES(..., plmn_id_t *plmn_id)
derive_kausf(..., plmn_id_t *plmn_id)
derive_kseaf(..., plmn_id_t *plmn_id)
```

all call the PLMN-only `servingNetworkName()`.

This must become:

```
transferRES(..., serving_network_id_t *sn)
derive_kausf(..., serving_network_id_t *sn)
derive_kseaf(..., serving_network_id_t *sn)
```

or an equivalent design where all three use one canonical SNN string.

Critical invariant:

> The UE and network must derive authentication material from the exact same SNN bytes.

Add a deterministic test vector for the HARP PLMN+NID string before attempting registration.

## 19. SNPN onboarding NAS gap

Current file:

```
openair3/NAS/NR_UE/5GS/5GMM/IES/FGSRegistrationType.h
```

currently defines:

```
001 initial
010 mobility update
011 periodic update
100 emergency
111 reserved
```

Current Release-17+ TS 24.501 defines:

```
101 = SNPN onboarding registration
```

Therefore onboarding cannot be activated by configuration alone.

Required work:

1. add the SNPN onboarding registration type;
2. add explicit NAS state indicating onboarding mode;
3. select type 101 only when the UE intentionally starts onboarding;
4. enforce onboarding-specific Registration Request content;
5. in particular, ensure the onboarding Registration Request does not improperly include normal Requested NSSAI when the standard forbids it for that procedure;
6. add encode/decode/unit tests before integrating with AMF.

Create a separate gate:

```
PASS_V5G_SNPN_ONBOARD_NAS
```

This gate proves only correct UE NAS message construction/parsing, not DCS/PVS provisioning.

## 20. Network-Specific-Identifier SUCI gap

Current `Suci5GSMobileIdentity_t` and `fill_suci()` are effectively IMSI-oriented.

Current encoder serializes:

- MCC/MNC digits;
- Routing Indicator;
- Protection Scheme ID;
- Home Network PKI;
- scheme output.

Current `fill_suci()` derives the identity from `uicc->imsiStr`.

For the external Credentials Holder architecture, TS 24.501 supports the **Network-Specific Identifier** SUPI format, whose SUCI carries an NAI/UTF-8 identity rather than the IMSI-oriented layout.

Therefore add a new mobile-identity variant/path rather than forcing an NAI into IMSI fields.

Suggested software-UE credential model:

```
identity_mode = IMSI | NSI
imsi = ...
nsi = "device-id@harp-realm.example"
credential_source = SOFTWARE_ME | USIM_STYLE
```

For the OAI RFsim laboratory, this credential object can live entirely in software.

Current OAI `uicc_t` is already a software structure populated from configuration. No physical USIM/eUICC is required to prove the encoding and authentication flows.

New gate:

```
PASS_HARP_NSI_SUCI
```

Requires:

- NSI/NAI configured in software UE;
- correct SUPI-format bits;
- correct UTF-8 NAI encoding;
- decoder round trip;
- IMSI path remains unchanged;
- malformed NAI rejected.

Do not combine this gate with external AAA yet.

## 21. Core-side SNN gap

Current OAI AMF source constructs the serving-network name in:

```
src/utils/amf_conversions.cpp

get_serving_network_name(mnc, mcc)
```

Current implementation returns only:

```
5G:mncXXX.mccYYY.3gppnetwork.org
```

Current N1 handler obtains only MCC and MNC from:

```
itti_uplink_nas_data_ind
```

and the current ITTI class contains:

```
bstring nas_msg;
std::string mcc;
std::string mnc;
bool is_guti_valid;
std::string guti;
```

There is no NID field.

The AMF then places that SNN into the authentication context and its 5G-AKA KDF uses the string directly.

This is good news:

> The core KDF implementation already consumes a general SNN string. The cryptographic primitive does not need redesign; the identity propagation and SNN construction do.

Required AMF changes:

1. add optional NID to serving-network context;
2. extend ITTI/RAN-to-N1 path to carry it;
3. extend `get_serving_network_name()` with SNPN form;
4. store exact SNN in NAS context;
5. pass it unchanged to AUSF or local/simple-scenario auth;
6. assert UE and network SNN byte-for-byte equality in tests.

## 22. NGAP version gap

A critical implementation finding:

Current OAI gNB NGAP build is based on:

```
openair3/NGAP/MESSAGES/ASN1/ngap-15.8.0.cmake
```

Current TS 38.413 behavior for SNPN requires the NG-RAN/AMF interface to preserve SNPN identity.

For network-shared SNPN operation, the INITIAL UE MESSAGE identifies the selected SNPN through:

```
PLMN Identity in TAI
+
NID in User Location Information
```

Current OAI code constructing the Initial UE Message in:

```
openair3/NGAP/ngap_gNB_nas_procedures.c
```

fills ordinary NR CGI and TAI information but current repository search does not expose the later NID/NPN IEs required for the SNPN path.

Therefore HARP must not claim standards-compliant end-to-end SNPN registration until this boundary is solved.

### Two-path implementation strategy

#### Path A — fast cryptographic laboratory

Inject the same lab NID into UE and AMF configuration out-of-band.

Topology:

```
RRC:
  gNB broadcasts PLMN+NID
  nrUE selects PLMN+NID

NAS UE:
  builds SNPN SNN from selected PLMN+NID

AMF:
  uses configured matching PLMN+NID
  builds identical SNPN SNN

KDF:
  UE SNN == AMF SNN
```

This path deliberately bypasses standards-based NID transport across NGAP.

Gate:

```
PASS_V5G_SNPN_KDF_LAB
```

Requires:

- exact same canonical SNN on both sides;
- RES*/K_AUSF/K_SEAF validation succeeds;
- changing only NID causes authentication failure or deterministic KDF mismatch;
- ordinary PLMN baseline remains working.

This is a valid research proof but **not** a standards-compliant SNPN network proof.

#### Path B — standards-compliant NGAP path

Upgrade/extend OAI NGAP support sufficiently to encode/decode the SNPN NID in the standard NGAP location and carry it through gNB -> AMF internal context.

Gate:

```
PASS_V5G_SNPN_NGAP
```

Requires:

- packet capture shows NID over NGAP in the correct IE/structure;
- AMF obtains NID from received NGAP, not static HARP configuration;
- wrong/missing NID produces the expected rejection/mismatch behavior.

Only then combine with AKA:

```
PASS_V5G_SNPN_AUTH
```

## 23. Revised SNPN gate ladder

Use these gates in order:

```
PASS_V5G_SNPN_BROADCAST
        |
PASS_V5G_SNPN_SELECT
        |
PASS_V5G_SNPN_KDF_LAB
        |
PASS_V5G_SNPN_NGAP
        |
PASS_V5G_SNPN_AUTH
        |
PASS_V5G_SNPN_ONBOARD_NAS
        |
PASS_HARP_NSI_SUCI
        |
PASS_HARP_CH
```

Meaning:

- **BROADCAST:** gNB SIB1 carries correct PLMN+44-bit NID.
- **SELECT:** nrUE selects exact PLMN+NID.
- **KDF_LAB:** UE/core use matching SNPN SNN; NID affects cryptographic derivation, with NID supplied to AMF out-of-band.
- **NGAP:** NID travels by standards-based gNB->AMF signaling.
- **AUTH:** actual SNPN 5G-AKA registration succeeds end-to-end.
- **ONBOARD_NAS:** UE can construct correct type-101 onboarding Registration Request.
- **NSI_SUCI:** software UE supports Network-Specific-Identifier/NAI identity format.
- **HARP_CH:** external HARP Credentials Holder authentication succeeds.

This ladder keeps three different problems separate:

```
radio network identity
!=
5G-AKA serving-network binding
!=
external Credentials Holder onboarding
```

## 24. Updated first patch scope

The first HARP patch series should be intentionally smaller than full SNPN:

```
Patch 1: common 44-bit NID parser/formatter + tests
Patch 2: gNB config + SIB1 NPN identity broadcast
Patch 3: nrUE SIB1 SNPN decode/select
Patch 4: nrUE serving-network context PLMN+NID
Patch 5: canonical SNPN SNN helper + KDF tests
Patch 6: AMF optional configured lab NID + canonical SNN helper
Patch 7: PASS_V5G_SNPN_KDF_LAB
```

Only after Patch 7:

```
Patch 8+: NGAP ASN.1/version work and standards-based NID propagation
```

This order gives HARP measurable progress without letting the current NGAP Release-15 boundary block all SNPN research.


## 25. Concrete upstream OAI anchor — verified 2026-10-07

The current official GitHub mirror reviewed for this plan is:

```
openairinterface/openairinterface5g
branch: develop
commit: f8f769592a7030be88ede4bb5ca66fa1ca6a80e0
integration: 2026.w40
```

This matters because the SNPN conclusions below are now tied to a concrete upstream source snapshot rather than generic OAI documentation.

### 25.1 SIB1 construction anchor

Current SIB1 is built in:

```
openair2/LAYER2/NR_MAC_gNB/nr_radio_config.c
get_SIB1_NR(...)
```

The current function builds:

```
sib1->cellAccessRelatedInfo.plmn_IdentityInfoList
```

from `plmn_id_t`, MCC/MNC, TAC and cell identity.

There is currently no HARP-required NPN/NID population in that construction path.

Therefore the first broadcast patch should be anchored directly after the current PLMN identity construction, not scattered through scheduler code.

Target structure:

```
SIB1
  cellAccessRelatedInfo
    plmn-IdentityInfoList        <- existing OAI path
    npn-IdentityInfoList-r16     <- HARP Patch 2
       NPN-IdentityInfo-r16
         plmn-Identity
         nid-r16 = 44-bit NID
```

Lab target remains:

```
MCC 999
MNC 99
NID 10000000001
```

### 25.2 Split the first SNPN gate

The previous `PASS_V5G_SNPN_ID` gate is now split into two smaller gates.

#### Gate A — broadcast only

```
PASS_V5G_SNPN_BROADCAST
```

Requirements:

1. unmodified RFsim/gNB baseline still starts;
2. generated SIB1 ASN.1 dump contains the configured PLMN+NID;
3. NID is exactly 44 bits on the wire;
4. changing the configured NID changes only the expected NPN identity field;
5. ordinary PLMN SIB1 construction still works when SNPN mode is disabled.

This gate does not require nrUE selection logic.

#### Gate B — nrUE decode/select

```
PASS_V5G_SNPN_SELECT
```

Requirements:

1. nrUE decodes the NPN identity from received SIB1;
2. logs canonical PLMN+NID;
3. configured target NID is selected;
4. mismatched NID is rejected/ignored according to the experiment policy;
5. selected NID is stored in UE serving-network context for later NAS/KDF work.

This sequencing keeps RRC encoding bugs separate from UE selection bugs.

### 25.3 F1AP remains a later boundary

Current upstream code still contains an explicit unsupported path in:

```
openair2/F1AP/lib/f1ap_interface_management.c
```

for:

```
F1AP_ProtocolIE_ID_id_AvailableSNPN_ID_List
```

which triggers:

```
AvailableSNPN_ID_List is not supported
```

This confirms that a distributed CU/DU SNPN implementation is not currently turnkey.

HARP should therefore use the monolithic/full-stack RFsim path for `PASS_V5G_SNPN_BROADCAST` and `PASS_V5G_SNPN_SELECT`.

Do not make F1AP support a prerequisite for the first NID experiment.

### 25.4 ASN.1 support versus runtime support

The same upstream snapshot contains generated Rel-17 ASN.1 artifacts for:

- `NR_NID-r16`;
- `NR_NPN-IdentityInfoList-r16`;
- `NR_SIB18-r17`;
- other Release-17 RRC types.

This is useful because HARP does not need to invent ASN.1 definitions for the first SIB1 experiment.

But generated ASN.1 presence is not runtime support.

The implementation rule remains:

```
ASN.1 type exists
!=
OAI populates it
!=
nrUE acts on it
!=
core understands it
```

Each transition requires its own gate.

### 25.5 Revised minimum patch order from the verified source

```
Patch 1
  common NID parser/formatter
  - exactly 44 bits
  - assignment-mode validation
  - canonical hex/text representation
  - unit tests

Patch 2
  gNB config fields:
    snpn_enabled
    snpn_nid

  get_SIB1_NR():
    populate npn-IdentityInfoList-r16

  PASS_V5G_SNPN_BROADCAST

Patch 3
  nrUE SIB1 decode:
    extract PLMN+NID
    canonical log
    target match

  PASS_V5G_SNPN_SELECT

Patch 4
  persist selected NID in nrUE serving-network context

Patch 5+
  canonical SNPN SNN/KDF laboratory work
```

This is now the preferred implementation sequence.
