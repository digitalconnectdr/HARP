# W18 — Egress capability-change revocation gate (2026-11-03)

**Evidence:** source review of `InternetNetworkSelector.java` and `Stage2RelayServer.java` at GitHub, plus current official Android API documentation. **No Android build, handset, traffic, operator bill or network callback test.**

## New actionable integration gap

W17 closes accepted and outbound TCP sockets when `Stage2RelayServer.close()` is explicitly invoked. The current `InternetNetworkSelector.choose()` is a one-time enumeration over `ConnectivityManager.getAllNetworks()`, selecting a validated direct unmetered network first but allowing **metered direct, other non-VPN, then VPN** fallback. Neither class shown here registers an Android `NetworkCallback`. W12/W13 already identified the consent/quota gap; W18 defines the **precise capability-event-to-revocation contract** that remains missing after W17.

Android explicitly warns that synchronous `getNetworkCapabilities()` snapshots can immediately become stale and recommends callbacks. In callbacks, Android warns **not** to call synchronous capability getters; use the `NetworkCapabilities` object passed to `onCapabilitiesChanged()`. `onAvailable()` is not proof that an approved egress is still eligible. `onLost()` is not necessarily a loss of *all* Internet when using a default-network callback; only the selected bound `Network` matters. `NET_CAPABILITY_TEMPORARILY_NOT_METERED` can disappear at any time. `NET_CAPABILITY_VALIDATED` is an Android reachability signal, not proof of permitted resale.

Official:
- https://developer.android.com/reference/android/net/NetworkCapabilities
- https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback
- https://developer.android.com/reference/android/net/ConnectivityManager

## W18 proposed fail-closed contract (NOT IMPLEMENTED)

1. On B's explicit, recorded opt-in, pin the **exact** Android `Network` selected for outbound sockets; separately record consent scope, expiry, per-network policy (unmetered/metered/roaming/VPN), approved byte budget, and upstream redistribution rights.
2. Register a `registerNetworkCallback()` subscription with a broad enough `NetworkRequest` to observe the pinned network even after it loses `VALIDATED` or `NOT_METERED`. A narrowly filtered request can result in `onLost()` on capability mismatch; treat that as **revocation**, not automatic permission to select a new network.
3. Use the callback-supplied capabilities, not a synchronous query inside callbacks. If the pinned network loses eligibility, loses `VALIDATED`, becomes metered without explicit metered consent, becomes roaming without approval, becomes VPN without verified underlying consent, or becomes blocked, **stop the relay and close both socket directions**. On `onLost(pinnedNetwork)`, revoke immediately.
4. Do not auto-switch from Wi-Fi to cellular or another `Network`. A new network needs a **new explicit authorization**, even if Android marks it validated. Never interpret `NOT_METERED` as an ISP redistribution license.
5. Registration and initial state are a race: start in **DENY**, subscribe, await the pinned network's callback `onCapabilitiesChanged`, then allow forwarding only after current eligibility is observed; if network disappears during registration, remain denied. Keep the listener/forwarding disabled until the policy is armed. Re-check consent and quota on every connection and byte transfer. Ensure callback unregistration and relay closure are idempotent.
6. W17's socket-close test only covers a direct `close()` invocation. A full Android test must prove the callback triggers it; a reference state machine test alone cannot establish OS behavior.

## Falsifiable negative test matrix

| Test | Setup | Expected outcome |
|---|---|---|
| W18-N1 | B Wi-Fi validated, allowed; drop `NOT_METERED` without metered consent | No new transfers; existing sockets closed |
| W18-N2 | B switches from authorized Wi-Fi to cellular | No automatic cellular relay, regardless of `VALIDATED` |
| W18-N3 | Pinned network loses `VALIDATED` | Relay closed, no silent fallback |
| W18-N4 | Pinned network becomes roaming or VPN without specific consent | Relay closed |
| W18-N5 | Network disappears before first capability callback | Never forwards |
| W18-N6 | `onLost` of unrelated network | No false revocation of pinned network |
| W18-N7 | Simultaneous callback, manual stop and new SOCKS connection | No socket remains usable after stop |
| W18-N8 | Callback unregister/re-register and relay restart | No leaked callback, no stale consent |
| W18-N9 | Data cap reached midstream | Both transfer directions terminate within a documented overshoot bound |
| W18-N10 | Actual A/B physical run | Verify egress identity, operator bytes, Chrome DNS/HTTPS and all negative cases |

**Acceptance:** local JDK policy tests + local Android compilation + Android instrumented network-transition tests + authorized physical A/B demonstration; each evidence level reported separately. Do not claim N1–N10 passed.

## Infrastructure and commercial decision

A zero-subscription A has three materially different paths:
- **Direct authorized Wi-Fi**: most testable. A needs no HARP app, but the AP and paid ISP backhaul are essential.
- **Volunteer B with HARP**: can forward only if B explicitly opts in and pays/authorizes backhaul; W11 identity, W13 quota and W18 callback integration are still NO-GO.
- **Unknown stock B without HARP/opt-in**: neither Android DPP/CAPPORT/OWE nor 3GPP ProSe creates an unauthorized Internet relay. No demonstrated mechanism.

Community mesh adds coverage hops but not free Internet upstream. 5G ProSe/SNPN needs compatible and provisioned UE/network; eSIM is profile provisioning only.

**Pilot cost equation:** monthly total = authorized backhaul + AP/router amortization + power + venue/ops + support; cost per useful user-hour = total / measured successful user-hours. Illustrative **not quoted**: RD$5,700/month / 600 useful hours = RD$9.50/hour; at 60 hours = RD$95/hour. For mobile B, at 2 Mbps, 1 hour/day, 30 days, 10% overhead: 29.7 GB/month per sustained flow (decimal GB). Obtain operator/ISP quotes before pricing.

## Next experiment

P0: authorized venue/ISP guest AP, phone A cellular OFF, DNS + HTTPS + gateway egress, isolation and dated real backhaul quote. P1: integrate W13 quotas and W18 callbacks with W17 socket close; JDK and local Android builds only (no Actions). P2: resolve W14 UDP contract and W11 authenticated pairing before public relay. Keep RFsim/PC5 a separate evidence lane.

**No GitHub Actions/CI, no phone installation, no unauthorized network tests, no physical Internet PASS.**
