# W36 — 2026-11-21 — ProSe authorization boundary and backhaul unit economics

## Scope and evidence
Reviewed current committed `docs/PROJECT_STATUS.md` (last section 77, W32), `tools/w27/audit.py` and `tools/w27/test_audit.py` via authenticated GitHub fetch. Existing auditor has 20 declared unittest methods, binary DNS/HTTPS/egress assertions and `verified_hours=success/12`; **no new test execution**. A separate raw.githubusercontent.com download attempt failed DNS resolution; therefore no clean exact-blob local execution. No hardware or network trials.

## New standard-grounded constraint
ETSI TS 123 304 V18.8.0 (2025-04), 3GPP TS 23.304 Rel-18, section 6.5.1.1 explicitly requires **service authorization and provisioning for both the UE-to-Network relay and the remote UE**, and a PDU session for relayed traffic. It describes relay discovery and PC5 link establishment; it does **not** grant arbitrary commercial Internet through any nearby phone. A random B without HARP, provisioned ProSe relay capability and operator authorization is not an available upstream. Official PDF: https://www.etsi.org/deliver/etsi_ts/123300_123399/123304/18.08.00_60/ts_123304v180800p.pdf .

This narrows a falsifiable vendor gate: request (1) supported A/B handset SKUs and modem/firmware PC5 features, (2) operator entitlement/provisioning for *both* roles, (3) commercial service policy for relay PDU traffic, (4) a reproducible permitted test on a named PLMN. If any is missing, classify ProSe as specification capability only (L0), not deployment-ready. SNPN is not an escape from licensed spectrum, compatible devices, network provisioning or upstream cost. eSIM is profile provisioning, not a data service or radio backhaul.

## Cost model — no provider quote
For a sponsored Wi-Fi hotspot, set monthly fixed upstream+site+power+maintenance = F, variable traffic cost per GB = p, active user count = N, GB/user/month = g, simultaneously active users = C, target busy-hour Mbps/user = r, headroom factor = h. Then monthly cost = F + p*N*g and minimum *usable* backhaul busy-hour capacity = C*r*h Mbps (subject to overhead, upstream policy and measured quality). Example **hypothetical only**: F=RD$6,200, p=RD$0/GB if truly unmetered and redistribution permitted, N=100, g=10 GB, C=15, r=2 Mbps, h=1.3 => 1,000 GB/month and 39 Mbps busy-hour provisioned. The 1 TB volume does NOT establish 39 Mbps performance; conversely a nominal 50 Mbps service does NOT establish allowed resale or sufficient contention/latency. At F=RD$6,200, cost=RD$62 per user-month before equipment amortization and support; at 50 users RD$124. If 1,000 GB is charged at p=RD$5/GB, monthly total RD$11,200 (RD$112/user). All are sensitivities, not Dominican provider prices.

## Architecture decision and falsifiable next steps
1. **Authorized community/sponsored Wi-Fi**: highest practical probability. Get two dated quotations with upstream rate, FUP, public redistribution rights, static/dynamic IP, CGNAT, power and equipment costs; measure A with cellular OFF and two HTTPS origins plus gateway WAN correlation.
2. **Voluntary B relay**: integrate W13 quotas + W17 socket teardown + W22 revocation; require B consent, measured B uplink and battery/thermal cost; prohibit public deployment until fail-closed integration tests.
3. **Unknown B without HARP**: no general unconsented tethering route; only explicitly available Wi-Fi or pre-authorized operator-managed mechanisms are candidates.
4. **ProSe/SNPN**: ask operator/equipment suppliers for the four evidentiary items above before hardware investment.
5. **eSIM**: cannot create free data. Sponsored operator-paid profiles may remove A's personal subscription but shift recurring expense to sponsor.

## Auditor integrity debt
Do not silently redefine `verified_hours`: it is **successful five-minute sample-equivalent hours**, not independently verified continuous uptime. Preserve legacy schema; design a separate v2 evidence manifest with three-valued `observed_pass/observed_fail/unknown` and gateway corroboration, plus separately reported sample counts and observed test durations. DNS encrypted or cached may be unknown while HTTPS succeeds. W33–W35 were conversation-level findings, not committed code; no claim they were published.

**Decision:** public relay NO-GO. No CI/Actions, installs, phone tests, RFsim/PC5 or real backhaul measurements.
