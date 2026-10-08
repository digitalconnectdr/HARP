# HARP — OAI AMF/AUSF SNPN SNN integration map

**Date:** 2026-10-08

## Source anchor

```
openairinterface/oai-cn5g-amf
develop
5eedea557a3745b13ed9ec4bf29e6a28bd912574
```

## Current SNN lifecycle

AMF currently builds:

```
get_serving_network_name(mnc, mcc)
        |
        v
snn
        |
        +-> nc->serving_network
        |
        +-> registration_request_handle(...)
```

The NAS context therefore already has a single string field that acts as the serving-network identity used by authentication.

## External AUSF mode

When:

```
enable_simple_scenario == false
```

AMF calls:

```
get_authentication_vectors_from_ausf(nc)
```

and sends:

```
AuthenticationInfo.servingNetworkName = nc->serving_network
```

Therefore for a real HARP Credentials Holder/AUSF architecture, the SNPN NID must already be present in the SNN before this call.

Expected HARP lab value:

```
5G:mnc099.mcc999.3gppnetwork.org:10000000001
```

In this mode the AMF is primarily transporting the SNN into the AUSF authentication request; it is not the component that derives K_AUSF.

## Simple-scenario mode

When:

```
enable_simple_scenario == true
```

OAI emulates UDM/AUSF locally inside the AMF process.

Path:

```
auth_vectors_generator()
   |
   +-> authentication_vectors_generator_in_udm(nc)
   |     -> generate_5g_he_av_in_udm(..., nc->serving_network, ...)
   |
   +-> authentication_vectors_generator_in_ausf(nc)
         -> Authentication_5gaka::derive_kseaf(
                nc->serving_network,
                kausf,
                kseaf)
```

This makes the simple scenario a useful low-cost gate for HARP before deploying separate AUSF/UDM services.

## Recommended validation ladder

### AMF formatter

```
PASS_AMF_SNN_VECTORS
```

### Cross-language identity

```
PASS_UE_AMF_SNN_MATCH
```

### Simple-scenario positive

```
UE NID  = 10000000001
AMF SNN = ...:10000000001
```

Expected:

- same SNN on both sides;
- authentication reaches expected positive state.

Gate:

```
PASS_V5G_SNPN_KDF_LAB
```

### Simple-scenario negative

Change only AMF-side NID:

```
UE NID  = 10000000001
AMF NID = 10000000002
```

Expected:

- SNN differs;
- RES*/KDF path must not authenticate successfully.

Negative marker:

```
PASS_V5G_SNPN_KDF_NEGATIVE
```

The negative case is essential because a positive-only test can accidentally pass while the NID is merely logged but not cryptographically consumed.

## Credentials Holder implication

The external AUSF mode maps directly to the longer-term HARP Credentials Holder concept:

```
HARP-selected SNPN identity
       |
       v
AMF nc->serving_network
       |
       v
AuthenticationInfo.servingNetworkName
       |
       v
AUSF / Credentials Holder integration
       |
       v
authentication vectors / key hierarchy
```

The AMF does not need to own the subscriber credential database for this architecture. It must, however, forward the exact canonical SNN containing the selected NID.

## Implementation consequence

The next AMF lab patch should add an optional configured NID at the point where the initial SNN is constructed, before the NAS context stores `nc->serving_network`.

Do not patch only the local `derive_kseaf()` call: that would make the simple scenario work while leaving the real external-AUSF path incorrect.
