# OAI patch staging

These patches are staged against:

```
openairinterface/openairinterface5g
commit f8f769592a7030be88ede4bb5ca66fa1ca6a80e0
```

They are research patches stored in HARP so the work does not depend on GitHub Actions.

## Order

```
0001-nr-ue-serving-network-baseline-refactor.patch
0002-nr-ue-snpn-serving-network-name.patch
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

Do not apply patch 0002 until ordinary PLMN registration/KDF regression tests pass.

### Patch 0002

Purpose:

- format SNPN SNN when `has_nid=true`;
- enforce a 44-bit maximum;
- render exactly 11 uppercase hexadecimal NID digits.

It does not populate NID from SIB1.

Gate:

```
PASS_V5G_SNPN_SNN
```

## Validation policy

These patch files are source-staged but have not yet been compiled against a local OAI checkout in the current environment.

Required validation after an OAI checkout/build environment exists:

```
git checkout f8f769592a7030be88ede4bb5ca66fa1ca6a80e0
git apply --check < 0001...
git apply < 0001...
build/tests
baseline KDF comparison
git apply --check < 0002...
git apply < 0002...
SNN vectors
```

Do not claim either OAI gate from the existence of these patch files alone.

## Later patches

Still intentionally deferred:

- SIB1 NID population in gNB;
- nrUE `npn-IdentityInfoList-r16` decode;
- storing selected NID into `serving_network`;
- AMF SNPN SNN;
- KDF positive/negative lab;
- NGAP/F1AP NID propagation.
