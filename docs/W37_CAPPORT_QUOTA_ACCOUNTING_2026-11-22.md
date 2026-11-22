# W37 — CAPPORT quota accounting and evidence boundary (2026-11-22)

## New standards-grounded finding
RFC 8908 section 5 defines optional `bytes-remaining` as **layer-3 IP bytes sent AND received by the client, including IP headers**. This is not identical to gateway WAN byte totals, application payload bytes, a cellular carrier's billed bytes, or a generic GB plan allowance. The optional `seconds-remaining` indicates session expiry, not a guarantee of continuous Internet service. `captive=false` is a portal state, not proof of end-to-end HTTPS or attributable gateway egress. Source: https://www.rfc-editor.org/rfc/rfc8908.html#section-5

RFC 8908 section 4 also requires the captive API and enforcement gateway to agree on client identity; per-client API URIs may be used when identity is otherwise unavailable. Source: https://www.rfc-editor.org/rfc/rfc8908.html#section-4

## Falsifiable implementation gates (not executed)
1. Controlled, consenting guest network: grant session quota Q=100,000,000 L3 bytes; record both IPv4/IPv6 directions at enforcement. Compare consumed bytes with API `bytes-remaining`, allowing only a documented bounded counter lag. Verify no fresh quota on reconnect or IP rotation.
2. Revoke session: subsequent external TCP/UDP and IPv6 paths must be denied. Keep CAPPORT API available only as needed for portal flow.
3. Compare independently captured gateway WAN bytes and per-client L3 bytes; **do not demand equality** because NAT, retransmissions, other users, and protocol overhead change totals. Record measurement boundaries explicitly.
4. Use two independent HTTPS endpoints, cellular disabled, and a negative test with Wi-Fi disconnected. Record gateway evidence separately from browser success.
5. Do not equate a five-minute positive sample with five continuous verified minutes. W27 `verified_hours` remains a *sample-weighted proxy*, not measured duration; rename only in a versioned output schema with backward-compatible migration.

## Hypothetical backhaul arithmetic
100 clients x 10 decimal GB/month = 1,000 GB/month = 8,000 Gb/month. Spread evenly across 30 days this is 3.09 Mbps **mean**, not peak. At 15 simultaneous users x 2 Mbps and 30% headroom, peak target is 39 Mbps. At 80% usable backhaul efficiency, a nominal link should exceed 48.75 Mbps before any further SLA or contention margin. A 50 Mbps marketed link is therefore borderline even in this simple scenario. These are planning assumptions, not observed demand or an ISP quote.

## Route decision
- Authorized venue/community Wi-Fi + funded upstream: viable pilot candidate, subject to permission, isolation, capacity and real pricing.
- Opt-in B relay: conditional on authenticated pairing, explicit metered consent, quotas, revocation and real Android proof.
- Unknown stock B without cooperation: no generic mechanism demonstrated.
- ProSe/SNPN: operator/device provisioning and compatible infrastructure needed; reference specifications are not physical proof.
- eSIM: credential provisioning, not free Internet or backhaul.
- Mesh without an Internet gateway: local connectivity only.

## Evidence and blockers
Read current `tools/w27/audit.py` and `test_audit.py` from GitHub. W27 accepts only binary probe flags and calls sample-weighted successful intervals `verified_hours`; no independent egress evidence is parsed. This W37 note is a standards/measurement gate, **not** a code fix, unit-test execution, device test or physical Internet proof.

Next: version the auditor evidence schema; execute exact-source local tests without CI; obtain venue/ISP authorization and two written backhaul quotes; then perform the seven-day consented pilot. Public relays remain NO-GO. No GitHub Actions, CI, phone installation, or physical probes in this iteration.
