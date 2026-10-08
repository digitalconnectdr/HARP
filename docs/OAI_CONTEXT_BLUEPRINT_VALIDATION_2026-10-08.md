# HARP — Context blueprint validation checkpoint

**Date:** 2026-10-08

## Why this checkpoint exists

The files under `patches/oai/` and `patches/oai-amf/` were originally
written as research diffs with context-only `@@` sections. They are not
standard unified diffs and should not be passed directly to `git apply`.

HARP now provides:

```
tools/apply_context_blueprints.py
tools/oai_snpn/apply_oai_blueprints.sh
tools/oai_amf_snpn/apply_amf_blueprints.sh
```

The applicator treats every hunk as an exact context transformation:

```
old = context + removed lines
new = context + added lines
```

Every old block must match exactly once. Missing or ambiguous anchors fail
closed. After writing, the tool runs:

```
git diff --check
```

A normal `git diff` from the checkout is then the authoritative standard diff.

## OAI RAN anchor validation

Pinned source:

```
openairinterface/openairinterface5g
f8f769592a7030be88ede4bb5ca66fa1ca6a80e0
```

Blueprints:

```
0001-nr-ue-serving-network-baseline-refactor.patch
0001b-nr-ue-snn-formatter-baseline-test.patch
0001c-nr-ue-kdf-baseline-test.patch
0002-nr-ue-snpn-serving-network-name.patch
0003a-gnb-snpn-config-plumbing.patch
0003b-gnb-snpn-sib1-encoding.patch
0003c-snpn-npn-codec-test.patch
0004-nr-ue-snpn-selection.patch
0004b-nr-ue-snpn-selection-unit-test.patch
```

Static in-memory application against GitHub contents found and corrected two
ambiguous anchors in `0001`, one dependency-order anchor in `0002`, and one
ambiguous insertion anchor in `0003c`.

After those corrections:

- `0001` through `0003c`: all 48 context hunks matched exactly once in
  sequential dependency order;
- `0004`: all 7 hunks matched exactly once on the state produced by `0001`
  (the only earlier blueprint that changes its RRC target, while its UICC
  targets are unchanged);
- `0004b`: all 10 hunks matched exactly once on the dependency-equivalent
  state produced by `0001 + 0003c + 0004`.

Therefore all 65 OAI context hunks currently have exact, non-ambiguous anchors
against the pinned source and their relevant staged dependencies.

This is stronger than a textual review, but it is still not a compiler result.

## OAI AMF anchor validation

Pinned source:

```
openairinterface/oai-cn5g-amf
5eedea557a3745b13ed9ec4bf29e6a28bd912574
```

Blueprints:

```
0001-amf-optional-snpn-snn-formatter.patch
0002-amf-lab-snpn-nid-config.patch
```

Result:

```
PASS_AMF_CONTEXT_BLUEPRINT_SEQUENCE
17/17 hunks
6 files
```

Every hunk matched exactly once in sequential dependency order.

## New nrUE selection unit boundary

`0004` configures a lab target NID in the software UICC and blocks Random
Access unless the SIB1 SNPN identity exactly matches:

```
UICC IMSI-derived MCC
+
UICC IMSI-derived MNC / nmc_size
+
uiccN.snpn_nid
```

`0004b` moves the actual ASN.1 search into `asn1_msg.c/.h`, so the same
production lookup is exercised by OAI's existing `test_asn1_msg`.

Unit cases:

1. MCC 999 / MNC 99 / NID `10000000001` -> match;
2. same PLMN / NID `10000000002` -> reject;
3. MCC mismatch / correct NID -> reject.

Staged unit marker:

```
PASS_V5G_SNPN_SELECT_UNIT
```

Runtime selection marker remains:

```
PASS_V5G_SNPN_SELECT
```

and is not claimed until RFsim/nrUE execution.

## Gate ordering update

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
PASS_V5G_SNPN_NPN_CODEC
        |
PASS_V5G_SNPN_SELECT_UNIT
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

## Remaining validation boundary

Not yet completed in the current environment:

- execute the blueprint applicators against real local Git checkouts;
- compile generated Rel-17.3.0 ASN.1 headers;
- run OAI C/C++ unit tests;
- build/run RFsim;
- run physical Android phone tests.

No GitHub Actions are required for any of these steps.
