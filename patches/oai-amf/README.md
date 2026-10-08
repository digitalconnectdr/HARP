# OAI AMF patch staging

Anchor:

```
openairinterface/oai-cn5g-amf
commit 5eedea557a3745b13ed9ec4bf29e6a28bd912574
```

These files are context blueprints rather than direct `git apply` unified diffs while the research branch evolves.

Apply/check them with:

```bash
tools/oai_amf_snpn/apply_amf_blueprints.sh /path/to/oai-cn5g-amf --check
tools/oai_amf_snpn/apply_amf_blueprints.sh /path/to/oai-cn5g-amf
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
