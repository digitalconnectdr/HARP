# HARP — OAI staged patch static validation

**Date:** 2026-10-08

## nrUE patch anchor validation

Upstream:

```
openairinterface/openairinterface5g
f8f769592a7030be88ede4bb5ca66fa1ca6a80e0
```

Staged patch:

```
patches/oai/0001-nr-ue-serving-network-baseline-refactor.patch
```

All nine critical textual anchors used by the patch were re-checked against the pinned upstream commit and are present:

1. `plmn_id_t *sn_id;`
2. SIB1 `plmn_id_t` allocation
3. `nas->sn_id = plmn_id;`
4. `servingNetworkName(..., plmn_id_t *)`
5. `transferRES(..., plmn_id_t *)`
6. `derive_kausf(..., plmn_id_t *)`
7. `derive_kseaf(..., plmn_id_t *)`
8. NAS simulator `sn_id` allocation
9. NRPPa simulator `sn_id` allocation

Result:

```
PASS_OAI_PATCH_ANCHORS_9_OF_9
```

This does not replace `git apply --check` or compilation, but it proves the patch is not obviously stale relative to the pinned commit.

## AMF formatter validation

Upstream AMF anchor:

```
openairinterface/oai-cn5g-amf
5eedea557a3745b13ed9ec4bf29e6a28bd912574
```

The staged AMF formatter patch was checked against the current includes:

- `stdint.h` is already present in the header, so `uint64_t` is available;
- the staged implementation explicitly adds:
  - `<sstream>`
  - `<stdexcept>`
- `<iomanip>` is already present.

Standalone C++17 formatter vectors were compiled locally with:

```
-Wall -Wextra -Werror
```

and produced:

```
PASS_AMF_SNN_VECTORS
```

## UE/AMF cross-language invariant

The C reference formatter used for the nrUE branch and the C++ formatter used for the AMF branch were compiled independently.

For:

```
MCC = 999
MNC = 99
NID = 0x10000000001
```

both produced exactly:

```
5G:mnc099.mcc999.3gppnetwork.org:10000000001
```

Cross-check marker:

```
PASS_UE_AMF_SNN_MATCH
```

Repository runner:

```
tools/oai_amf_snpn/run_ue_amf_snn_crosscheck.sh
```

## Android fail-closed audit

Static review found that relay preflight errors could previously return while leaving B's NDP/listener registered.

Corrected behavior:

```
Stage0 peer/framing/error
Stage1 peer/no-upstream/proxy error
Stage2 control peer/ACK/error
NDP setup exception
upstream loss
Aware NDP loss
        |
        v
closeRelayTransport()
        |
        +-> unregister upstream callback
        +-> unregister Aware network callback
        +-> clear Aware interface metadata
        +-> close Stage2 relay or listener
        +-> clear server reference
```

Relevant commits:

```
8febedd44cfe01323931a5cfa904499d562e91ae
25de98cc0d48833f5bf7a2cd6a9a7a05e06e0cae
```

This remains static source validation until an APK is built and tested on physical devices.
