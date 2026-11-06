# W21 — Initial blocked-status authorization barrier (2026-11-06)

**Evidence class: Android API documentation + design correction; no executed Android/JDK test.**

## Delta from W20

W20 allows PENDING_VALIDATION -> ACTIVE upon eligible pinned-network `onCapabilitiesChanged`. This is insufficient for a conservative relay policy: Android exposes `onBlockedStatusChanged(network, blocked)` as an independent callback. Android 8+ documents the initial `onAvailable` sequence followed by capabilities, link properties, and blocked status. Therefore a relay that starts immediately after eligible capabilities can start **before its initial blocked state is known**. This is a **design gap in the proposed W20 state machine**, not a demonstrated flaw in running Android code.

Official source: https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback

## Revised fail-closed barrier

- New epoch starts with `capsSeen=false`, `blockedSeen=false`, `eligible=false`, `blocked=true` (unknown treated as blocked), `state=PENDING_VALIDATION`.
- `onAvailable` never starts the relay.
- Only callbacks whose captured epoch **and** pinned Network match may update this session.
- `onCapabilitiesChanged` records capability eligibility; ineligible capabilities revoke (or keep pending before first activation; safest initial implementation revokes).
- `onBlockedStatusChanged(false)` records a confirmed unblocked state. `true` revokes immediately, including while pending.
- **Start only if both capsSeen and blockedSeen and eligible and !blocked**, while holding a serialized state transition that prevents concurrent revoke/start. If a callback is missed, remain pending and time out fail-closed; no optimistic start.
- `onLost`, callback registration failure, consent revocation and timeout revoke. Unregister callback, close W17 sockets and never auto-restart. Fresh authorization gets a fresh epoch.
- Preserve W19's broad observation request with explicit eligibility checks; do not use a narrow `VALIDATED` request that hides capability-loss details. `onLost` still handles no-longer-matching networks.
- Avoid synchronous `getNetworkCapabilities` inside callbacks. Android callback sequencing does not guarantee that two independent events can be treated as one atomic policy snapshot; fail-closed and serialized lifecycle are required. This is a proposed safety property, **not a proof of zero packets during an OS transition**.

## Falsifiable next test matrix (not executed)

| ID | Sequence | Expected |
|---|---|---|
| B01 | available, eligible caps, no blocked callback | PENDING; no socket |
| B02 | available, eligible caps, blocked=false | ACTIVE once |
| B03 | available, blocked=false, eligible caps | ACTIVE once |
| B04 | eligible caps, blocked=true | REVOKED; no socket |
| B05 | active then blocked=true | REVOKED; close once |
| B06 | active then caps become metered | REVOKED; close once |
| B07 | pending then timeout | REVOKED; no socket |
| B08 | revoked then late blocked=false | REVOKED |
| B09 | old epoch blocked=false, new epoch same Network | new epoch remains PENDING |
| B10 | unrelated Network eligible+unblocked | no effect |
| B11 | revoke races with second barrier event | never start after revoke |
| B12 | restart process | INACTIVE; no retained authorization |

**Next executable artifact:** isolated JDK state machine plus deterministic B01–B12 and W20 E01–E12 (adjust E01 to include blocked=false), followed by actual Android callback integration and local build. Tests must distinguish model PASS from Android behavior. Do not install on phones or use GitHub Actions/CI.

## Connectivity and economics unchanged

P0: authorized guest/community Wi-Fi with paid upstream, A mobile subscription OFF, DNS/HTTPS/gateway evidence, isolation, dated backhaul quotations and measured successful user-hours. Voluntary B needs explicit sharing rights, identity, metering/quotas and callback revocation; unknown stock B without HARP is not a general relay. 5G ProSe/SNPN requires provisioned network/device support; eSIM only provisions a profile. At illustrative RD$5,700/month, 600 useful user-hours = RD$9.50/hour versus 60 hours = RD$95/hour; **not quotes or measurements**.

**No production changes, no JDK/Android test execution, no APK, no phone installation, no Actions/CI, no physical Internet/PC5/RFsim. Public relay remains NO-GO.**
