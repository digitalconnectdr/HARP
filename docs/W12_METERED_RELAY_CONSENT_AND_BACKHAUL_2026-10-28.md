# HARP W12 — Metered relay consent, real traffic budget, and Android upstream policy (2026-10-28)

**Evidence grade:** current HARP source audit on `main`, official Android SDK documentation, deterministic offline arithmetic. **No Android build, RFsim, phone, AP, ISP agreement, live Internet test, or actual mobile-data usage in this run.**

## Decision: W5's flat volunteer incentive cannot stand in for upstream data cost

The W5 illustrative RD$165.12/month/volunteer is **not a validated cost of Internet forwarding**. A's missing mobile subscription does not eliminate B's upstream bill. When B uses a metered cellular plan, traffic volume, operator permissions, consent and hard quotas dominate feasibility. Do not multiply the W5 per-volunteer assumption by a density estimate and call it a network budget.

## Source audit: real, testable policy gap in HARP L2

Read directly on `main`:
- `app/src/main/java/org/harp/l2/InternetNetworkSelector.java`: `choose()` prefers direct validated unmetered, **then falls back to direct validated metered**, then non-VPN other, then **VPN fallback**. It does not require explicit metered authorization, a byte cap, a sponsorship/ISP contract, or a VPN-underlay cost check.
- `app/src/main/java/org/harp/l2/MainActivity.java`: the user explicitly taps **B — RELAY**. That is consent to begin a lab role, **not informed consent to share a particular metered/roaming plan or an amount of data**. No explicit budget or upstream choice is collected here.
- `app/src/main/java/org/harp/l2/HarpAwareController.java`: Stage-1 and Stage-2 select B's upstream; `registerRelayUpstreamWatch()` stops on loss of INTERNET/VALIDATED/Aware exclusion, but does **not** stop when the chosen network becomes metered or roaming, nor when a GB cap is exhausted.
- `app/src/main/java/org/harp/l2/Stage2RelayServer.java`: at most eight **simultaneous SOCKS TCP sessions**, not eight authenticated Phone A devices, and no aggregate byte quota is enforced.
- W11 already established a separate public-release blocker: shared lab Wi-Fi Aware PSK. W12 does not repeat or fix that identity problem.

**Risk:** B may incur unexpected metered charges or forward over a VPN whose underlying data costs/terms are unknown. This is a **source-level risk**, not an observed billing incident or proof of a successful relay.

Android explicitly documents `NET_CAPABILITY_NOT_METERED`, `NET_CAPABILITY_TEMPORARILY_NOT_METERED` (which can change), and callback-based capability changes. `NET_CAPABILITY_VALIDATED` means Android detected Internet; it does **not** authorize redistribution or guarantee throughput. The absence of `NOT_METERED` means the app should conservatively treat the network as possibly metered; it does not prove a charge. A VPN can obscure its underlying transport and policy.

Official sources:
- https://developer.android.com/reference/android/net/NetworkCapabilities
- https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback
- https://developer.android.com/develop/connectivity/network-ops/reading-network-state

## Quantitative correction (synthetic, decimal GB)

At average traffic rate `r` Mbps per active user, `u` simultaneously active users, `h` hours/day, `d` days and actual traffic overhead fraction `o`:

```
monthly_GB = u * r * h * d * (3600 / 8 / 1000) * (1 + o)
peak_required_Mbps = peak_users * r * (1 + o)
metered_monthly_cost = monthly_GB * assumed_price_per_GB + other_monthly_cost
break_even_price_per_GB = assumed_fixed_backhaul_monthly_cost / monthly_GB
```

**Do not confuse** the overhead applied to transferred bytes here with the separate reserved-capacity headroom in W7 (`required_Mbps = payload_Mbps / (1 - reserved_fraction)`). They are different assumptions.

| Synthetic demand | Volume with 10% traffic overhead | Interpretation |
|---|---:|---|
| 1 active A, 2 Mbps average, 1 h/day, 30 days | **29.7 GB/month** | Already substantial for a small metered allowance |
| 1 active A, 5 Mbps average, 2 h/day, 30 days | **148.5 GB/month** | Requires explicit budget and throttling/stop |
| 8 simultaneous demand streams, 5 Mbps each, 2 h/day, 30 days | **1,188 GB/month** | Stress model only; not evidence that current HARP supports eight A devices |

For the third **synthetic** case, at a hypothetical **RD$20/GB**, traffic alone would cost **RD$23,760/month**. A hypothetical fixed-backhaul fee of **RD$4,500/month** breaks even at **RD$3.7879/GB**, before differences in equipment, power, service quality, installation and commercial rights. These are **not real RD carrier prices, quotes or agreements**. With only 100 GB/month, the third demand scenario permits about **0.168 h/day (10.1 minutes/day)** at that sustained rate.

A fixed backhaul is not automatically cheaper or legal to share: obtain actual ISP public-guest/redistribution rights, contention, upload limits, SLA, caps and monthly fees first.

## New local artifact and verification

- [Offline W12 backhaul model](../tools/w12_relay_backhaul_budget.py), no dependencies beyond Python standard library, no network calls.
- Executed locally: `PASS_HARP_W12_SYNTHETIC_BACKHAUL_BUDGET_SELFTEST`.
- Source Git blob of locally tested tool equals published blob: `26094907c64bfac5c17de1563dae95f7a3b648c4`.
- Tool distinguishes average traffic, peak bandwidth, decimal GB, quota, assumed price, fixed backhaul and break-even. All input numbers are synthetic unless independently measured.

Example:
```bash
python3 tools/w12_relay_backhaul_budget.py --self-test
python3 tools/w12_relay_backhaul_budget.py --active-users 1 --average-mbps-per-user 5 \
  --active-hours-per-day 2 --days 30 --peak-users 1 --gb-budget 30 \
  --price-per-gb 20 --fixed-backhaul-cost 4500
```

## Next falsifiable engineering gates — do not silently change lab behavior

1. **P12-0 (source)**: add a B-facing consent screen that identifies selected upstream network, metered/roaming/VPN state, explicitly permitted monthly GB and whether carrier/ISP terms allow sharing. No implicit acceptance from tapping B — RELAY.
2. **P12-1 (policy)**: default **deny metered, roaming, or opaque VPN** until informed opt-in and a verified entitlement/underlay. Re-evaluate capabilities via callbacks; fail closed when conditions change.
3. **P12-2 (enforcement)**: add accurate aggregate bytes in both directions, hard budget, rate limit, expiry and revocation; test counters under half-close, reconnect and concurrent streams. Do not assume Android traffic estimates are billing-grade.
4. **P12-3 (negative tests)**: unmetered -> metered transition, cellular fallback, VPN-underlay, 30GB exhaustion, upstream validation loss, network switch, A disconnect; all must stop when outside approved policy.
5. **P12-4 (physical)**: after authenticated Aware pairing, local APK build and **explicit consent**, demonstrate A HTTPS through B and B egress; record actual bytes and battery. None has occurred.
6. **P0 direct Wi-Fi** remains the quickest Internet proof: signed venue+ISP permissions, isolated AP, A mobile data off, measured DNS/HTTPS/egress and price/user-hour. The W12 tool does not replace this.
7. **Long horizon**: W5 stock B requires opt-in and sharing support; unknown non-HARP B is **not** an arbitrary relay. Community mesh requires APs and authorized backhaul. 5G ProSe/SNPN requires capable provisioned UEs/RAN and spectrum; eSIM (GSMA SGP.21/22) provisions subscription credentials, not free connectivity.

**Status: code audit + mathematical self-test only; B metered/roaming/VPN quota policy remains NOT IMPLEMENTED. Public relay is still NO-GO for W11 peer-authentication and W12 billing-consent reasons. No GitHub Actions, CI, phone installs, external network scans or physical PASS.**
