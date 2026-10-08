# HARP Research — Stock-phone relay surfaces and the “B without HARP” boundary

**Research date:** 2026-10-07  
**Scope:** determine whether Phone A can obtain Internet through a nearby ordinary Android Phone B when B does not run HARP, and separate local peer connectivity from actual upstream forwarding.

## 1. Executive conclusion

On current public Android APIs, there is no general mechanism by which an ordinary application on Phone A can transmit a packet or instruction to an arbitrary stock Phone B and thereby cause B to become an Internet relay.

The missing primitive is not radio reachability. It is an authorized forwarding function on B.

A packet received by B is useful for Internet relaying only if some component already running on B is authorized and implemented to:

1. receive or accept the local connection;
2. interpret the request;
3. open or forward traffic onto an upstream network;
4. return the response to A.

This component can be:

- Android's stock tethering stack, when enabled/authorized;
- a HARP relay process;
- an OEM/system service;
- a carrier-integrated service such as standardized 5G ProSe UE-to-Network Relay;
- another pre-existing service explicitly designed for forwarding.

The packet itself cannot install this forwarding behavior merely by containing instructions.

## 2. Decision matrix

| Mechanism | Local A↔B link | Internet through B | HARP on B required | Can ordinary app on A silently enable B as relay? | Project role |
|---|---|---|---|---|---|
| Wi-Fi Aware | Yes | Not by framework itself | Yes for current HARP relay | No | Best current PoC transport |
| Wi-Fi Direct / P2P | Yes | B may retain its own uplink, but P2P API does not provide generic NAT/relay for A | A forwarding component still needed | No | Alternate peer transport |
| LocalOnlyHotspot | Yes | **No** by definition | No for local association | No Internet to relay | Reject as upstream solution |
| Android Wi-Fi tethering | Yes | Yes | No HARP needed once stock tethering is running | Not generally from an unprivileged third-party app; Wi-Fi tethering configuration requires privileged permission and provisioning may run | Viable only with user/system/carrier cooperation |
| Android VpnService on A + HARP on B | Yes | Yes, through B | Yes | N/A | Current Stage-2A PoC |
| 5G ProSe UE-to-Network Relay | PC5 sidelink | Yes by standardized cellular relay | Not necessarily as an Android app | No public ordinary-app control path identified; requires network/OEM/modem authorization and support | Closest standards-native final architecture |
| “Instructions embedded in data” sent to arbitrary B | Radio-dependent | No by itself | Some pre-existing interpreter/forwarder required | No | Not a viable standalone primitive |

## 3. Android Wi-Fi tethering: useful boundary, but privileged

Android API level 36 exposes `TetheringManager`, whose purpose is to start/stop tethering and whose global connectivity scope explicitly covers connectivity beyond the device, including Internet access.

This is significant because Android now exposes the stock relay concept in public API documentation.

However, the Wi-Fi path has an important privilege boundary:

- a non-system caller using `TETHERING_WIFI` must supply a `SoftApConfiguration`;
- `TetheringRequest.Builder.setSoftApConfiguration()` requires `android.permission.TETHER_PRIVILEGED`;
- `startTethering()` may also perform tethering provisioning and can fail if provisioning fails.

Therefore, a normal Play-style application cannot be assumed to silently activate arbitrary stock-phone Wi-Fi tethering.

Official sources:

- https://developer.android.com/reference/android/net/TetheringManager
- https://developer.android.com/reference/android/net/TetheringManager.TetheringRequest.Builder
- https://developer.android.com/reference/android/net/wifi/SoftApConfiguration

### HARP implication

Stock tethering is a valid **B-without-HARP forwarding engine only when it already has user/system/carrier authorization**.

That produces a distinct product path:

```
B user/system enables stock tethering
        |
        v
A joins B hotspot
        |
        v
Android tethering/NAT on B
        |
        v
B upstream -> Internet
```

This is technically real but is not the autonomous HARP behavior we are trying to discover.

## 4. LocalOnlyHotspot: explicitly not Internet

Android's LocalOnlyHotspot exists to let devices/apps communicate through a locally created Wi-Fi network.

Android's documentation explicitly states that the network created by this method **will not have Internet access**.

Source:

- https://developer.android.com/develop/connectivity/wifi/localonlyhotspot

### HARP implication

LocalOnlyHotspot can replace or supplement an A↔B local transport in some experiments, but it cannot replace the B forwarding function.

Do not treat “hotspot successfully created” as evidence of Internet relay capability.

## 5. Wi-Fi Direct: peer connectivity is not generic tethering

Android's Wi-Fi P2P API allows an application to:

- discover peers;
- create a P2P connection;
- communicate with peer applications/services.

Android also notes that a device participating in a P2P connection can continue to maintain its own mobile or other Internet uplink.

That means B can simultaneously possess:

```
A <-> B over P2P
B <-> Internet over another network
```

but that fact alone does not give A an IP forwarding/NAT path through B. A forwarding service still has to run on B.

Official sources:

- https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pManager
- https://developer.android.com/develop/connectivity/wifi/wifip2p
- https://developer.android.com/develop/connectivity/wifi/nsd-wifi-direct

### HARP implication

Wi-Fi Direct is an alternate transport candidate, not the missing relay primitive.

It may be worth benchmarking later against Wi-Fi Aware for:

- device coverage;
- range;
- discovery reliability;
- power;
- background behavior;
- simultaneous uplink behavior.

It does not remove the need for B-side forwarding code or a system relay.

## 6. Wi-Fi Aware API 37 changes do not remove the B-side requirement

Android 17 / API 37 adds explicit application APIs for Wi-Fi Aware data-path negotiation, including:

- `AwareDataPathRequest`;
- subscriber-initiated data-path requests;
- publisher-side `acceptDataPathRequest()`;
- callbacks for connected/failed data paths.

Official sources:

- https://developer.android.com/reference/android/net/wifi/aware/AwareDataPathRequest
- https://developer.android.com/reference/android/net/wifi/aware/PublishDiscoverySession
- https://developer.android.com/reference/android/net/wifi/aware/SubscribeDiscoverySession

This improves how two participating applications negotiate an Aware NDP. It does **not** make an arbitrary nearby stock Android phone accept application traffic and forward it to Internet.

### HARP implication

Keep Wi-Fi Aware as the current laboratory transport. API 37 may simplify a later implementation, but it does not solve “B without HARP”.

## 7. Why “send instructions in the outbound data and have B return Internet data” fails on arbitrary B

The idea can be represented as:

```
A -> B:
  [destination = example.com:443]
  [payload = ...]
  [instruction = forward this to Internet]
```

The radio can deliver those bytes only through a protocol/service that B already accepts.

After reception, one of two cases exists:

### Case 1 — B has a compatible relay/interpreter

Then the idea works.

This is exactly what HARP's current `Stage2RelayServer` does:

```
HARP protocol on A
    -> Aware NDP
    -> HARP relay on B
    -> B validated upstream
    -> Internet
```

### Case 2 — B has no compatible relay/interpreter

Then the payload is inert.

The Android networking stack does not infer arbitrary application semantics such as:

> “These bytes contain an instruction telling me to originate a new Internet connection for another device.”

A pre-existing service must own the receiving socket/protocol and possess the authority to forward.

### General rule

```
Transport capability
!=
remote execution capability
!=
Internet forwarding authority
```

This is the cleanest boundary for all future HARP designs.

## 8. Cellular-native path: ProSe remains the closest architectural match

3GPP 5G ProSe defines UE-to-Network relaying rather than merely peer messaging.

Release-19 work adds multi-hop UE-to-Network relaying and corresponding authorization/protocol support.

Current 3GPP change records explicitly include:

- multi-hop UE-to-network relay selection;
- direct-link establishment;
- authorization information for Layer-2 and Layer-3 multi-hop UE-to-Network roles.

Sources:

- https://portal.3gpp.org/DesktopModules/CRs/CrDetails.aspx?CrId=556504
- https://portal.3gpp.org/DesktopModules/CRs/CrDetails.aspx?CrId=556501
- https://portal.3gpp.org/DesktopModules/CRs/CrDetails.aspx?CrId=565896
- https://portal.3gpp.org/ChangeRequests.aspx?q=1&specnumber=23.304

### HARP implication

ProSe is strategically different from Wi-Fi Aware/P2P because the relay function is part of the cellular architecture.

But it is not an unpermissioned mechanism for commandeering another subscriber's phone:

- relay/remote roles are policy/authorization controlled;
- modem/OEM support is required;
- network support is required;
- no public Android application API has been identified that lets a normal app configure the complete ProSe relay role.

Therefore the ProSe branch should remain an OEM/modem/network research track.

## 9. Updated architecture map

There are now three distinct HARP product paths.

### Path A — HARP-controlled application relay

```
A: HARP/VPN
   |
   | Wi-Fi Aware or P2P
   v
B: HARP relay
   |
   v
Internet
```

Pros:

- controllable now;
- can be prototyped with retail Android;
- proves routing, security, policy and UX.

Cons:

- B must participate with HARP;
- B must have an upstream.

### Path B — pre-existing stock/OEM relay

```
A
 |
 v
B stock tethering / OEM relay
 |
 v
Internet
```

Pros:

- HARP code need not run on B if a suitable forwarding service already exists.

Cons:

- user/system/carrier must activate/authorize the service;
- ordinary A cannot assume permission to enable it remotely.

### Path C — standards-native cellular relay

```
A = ProSe Remote UE
 |
 | PC5
 v
B = ProSe UE-to-Network Relay
 |
 | Uu / PDU session
 v
5G network -> Internet
```

Pros:

- architecture directly matches indirect cellular network access;
- relay semantics are standardized.

Cons:

- feature-level device/modem/OEM/network support;
- network authorization/provisioning;
- current Android public API gap.

## 10. Project decision

Do **not** continue searching for a magic packet format that causes an arbitrary stock Phone B to forward Internet traffic.

Continue two branches in parallel:

1. **PoC branch:** finish and physically validate the current Wi-Fi Aware + authenticated relay + VpnService path.
2. **final-architecture branch:** investigate already-authorized system/OEM/carrier relay mechanisms, especially 5G ProSe and SNPN/Credentials Holder integration.

Wi-Fi Direct and newer Wi-Fi Aware APIs can be evaluated as alternative local transports, but they should not be treated as solutions to the forwarding-authority problem.

## 11. Phone-installation decision

This research does **not** require installing anything on the two phones yet.

The next two-phone installation remains gated on a locally compiled HARP debug APK.

Until that APK exists, useful work can continue in:

- static Android source review;
- relay security/lifecycle review;
- OAI RFSimulator virtual 5G work;
- SNPN/ProSe source research;
- hardware/modem feature qualification.
