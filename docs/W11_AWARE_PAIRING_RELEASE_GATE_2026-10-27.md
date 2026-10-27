# HARP W11 — Wi-Fi Aware peer pairing and Stage-2A release gates (2026-10-27)

**Evidence class:** verified Android API documentation + read-only review of the actual HARP `main` source at commit `97a6e5658b11f33faa095025d04773248bac59e2`. **No APK build, Android phone installation, physical NDP, HTTPS egress, RFsim/PC5 test, network scan, or partner agreement was executed.**

## Decision: stop adding access architectures; fix the cooperative-relay identity boundary

W10 established the shortest direct-access Wi-Fi pilot. W11 addresses a different existing branch: **HARP L2 A↔B relay**. Read-only inspection of `app/src/main/java/org/harp/l2/HarpAwareController.java` found:

```java
private static final String SERVICE = "harp-relay-v1";
private static final String PSK = "harp-lab-2026";
```

Both the legacy NDP and the API 33+ security path use this shared, source-embedded PSK. The discovery name is likewise public. Stage-2 generates ephemeral SOCKS credentials, but distributes them over the already established Aware NDP. Therefore **ephemeral SOCKS credentials do not cure an unauthenticated/compromised initial peer identity**. A malicious device that knows the published lab PSK may be able to participate as a peer if the radio/firmware and application permit it; do not claim a tested exploit or that encryption is absent. The PoC is **LAB ONLY**, not ready for an open volunteer relay network.

Source:
- https://github.com/digitalconnectdr/HARP/blob/main/app/src/main/java/org/harp/l2/HarpAwareController.java
- https://github.com/digitalconnectdr/HARP/blob/main/app/src/main/java/org/harp/l2/AwareSecurityApi33.java
- https://github.com/digitalconnectdr/HARP/blob/main/app/src/main/java/org/harp/l2/Stage2ControlProtocol.java

## New platform option: Android 14 / API 34 Wi-Fi Aware Pairing

Android **API 34** introduced:
- `Characteristics.isAwarePairingSupported()`;
- `Characteristics.getSupportedPairingCipherSuites()`, with `NCS_PK_PASN_128/256`;
- `DiscoverySession.initiatePairingRequest(...)`, `acceptPairingRequest(...)`, and pairing result callbacks;
- optional bootstrapping and pairing-cache features.

These are public SDK APIs, not an OEM guarantee. Android version >=34 alone does **not** establish chipset/HAL/firmware support. The existing HARP `AwareSecurityApi33` only selects `NCS_SK_256/128` for shared-key NDP, not the API 34 peer-pairing flow. **Do not replace the lab PSK with opportunistic pairing and call it authenticated:** Android documents that a null/empty password requests opportunistic pairing; user-confirmed password/out-of-band verification is needed for a meaningful trusted-peer gate.

Official sources:
- https://developer.android.com/reference/android/net/wifi/aware/Characteristics
- https://developer.android.com/reference/android/net/wifi/aware/DiscoverySession
- https://developer.android.com/reference/android/net/wifi/aware/WifiAwareNetworkSpecifier.Builder
- https://developer.android.com/reference/android/net/wifi/aware/AwarePairingConfig

### Falsifiable compatibility gates (not executed)

| Gate | Required evidence | Decision |
|---|---|---|
| A34-0 | Both A and B API >=34, Wi-Fi Aware available | Else keep controlled lab PSK only |
| A34-1 | On **both devices** `isAwarePairingSupported()==true` and compatible `getSupportedPairingCipherSuites()` | Else no claim of native pairing |
| A34-2 | Explicit B opt-in and authenticated peer confirmation via shared password/out-of-band verification; reject opportunistic-only mode for trust | Else no public relay |
| A34-3 | A and B both observe pairing success; NDP associated to the **verified** peer; negative test with wrong credential/rogue peer | Else no security PASS |
| A34-4 | Stage-0/1/2 with the verified NDP, then normal-app HTTPS on A and egress on B, with cellular data off on A | Else no Internet PASS |
| A34-5 | B has explicit consent for **metered** upstream, quotas, revocation and expiry; network loss closes the relay | Else no commercial relay |

Do not implement API 34 pairing unconditionally or buy hardware before the capability gates. Android 13 and earlier need a separately designed consented, out-of-band per-session PSK/PMK exchange; do not rely on the hardcoded lab key.

## Second audited boundary: IPv6 is blocked by default in the current IPv4-only VPN

`HarpVpnService` sets `addAddress(198.18.0.1/32)`, `addRoute(0.0.0.0/0)`, `addDnsServer(198.18.0.2)`, and **does not** call `allowFamily(AF_INET6)`, add an IPv6 route/address, or call `allowBypass()`. Android `VpnService.Builder.allowFamily` documentation states that an address family without any VPN address, route, or DNS server is **blocked by default**. Therefore the correct expected Stage-2A policy is **IPv4 TCP web via B; IPv6 blocked**, **not** an asserted IPv6 leak. It still needs device-level negative tests. Adding `allowFamily(AF_INET6)` without an IPv6 tunnel route could cause underlying-network fall-through and is **not** a safe quick fix.

`MiniSocks5` implements SOCKS5 CONNECT, and `SocksPolicies.publicWeb()` allows public destinations only on TCP 80/443. `Stage2TunnelConfig` requests HEV `socks5.udp: 'tcp'`, but the Java relay does not implement that UDP-over-TCP extension. General UDP/QUIC/HTTP3 and full dual-stack Internet are **not** demonstrated. This is a deliberate Stage-2A TCP-first scope, not a generic Internet service.

Sources:
- https://github.com/digitalconnectdr/HARP/blob/main/app/src/main/java/org/harp/l2/HarpVpnService.java
- https://github.com/digitalconnectdr/HARP/blob/main/app/src/main/java/org/harp/l2/MiniSocks5.java
- https://github.com/digitalconnectdr/HARP/blob/main/app/src/main/java/org/harp/l2/SocksPolicies.java
- https://github.com/digitalconnectdr/HARP/blob/main/app/src/main/java/org/harp/l2/Stage2TunnelConfig.java
- https://developer.android.com/reference/android/net/VpnService.Builder
- https://github.com/heiher/hev-socks5-tunnel/blob/main/conf/main.yml

## Hard decisions

1. **LAB GO / PUBLIC NO-GO** for the current L2 relay. Do not expose a shared lab PSK as production security.
2. **W10 P0 remains the shortest first IP proof**: authorized isolated guest Wi-Fi, A with mobile data off, real DNS/HTTPS and gateway egress; no CAPPORT/OWE/DPP prerequisite.
3. **Native Aware pairing is a conditional improvement** for consenting, HARP-equipped A and B; it does **not** allow an unknown stock B without HARP to forward traffic.
4. **5G ProSe/SNPN remains a managed/provisioned research track** with no real RFsim registration/PC5 IP PASS. eSIM is credential provisioning, not free Internet. Community mesh and volunteer hotspots still require consenting backhaul.
5. **No price claim**: cost of B's metered data + battery + incentive + support must be measured/contracted. If B uses mobile data, A's lack of subscription does not eliminate the network operator's cost.

## Next executable gates (without GitHub Actions)

- Controlled **read-only device capability** capture after an authorized local Android build; record API level, `FEATURE_WIFI_AWARE`, pairing support and cipher suites **without** logging credentials or device identifiers. No device test is claimed today.
- On a consented lab pair, validate correct-peer and wrong-peer pairing **before** replacing the static PSK; keep the current lab preflight unchanged until compatibility is proven.
- Execute existing `tools/run_pure_java_selftest.sh` and `tools/oai_snpn/run_standalone_suite.sh` on a local checkout, then the existing Android build/RFsim runbooks when the prerequisites exist. **Not executed in this run.**
- For W10 P0, prioritize signed venue/ISP authorization and real field evidence over more documentary architectures.

**No GitHub Actions were created/triggered, no phones were installed, no field network was accessed, and no live HARP connectivity was demonstrated.**
