# W31 — Network-specific proof and provenance boundary (2026-11-16)

## Scope and evidence
Read the actual default-branch `tools/w27/audit.py`, `tools/w27/test_audit.py` and the W30 status section via the GitHub connector. This is **source inspection only**; no Python execution, Android build, RF test or physical Internet measurement. The current CSV auditor evaluates asserted flags, not cryptographic proof or actual interface attribution.

## New technical distinction: no-install A versus app-instrumented A
Android's official `Network.getSocketFactory()` creates sockets tied to a specific Network; `Network.getAllByName()` can resolve on that Network. Android explicitly prefers these per-network mechanisms to process-wide `ConnectivityManager.bindProcessToNetwork()`. These APIs require code running on A. Therefore **they cannot be presented as proof available from an unchanged stock-phone browser without installing/instrumenting software**.

References:
- https://developer.android.com/reference/android/net/Network
- https://developer.android.com/reference/android/net/ConnectivityManager
- https://www.rfc-editor.org/rfc/rfc8908.html

For the **zero-install pilot**, use the phone's OS settings to disable mobile data, join an authorized isolated guest Wi-Fi, open two certificate-valid HTTPS origins and perform a DNS/browser check. A cooperating gateway/AP operator independently records client association, DHCP lease (or IPv6 neighbor mapping), NAT/flow logs where lawful, WAN byte deltas and outage windows. OS settings + gateway records improve attribution but are not tamper-proof; avoid claiming a cryptographic binding between browser transaction and WAN flow without packet-level/session correlation. CAPPORT is optional for an open guest SSID, not a prerequisite for ordinary HTTPS.

For a **future app-instrumented pilot** (only after explicit authorization to install), pin DNS and sockets to a selected Android `Network`; record the network handle, session nonce, network loss and callback changes, and cross-check gateway logs. This is a different experimental tier; do not confuse it with the zero-install result.

## Falsifiable gates and acceptance
1. **Negative cellular-fallback control**: with Wi-Fi intentionally disconnected and cellular data OFF, both HTTPS checks must fail; otherwise the experimental assumption is false. Do not attempt this against third-party infrastructure.
2. **Independent egress**: with Wi-Fi restored, two independent HTTPS origins succeed with valid TLS, and the cooperating gateway observes corresponding client-to-WAN traffic within a documented clock tolerance.
3. **Attribution**: a declared client ID must map to a consented device and an AP association/lease epoch; MAC randomization and IPv6 privacy addresses require explicit epoch mapping. NAT/public IP alone does not identify a device.
4. **Unknown interval policy**: if gateway telemetry is missing or ambiguous, the interval is UNKNOWN even when client CSV flags say success. A failure observed on either side is FAILURE. Verified success requires BOTH independent evidence paths.
5. **Budget gate**: report total WAN bytes, total authorized backhaul price and verified client-hours for identical billing windows. If verified hours are zero, cost/hour is undefined, not zero.

Proposed join key for a later provenance manifest: `pilot_id, client_id, interval_start_utc, ap_id, lease_epoch_id, client_probe_id, gateway_log_id, evidence_status`. Require uniqueness by pilot/client/interval, immutable raw evidence references, UTC clock offset bounds, and explicit source/retention/consent metadata. These are proposed fields, **not implemented or validated**. Avoid storing plaintext browsing destinations beyond the controlled test origins.

## Economic sensitivity (hypothetical, not quotes)
At RD$6,200/month: 540 independently verified client-hours cost RD$11.48 per verified client-hour; 432 hours cost RD$14.35; 270 hours cost RD$22.96. For a 25 Mbps usable uplink, 10 concurrent users at 2 Mbps each consume 80% of nominal capacity before overhead and bursts. WAN billing and CAPPORT `bytes-remaining` are not equivalent: RFC 8908 counts bidirectional L3 IP bytes under portal policy.

## Architecture decision
- **Priority**: authorized guest/community Wi-Fi with funded backhaul and a zero-install A pilot.
- **Volunteer B with HARP**: possible research path only after consent, quota enforcement, socket revocation and network-pinning integration (W13/W17/W22), plus real tests.
- **Unknown B without HARP**: no demonstrated generic, lawful automatic forwarding capability.
- **ProSe/SNPN**: provisioning and compatible network/UE support required; not a universal stock-phone solution.
- **eSIM**: provisioning mechanism; no intrinsic upstream connectivity or cost elimination.
- **Low-cost AP/mesh/point-to-point links**: may reduce last-hop distribution cost but cannot eliminate paid/authorized upstream, power, installation, maintenance or oversubscription.

## Next experiment
Obtain written venue/ISP redistribution permission and two comparable dated backhaul quotes; run a 7-day authorized A cellular-OFF pilot with gateway-side correlation and negative fallback control. Separately execute the **exact** fetched W27 GitHub files in an isolated offline interpreter, without GitHub Actions/CI. Current result: **documented protocol only; public relay NO-GO**.
