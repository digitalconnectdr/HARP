# OAI AMF patch staging

Anchor:

```
openairinterface/oai-cn5g-amf
commit 5eedea557a3745b13ed9ec4bf29e6a28bd912574
```

These files are context blueprints rather than direct `git apply` unified diffs while the research branch evolves.

Apply/check them with:

```bash
bash tools/oai_amf_snpn/apply_amf_blueprints.sh /path/to/oai-cn5g-amf --check
bash tools/oai_amf_snpn/apply_amf_blueprints.sh /path/to/oai-cn5g-amf
```

The applicator requires exact unique anchors and runs `git diff --check` after writing.

Current staged blueprints:

```
0001-amf-optional-snpn-snn-formatter.patch
0002-amf-lab-snpn-nid-config.patch
```

Purpose:

- preserve current PLMN-only `get_serving_network_name(mnc, mcc)`;
- add an overload accepting a 44-bit NID;
- render exactly 11 uppercase hexadecimal digits;
- reject values larger than 44 bits.

This patch intentionally does **not** change existing AMF call sites yet.

That separation is deliberate:

```
formatter support
  -> unit vectors
  -> optional lab configuration
  -> positive/negative KDF lab
  -> standards-compliant NID transport later
```

No AMF authentication gate should be claimed until UE and AMF derive with the same explicit serving-network identity and the mismatched-NID negative test fails authentication as expected.


## Patch 0002 — lab NID source

Adds optional:

```yaml
amf:
  snpn_nid: "10000000001"
```

Rules:

- absent/empty -> ordinary PLMN behavior;
- exactly 11 hexadecimal digits when present;
- parsed value must fit in 44 bits;
- both current SNN construction sites in `amf_n1.cpp` use the configured NID.

This is intentionally a lab bridge. A production/standards-compliant implementation must derive the selected NID from serving-network context/NGAP rather than a global AMF setting.

The location is chosen deliberately before `nc->serving_network` is stored, so the same SNN feeds:

- external AUSF through `AuthenticationInfo.servingNetworkName`;
- local simple-scenario UDM/AUSF emulation;
- local `derive_kseaf()`.


Runtime evidence added to both AMF SNN construction sites:

```
HARP_SNPN_AMF_SNN 5G:mnc099.mcc999.3gppnetwork.org:10000000001
```

This log must appear before the authentication path is considered aligned with the UE. It is an observability marker only; it does not itself prove successful SNPN authentication.


## Local AMF build gate

Runner:

```
bash tools/oai_amf_snpn/run_amf_build_gate.sh /path/to/oai-cn5g-amf
```

Requirements:

- checkout exactly at `5eedea557a3745b13ed9ec4bf29e6a28bd912574`;
- clean worktree;
- initialized `src/common-src` and `build/common-build`;
- AMF native build dependencies installed;
- no GitHub Actions.

It validates/applies the HARP AMF blueprints, configures the upstream AMF CMake tree in Release mode and builds only the `amf` target.

Success marker:

```
PASS_HARP_AMF_SNPN_BUILD
```

This proves compilation/link closure only. It does not prove startup, registration or authentication.


## Explicit SNPN authentication markers

The staged AMF patch now emits exact markers at the real authentication branches:

Successful RES* validation:

```
PASS_V5G_SNPN_AUTH_AMF nid=10000000001
```

Completed registration:

```
PASS_V5G_SNPN_REGISTERED_AMF nid=10000000001
```

Authentication rejection:

```
HARP_SNPN_AUTH_REJECT_AMF nid=10000000002
```

The KDF lab gate is intentionally tied to the authentication marker, not to Registration Complete:

```
PASS_V5G_SNPN_AUTH_AMF
        ->
PASS_V5G_SNPN_KDF_LAB
```

Full registration is a later optional gate:

```
PASS_V5G_SNPN_REGISTERED_AMF
        ->
PASS_V5G_SNPN_REGISTERED
```

This separation avoids conflating SNPN/KDF correctness with downstream SMF/PCF/UPF availability.


## Docker AMF build runner

For a Linux host with Docker and Internet access:

```bash
bash tools/oai_amf_snpn/run_amf_build_in_docker.sh /path/to/oai-cn5g-amf
```

Requirements:

- checkout exactly at `5eedea557a3745b13ed9ec4bf29e6a28bd912574`;
- clean checkout;
- submodules initialized at their pinned gitlink revisions.

The runner:

1. validates submodule state;
2. creates a disposable local clone;
3. applies the HARP AMF SNPN blueprints;
4. copies the already-pinned submodule contents into the disposable build tree
   without Git metadata;
5. builds the official upstream `oai-amf-builder` stage from
   `docker/Dockerfile.amf.ubuntu`;
6. verifies that the resulting `oai_amf` binary exists and is executable.

Expected final marker:

```
PASS_HARP_AMF_SNPN_DOCKER_BUILD
```

No GitHub Actions are used.
