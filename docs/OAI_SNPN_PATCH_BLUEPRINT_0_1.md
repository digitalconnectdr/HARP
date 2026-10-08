# HARP — OAI SNPN Patch Blueprint 0.1

**Date:** 2026-10-08  
**Upstream anchor:** `openairinterface/openairinterface5g` `develop` @ `f8f769592a7030be88ede4bb5ca66fa1ca6a80e0`  
**Purpose:** minimum implementation blueprint for `PASS_V5G_SNPN_BROADCAST` and `PASS_V5G_SNPN_SELECT`.

## 1. Scope boundary

This blueprint intentionally stops before:

- F1AP `AvailableSNPN_ID_List`;
- NGAP NID propagation;
- 5G-AKA SNPN serving-network binding;
- SNPN onboarding;
- external Credentials Holder;
- ProSe.

The first objective is only:

```
gNB config
  -> SIB1 PLMN+NID
  -> RFsim
  -> nrUE decodes exact PLMN+NID
```

## 2. Standards structure

TS 38.331 places SNPN identity under:

```
CellAccessRelatedInfo
  npn-IdentityInfoList-r16
    NPN-IdentityInfo-r16
      npn-IdentityList-r16
        NPN-Identity-r16
          snpn-r16
            plmn-Identity-r16
            nid-List-r16
              NID-r16
```

`NID-r16` is a `BIT STRING (SIZE(44))`.

Lab identity:

```
MCC = 999
MNC = 99
NID = 10000000001
```

Treat the NID string as hexadecimal:

```
0x10000000001
```

It occupies exactly 44 bits.

## 3. Do not extend `plmn_id_t`

Current upstream type:

```c
typedef struct {
  uint16_t mcc;
  uint16_t mnc;
  uint8_t mnc_digit_length;
} plmn_id_t;
```

Keep it unchanged.

Reason:

```
PLMN identity != SNPN identity

SNPN identity = PLMN identity + NID
```

Changing `plmn_id_t` would leak an SNPN-specific property into F1AP/XnAP/NGAP helpers and unrelated PLMN code before those paths are ready.

## 4. Proposed common HARP SNPN value type

Add a small type in a common NR configuration header:

```c
typedef struct {
  bool enabled;
  uint64_t nid;  // lower 44 bits used
} nr_snpn_config_t;
```

Validation helper:

```c
#define NR_SNPN_NID_BITS 44
#define NR_SNPN_NID_MAX ((1ULL << NR_SNPN_NID_BITS) - 1)

bool nr_snpn_nid_valid(uint64_t nid)
{
  return nid <= NR_SNPN_NID_MAX;
}
```

Do not use signed storage.

Do not encode the NID as an ASCII string on the RRC wire.

## 5. 44-bit BIT STRING encoding

ASN.1 `NID-r16` is 44 bits, therefore the generated `BIT_STRING_t` representation needs six octets with four unused bits.

Canonical helper concept:

```c
static void nid44_to_bit_string(uint64_t nid, BIT_STRING_t *out)
{
  AssertFatal(nid <= 0xFFFFFFFFFFFULL, "NID exceeds 44 bits");

  out->size = 6;
  out->bits_unused = 4;
  out->buf = calloc_or_fail(out->size, 1);

  /*
   * ASN.1 BIT STRING is emitted MSB-first.
   * Place the 44-bit value in the high 44 bits of the six-octet buffer.
   */
  uint64_t shifted = nid << 4;

  out->buf[0] = (shifted >> 40) & 0xff;
  out->buf[1] = (shifted >> 32) & 0xff;
  out->buf[2] = (shifted >> 24) & 0xff;
  out->buf[3] = (shifted >> 16) & 0xff;
  out->buf[4] = (shifted >> 8) & 0xff;
  out->buf[5] = shifted & 0xff;
}
```

Decoder concept:

```c
static bool bit_string_to_nid44(const BIT_STRING_t *in, uint64_t *nid)
{
  if (!in || !nid || in->size != 6 || in->bits_unused != 4)
    return false;

  uint64_t v = 0;
  for (size_t i = 0; i < 6; ++i)
    v = (v << 8) | in->buf[i];

  if ((v & 0x0f) != 0)
    return false;

  *nid = v >> 4;
  return true;
}
```

### Required unit vectors

```
00000000000 -> 00 00 00 00 00 00 / unused=4
00000000001 -> 00 00 00 00 00 10 / unused=4
10000000001 -> 10 00 00 00 00 10 / unused=4
FFFFFFFFFFF -> FF FF FF FF FF F0 / unused=4
```

Reject:

- values larger than `0xFFFFFFFFFFF`;
- buffers not exactly six bytes;
- `bits_unused != 4`;
- non-zero padding nibble.

## 6. gNB configuration

Preferred experimental configuration:

```
snpn_enabled = "yes";
snpn_nid = "10000000001";
```

Keep the NID textual in the config parser, parse once to `uint64_t`, then carry it internally as numeric 44-bit data.

Do not use decimal parsing for this field: NID notation is naturally hexadecimal and leading zeros can be meaningful to human operators.

Suggested parsed structure:

```c
nr_snpn_config_t snpn = {
  .enabled = true,
  .nid = 0x10000000001ULL,
};
```

## 7. gNB call path

Current path:

```
nr_mac_configure_sib1(...)
  -> get_SIB1_NR(scc, plmn, cellID, tac, mac_config)
```

Preferred experimental signature:

```c
NR_BCCH_DL_SCH_Message_t *get_SIB1_NR(
    const NR_ServingCellConfigCommon_t *scc,
    const plmn_id_t *plmn,
    uint64_t cellID,
    int tac,
    const nr_mac_config_t *mac_config,
    const nr_snpn_config_t *snpn);
```

Alternative: store `nr_snpn_config_t` in the per-cell structure and pass it from `nr_mac_configure_sib1()`.

Do not place it in `plmn_id_t`.

## 8. SIB1 insertion point

Upstream anchor:

```
openair2/LAYER2/NR_MAC_gNB/nr_radio_config.c
get_SIB1_NR()
```

Current code first constructs:

```
sib1->cellAccessRelatedInfo.plmn_IdentityInfoList
```

After the normal PLMN/TAC/cellIdentity block is complete, when `snpn->enabled`, populate:

```
sib1->cellAccessRelatedInfo.npn_IdentityInfoList_r16
```

Expected generated asn1c topology follows the ASN.1 names:

```
NR_NPN_IdentityInfoList_r16_t
  list[]
    NR_NPN_IdentityInfo_r16_t
      npn_IdentityList_r16
        list[]
          NR_NPN_Identity_r16_t
            present = ..._PR_snpn_r16
            choice.snpn_r16
              plmn_Identity_r16
              nid_List_r16
```

The exact C field spellings must be taken from the generated headers produced by the pinned OAI build. Do not guess them in a committed upstream patch.

## 9. PLMN duplication helper

The SNPN branch carries its own `PLMN-Identity`.

Avoid manually duplicating MCC/MNC digit-building logic twice.

Extract the existing logic in `get_SIB1_NR()` into a helper:

```c
static void fill_rrc_plmn_identity(
    NR_PLMN_Identity_t *dst,
    const plmn_id_t *src);
```

Use the same helper for:

- ordinary `plmn_IdentityInfoList`;
- SNPN `plmn-Identity-r16`.

This reduces the risk that a 2-digit MNC is encoded differently in the two identities.

## 10. NPN identity information fields

For the first broadcast gate use one NPN info entry and one SNPN identity.

Populate:

- PLMN identity;
- one NID in `nid-List-r16`;
- tracking area code;
- cell identity;
- `cellReservedForOperatorUse-r16 = notReserved`.

Reuse the same TAC and NR Cell Identity already used by the ordinary PLMN info block.

Do not add CAG/PNI-NPN support.

## 11. Broadcast logging

Before encoding SIB1, emit one deterministic log:

```
HARP_SNPN_BROADCAST mcc=999 mnc=99 nid=10000000001 bits=44
```

Also keep ASN.1 `xer_fprint` available for debug verification.

Gate:

```
PASS_V5G_SNPN_BROADCAST
```

must not be emitted merely because configuration parsed.

Emit PASS only after SIB1 encoding succeeds and a decode/inspection test confirms the NID in the produced SIB1.

## 12. nrUE decode anchor

Upstream anchor:

```
openair2/RRC/NR_UE/rrc_UE.c
nr_rrc_process_sib1(...)
```

Current behavior:

```
SIB1
  -> selected PLMN
  -> plmn_id_t
  -> nas->sn_id
  -> start RA
```

Add SNPN processing before Random Access is triggered.

Concept:

```
if (target_snpn_enabled) {
  find SNPN identity matching selected/configured PLMN;
  iterate nid-List-r16;
  decode each NID with bit_string_to_nid44();
  compare to configured target NID;

  if no exact match:
    do not mark SNPN selection PASS;
    do not copy an arbitrary NID into serving-network context;
}
```

## 13. UE serving-network context

Do not overload `nas->sn_id`, which is currently only `plmn_id_t *`.

For the research branch introduce a separate value, for example:

```c
typedef struct {
  plmn_id_t plmn;
  bool has_nid;
  uint64_t nid;
} nr_serving_network_id_t;
```

Longer term this should become the common source for:

- PLMN serving-network name;
- SNPN serving-network name;
- NGAP/NAS context;
- KDF inputs.

For the first two gates, it can remain nrUE-local.

## 14. nrUE deterministic logging

When SIB1 contains an SNPN:

```
HARP_SNPN_SEEN mcc=999 mnc=99 nid=10000000001
```

When exact configured identity matches:

```
PASS_V5G_SNPN_SELECT mcc=999 mnc=99 nid=10000000001
```

When PLMN matches but NID does not:

```
HARP_SNPN_REJECT reason=nid_mismatch expected=10000000001 seen=<...>
```

Do not print PASS from mere ASN.1 decode.

## 15. Test matrix

### Broadcast tests

1. SNPN disabled:
   - ordinary SIB1 unchanged;
   - no NPN identity list.

2. SNPN enabled with `10000000001`:
   - SIB1 contains exactly one SNPN identity;
   - NID decodes to exact 44-bit value.

3. NID max:
   - `FFFFFFFFFFF` accepted.

4. Overflow:
   - `100000000000` rejected before SIB1 generation.

### Selection tests

1. UE target equals broadcast NID -> PASS.
2. PLMN same, NID different -> reject.
3. NID same, PLMN different -> reject.
4. malformed NID BIT STRING -> reject.
5. SNPN target configured but no NPN list -> reject/no PASS.
6. ordinary PLMN mode -> existing registration behavior unchanged.

## 16. Gate definitions

### PASS_V5G_SNPN_BROADCAST

Requires all:

```
config parse OK
NID validation OK
SIB1 encode OK
encoded/decoded SIB1 contains exact PLMN+44-bit NID
ordinary baseline remains valid
```

### PASS_V5G_SNPN_SELECT

Requires:

```
RFsim nrUE receives SIB1
exact PLMN found
exact 44-bit NID found
selected serving-network context stores PLMN+NID
mismatch negative test passes
```

Neither gate proves registration/authentication.

## 17. Known later blockers

After these gates:

1. SNPN serving-network-name construction/KDF;
2. AMF matching NID for lab KDF;
3. NGAP NID propagation;
4. F1AP `AvailableSNPN_ID_List` for split CU/DU;
5. SNPN authentication;
6. onboarding;
7. external Credentials Holder;
8. ProSe.

Do not collapse these into the first patch.


## 18. Configuration placement decision

Do **not** add `nid` to OAI's existing `plmn_list`.

Current `GNBPLMNPARAMS_DESC` is reused by multiple subsystems and represents only:

```
mcc
mnc
mnc_length
```

Adding NID there would make ordinary PLMN consumers see an SNPN-specific parameter before E1AP/F1AP/NGAP paths are ready.

Preferred HARP laboratory configuration is a separate gNB-level block:

```
snpn = {
  enabled = "yes";
  nid = "10000000001";
};
```

Suggested parameter names:

```c
#define GNB_CONFIG_STRING_SNPN_CONFIG "snpn"
#define GNB_CONFIG_STRING_SNPN_ENABLED "enabled"
#define GNB_CONFIG_STRING_SNPN_NID "nid"
```

The parser should produce one optional `nr_snpn_config_t` owned by the gNB/RRC configuration and copied into the per-cell MAC/RRC configuration needed for SIB1 generation.

When the block is absent or `enabled = "no"`, the upstream PLMN baseline must remain byte-for-byte behaviorally equivalent.

## 19. NID helper gate

HARP now carries a standalone reference implementation under:

```
tools/oai_snpn/nid44.h
tools/oai_snpn/nid44.c
tools/oai_snpn/nid44_selftest.c
tools/oai_snpn/run_nid44_selftest.sh
```

The implementation has been validated with a local C11 compile using `-Wall -Wextra -Werror`.

Current pass marker:

```
PASS_NID44_VECTORS
```

Vectors:

```
0x00000000000 -> 00 00 00 00 00 00
0x00000000001 -> 00 00 00 00 00 10
0x10000000001 -> 10 00 00 00 00 10
0xFFFFFFFFFFF -> FF FF FF FF FF F0
```

Negative tests:

- 45-bit overflow rejected;
- non-zero padding nibble rejected.

Before Patch 2 modifies SIB1, this test must remain green.

## 20. First SIB1 test should not require RFsim

OAI currently has no direct unit test for `get_SIB1_NR()`.

HARP should add a deterministic encode/decode test before using RFsim:

```
construct SCC + PLMN + TAC + cell ID + SNPN config
  -> get_SIB1_NR()
  -> encode_SIB_NR()
  -> uper_decode BCCH-DL-SCH/SIB1
  -> inspect npn-IdentityInfoList-r16
  -> decode NID-r16
  -> assert exact PLMN+NID
```

Suggested pass marker:

```
PASS_V5G_SNPN_SIB1_CODEC
```

Required cases:

1. SNPN disabled -> no NPN list.
2. lab NID -> exact 44-bit round trip.
3. max NID -> exact round trip.
4. overflow -> rejected before ASN.1 generation.
5. ordinary PLMN fields remain unchanged.

Updated gate order:

```
PASS_NID44_VECTORS
        |
PASS_V5G_SNPN_SIB1_CODEC
        |
PASS_V5G_SNPN_BROADCAST
        |
PASS_V5G_SNPN_SELECT
```
