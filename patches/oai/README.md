# OAI patch staging

These patches are staged against:

```
openairinterface/openairinterface5g
commit f8f769592a7030be88ede4bb5ca66fa1ca6a80e0
```

They are research **context blueprints** stored in HARP so the work does not depend on GitHub Actions.

Important: these files intentionally use context-only `@@` sections while the research branch is still evolving. They are not standard unified diffs for direct `git apply`.

Use:

```bash
bash tools/oai_snpn/apply_oai_blueprints.sh /path/to/openairinterface5g --check
bash tools/oai_snpn/apply_oai_blueprints.sh /path/to/openairinterface5g
```

The strict applicator requires every old context block to match exactly once and then runs `git diff --check`. After application, `git diff` is the authoritative standard diff.

## Order

```
0001-nr-ue-serving-network-baseline-refactor.patch
0001b-nr-ue-snn-formatter-baseline-test.patch
0001c-nr-ue-kdf-baseline-test.patch
0002-nr-ue-snpn-serving-network-name.patch
0003a-gnb-snpn-config-plumbing.patch
0003b-gnb-snpn-sib1-encoding.patch
0003c-snpn-npn-codec-test.patch
0003d-snpn-cell-access-codec-test.patch
0004-nr-ue-snpn-selection.patch
0004b-nr-ue-snpn-selection-unit-test.patch
0004c-snpn-full-sib1-codec-test.patch
```

### Patch 0001

Purpose:

- replace the current heap-owned `plmn_id_t *sn_id`;
- introduce `nr_serving_network_id_t`;
- preserve ordinary PLMN SNN behavior;
- migrate current RRC and simulator users.

Gate:

```
PASS_V5G_SNN_REFACTOR_BASELINE
```

Then apply `0001b` and require:

```
PASS_V5G_SNN_FORMAT_BASELINE
```

This proves the refactored formatter preserves the ordinary PLMN SNN and rejects truncation.

Do not treat this as the full KDF baseline.

### Patch 0001c

Purpose:

- add deterministic ordinary-PLMN RES*, K_AUSF and K_SEAF vectors to OAI's existing `nas_lib_test`;
- prove the serving-network type refactor has not changed any of those outputs.

Gate:

```
PASS_V5G_SNN_REFACTOR_BASELINE
```

The reference vectors are independently regenerable with:

```
tools/oai_snpn/kdf_baseline_vectors.py
```

Do not apply patch 0002 until both `PASS_V5G_SNN_FORMAT_BASELINE` and `PASS_V5G_SNN_REFACTOR_BASELINE` pass.

### Patch 0002

Purpose:

- format SNPN SNN when `has_nid=true`;
- enforce a 44-bit maximum;
- render exactly 11 uppercase hexadecimal NID digits.

It does not populate NID from SIB1.

Gate:

```
PASS_V5G_SNPN_SNN
PASS_V5G_SNPN_KDF_UE
```

### Patch 0003a

Purpose:

- add a separate lab-only `snpn` gNB configuration block;
- parse/validate an exactly 44-bit NID;
- carry the typed SNPN config through `nr_mac_config_t` into `get_SIB1_NR()`;
- do not alter ASN.1 yet.

Example:

```
snpn = {
  enabled = "yes";
  nid = "10000000001";
};
```

### Patch 0003b

Purpose:

- build `npn-IdentityInfoList-r16` in SIB1 when SNPN is enabled;
- reuse PLMN/TAC/cell-ID semantics;
- encode NID as a six-octet BIT STRING with four unused low bits;
- leave ordinary PLMN SIB1 behavior intact when disabled.

The generated C layout was cross-checked against public OAI/asn1c Rel-16 build artifacts and the exact Rel-17.3.0 ASN.1 grammar blob from the pinned OAI commit.

This patch still requires compilation against the actual generated Rel-17.3.0 headers.

### Patch 0003c

Purpose:

- exercise `NPN-IdentityInfoList-r16` directly in OAI's existing GTest RRC ASN.1 test target;
- encode/decode PLMN 999/99 + NID `10000000001`;
- verify NID bytes `10 00 00 00 00 10`;
- verify `bits_unused == 4`.

Gate:

```
PASS_V5G_SNPN_NPN_CODEC
```

This precedes a second extension-container codec gate and then the full-SIB1 gate:

```
PASS_V5G_SNPN_NPN_CODEC
        |
PASS_V5G_SNPN_CELL_ACCESS_CODEC
        |
PASS_V5G_SNPN_SIB1_CODEC
```

### Patch 0003d

Purpose:

- encode/decode the actual `CellAccessRelatedInfo` container;
- prove that the Rel-16 NPN list is carried under `ext1`;
- verify PLMN 999/99 + exact 44-bit HARP NID after UPER round-trip;
- keep the Rel-17 `ext2/snpn-AccessInfoList-r17` path independent.

Gate:

```
PASS_V5G_SNPN_CELL_ACCESS_CODEC
```

The `ext1`/SNPN-choice layout was cross-checked against generated OAI/asn1c artifacts, including a Rel-17 artifact exposing `ext1->npn_IdentityInfoList_r16` and `ext2->snpn_AccessInfoList_r17`.

### Patch 0004

Purpose:

- add optional `uiccN.snpn_nid` as the nrUE lab target identity;
- derive expected home MCC/MNC from the UICC IMSI and `nmc_size`;
- inspect SIB1 `npn-IdentityInfoList-r16`;
- require exact PLMN + 44-bit NID match;
- only then set `serving_network.has_nid=true`;
- return before SIB1 validity / Random Access on mismatch.

Runtime evidence:

```
HARP_SNPN_SEEN ...
PASS_V5G_SNPN_SELECT ...
```

The marker is not considered validated until a real OAI build/RFsim run emits it.

### Patch 0004b

Purpose:

- move the ASN.1 PLMN+NID lookup into `asn1_msg.c/.h`;
- make the production lookup available to the existing `test_asn1_msg` target;
- test exact PLMN+NID success;
- reject NID mismatch;
- reject PLMN mismatch.

Gate:

```
PASS_V5G_SNPN_SELECT_UNIT
```

This is a pre-RFsim unit gate. The runtime `PASS_V5G_SNPN_SELECT` still requires an actual nrUE SIB1 processing path.

### Patch 0004c

Purpose:

- reuse OAI's simulator SCC fixture path (`prepare_scc/fill_scc_sim/fix_scc`);
- call the real `get_SIB1_NR()` with HARP SNPN enabled;
- encode using `encode_SIB_NR()`;
- UPER-decode the complete BCCH-DL-SCH/SIB1;
- verify exact PLMN+NID through the shared production selector;
- repeat with SNPN disabled and require the NPN identity list to be absent.

Gate:

```
PASS_V5G_SNPN_SIB1_CODEC
```

The staged test currently links `test_asn1_msg` against `L2_NR`, because that library already contains both the gNB SIB1 builder and SCC simulator helpers. This linkage is context-valid but still requires a real CMake/build pass; if the static link proves too heavy, split a dedicated test target instead of weakening the assertions.

## Validation policy

These patch files are source-staged but have not yet been compiled against a local OAI checkout in the current environment.

Required validation after an OAI checkout/build environment exists:

```
git checkout f8f769592a7030be88ede4bb5ca66fa1ca6a80e0
bash tools/oai_snpn/apply_oai_blueprints.sh <checkout> --check
bash tools/oai_snpn/apply_oai_blueprints.sh <checkout>
git diff --check
build/tests
baseline KDF comparison
SNPN codec/selection tests
```

Do not claim either OAI gate from the existence of these patch files alone.

## Later work

Still intentionally deferred:

- production-grade nrUE NID provisioning beyond the lab UICC field;
- replacing lab-global AMF NID with serving-network/NGAP-derived NID;
- end-to-end simple-scenario positive/negative KDF execution;
- standards-compliant NGAP/F1AP NID propagation;
- RFsim broadcast/select validation.


## Local pre-RFsim gate runner

A focused local runner is available:

```
bash tools/oai_snpn/run_pre_rfsim_gates.sh /path/to/openairinterface5g
```

Requirements:

- checkout must be exactly at:
  `f8f769592a7030be88ede4bb5ca66fa1ca6a80e0`;
- checkout must be clean;
- CMake + Ninja + OAI build dependencies must already be installed;
- no GitHub Actions are used.

The runner:

1. checks all HARP OAI blueprints in order;
2. applies them to the disposable checkout;
3. configures with `ENABLE_TESTS=ON` and ASAN disabled;
4. builds only:
   - `nas_lib_test`
   - `test_asn1_msg`
   - `test_snpn_sib1_codec`
5. runs only those CTest entries;
6. requires all expected HARP PASS markers;
7. emits:

```
PASS_HARP_OAI_SNPN_PRE_RFSIM_GATES
```

This marker is stronger than blueprint applicability but still precedes RFsim registration/authentication.


## Independent SNPN KDF reference gate

Reference checker:

```
tools/oai_snpn/kdf_reference.py
```

It independently recalculates the same TS 33.501 HMAC-SHA-256 inputs used by the staged OAI UE tests.

Verified vectors:

```
baseline SNN:
5G:mnc015.mcc234.3gppnetwork.org

SNPN:
5G:mnc099.mcc999.3gppnetwork.org:10000000001

mismatched SNPN:
5G:mnc099.mcc999.3gppnetwork.org:10000000002
```

It checks exact `RES*`, `K_AUSF`, and `K_SEAF` bytes and confirms that changing only the NID changes all three outputs.

Success marker:

```
PASS_V5G_SNPN_KDF_REFERENCE
```

The checker also audits the exact OAI KDF input lengths for the HARP SNN:

```
SNN                  44 bytes
RES* KDF input       75 bytes
K_AUSF KDF input     55 bytes
K_SEAF KDF input     47 bytes
```

All fit within the existing 100-byte OAI KDF work buffers.

Patch `0002` was hardened so each KDF caller passes its actual remaining buffer size directly to `nr_format_serving_network_name()`; the old static fixed-size wrapper is removed.


## Standalone suite

All standalone helpers can be compiled and executed without a full OAI checkout:

```bash
bash tools/oai_snpn/run_standalone_suite.sh
```

Expected final marker:

```
PASS_HARP_SNPN_STANDALONE_SUITE
```

The suite covers NID44 encode/decode, UE SNN formatting, AMF SNN formatting, UE↔AMF canonical SNN equality, and the independent KDF reference vectors.


## Docker pre-RFsim runner

For a Linux host with Docker and Internet access, the OAI dependency/build
environment can be reproduced from the pinned upstream Dockerfile:

```bash
bash tools/oai_snpn/run_pre_rfsim_in_docker.sh /path/to/openairinterface5g
```

The runner:

1. requires the pinned OAI commit to be present locally;
2. creates a self-contained disposable local clone at that commit;
3. builds the upstream `docker/Dockerfile.base.ubuntu` image (Ubuntu 24.04);
4. explicitly maps host architecture to `amd64` or `arm64`;
5. explicitly sets `HARP_ASN1C_EXEC=/opt/asn1c/bin/asn1c`;
6. mounts the disposable clone read/write and HARP read-only;
7. runs the focused pre-RFsim gate suite inside the container.

Expected final marker:

```
PASS_HARP_OAI_SNPN_DOCKER_PRE_RFSIM
```

The disposable clone avoids modifying the user's primary OAI checkout.
The Docker image build requires network access because the official OAI
dependency image installs packages and source dependencies.
