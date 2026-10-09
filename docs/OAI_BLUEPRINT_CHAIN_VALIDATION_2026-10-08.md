# HARP — Current blueprint chain validation

**Date:** 2026-10-08

## OAI critical chain

Validated in memory against:

```
openairinterface/openairinterface5g
f8f769592a7030be88ede4bb5ca66fa1ca6a80e0
```

Sequential chain checked:

```
0001-nr-ue-serving-network-baseline-refactor.patch
0003c-snpn-npn-codec-test.patch
0003d-snpn-cell-access-codec-test.patch
0004-nr-ue-snpn-selection.patch
0004b-nr-ue-snpn-selection-unit-test.patch
0004c-snpn-full-sib1-codec-test.patch
```

Every context hunk was applied to an in-memory copy of the exact upstream source file, with the rule:

```
old context must occur exactly once
```

Result:

```
PASS_OAI_CRITICAL_BLUEPRINT_CHAIN_41_OF_41
```

The validation caught and corrected one real dependency drift before this PASS:

- `0004` had been hardened with a local SNPN pointer variable;
- `0004b` still expected the previous selector body;
- commit `0bdcee8603053fab81030941ce36c6593f53c79c` realigned the replacement hunk.

It also confirmed the hardened `0004` / `0004b` decoder null-buffer changes remain compatible.

## AMF chain

Validated in memory against:

```
openairinterface/oai-cn5g-amf
5eedea557a3745b13ed9ec4bf29e6a28bd912574
```

Sequential chain:

```
0001-amf-optional-snpn-snn-formatter.patch
0002-amf-lab-snpn-nid-config.patch
```

Result:

```
PASS_AMF_BLUEPRINT_CHAIN_17_OF_17
```

## What these PASS markers mean

They prove that the current context blueprints are structurally applicable, in order, to the pinned upstream source snapshots.

They do **not** prove:

- C/C++ compilation;
- CMake link closure;
- generated ASN.1 header compatibility at build time;
- unit-test execution;
- RFsim execution;
- successful 5G registration/authentication.

The next required validation layer is:

```
context applicability
        |
        v
real OAI/AMF compile
        |
        v
unit/codec tests
        |
        v
RFsim positive SNPN
        |
        v
RFsim mismatched-NID negative
```

No GitHub Actions were used.
