# W19 — 2026-11-04: Pinned egress callback semantics and false-revocation gate

**Evidence:** Android official API documentation + inspection of current `InternetNetworkSelector.java` and `Stage2RelayServer.java` from GitHub. **Design and test specification only; no Android build, callback execution, or physical Internet test.**

## New finding relative to W18

W18 proposed `onCapabilitiesChanged()` / `onLost()` to revoke B's relay. The exact **registration mode** is a critical implementation detail:

- `registerDefaultNetworkCallback()` monitors the system default, which may **not be the pinned `Network`** selected by `InternetNetworkSelector.choose()`. Switching the default could generate irrelevant events or fail to express the selected network's eligibility.
- `registerNetworkCallback(NetworkRequest, callback)` can report each matching network. The request should be **broad enough** to continue receiving capability changes that render the pinned network ineligible. If `NET_CAPABILITY_VALIDATED` or `NET_CAPABILITY_NOT_METERED` are imposed as *request filters*, losing one may manifest as `onLost` rather than `onCapabilitiesChanged`; either must revoke the **same pinned network**.
- `onAvailable` alone is **not** proof of validation. Android O+ delivers a subsequent `onCapabilitiesChanged`; the gate should remain **closed until an eligible snapshot is received**. Do not synchronously re-query capabilities inside callbacks.
- `onBlockedStatusChanged(network, true)` must also be considered. A blocked pinned network cannot be treated as usable.
- The callback must be unregistered when relay stops; callback events after closure must not restart it.
- An `onLost` event for an unrelated network must not revoke the pinned network; a later new network must not silently replace the pinned one.

Sources (official, reviewed 2026-11-04):
- https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback
- https://developer.android.com/reference/android/net/ConnectivityManager
- https://developer.android.com/reference/android/net/NetworkCapabilities

## Proposed minimal architecture (not implemented)

A dedicated session object stores an **immutable selected Network identity**, explicit user/ISP consent policy, and an atomic `REVOKED` latch. Before relay start, register a sufficiently broad `NetworkRequest` callback; receive eligible capabilities for that exact identity and obtain a fresh consent decision; only then open the relay. The selected network's `SocketFactory` must remain pinned, not silently fall back to cellular or VPN. On a disallowed capability, blocked status, selected-network loss, expired consent, or quota exhaustion: atomically latch revoked, close W17's `Stage2RelayServer`, close both accepted/outbound sockets, and unregister callback. A new network or restart requires a **new session and new authorization**. Do not call `getNetworkCapabilities` inside callbacks.

For a first implementation, **default deny** measured/roaming/VPN and uncertain capabilities. This is deliberately stricter than the current selector's fallback policy. `NOT_METERED` is a system hint, **not** ISP redistribution permission or a billing guarantee.

## Falsifiable event sequence matrix

| Test | Synthetic callback/event sequence | Required outcome |
|---|---|---|
| C1 | `onAvailable(pinned)`, no capabilities | Closed; no outbound socket |
| C2 | `onCapabilitiesChanged(pinned, eligible)` + valid consent | Open only after eligibility |
| C3 | `onCapabilitiesChanged(other, ineligible)` | Pinned session unaffected |
| C4 | `onCapabilitiesChanged(pinned, metered)` | Revoke and close both socket ends |
| C5 | `onCapabilitiesChanged(pinned, no VALIDATED)` | Revoke; no fallback |
| C6 | `onCapabilitiesChanged(pinned, VPN/roaming)` | Revoke; no fallback |
| C7 | `onBlockedStatusChanged(pinned, true)` | Revoke |
| C8 | `onLost(other)` | No unrelated revoke |
| C9 | `onLost(pinned)` | Revoke and close |
| C10 | `onAvailable(new cellular)` after revoke | Remain closed |
| C11 | Concurrent stop/capability change/registration | Exactly one logical revoke, no socket survives stop return |
| C12 | Callback arrives after unregister | Ignore; never resurrect |
| C13 | Expired consent or W13 quota exhausted | Revoke regardless of Android network state |
| C14 | Callback registration failure / permission missing | Fail closed; no relay |
| C15 | Device capability says unmetered but ISP prohibits sharing | Deny; no relay |

**Required proof:** executable local JDK state-machine tests first, then an Android build and callback instrumentation (no phone installation unless separately authorized). Record event ordering and socket closure without exposing private device identifiers. None of C1–C15 have been executed.

## Economics and feasibility

The upstream budget remains the dominant barrier. A network labeled `NOT_METERED` is not a free public backhaul contract. For any candidate venue, collect written ISP redistribution permission, dated monthly upstream cost, busy-hour capacity, and observed successful A user-hours. Calculate `all_in_monthly_cost / successful_user_hours`. No new vendor quote or measured throughput was obtained.

- **Cooperating B:** technically plausible but no public GO until W11 pairing, W12 consent/rights, W13 byte quota and this callback contract are integrated.
- **Unknown stock B without HARP:** no evidence of permissionless general Internet relay; cannot be assumed.
- **Community Wi-Fi/mesh:** credible zero-A-subscription route inside authorized coverage, but requires funded backhaul and site operation.
- **5G ProSe/SNPN:** depends on compatible, provisioned UEs and network; no PC5 or RFsim PASS.
- **eSIM:** profile provisioning, not Internet without an operator/network agreement.
- **Low-cost first pilot:** authorized existing guest AP + manual Wi-Fi join, no captive portal requirement; prove DNS/HTTPS/gateway egress with A mobile data off before optional OWE/DPP/CAPPORT.

## Release status and next action

**W19 status: documented semantics and falsifiable tests only.** The code still uses a one-time selector; no production callback, quota integration, Android compile, device install, or physical test. Next: build a pure-Java pinned-network state machine and deterministic callback tests C1–C15, then integrate only after review. P0 remains the authorized Wi-Fi Internet demonstration.

No GitHub Actions/CI, phone installs, RFsim, PC5, network probing or carrier billing tests.
