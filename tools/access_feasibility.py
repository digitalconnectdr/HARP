#!/usr/bin/env python3
"""HARP W3: transparent, zero-dependency planning cost/capacity model.

ALL monetary inputs are user supplied, in one chosen currency. No real
market tariffs, geographic coverage, or measured peak concurrency are implied.
"""
from __future__ import annotations

import argparse
import json
from dataclasses import dataclass, asdict

DAYS_PER_MONTH = 30
SECONDS_PER_MONTH = DAYS_PER_MONTH * 24 * 3600


@dataclass(frozen=True)
class Scenario:
    users: int
    gb_per_user_month: float
    backhaul_monthly: float
    equipment_cost: float
    amortization_months: int
    operations_monthly: float
    gateway_watts: float
    energy_price_kwh: float
    peak_multiplier: float
    backhaul_capacity_mbps: float
    currency: str = "UNSPECIFIED"


def estimate(s: Scenario) -> dict:
    if s.users <= 0 or s.amortization_months <= 0:
        raise ValueError("users and amortization_months must be positive")
    for key, val in asdict(s).items():
        if key in {"users", "amortization_months", "currency"}:
            continue
        if not 0 <= val < float("inf"):
            raise ValueError(f"{key} must be non-negative and finite")
    if s.peak_multiplier < 1:
        raise ValueError("peak_multiplier must be >= 1")
    if s.gb_per_user_month == 0:
        raise ValueError("gb_per_user_month must exceed zero")
    monthly_gb = s.users * s.gb_per_user_month
    avg_mbps = monthly_gb * 8_000 / SECONDS_PER_MONTH
    assumed_peak_mbps = avg_mbps * s.peak_multiplier
    monthly_energy_kwh = s.gateway_watts / 1000 * 24 * DAYS_PER_MONTH
    monthly_cost = (
        s.backhaul_monthly
        + s.equipment_cost / s.amortization_months
        + s.operations_monthly
        + monthly_energy_kwh * s.energy_price_kwh
    )
    return {
        "scenario": asdict(s),
        "monthly_total_gb_decimal": round(monthly_gb, 4),
        "average_demand_mbps": round(avg_mbps, 4),
        "planning_peak_mbps_not_measured": round(assumed_peak_mbps, 4),
        "capacity_margin_mbps_planning_only": round(
            s.backhaul_capacity_mbps - assumed_peak_mbps, 4
        ),
        "planning_capacity_sufficient": s.backhaul_capacity_mbps >= assumed_peak_mbps,
        "monthly_energy_kwh": round(monthly_energy_kwh, 4),
        "monthly_total_cost": round(monthly_cost, 4),
        "monthly_cost_per_user": round(monthly_cost / s.users, 4),
        "cost_per_gb_decimal": round(monthly_cost / monthly_gb, 4),
        "caveats": [
            "All prices are scenarios supplied by the user, not market quotes.",
            "30-day month, decimal GB; average bitrate excludes protocol overhead.",
            "Peak multiplier is a planning assumption, NOT a measured traffic peak.",
            "Backhaul capacity is not the same as RF airtime or guaranteed QoS.",
            "Confirm coverage, ISP redistribution rights, privacy and local regulation.",
        ],
    }


def self_test() -> None:
    s = Scenario(
        users=40,
        gb_per_user_month=2,
        backhaul_monthly=3000,
        equipment_cost=12000,
        amortization_months=36,
        operations_monthly=1000,
        gateway_watts=15,
        energy_price_kwh=14,
        peak_multiplier=20,
        backhaul_capacity_mbps=50,
        currency="DOP hypothetical",
    )
    out = estimate(s)
    assert out["monthly_total_gb_decimal"] == 80
    assert abs(out["average_demand_mbps"] - 0.24691358) < 1e-4
    assert abs(out["planning_peak_mbps_not_measured"] - 4.93827160) < 1e-4
    assert out["planning_capacity_sufficient"]
    assert abs(out["monthly_energy_kwh"] - 10.8) < 1e-9
    assert abs(out["monthly_total_cost"] - (3000 + 12000 / 36 + 1000 + 151.2)) < 1e-4
    assert not estimate(Scenario(**{**asdict(s), "backhaul_capacity_mbps": 2}))[
        "planning_capacity_sufficient"
    ]
    for overrides in (
        {"users": 0},
        {"amortization_months": 0},
        {"peak_multiplier": 0.3},
        {"gb_per_user_month": 0},
        {"energy_price_kwh": -2},
    ):
        try:
            estimate(Scenario(**{**asdict(s), **overrides}))
        except ValueError:
            pass
        else:
            raise AssertionError(f"invalid scenario accepted: {overrides}")
    print("PASS_HARP_W3_FEASIBILITY_SELFTEST")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--users", type=int)
    parser.add_argument("--gb-per-user", type=float)
    parser.add_argument("--monthly-backhaul", type=float)
    parser.add_argument("--equipment-cost", type=float)
    parser.add_argument("--amortization-months", type=int, default=36)
    parser.add_argument("--monthly-operations", type=float, default=0)
    parser.add_argument("--gateway-watts", type=float, default=0)
    parser.add_argument("--energy-price-kwh", type=float, default=0)
    parser.add_argument("--peak-multiplier", type=float, default=10)
    parser.add_argument("--backhaul-capacity-mbps", type=float)
    parser.add_argument("--currency", default="UNSPECIFIED")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return
    for name in ("users", "gb_per_user", "monthly_backhaul", "equipment_cost",
                 "backhaul_capacity_mbps"):
        if getattr(args, name) is None:
            parser.error("--" + name.replace("_", "-") + " required")
    s = Scenario(
        users=args.users,
        gb_per_user_month=args.gb_per_user,
        backhaul_monthly=args.monthly_backhaul,
        equipment_cost=args.equipment_cost,
        amortization_months=args.amortization_months,
        operations_monthly=args.monthly_operations,
        gateway_watts=args.gateway_watts,
        energy_price_kwh=args.energy_price_kwh,
        peak_multiplier=args.peak_multiplier,
        backhaul_capacity_mbps=args.backhaul_capacity_mbps,
        currency=args.currency,
    )
    try:
        result = estimate(s)
    except ValueError as exc:
        parser.error(str(exc))
    print(json.dumps(result, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
