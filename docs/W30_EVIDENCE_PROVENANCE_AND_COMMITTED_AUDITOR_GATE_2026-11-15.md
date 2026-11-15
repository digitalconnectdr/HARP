# W30 — Evidence provenance and independent committed-auditor verification gate (2026-11-15)

## Scope and observed state
Read the actual default-branch blobs of `tools/w27/audit.py`, `tools/w27/test_audit.py` and `docs/PROJECT_STATUS.md` via authenticated GitHub file reads. W29 reported 20/20 tests on a *locally equivalent* implementation, not an independently executed post-commit checkout. On this run an isolated container attempted `git clone --depth 1 https://github.com/digitalconnectdr/HARP.git` and failed at DNS resolution of github.com. **No claim of post-commit execution, syntax success, 20/20 PASS, or field connectivity is made for W30.** The fetched code visibly contains the 20 named tests, but inspection is not execution.

## New falsifiable evidence-risk hypothesis: CSV self-report is not a proof of transport
W27's flags `cellular_off,dns_ok,https1_ok,https2_ok,egress_ok` are asserted by a CSV producer. A syntactically perfect seven-day CSV can still be fabricated, collected on cellular, or reflect DNS/HTTPS on the wrong interface. Android's `NET_CAPABILITY_INTERNET` means intended Internet reachability, whereas `NET_CAPABILITY_VALIDATED` means Android last found actual reachability; neither alone cryptographically proves a given HTTPS probe used a specific network. Official: https://developer.android.com/reference/android/net/NetworkCapabilities .

**E01 (wrong-interface control):** record same endpoint probe over Wi-Fi-only and separately over cellular; reject any proof where the probe's bound network/interface is not independently recorded and cellular disablement not independently witnessed. On-device screenshots alone are weak evidence.

**E02 (offline replay control):** replay the same otherwise valid CSV for two distinct seven-day windows; the existing auditor's grid/window validation should reject mismatched timestamps, but a timestamp-rewritten replay can still pass. Introduce run-specific random nonce, signed probe transcript and gateway-side independent correlation before treating CSV as trusted.

**E03 (gateway reconciliation):** compare gateway WAN byte deltas and authorized client L3 counters over identical time boundaries. Reject as unverified if gateway counters are absent, reset without accounting, or scopes differ. Never equate CAPPORT `bytes-remaining` to ISP billed WAN bytes; RFC 8908 defines it as bidirectional L3 including headers, possibly excluding whitelisted traffic: https://www.rfc-editor.org/rfc/rfc8908.html .

**E04 (endpoint independence):** HTTPS success must use certificate-verified TLS to two independently controlled public origins, with redirects and portal-only allowlists detected; DNS must resolve through the intended network. Probe destination choice and logging must avoid collecting unnecessary personal data.

**E05 (measurement integrity):** validate expected client roster and 2016 five-minute slots per client for seven days, retain missing as unknown, and separately verify that the probe actually ran at those times (e.g., monotonic time, bounded clock drift, gateway log corroboration). An offline CSV cannot establish provenance by itself.

## Acceptance levels (do not conflate)
L0: source/doc design only. L1: exact committed blob fetched and inspected. L2: exact committed blob executed in clean offline environment with captured command/output/hash. L3: Android integration build and callbacks on real hardware. L4: authorized A data-OFF DNS/dual HTTPS/egress with independent gateway evidence. L5: >=7-day coverage, ISP/venue redistribution permission, dated backhaul quotes, actual WAN and user-hour accounting. Current W30: **L1 only for W27 auditor**, prior W29 locally equivalent tests are separately recorded, not L2.

## Economic sensitivity (hypothetical, not quotations)
Assume RD$6,200 monthly fixed cost and 540 *verified* monthly client-hours: RD$11.48/hour. If 20% of nominally successful hours cannot be independently attributed to the intended network, independently verified hours fall to 432 and cost rises to RD$14.35/hour. This is a **scenario**, not evidence that 20% are invalid. For 135 GB verified WAN consumption, allocated cost is RD$45.93/GB, but WAN and client IP accounting must not be conflated. If 7-day observations are used, compare with the same seven-day prorated cost or extrapolate both numerator and denominator with explicit uncertainty.

## Architecture decision
1. Authorized community/venue Wi-Fi + licensed backhaul: fastest falsifiable L4 route, subject to consent, CAPPORT, independent WAN logging and actual ISP terms.
2. Voluntary B relay: separate W13 quota/W17 socket-close/W22 pinned-egress integration and explicit consent; no general public relay readiness.
3. Unknown uncooperative B without HARP: no universal legal/technical forwarding mechanism established; require a concrete already-enabled compatible service and explicit authorization.
4. 5G ProSe/SNPN: standards do not imply arbitrary commercial handset support, network access or free upstream; demand specific UE/modem/operator/core compatibility matrix before spending.
5. eSIM: provisioning mechanism; requires active entitlement and funded upstream.
6. Low-cost fixed Wi-Fi, directional point-to-point or community mesh: transport/distribution alternatives, not free Internet; cost depends on permitted backhaul, installation, power, availability and utilization.

## Next execution gates
P0: independently download exact GitHub committed auditor and run `python -m py_compile tools/w27/audit.py tools/w27/test_audit.py` and `python -m unittest discover -s tools/w27 -p test_audit.py -v` offline, record SHA and stdout. Retry when checkout DNS/network access is available; no Actions/CI. P1: add optional provenance manifest/nonce/gateway counters to a separate research schema without retroactively claiming W27 proves connectivity. P2: obtain written ISP/venue permission and two dated Dominican backhaul quotes; authorized seven-day physical A mobile-data-OFF pilot. No phone installs or hardware experiments were conducted in W30.

**Status: public relay NO-GO.** No GitHub Actions, CI, phone installations, Android build, RFsim, PC5, physical Internet test or billed traffic observations.
