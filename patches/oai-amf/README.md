# OAI AMF patch staging

Anchor:

```
openairinterface/oai-cn5g-amf
commit 5eedea557a3745b13ed9ec4bf29e6a28bd912574
```

Current staged patch:

```
0001-amf-optional-snpn-snn-formatter.patch
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
