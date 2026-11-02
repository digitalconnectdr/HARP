# HARP W17 — Stage-2 outbound TCP revocation gate (2026-11-02)

**Evidence grade:** source from `digitalconnectdr/HARP` plus an **actual-source OpenJDK 21 loopback socket integration test**. This is a real *local TCP socket test*, **not** Android, Wi-Fi Aware, phone A/B, ISP Internet, RFsim, PC5, 5G or carrier billing. No GitHub Actions or hosted CI.

## 1. New falsifiable gap after W13/W14

Before W17, `Stage2RelayServer.close()` closed its listener and its **accepted client sockets**, but it did not retain references to the separate **outbound TCP sockets** returned by the selected Android `Network.getSocketFactory()` (passed through `MiniSocks5.serveOne`). The old relay could therefore return from `close()` while an already-connected outbound socket was still open. Closing the client alone does not provide a synchronous, explicit close of the other socket.

This is a **lifecycle and revocation gap** in the actual Java source, not evidence of an exploitable network path or of billable traffic after consent withdrawal.

Java 21 specifies that `Socket.close()` closes both streams and wakes threads blocked in I/O on that socket. A half-close via `shutdownOutput()` is not equivalent to closing the entire socket.

- Oracle Java 21 Socket: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/Socket.html
- IETF SOCKS5 CONNECT and UDP ASSOCIATE: https://www.rfc-editor.org/rfc/rfc1928
- Android connectivity capability callbacks: https://developer.android.com/training/monitoring-device-state/connectivity-status-type

## 2. Implemented narrow production fix

File: `app/src/main/java/org/harp/l2/Stage2RelayServer.java`

- Wrap the **same caller-selected** `SocketFactory` instead of replacing it with a default/system factory. This is important: Android `Network` binding is not intentionally changed.
- Track each outbound `Socket` created by the factory, separately from accepted client sockets.
- On `close()`, close both accepted sockets and all tracked outbound sockets before executor shutdown.
- Register the `createSocket()` result **before** `MiniSocks5` calls `connect()`, and double-check the closed flag to fail closed when stop races with socket registration.
- Remove already-closed connection candidates on subsequent registrations to avoid unbounded retention of failed attempts.
- Preserve the existing SOCKS5 wire format, DNS resolver, destination policy, authentication, port restrictions, transport and session-slot count.

**Scope caveat:** The other `SocketFactory` overloads delegate then track; only the zero-argument `createSocket()` path currently used by `MiniSocks5` is tracked *before* connect. The design does not promise a complete atomic cancellation of kernel/network operations or carrier charging. Android network-change callbacks are **not yet wired** to trigger revocation, and W13 byte quotas remain reference-only.

## 3. Actual-source local test: baseline versus patched

All six actual Java dependencies were fetched from the repository, reproduced locally with matching Git blob hashes, and compiled with OpenJDK 21. Test file: `tools/w17/Stage2OutboundCloseSelfTest.java`.

The test opens only `127.0.0.1` sockets and uses an explicitly **test-only** loopback destination policy; production `SocksPolicies.publicWeb()` is not weakened. It authenticates a SOCKS5 CONNECT session, sends one byte in **each direction**, captures the outbound socket returned by the injected factory, then stops the relay and checks `isClosed()` immediately.

| Source | Git blob | Local result |
|---|---|---|
| Original `Stage2RelayServer.java` | `b43288e042ffc2ac866009145f12b6bd18817bd2` | `PASS_HARP_W17_BASELINE_OUTBOUND_REMAINS_OPEN_AFTER_STOP` — 3/3 runs |
| Patched `Stage2RelayServer.java` | `3050b1f146e21c74a60f349daa761d23f72f02bb` | `PASS_HARP_W17_JDK_LOOPBACK_RELAY_OUTBOUND_CLOSED` — 10/10 runs |
| W17 test source | `13a43570dab5a22ae1f64ad6ef42314b2ab73705` | Compiled and executed locally |

The original baseline result means the outbound socket was still open **at the instant `close()` returned**. It does not establish how long it would have remained open or any traffic continuing afterward. The patched result confirms immediate explicit closure in these bounded loopback test cases; it does **not** establish all concurrency interleavings.

Reproduce on a machine with local JDK 17+ and a complete checkout (no Android SDK needed):

```sh
mkdir -p /tmp/harp-w17-classes
javac -d /tmp/harp-w17-classes \
  app/src/main/java/org/harp/l2/Stage2RelayServer.java \
  app/src/main/java/org/harp/l2/Stage2SessionCredentials.java \
  app/src/main/java/org/harp/l2/SocksDestinationPolicy.java \
  app/src/main/java/org/harp/l2/SocksAddressResolver.java \
  app/src/main/java/org/harp/l2/SocketPeerPolicy.java \
  app/src/main/java/org/harp/l2/MiniSocks5.java \
  tools/w17/Stage2OutboundCloseSelfTest.java
java -cp /tmp/harp-w17-classes org.harp.l2.Stage2OutboundCloseSelfTest
```

**Do not run `--expect-baseline-gap` against the patched source:** it is reserved for historical baseline comparison.

## 4. HARP viability and cost/backhaul decisions (unchanged, not new field evidence)

| Architecture | Current evidence | Required next gate |
|---|---|---|
| Authorized guest Wi-Fi for A (no mobile subscription/app) | Documented Android support; no physical HARP pilot | Written ISP/venue redistribution permission, isolated AP, A cellular OFF, real DNS + HTTPS + gateway egress, useful user-hours and cost |
| Voluntary B relay with HARP | W17 real JDK loopback lifecycle improvement; no A/B field proof | Android build, authenticated Aware pairing, informed metered/roaming consent, W13 quotas integrated, real two-phone traffic and negative tests |
| Unknown stock B without HARP/consent | No generic relay mechanism | **NO-GO** absent a legitimate OS/operator feature and explicit cooperation |
| Community mesh / sponsored hotspots | Architectural option, not deployed by HARP | Coverage survey, permission, site backhaul and capacity/energy budget |
| 5G ProSe/SNPN | Standards for provisioned UE-to-network relay, not a stock-phone demonstration | Compatible UEs, network authorization, OAI/RFsim auth positive/negative, separate PC5 data-plane proof |
| eSIM | GSMA remote profile provisioning, not a bearer/backhaul | Real operator profile + allowed service + compatible device, no free-Internet assumption |

Official cross-checks:
- ETSI TS 123 304 v19.6.0 (January 2026), 5G ProSe incl. UE-to-Network relay, service authorization and SNPN: https://www.etsi.org/deliver/etsi_ts/123300_123399/123304/19.06.00_60/
- GSMA SGP.22 (consumer eSIM remote provisioning): https://www.gsma.com/solutions-and-impact/technologies/esim/gsma_resources/sgp-22-v3-0/
- GSMA SGP.32 (IoT eSIM remote provisioning): https://www.gsma.com/solutions-and-impact/technologies/esim/gsma_resources/sgp-32-v1-3/

**Backhaul economics remain unverified.** Example only, **not a quote**: if approved fixed guest access costs RD$4,500/mo plus RD$700 equipment amortization and RD$500 power/ops, total is RD$5,700/mo. At 600 **measured successful** user-hours, RD$9.50 per user-hour; at 60 user-hours, RD$95. No actual provider price, hours or concurrent throughput has been observed. An authorized ISP and upstream capacity remain necessary even if A does not subscribe individually.

## 5. Remaining falsifiable gates and decision

1. **P0 — Internet useful to A:** Obtain authorized guest AP + backhaul; measure A with cellular OFF, DNS/HTTPS, egress, isolation, uptime, cost/user-hour. Not tested.
2. **W17-G1 — Android compilation:** Build locally with Android SDK/Gradle when available; no Actions. Not tested here (SDK absent).
3. **W17-G2 — stop and network transition:** On a controlled Android B, validate `onCapabilitiesChanged`/`onLost`, stop during connect/read/write, multiple clients, half-close, and no new outbound after stop. Not tested.
4. **W17-G3 — metered traffic:** Integrate W13 quotas in both directions and reconcile against carrier counters; test revocation and budget exhaustion. Not implemented.
5. **W17-G4 — transport/identity:** W11 peer authentication and W14 UDP mismatch remain open; do not release public relay.
6. **Cellular independent:** OAI RFsim SNPN auth and PC5 user-plane must each pass separately before any physical 5G claims.

**Final grade:** source-level fix **COMMITTED**, local JDK loopback test **PASS**, Android build **NOT RUN**, actual A↔B **NOT RUN**, Internet from A **NOT RUN**, PC5/RFsim **NOT RUN**, carrier billing **NOT MEASURED**. Public relay remains **NO-GO**.

**Operational constraints:** No GitHub Actions created, enabled or invoked; no CI jobs, phone installs, public network probes or purchases.
