# HARP W13 — Fail-closed upstream policy and bounded relay-payload quota (2026-10-29)

**Evidence level:** read-only audit of HARP `main` + official Android/AOSP docs + JDK-only Java reference compiled and executed locally. **NOT an Android integration, no relay test, no mobile-data measurement, no ISP agreement, no physical Internet proof.**

## Findings beyond W12

1. **A selected network is not billing permission.** `InternetNetworkSelector.choose()` prefers validated non-metered direct networks; `HarpAwareController` uses the chosen `Network.getAllByName()` and `getSocketFactory()` for Stage-2 DNS and outbound TCP. That reduces process-default routing errors but does not enforce consent, redistribution rights, metered/roaming/VPN policy, or quotas. Android warns that `getNetworkCapabilities(Network)` snapshots can immediately become stale; use callbacks and re-evaluate before opening flows.
2. **Eight SOCKS sessions are not a data cap.** `Stage2RelayServer` limits concurrent TCP sessions to eight. `MiniSocks5.pipe()` copies in 32 KiB chunks with no quota check in either direction. `close()` closes accepted sockets, but remote socket traffic can be in flight. Callback-based revocation cannot be assumed instantaneous. This is a source-level risk, not a measured leak.
3. **Android system tethering counters do not enforce a HARP proxy quota.** AOSP tethering hardware offload/HAL belongs to Android's tethering subsystem, not HARP's userspace SOCKS proxy. `TrafficStats` mobile/UID totals are not a precise per-relay per-network carrier billing meter. Account application payload in *both directions*, reserve conservative margin for overhead/in-flight traffic, and reconcile with operator/account usage if possible.
4. **Conservative capability interpretation.** Missing `NET_CAPABILITY_NOT_METERED` means possibly metered, even on Wi-Fi. `NET_CAPABILITY_TEMPORARILY_NOT_METERED` is not permanent free usage. Missing `NET_CAPABILITY_NOT_ROAMING` on cellular cannot imply non-roaming. VPN egress requires verified underlay and explicit permission; Wi-Fi Aware is never Internet egress.

Official references (consulted 2026-10-29):
- https://developer.android.com/reference/android/net/NetworkCapabilities
- https://developer.android.com/reference/android/net/Network
- https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback
- https://developer.android.com/reference/android/net/TrafficStats
- https://source.android.com/docs/core/data/tethering-offload

Audited source paths: `app/src/main/java/org/harp/l2/InternetNetworkSelector.java`, `HarpAwareController.java`, `Stage2RelayServer.java`, `MiniSocks5.java`.

## Offline reference artifacts — not wired into Android

- [RelayQuotaGate.java](../tools/w13/RelayQuotaGate.java): JDK-only policy model with explicit user and ISP authorization flags, pinned upstream identity, metered/roaming/VPN gating, monotonic expiry, irreversible revocation and synchronized aggregate payload reservations.
- [RelayQuotaGateSelfTest.java](../tools/w13/RelayQuotaGateSelfTest.java): negative tests for missing consent/rights, metered Wi-Fi, cellular roaming, opaque VPN, Aware exclusion, expiry, network switch, quota exhaustion, and concurrent reservations.

Executed locally using OpenJDK 21 (no Android SDK, no Actions):
```sh
javac -d /tmp/harp-w13-javac tools/w13/RelayQuotaGate.java tools/w13/RelayQuotaGateSelfTest.java
java -cp /tmp/harp-w13-javac RelayQuotaGateSelfTest
# PASS_HARP_W13_JDK_ONLY_QUOTA_GATE_SELFTEST
# NO_ANDROID_BUILD_NO_LIVE_RELAY_NO_BILLING_MEASUREMENT
```

Eight threads attempted 8,000 reservations of 16 payload bytes against a 777 × 16 = **12,432-byte** quota. Exactly 777 reservations were allowed **in the local reference model**. No real sockets or carrier counters were used. Git blobs of both published files match locally compiled sources.

**Critical limits:** `RelayQuotaGate` is not called by `MiniSocks5`, `Stage2RelayServer`, Android `NetworkCallback` or UI; no persistent quota, authenticated permission source or operator billing meter is implemented. `reserve()` counts application payload only. A boolean supplied by a caller is not independently verified ISP authorization.

## Falsifiable next gates

| Gate | Required observation | Stop condition |
|---|---|---|
| W13-G0 | B consent + verified ISP rights + approved upstream | Missing permissions or opaque fallback |
| W13-G1 | Android capability adapter pins network and revokes on switch, metered/roaming/VPN transition | Relay continues on unauthorized network |
| W13-G2 | Both SOCKS pipe directions reserve before write, half-close and concurrent flows covered | Bytes forwarded beyond payload budget |
| W13-G3 | Operator-counter reconciliation, conservative overhead margin and restart persistence | Billable usage exceeds approved allowance |
| W13-G4 | Local Android build and controlled A/B hardware, actual bytes/battery/HTTPS | Only source or synthetic test |
| W13-P0 | Separately: consented guest Wi-Fi on A, data OFF, DNS+HTTPS+gateway egress, actual cost | No authorized venue or physical proof |

A check in the accept loop alone is insufficient: policy must be checked before DNS/connect and for each forwarding chunk; track and close both client and upstream sockets. Callbacks are asynchronous and cannot guarantee an instantaneous zero-overrun carrier bill.

## Economics

At 5 Mbps average payload for 2 h/day, 30 days: **135 GB payload/month**. At a hypothetical extra 10% billed overhead: **148.5 GB/month**. A hypothetical **30 GB carrier allowance** corresponds to **27.27 GB payload** at that assumed overhead: 12.12 hours at 5 Mbps or **6.06 days** at 2 h/day, before unrelated phone traffic. These are **synthetic arithmetic, not safe carrier limits, measured usage or real quotations**.

## Architecture decision

The fastest practical proof for A without its own mobile subscription is still a **consented Wi-Fi AP with funded, authorized ISP backhaul**. Voluntary B with HARP needs W11 peer authentication and W12/W13 consent+quota integration. Unknown stock B without HARP cannot be assumed to forward arbitrary traffic. Community mesh needs powered routers and backhaul; 5G ProSe/SNPN needs provisioned compatible UE/RAN and physical PC5 tests; eSIM provisions credentials rather than creating connectivity.

**State:** W13 reference-only PASS, Android runtime integration NOT DONE, public relay NO-GO, physical Internet NOT DEMONSTRATED. No GitHub Actions, CI, phone installs, RFsim or external network tests.
