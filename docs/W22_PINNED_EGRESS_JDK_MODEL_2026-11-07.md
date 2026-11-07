# W22 — Pinned-egress fail-closed JDK model (2026-11-07)

**Evidence:** Actual isolated Java reference compiled and executed locally, 24 deterministic assertions PASS. NOT integrated with Android, sockets, W13 quotas, W17 close or any phone. This is NOT proof of a physical relay or absence of transient traffic.

## New official API nuance
Android's [NetworkCallback reference](https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback) documents `onBlockedStatusChanged` as **added in API 29**; the Android 8+ (API 26) callback sequencing text is not a promise that applications on API 26–28 receive a callback method that was only added in API 29. W21's wording should not be interpreted as API 26 availability. Before implementing Android integration, either enforce minSdk 29 for this gate, or design and test a separately justified fail-closed older-API policy; never silently assume blocked=false.

## Executed reference
- [State machine](../tools/w22/PinnedEgressGate.java) implements per-epoch and pinned-network matching; PENDING until both eligible capabilities and explicit blocked=false; terminal REVOKED on loss, ineligible caps, blocked=true, timeout or consent revocation.
- [Self-test](../tools/w22/PinnedEgressGateSelfTest.java): `javac tools/w22/*.java && java -cp tools/w22 PinnedEgressGateSelfTest` (run in a JDK environment). Local output: `PASS_HARP_W22_JDK_ONLY_PINNED_EGRESS_MODEL checks=24`.
- Covers B01–B11 as deterministic sequential scenarios; B12 checks a new object's PENDING default only, **not** real process restart/persistence. Additional stale loss and idempotent revoke assertions. No concurrency stress, OS callback ordering or socket lifecycle verification. No complete W20 E01–E12 execution claim.
- No production files modified. Next: add actual callback adapter with API-level gate, serialize lifecycle, wire W17 closure and W13 quotas, execute adversarial interleavings and Android build locally; keep PUBLIC RELAY NO-GO.

## Connectivity and economics
P0 remains written ISP/venue-approved guest/community Wi-Fi with A mobile data disabled, real DNS/HTTPS/gateway egress, guest isolation, measured useful user-hours and dated backhaul offers. Volunteer B requires upstream redistribution permission, consent, quota and identity; arbitrary stock B without HARP is not a general relay. ProSe/SNPN needs provisioned infrastructure/compatible devices; eSIM provisions credentials, not independent access. Illustrative RD$5,700/month at 600 successful user-hours = RD$9.50/hour, or RD$95 at 60 hours; neither is a quote or measurement. Compare fixed backhaul with measured mobile GB costs before deciding.

**No GitHub Actions/CI, no phones, APK, external network traffic, PC5, RFsim or physical Internet tests in W22.**
