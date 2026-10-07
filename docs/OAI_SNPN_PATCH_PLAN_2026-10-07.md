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
