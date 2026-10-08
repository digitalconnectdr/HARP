# HARP — SNPN Serving Network Name / KDF Refactor Plan

**Date:** 2026-10-08

## Verified source anchors

### nrUE

Repository:

```
openairinterface/openairinterface5g
develop
f8f769592a7030be88ede4bb5ca66fa1ca6a80e0
```

Files:

```
openair2/RRC/NR_UE/rrc_UE.c
openair3/NAS/NR_UE/nr_nas_msg.h
openair3/NAS/NR_UE/nr_nas_msg.c
```

Current UE NAS context contains:

```c
plmn_id_t *sn_id;
```

Current KDF-facing path is PLMN-only:

```
derive_ue_keys()
  -> transferRES(..., nas->sn_id)
  -> derive_kausf(..., nas->sn_id)
  -> derive_kseaf(..., nas->sn_id)
```

All three call the current:

```c
servingNetworkName(uint8_t *msg, plmn_id_t *plmn_id)
```

which emits only:

```
5G:mncXXX.mccYYY.3gppnetwork.org
```

### AMF

Repository:

```
openairinterface/oai-cn5g-amf
develop
5eedea557a3745b13ed9ec4bf29e6a28bd912574
```

Current AMF helper:

```
src/utils/amf_conversions.cpp
amf_conv::get_serving_network_name(mnc, mcc)
```

Current N1 caller:

```
src/amf-app/amf_n1.cpp
```

The AMF therefore also constructs a PLMN-only SNN.

## Standards format

For ordinary PLMN:

```
5G:mnc<3 digits>.mcc<3 digits>.3gppnetwork.org
```

For SNPN:

```
5G:mnc<3 digits>.mcc<3 digits>.3gppnetwork.org:<11 uppercase hex NID digits>
```

HARP lab value:

```
MCC = 999
MNC = 99
NID = 0x10000000001
```

Canonical SNN:

```
5G:mnc099.mcc999.3gppnetwork.org:10000000001
```

NID value `1` must render with leading zeros:

```
...:00000000001
```

Maximum:

```
...:FFFFFFFFFFF
```

## Standalone reference implementation

HARP now carries:

```
tools/oai_snpn/snn.h
tools/oai_snpn/snn.c
tools/oai_snpn/snn_selftest.c
tools/oai_snpn/run_snn_selftest.sh
```

Expected pass marker:

```
PASS_SNN_VECTORS
```

The reference implementation was compiled locally with:

```
-std=c11 -Wall -Wextra -Werror
```

and the vectors passed.

## UE refactor

Replace the PLMN-only pointer in `nr_ue_nas_t` with a serving-network object:

```c
typedef struct {
  plmn_id_t plmn;
  bool has_nid;
  uint64_t nid;
} nr_serving_network_id_t;
```

Prefer value ownership in the NAS context rather than a raw heap pointer:

```c
nr_serving_network_id_t serving_network;
bool serving_network_valid;
```

This avoids lifetime ambiguity from the current `plmn_id_t *sn_id` allocation performed during SIB1 processing.

## Baseline-first migration

Patch order:

1. introduce type;
2. populate PLMN only from existing SIB1 path;
3. make SNN formatter accept the new type;
4. migrate RES*/K_AUSF/K_SEAF callers;
5. prove ordinary PLMN KDF outputs unchanged;
6. only then populate NID from SNPN SIB1;
7. enable SNPN SNN formatting.

Gate:

```
PASS_V5G_SNN_REFACTOR_BASELINE
```

must prove byte-for-byte equality of:

- ordinary PLMN SNN;
- RES*;
- K_AUSF;
- K_SEAF.

## SNPN SNN gate

After baseline regression passes, enable:

```
has_nid = true
nid = selected SIB1 NID
```

Require:

```
PASS_V5G_SNPN_SNN
```

Evidence:

- UE log prints canonical SNN;
- same PLMN + different NID produces different SNN;
- NID is always exactly 11 uppercase hex digits;
- overflow is rejected.

## AMF laboratory change

For `PASS_V5G_SNPN_KDF_LAB`, do not wait for full NGAP NID propagation.

Add an **optional configured lab NID** to AMF serving-network configuration.

Conceptual AMF helper:

```cpp
std::string get_serving_network_name(
    const std::string& mnc,
    const std::string& mcc,
    const std::optional<uint64_t>& nid);
```

When NID is absent, behavior must remain identical.

When present:

```
PLMN SNN + ":" + uppercase 11-digit hex NID
```

## KDF lab invariant

The important experiment is not only “authentication succeeds.”

Require two cases:

### Positive

```
UE NID = 10000000001
AMF NID = 10000000001
```

Expected:

```
UE SNN == AMF SNN
authentication/KDF path can succeed
```

### Negative

Change only one side:

```
UE NID = 10000000001
AMF NID = 10000000002
```

Expected:

```
UE SNN != AMF SNN
RES*/key derivation mismatch
authentication must not be treated as success
```

This is the strongest evidence that NID is cryptographically bound rather than merely displayed.

## Gate order

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

Only after this sequence should HARP invest in standards-compliant NGAP NID transport.
