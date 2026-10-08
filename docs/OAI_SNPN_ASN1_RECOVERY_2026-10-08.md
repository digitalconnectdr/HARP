# OAI SNPN ASN.1 recovery and generated-layout verification

**Date:** 2026-10-08

## Pinned OAI source

```
openairinterface/openairinterface5g
f8f769592a7030be88ede4bb5ca66fa1ca6a80e0
```

OAI selects:

```
NR_RRC_VERSION 17.3.0
```

from:

```
openair2/RRC/NR/MESSAGES/CMakeLists.txt
```

and runs:

```
run_asn1c(... "NR_" ...)
```

with:

```
-pdu=all
-fcompound-names
-gen-UPER
-gen-APER
-findirect-choice
```

## OAI asn1c anchor

OAI's build helper pins:

```
https://github.com/mouse07410/asn1c
940dd5fa9f3917913fd487b13dfddfacd0ded06e
```

The current execution container does not have `asn1c`, `bison` or `flex`, and DNS is unavailable, so the pinned generator could not be rebuilt locally.

## Large grammar file retrieval

The normal GitHub file fetch returned empty content for:

```
openair2/RRC/NR/MESSAGES/ASN.1/nr-rrc-17.3.0.asn1
```

because of the file size.

The underlying Git blob was retrieved directly:

```
blob SHA:
5db0502a4d916311e3cd9cc1bfd0e28d06d09b85

size observed:
~1.2 MB
```

This recovered the actual ASN.1 grammar.

## Exact ASN.1 structure verified

```
CellAccessRelatedInfo ::= SEQUENCE {
  plmn-IdentityInfoList ...
  ...
  [[
    cellReservedForFutureUse-r16 OPTIONAL,
    npn-IdentityInfoList-r16 NPN-IdentityInfoList-r16 OPTIONAL
  ]],
  [[
    snpn-AccessInfoList-r17 ... OPTIONAL
  ]]
}

NPN-IdentityInfoList-r16 ::=
  SEQUENCE (SIZE (1..maxNPN-r16)) OF NPN-IdentityInfo-r16

NPN-IdentityInfo-r16 ::= SEQUENCE {
  npn-IdentityList-r16
    SEQUENCE (...) OF NPN-Identity-r16,
  trackingAreaCode-r16 TrackingAreaCode,
  ranac-r16 RAN-AreaCode OPTIONAL,
  cellIdentity-r16 CellIdentity,
  cellReservedForOperatorUse-r16 ENUMERATED {reserved, notReserved},
  ...
}

NPN-Identity-r16 ::= CHOICE {
  pni-npn-r16 ...,
  snpn-r16 SEQUENCE {
    plmn-Identity-r16 PLMN-Identity,
    nid-List-r16 SEQUENCE (...) OF NID-r16
  }
}

NID-r16 ::= BIT STRING (SIZE (44))
```

## Generated C layout cross-check

A public repository preserving an OAI Rel-16 generated build tree was used only to verify asn1c naming/layout conventions:

```
rightbear/OAI_CU
commit 2be5284de496e746380255851f4abacf0415d05b
```

Confirmed generated details:

```
NR_NPN_Identity_r16_PR_snpn_r16
```

```
struct NR_NPN_IdentityInfo_r16
```

```
NR_NID_r16_t == BIT_STRING_t
```

```
CellAccessRelatedInfo.ext1
  -> npn_IdentityInfoList_r16
```

and:

```
choice.snpn_r16
```

is a pointer to:

```
struct NR_NPN_Identity_r16__snpn_r16
```

The first Rel-16 extension group remains the first extension group in the pinned Rel-17.3.0 grammar. Rel-17 adds `snpn-AccessInfoList-r17` in a later extension group.

## Resulting HARP patch split

```
0003a-gnb-snpn-config-plumbing.patch
```

Adds typed lab configuration only.

```
0003b-gnb-snpn-sib1-encoding.patch
```

Builds the SIB1 NPN/SNPN ASN.1 branch.

```
0003c-snpn-npn-codec-test.patch
```

Adds a direct ASN.1 encode/decode test for `NPN-IdentityInfoList-r16`.

## New intermediate gate

```
PASS_V5G_SNPN_NPN_CODEC
```

Required assertions:

- one NPN identity-info entry;
- one SNPN identity;
- PLMN MCC 999 / MNC 99;
- one NID;
- NID bytes:
  `10 00 00 00 00 10`;
- `size == 6`;
- `bits_unused == 4`;
- TAC/cell identity survive round-trip.

Then:

```
PASS_V5G_SNPN_NPN_CODEC
        |
PASS_V5G_SNPN_SIB1_CODEC
        |
PASS_V5G_SNPN_BROADCAST
```

## Validation boundary

The generated-layout cross-check materially reduces field-name uncertainty, but it does not replace compiling the patches against the actual Rel-17.3.0 generated headers.

Therefore:

- `PASS_V5G_SNPN_NPN_CODEC` is **not yet claimed**;
- `PASS_V5G_SNPN_SIB1_CODEC` is **not yet claimed**;
- `PASS_V5G_SNPN_BROADCAST` is **not yet claimed**.
