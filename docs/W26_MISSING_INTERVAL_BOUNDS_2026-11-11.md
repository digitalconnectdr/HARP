# W26 — Missing-interval bounds for seven-day community Wi-Fi pilot (2026-11-11)

## Finding and scope
W25's synthetic CSV audit did not establish coverage of a complete seven-day observation window. Missing intervals are **unknown**, not observed failures and never verified successes. Report two bounds: verified successful user-hours (lower bound) and verified plus missing hours (upper bound). An interval with a failed probe is a known failure; an absent record is unknown. This distinction prevents inflated service availability and optimistic backhaul economics.

## Falsifiable gate
For one client and 5-minute intervals, 7 days requires 7*24*12 = **2016 unique UTC intervals**. The collector must establish a UTC window, align timestamps, reject duplicates and invalid flags, and report missing count explicitly. Five independent positive observations per interval: cellular OFF, DNS success, two distinct HTTPS origins, and public egress verification. This is a proposed acceptance contract, not a statement that probes were run. An observation is successful only if all five are true. No successful service-hour claim is allowed for missing intervals.

Definitions: N=expected, S=verified successful, F=observed failed, M=missing, with N=S+F+M. Verified successful user-hours = S*5/60. Potential upper bound = (S+M)*5/60. Coverage = (S+F)/N. If N=2016, S=1400, F=400, M=216, lower bound 116.67 hours and upper bound 134.67 hours; coverage 89.29%. These are illustrative, not measurements. If allocated monthly cost is RD$6200 and the seven-day sample is used to extrapolate a monthly business case, **do not divide monthly cost by unscaled seven-day hours**. Report observation-period cost aligned with observation-period hours or explicitly model monthly extrapolation and uncertainty.

## Local execution, distinct from field results
A Python 3 standard-library offline auditor and seven unittest cases were created and executed in a local ephemeral workspace on 2026-11-11: 7/7 PASS (complete, missing, failed, duplicate, invalid flag, outside window, misaligned timestamp). The local scripts are **not committed to this repository** in this iteration. These tests use synthetic CSV records only, with no network traffic. No 7-day capture exists and no physical A/B or Android test was performed.

## Decision gates
- P0: written venue and ISP redistribution authorization; two dated comparable quotes; a real 7-day client-side capture on A with cellular disabled, independent DNS/TLS/egress probes, and gateway-side WAN counters.
- Reject any pilot report that equates Wi-Fi association, CAPPORT API state, or missing rows with successful Internet. The CAPPORT API's byte quota is bidirectional L3 traffic, not automatically ISP billable bytes.
- B voluntary: consent, metering, authentication, W13 quota + W17 close + W22 callback gate still unintegrated. Unknown stock B without cooperative relay is not a general route. Community mesh still needs backhaul; 5G ProSe/SNPN requires compatible provisioned infrastructure; eSIM is credential provisioning, not free access.
- **NO-GO public relay.** No Actions, CI, phone installs, physical Internet, PC5, RFsim, or billing tests.

## Standards
- RFC 8908: https://www.rfc-editor.org/rfc/rfc8908.html
- RFC 8910: https://www.rfc-editor.org/rfc/rfc8910.html
- Prior reports: [W24](W24_CAPTIVE_PORTAL_USABLE_INTERNET_GATE_2026-11-09.md), [W25](W25_CAPPORT_L3_BYTES_AND_EVIDENCE_DENOMINATOR_2026-11-10.md)
