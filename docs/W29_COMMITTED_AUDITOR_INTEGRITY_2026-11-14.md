# W29 — Restore committed auditor and independently exercise seven-day denominator (2026-11-14)

## Finding (repository read, not inferred from prior summaries)
The default-branch `tools/w27/audit.py` fetched on 2026-11-14 was **syntactically malformed**: its first `UTC_PATTERN=re.compile(...` declaration was truncated and a second stale `FIELDS`/function block was appended after `if __name__=='__main__': main()`. Consequently the W28 claims about an equivalent local implementation did not establish that the committed tool was executable. This is a release-blocking **source integrity defect**, not a connectivity finding.

## Repair
Replaced the malformed duplicated prefix and stale parser with one canonical UTC parser and explicit five-minute window alignment; retained the original W27 CSV schema and accounting. Extended `tools/w27/test_audit.py` from 15 to 20 test cases: seven-day two-client roster with B completely absent (4032 expected client-intervals; 2016 missing for B), naive/offset windows, empty client and trailing garbage in timestamps. Code and tests were committed directly via GitHub contents API, **without workflows or CI**.

## Verification boundary
A locally authored **equivalent clean Python implementation** was compiled with `py_compile` and its 20 synthetic unittest cases passed on 2026-11-14. This is **not** a post-publication independent checkout-and-run of the exact GitHub blobs. The next step is to fetch the committed files to an isolated local directory and run `python -m unittest` there, with no GitHub Actions.

## Falsifiable gates
1. On a clean checkout, `python -m py_compile tools/w27/audit.py tools/w27/test_audit.py` exits zero and `cd tools/w27 && python -m unittest -v test_audit.py` reports 20 tests.
2. Seven days × 24 h × 12 intervals/h × 2 enrolled clients = **4032** expected client-intervals, even if B sends no records.
3. With one successful A interval and B entirely absent: verified hours = **1/12**; B missing = **2016**; do not infer service availability from missing intervals.
4. Any offset/naive window or noncanonical timestamp is rejected under HARP CSV policy; RFC 3339 itself permits offsets and fractions (HARP's restriction is narrower).

## Backhaul economic decision
The W23 illustrative RD$6,200 monthly allocation is not a quotation. For 540 verified monthly user-hours, RD$11.48/h; for 360, RD$17.22/h. A seven-day measurement cannot be compared with a full-month numerator without prorating costs or extrapolating with stated uncertainty. Network access requires paid/authorized upstream regardless of local Wi-Fi topology. See Internet Society's *Solutions for Traffic Backhaul in Community Networks* (2019), https://www.internetsociety.org/resources/doc/2019/solutions-for-traffic-backhaul-in-community-networks/ and IETF RFC 3339, https://www.rfc-editor.org/rfc/rfc3339.html .

## Architecture decision / blockers
- **Authorized Wi-Fi/community access:** first field pilot candidate; still needs written venue and ISP redistribution permission, dated quotes, physical A cellular-OFF DNS/dual HTTPS/public egress and gateway WAN readings.
- **Voluntary B:** feasible conditional relay; W13 quotas, W17 socket closure and W22 pinned-egress revocation remain unintegrated in production.
- **Unknown uncooperative B without HARP:** no general Internet relay demonstrated.
- **5G ProSe/SNPN:** compatible UEs, radio/network implementation and provisioning required; no PC5 field proof.
- **eSIM:** credential/profile provisioning, not free backhaul.
- **Low-cost alternatives:** authorized existing fiber plus Wi-Fi AP, point-to-point Wi-Fi to a permitted upstream, or satellite where terrestrial upstream is absent; obtain equipment, power, licensing and monthly backhaul quotes before selecting.

**Status:** public relays **NO-GO**. No physical Internet, Android builds, phone installs, RFsim, PC5, ISP invoices, GitHub Actions or CI were used in W29.
