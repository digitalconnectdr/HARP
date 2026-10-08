# HARP — OAI SNPN SIB1 Codec Test Plan

**Date:** 2026-10-08  
**OAI source anchor:** `openairinterface/openairinterface5g` `develop` @ `f8f769592a7030be88ede4bb5ca66fa1ca6a80e0`

## Objective

Create a deterministic pre-RFsim test proving that the HARP SNPN identity survives:

```
C configuration
  -> get_SIB1_NR()
  -> UPER encode
  -> UPER decode
  -> SIB1 NPN identity inspection
  -> exact 44-bit NID round trip
```

Pass marker:

```
PASS_V5G_SNPN_SIB1_CODEC
```

This test does not prove over-the-air/RFsim delivery, nrUE selection, NAS registration or authentication.

## Upstream anchors

SIB1 builder:

```
openair2/LAYER2/NR_MAC_gNB/nr_radio_config.c
get_SIB1_NR(...)
```

SIB1 encoder:

```
encode_SIB_NR(...)
```

The source is part of OAI's top-level `MAC_NR_SRC` and therefore the `L2_NR` library.

Generated NR RRC ASN.1 support is exposed through:

```
asn1_nr_rrc
asn1_nr_rrc_hdrs
```

## Recommended test location

Add:

```
openair2/LAYER2/NR_MAC_gNB/tests/test_snpn_sib1_codec.c
openair2/LAYER2/NR_MAC_gNB/tests/CMakeLists.txt
```

and conditionally include that directory from the build when `ENABLE_TESTS` is enabled.

A simpler research-only alternative is to add the executable from the top-level test CMake and link it against `L2_NR`.

## Proposed CMake target

Conceptual target:

```cmake
add_executable(test_snpn_sib1_codec
  test_snpn_sib1_codec.c
)

target_link_libraries(test_snpn_sib1_codec PRIVATE
  L2_NR
  asn1_nr_rrc
  asn1_nr_rrc_hdrs
  minimal_lib
)

add_dependencies(tests test_snpn_sib1_codec)
add_test(NAME test_snpn_sib1_codec
         COMMAND test_snpn_sib1_codec)
```

The final upstream patch may need additional OAI utility/logging targets because `L2_NR` has transitive runtime dependencies. Resolve these from the pinned source tree rather than hard-coding assumptions.

## Test construction

The test should reuse an existing OAI serving-cell configuration fixture if possible.

Minimum values:

```
PLMN:
  MCC = 999
  MNC = 99
  MNC length = 2

TAC:
  deterministic lab value

NR Cell ID:
  deterministic valid 36-bit value

SNPN:
  enabled = true
  NID = 0x10000000001
```

Call the patched builder:

```c
NR_BCCH_DL_SCH_Message_t *sib1 =
    get_SIB1_NR(scc, &plmn, cell_id, tac, &mac_config, &snpn);
```

## Encode

Use the same production encoder:

```c
uint8_t encoded[NR_MAX_SIB_LENGTH / 8] = {0};

int encoded_len =
    encode_SIB_NR(sib1, encoded, sizeof(encoded));

AssertFatal(encoded_len > 0, "SIB1 encoding failed");
```

Do not inspect only the in-memory object. The gate requires an encode/decode round trip because the 44-bit BIT STRING padding is part of what HARP needs to prove.

## Decode

Decode the BCCH-DL-SCH message using OAI's generated NR RRC ASN.1 descriptor:

```c
NR_BCCH_DL_SCH_Message_t *decoded = NULL;

asn_dec_rval_t dr = uper_decode_complete(
    NULL,
    &asn_DEF_NR_BCCH_DL_SCH_Message,
    (void **)&decoded,
    encoded,
    encoded_len);
```

Accept only a successful decode that consumes the encoded message according to the asn1c return contract used by the pinned OAI version.

## Inspect

Navigate:

```
decoded
  -> message.c1.systemInformationBlockType1
  -> cellAccessRelatedInfo
  -> npn-IdentityInfoList-r16
  -> first NPN-IdentityInfo-r16
  -> npn-IdentityList-r16
  -> SNPN branch
  -> PLMN identity
  -> nid-List-r16
  -> first NID-r16
```

Use the generated C field spellings from the build output.

Assertions:

1. one expected SNPN identity exists;
2. branch discriminator is `snpn-r16`;
3. MCC = 999;
4. MNC = 99 with two digits;
5. NID BIT STRING has:
   - `size == 6`;
   - `bits_unused == 4`;
6. `harp_nid44_decode()` returns:
   - `0x10000000001`.

## Mandatory cases

### T0 — upstream baseline

```
snpn.enabled = false
```

Require:

- normal SIB1 encodes/decodes;
- NPN identity list absent;
- existing PLMN/TAC/cell identity remain valid.

### T1 — HARP lab NID

```
0x10000000001
```

Require exact round trip.

### T2 — minimum

```
0x00000000000
```

Require exact round trip.

### T3 — maximum

```
0xFFFFFFFFFFF
```

Require exact round trip.

### T4 — overflow

```
0x100000000000
```

Require rejection before SIB1 encoding.

### T5 — wrong padding negative test

Directly exercise the common NID decoder with a six-octet buffer whose low padding nibble is non-zero. Require rejection.

## PASS rule

Print:

```
PASS_V5G_SNPN_SIB1_CODEC
```

only after T0-T5 all pass in one test execution.

A successful encoder call alone is not sufficient.

## Next gate

After this test passes:

```
RFsim gNB
  -> actual SIB1 delivery
  -> nrUE receives/decode
  -> packet/log evidence
  -> PASS_V5G_SNPN_BROADCAST
```

Then implement exact PLMN+NID selection in `nr_rrc_process_sib1()`.
