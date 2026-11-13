# W28 — Canonical UTC interval gate (2026-11-13)

## Incremental finding
W27 required five-minute alignment for each CSV record, but did **not** require the declared audit window start/end to align to the same UTC five-minute grid. A window 00:01–00:16 had three nominal slots even though its records could only be at 00:05, 00:10 and 00:15; this can silently misrepresent the observation denominator. The parser also accepted alternate ISO-like representations, complicating independent audit reproducibility.

## Change and falsifiable hypothesis
The offline W27 auditor now rejects non-UTC, non-grid-aligned window boundaries, noncanonical timestamps (exact `YYYY-MM-DDTHH:MM:00Z`, with minutes divisible by five) and whitespace-padded roster identifiers. The canonical timestamp is a **HARP CSV schema restriction**, not a claim that RFC 3339 forbids offsets or fractional seconds; RFC 3339 permits those. Tests must reject shifted start/end, abbreviated timestamps, fractional timestamps and padded roster IDs while retaining all ten W27 tests.

**Execution:** Local Python stdlib equivalent executed 2026-11-13: `python -m unittest -v test_audit.py` => **15/15 PASS**. The repository copy is source code published through the GitHub connector; no independent post-publication run, Android build, gateway capture, Internet measurement, or phone installation.

## Connectivity decision
This change increases audit reproducibility, **not** physical connectivity. Priority remains authorized community/patron Wi-Fi with funded upstream and explicit ISP permission. Voluntary B relay requires consent, authentication, pinned egress, quotas and socket revocation (W13/W17/W22 not yet integrated). Unknown noncooperating B is not a general-purpose relay. 5G ProSe/SNPN requires network and device provisioning; eSIM is profile provisioning rather than free backhaul. Compare actual ISP quotes, installation, amortization, power, upstream transfer rules and measured successful user-hours before any cost-per-hour claim. No new ISP quote or measured cost this iteration.

## Next falsifiable experiment
Re-run committed auditor from a clean checkout offline; then exercise a synthetic 7-day, two-client manifest with an entirely missing client and shifted boundaries. Separately obtain venue/ISP authorization and two dated backhaul quotes before A cellular-OFF physical DNS/dual HTTPS/egress + WAN counter trial. NO-GO public relays.

Sources: [RFC 3339 §5.6](https://www.rfc-editor.org/rfc/rfc3339.html); [Internet Society, community backhaul, 2019](https://www.internetsociety.org/resources/doc/2019/solutions-for-traffic-backhaul-in-community-networks/). No GitHub Actions or CI used.
