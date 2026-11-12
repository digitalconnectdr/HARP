# W27 — Reproducible multi-client offline pilot auditor (2026-11-12)

## Increment over W26
W26 specified completeness bounds and ran an ephemeral single-client auditor. W27 commits a reusable Python-standard-library CSV auditor and regression tests. The important new decision gate is the **client-interval denominator**: two clients observed over 3 intervals have 6 expected client-intervals, even if one client never produces a row. Explicitly enumerate enrolled clients rather than deriving the denominator from observed CSV identities; otherwise absent clients disappear and availability is inflated.

## Reproduction (local only; no GitHub Actions)
```sh
cd tools/w27
python3 -m unittest -v test_audit.py
python3 audit.py observations.csv --start 2026-11-01T00:00:00Z --end 2026-11-08T00:00:00Z --clients A,B
```
CSV header: `client_id,timestamp_utc,cellular_off,dns_ok,https1_ok,https2_ok,egress_ok`; flags strictly 0/1, UTC timestamps aligned every 5 minutes, window start inclusive/end exclusive. Use pseudonymous IDs. Missing rows are unknown, not failures or successes. The tool rejects duplicates, unexpected clients, malformed flags and out-of-window samples. It reports per-client and aggregate verified lower/possible upper hours, coverage, and missing intervals.

**Local result before publication:** 10/10 Python unittest scenarios passed on 2026-11-12, using synthetic rows only. No seven-day observations were supplied; the auditor cannot authenticate probe origins, independently verify cellular-off, prove TLS or identify WAN usage. These are input claims. No phone installs, RFsim, PC5, Android build or physical Internet test.

## Economic and capacity interpretation
For K enrolled clients and N intervals, denominator = K*N; verified user-hours = S/12 and possible upper hours = (S+M)/12. With 2 clients, 3 intervals each, 1 success, 1 observed failure, 4 missing: lower = 0.0833 user-hours; upper = 0.4167; coverage = 2/6. **These are synthetic values.** For an assumed RD$6,200 monthly fixed cost, dividing by a seven-day sample without aligning accounting periods is invalid. The pilot should pair gateway WAN counters, actual supplier invoices, distinct user-hours and concurrency profiles; do not infer backhaul from CAPPORT `bytes-remaining` (RFC 8908 counts bidirectional L3 and may exempt some traffic).

## Decision and next experiments
P0 remains written permission from venue and ISP to redistribute access, two dated comparable backhaul quotes, and an authorized 7-day A cellular-OFF physical pilot with independent DNS, TLS HTTPS to two public origins, egress checks and gateway WAN counters. Distinguish guest Wi-Fi directly on A from actual A->B relay: they prove different HARP hypotheses. Voluntary B still requires integrated W13 quota, W17 socket closure and W22 pinned callback gate; stock unknown B without cooperative service is not a general relay. Mesh requires financed upstream. 5G ProSe/SNPN needs compatible provisioned network and UEs. eSIM provisions credentials, not a free Internet bearer. **Public relay NO-GO.**

Official references: RFC 8908 https://www.rfc-editor.org/rfc/rfc8908.html ; RFC 8910 https://www.rfc-editor.org/rfc/rfc8910.html ; Internet Society community backhaul https://www.internetsociety.org/resources/doc/2019/solutions-for-traffic-backhaul-in-community-networks/ .
