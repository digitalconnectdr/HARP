# W20 — Egress session epoch and callback race gate (2026-11-05)

**Evidence class: source-reviewed design / unexecuted test vectors. No Android implementation or device test.**

## Delta since W19
Reviewed current `InternetNetworkSelector.java` (blob `96c76fe50c3c0a6eb5dcb435db7223579b60403b`) and `Stage2RelayServer.java` (blob `3050b1f146e21c74a60f349daa761d23f72f02bb`). W19 already established pinned network identity and callback filtering. W20 identifies an additional **session-generation/ABA race**: an old callback or asynchronous network selection can arrive after B has revoked a session and explicitly authorized a new one, possibly with the *same* Android `Network` identity. Filtering by `Network.equals()` alone cannot distinguish old and new authorizations. This is a **hypothesis about a future integration**, not a reproduced bug in production, since callbacks are not yet wired.

## Proposed minimal fail-closed contract
1. Every B authorization creates a strictly increasing, process-local `epoch` (never reuse a prior value within a process), a pinned `Network`, explicit consent and immutable allowed capability policy. Process restart begins with **no active authorization**.
2. Callback closure captures the epoch and pinned network. Before any state mutation, require both epoch match and pinned-network identity match. Old callback events are ignored, including an old `onAvailable` for the same Network object.
3. Session states: `INACTIVE -> PENDING_VALIDATION -> ACTIVE -> REVOKED`. Only fresh explicit B authorization can create a new epoch. No callback may transition `REVOKED -> ACTIVE`.
4. `onAvailable` does not activate. Only the matching `onCapabilitiesChanged` with required `INTERNET`, `VALIDATED`, allowed transports, `NOT_VPN`, `NOT_METERED` (unless separately consented), and roaming policy can activate. A mismatch while ACTIVE revokes immediately; `onLost` or blocked=true revokes. If request filtering causes a lost event rather than capabilities update, treat it as revocation.
5. **Fail-closed start gate**: register callback, await an eligible capability event for the pinned network, and recheck epoch/state atomically immediately before opening the relay. Avoid a gap where the listener starts before eligibility is confirmed. The exact registration and start ordering needs an Android integration test.
6. Revocation atomically changes state first, then calls W17 `Stage2RelayServer.close()`, closes both directions and unregisters callback. No silent fallback to metered, VPN, or a different network.
7. Callback lifecycle and relay lifecycle must share a serialized state machine or synchronized critical section. A late callback must never resurrect a closed relay. Bound queued events and log only metadata, not credentials.

## Falsifiable JDK-only test vectors (NOT executed)
| ID | Event sequence | Expected |
|---|---|---|
| E01 | authorize epoch 1; caps eligible pinned; start | ACTIVE exactly once |
| E02 | epoch 1 active; caps lose VALIDATED | REVOKED, close once |
| E03 | epoch 1 revoked; late eligible caps epoch 1 | remains REVOKED |
| E04 | epoch 1 revoked; authorize epoch 2 same Network; late caps epoch 1 | epoch 2 unchanged |
| E05 | epoch 2 active; unrelated Network loses connectivity | epoch 2 stays ACTIVE |
| E06 | epoch 2 active; pinned blocked=true | REVOKED |
| E07 | epoch 2 active; metered transition without separate consent | REVOKED |
| E08 | epoch 2 active; pinned onLost | REVOKED |
| E09 | epoch 2 pending; onAvailable only | no relay start |
| E10 | concurrent revoke vs start; 10k interleavings | no start after revoke |
| E11 | epoch 2 revoked; fresh epoch 3 Wi-Fi authorization | may activate only after new eligible caps |
| E12 | process restart | INACTIVE, no retained permission |

**Pass criteria:** no unapproved outbound socket creation; no resurrection; no network switch; close invoked once per active relay. Tests need deterministic fake callback dispatcher plus stress test; subsequently actual Android callback and network-transition tests. A JDK state-machine PASS would validate only the model, not Android network event ordering.

## Connectivity/economics decision
- **P0:** ISP/venue-authorized Wi-Fi/OWE/guest gateway with measured upstream and no A mobile subscription. Measure DNS, HTTPS, actual egress, isolation, usable user-hours, congestion and real invoices. Mesh nodes extend radio reach but do not create upstream.
- **Cooperative B:** requires app/OS support, explicit redistribution rights, peer identity, W13 integrated quotas and this epoch/callback gate. A cellular plan on B is still a paid backhaul.
- **Unknown stock B without HARP:** no authorized universal forwarding interface demonstrated; do not treat passive radio discovery as relay permission.
- **5G ProSe/SNPN:** standardization of sidelink/relay does not establish stock-handset support or operator provisioning.
- **eSIM:** remotely provisions a cellular subscription/profile; cannot replace upstream capacity.
- Fixed backhaul unit economics must use **successful user-hours**. Illustrative RD$5,700/month yields RD$9.50 at 600 user-hours, RD$95 at 60; not provider quotes. At 2 Mbps for one hour/day, 30 days and 10% assumed overhead: 29.7 GB/month per sustained stream (decimal GB). Never treat these figures as measured.

## References (official)
- Android ConnectivityManager.NetworkCallback: https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback
- Android NetworkRequest.Builder: https://developer.android.com/reference/android/net/NetworkRequest.Builder
- Android NetworkCapabilities: https://developer.android.com/reference/android/net/NetworkCapabilities
- 3GPP/ETSI TS 23.304: https://www.etsi.org/deliver/etsi_ts/123300_123399/123304/19.06.00_60/
- GSMA eSIM specifications: https://www.gsma.com/solutions-and-impact/technologies/esim/esim-specification/

**No GitHub Actions/CI, APK build, phone installs, physical A/B Internet, RFsim/PC5, or carrier billing tests. Public relay remains NO-GO.**
