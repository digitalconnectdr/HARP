# HARP W14 — HEV UDP-over-TCP vs CONNECT-only B: transport contract gate (2026-10-30)

**Evidence level:** read-only inspection of five real `main` Java files at the blob IDs below; IETF RFC 1928/1929, HEV upstream configuration, Android callback and Java Socket specifications; offline **synthetic** static-auditor self-test. **No APK build, phone installation, network packet capture, real UDP, Internet, operator contract, PC5, RFsim or billing test.**

## 1. New precise blocker beyond W11/W13

`Stage2TunnelConfig.java` generates HEV config `socks5.udp: 'tcp'`. HEV's official documentation defines this as **UDP-in-TCP**, not as an instruction to convert arbitrary UDP to normal SOCKS5 CONNECT. On B, `MiniSocks5.java` implements only `CMD_CONNECT = 1` and rejects any other SOCKS command with `REP=0x07`. RFC 1928 defines `UDP ASSOCIATE = 3`. Thus, the current HEV UDP relay configuration and B's SOCKS command surface **do not match**; there is no demonstrated end-to-end UDP relay. This is a **source-level protocol contract failure**, not an observed Android packet failure.

**Nuance:** `Stage2TunnelConfig.java` also enables HEV `mapdns` and supplies a mapped DNS IP to the Android VPN. HEV may answer DNS queries locally with synthetic mapped addresses and then issue SOCKS CONNECT by hostname, so **do not claim that all DNS necessarily fails**. Normal TCP HTTPS may still be viable. Native UDP/QUIC/HTTP3 and arbitrary UDP apps are **not proven**. The `SocksPolicies.publicWeb()` rule also restricts destination ports to TCP 80/443. The VPN routes IPv4 only; Android's unspecified IPv6 family remains blocked by default, as documented in W11.

This matters because the product target is **useful Internet**, not merely a positive Stage-2 proxy handshake. A browser loading a TCP HTTPS page does not demonstrate full IP or QUIC support.

## 2. Source evidence inspected 2026-10-30

| Source | Git blob | Observed condition |
|---|---|---|
| `Stage2TunnelConfig.java` | `62b7934252acd0e49b33887a71551b4c79e2f7c1` | `udp: 'tcp'`, `mapdns:` |
| `MiniSocks5.java` | `696d3e44cdc9ab05ef404889b639e1b62999d0f8` | `CMD_CONNECT=1`; non-CONNECT rejected `0x07` |
| `SocksPolicies.java` | `35a85fc66279f0f802fe6501322c41f0d47afe1a` | publicWeb only 80/443 |
| `HarpVpnService.java` | `442a1663dff9404e041402e67b57cffe22bcdab7` | IPv4 default route, no IPv6 default route |
| `Stage2RelayServer.java` | `b43288e042ffc2ac866009145f12b6bd18817bd2` | W13 quota not integrated; tracks accepted clients, not outbound sockets |

The `Stage2RelayServer` accepted-client-only socket tracking raises a **revocation/lifecycle risk**: explicit tracking and closure of both ends are needed before a public release. It does **not** establish that real sockets leaked during any run.

## 3. Standards / official implementation sources

- IETF [RFC 1928](https://www.rfc-editor.org/rfc/rfc1928.html), section 4: CONNECT 0x01, UDP ASSOCIATE 0x03, command-not-supported 0x07.
- IETF [RFC 1929](https://www.rfc-editor.org/rfc/rfc1929.html): SOCKS username/password is sent in cleartext **inside its transport**; W11's fixed shared NDP lab PSK is not an adequate public identity design.
- HEV upstream [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) and [configuration](https://github.com/heiher/hev-socks5-tunnel/blob/main/conf/main.yml): `socks5.udp` has `tcp|udp` relay modes; mapdns is a separate feature.
- Android [ConnectivityManager.NetworkCallback](https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback): callback state must be used rather than stale synchronous capability snapshots.
- Java [Socket.close()/shutdownOutput()](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/Socket.html): explicit close and half-close semantics.

## 4. New offline artifact and limits

`tools/w14_transport_contract_audit.py` is a **read-only** static source checker for this exact cross-file contract. It emits JSON with `STATIC_SOURCE_REVIEW_ONLY` and flags:
- `HEV_UDP_OVER_TCP_WITH_CONNECT_ONLY_SOCKS_SERVER`
- `W13_PAYLOAD_GATE_NOT_INTEGRATED`
- `VPN_IPV4_ONLY`

The synthetic self-test passed `PASS_HARP_W14_STATIC_CONTRACT_AUDIT_SELFTEST` locally; separately the five current GitHub source files were fetched and checked for these patterns. A pattern disappearing **does not** prove functional UDP. `--strict-release` exits 2 if a blocker is present; it is an offline developer gate, **not GitHub Actions**.

## 5. Falsifiable next experiments

| Gate | Evidence to collect | Failure/stop |
|---|---|---|
| W14-P0 | Independent authorized guest Wi-Fi A, data OFF, real DNS+HTTPS+gateway egress | No written venue/ISP rights or no actual egress |
| W14-G1 | JDK-only local SOCKS server handshake with authenticated CONNECT and UDP ASSOCIATE request, observe 0x07 for unsupported UDP | Any claim of UDP PASS based only on HEV YAML |
| W14-G2 | Local HEV tunnel+server reference pairing: TCP HTTPS vs UDP datagrams, mapped DNS behavior | No paired build or no packet evidence |
| W14-G3 | B relay tracks and closes **both** client/upstream sockets on revocation; both directions enforce W13 byte quota | Unaccounted bytes or unclosed sockets |
| W14-G4 | Controlled A/B hardware: TCP web, DNS, QUIC, IPv6 and app-specific traffic separately | Labeling TCP-only as full Internet |
| W14-G5 | Compare ISP-approved backhaul price and measured user-hours; do not infer carrier bill from payload counter | No operator rights/price/measurements |

**Implementation decision:** do **not** change `udp: 'tcp'` to `udp: 'udp'` as a supposed fix: B does not implement UDP ASSOCIATE in either case. For an initial *TCP-only* controlled demonstration, clearly mark UDP unsupported; for a general-Internet release, implement and test the correct UDP negotiation/transport, quotas, permissions, and socket cancellation, or choose an existing compatible, audited server under explicit upstream authorization.

## 6. Architecture boundary and priorities

No new general mechanism exists to use unknown nonconsenting B without HARP as a relay. Voluntary B needs W11 identity plus W12/W13 consent and quota enforcement. W4 community routers and W6–W10 guest Wi-Fi require funded backhaul. 5G ProSe/SNPN requires provisioned UEs and operator/RAN support; eSIM provisions credentials and does not itself provide RF or free Internet. None of these paths has a new physical PASS.

**Current status:** W14 transport mismatch **SOURCE-CONFIRMED**, static audit self-test **PASS (synthetic)**, Android integration and physical tests **NOT DONE**, public relay **NO-GO**. No Actions, CI, installs, external probes or hardware spend.
