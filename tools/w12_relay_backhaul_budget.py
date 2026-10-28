#!/usr/bin/env python3
"""HARP W12 synthetic traffic and budget model; never a physical network test."""
import argparse
import json
import math


def estimate(active_users, average_mbps_per_user, active_hours_per_day, days,
             traffic_overhead_fraction, peak_users, gb_budget,
             price_per_gb, monthly_other_cost, fixed_backhaul_cost):
    values = [active_users, average_mbps_per_user, active_hours_per_day, days,
              traffic_overhead_fraction, peak_users, gb_budget, price_per_gb,
              monthly_other_cost, fixed_backhaul_cost]
    if any(not math.isfinite(x) for x in values):
        raise ValueError('all numeric inputs must be finite')
    if not (active_users >= 0 and average_mbps_per_user > 0 and
            0 <= active_hours_per_day <= 24 and 1 <= days <= 31 and
            0 <= traffic_overhead_fraction < 1 and peak_users >= active_users and
            gb_budget >= 0 and price_per_gb >= 0 and monthly_other_cost >= 0 and
            fixed_backhaul_cost >= 0):
        raise ValueError('invalid parameter range or peak_users < active_users')
    # Decimal GB, not GiB. Mbps means 1,000,000 bits/s.
    gb_per_active_user_hour = average_mbps_per_user * 3600 / 8 / 1000 * (1 + traffic_overhead_fraction)
    monthly_gb = active_users * active_hours_per_day * days * gb_per_active_user_hour
    peak_mbps = peak_users * average_mbps_per_user * (1 + traffic_overhead_fraction)
    max_hours_per_day_under_budget = (gb_budget / (active_users * days * gb_per_active_user_hour)
                                      if active_users else None)
    return {
        'model': 'synthetic_not_measured',
        'gb_per_active_user_hour': round(gb_per_active_user_hour, 4),
        'monthly_gb': round(monthly_gb, 3),
        'peak_mbps_required': round(peak_mbps, 3),
        'monthly_metered_cost': round(monthly_gb * price_per_gb + monthly_other_cost, 2),
        'fixed_backhaul_monthly_cost': round(fixed_backhaul_cost + monthly_other_cost, 2),
        'max_active_hours_per_day_under_gb_budget': (
            None if max_hours_per_day_under_budget is None else round(max_hours_per_day_under_budget, 3)),
        'budget_sufficient': monthly_gb <= gb_budget,
        'break_even_price_per_gb_vs_fixed': (
            None if monthly_gb == 0 else round(fixed_backhaul_cost / monthly_gb, 4)),
        'caveat': 'No coverage, RF, power, ISP rights, data-plan tethering rights, or actual traffic measured.'
    }


def self_test():
    x = estimate(1, 2, 1, 30, .1, 1, 30, 20, 0, 4500)
    assert abs(x['monthly_gb'] - 29.7) < 1e-9
    assert abs(x['peak_mbps_required'] - 2.2) < 1e-9
    assert x['budget_sufficient'] is True
    y = estimate(8, 5, 2, 30, .1, 8, 100, 20, 0, 4500)
    assert abs(y['monthly_gb'] - 1188) < 1e-9
    assert y['budget_sufficient'] is False
    assert abs(y['monthly_metered_cost'] - 23760) < 1e-9
    assert abs(y['break_even_price_per_gb_vs_fixed'] - 3.7879) < 1e-4
    assert estimate(0, 1, 0, 30, 0, 0, 0, 0, 0, 0)['break_even_price_per_gb_vs_fixed'] is None
    for invalid in ((1, 1, 25, 30, 0, 1, 0, 0, 0, 0),
                    (2, 1, 1, 30, 0, 1, 0, 0, 0, 0),
                    (1, 1, 1, 30, float('nan'), 1, 0, 0, 0, 0)):
        try:
            estimate(*invalid)
            raise AssertionError('invalid input accepted')
        except ValueError:
            pass
    print('PASS_HARP_W12_SYNTHETIC_BACKHAUL_BUDGET_SELFTEST')
    print('NO_REAL_PHONE_OR_UPSTREAM_MEASUREMENT')


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--self-test', action='store_true')
    p.add_argument('--active-users', type=float, default=1)
    p.add_argument('--average-mbps-per-user', type=float, default=2)
    p.add_argument('--active-hours-per-day', type=float, default=1)
    p.add_argument('--days', type=float, default=30)
    p.add_argument('--traffic-overhead-fraction', type=float, default=.1)
    p.add_argument('--peak-users', type=float, default=1)
    p.add_argument('--gb-budget', type=float, default=30)
    p.add_argument('--price-per-gb', type=float, default=0)
    p.add_argument('--monthly-other-cost', type=float, default=0)
    p.add_argument('--fixed-backhaul-cost', type=float, default=0)
    args = p.parse_args()
    if args.self_test:
        self_test()
    else:
        try:
            print(json.dumps(estimate(args.active_users, args.average_mbps_per_user,
                args.active_hours_per_day, args.days, args.traffic_overhead_fraction,
                args.peak_users, args.gb_budget, args.price_per_gb,
                args.monthly_other_cost, args.fixed_backhaul_cost), indent=2))
        except ValueError as e:
            p.error(str(e))


if __name__ == '__main__':
    main()
