#!/usr/bin/env python3
"""Offline CSV interval audit; never sends network traffic."""
import argparse, csv, datetime as dt, json
FIELDS=('client_id','timestamp_utc','cellular_off','dns_ok','https1_ok','https2_ok','egress_ok')
FLAGS=FIELDS[2:]
def parse_utc(s):
    if not s.endswith('Z'): raise ValueError('timestamp must end in Z')
    t=dt.datetime.fromisoformat(s[:-1]+'+00:00')
    if t.second or t.microsecond or t.minute%5: raise ValueError('unaligned UTC interval')
    return t
def audit(rows,start,end,clients):
    if end<=start or (end-start).total_seconds()%300: raise ValueError('invalid window')
    if not clients or len(set(clients))!=len(clients) or any(not c for c in clients): raise ValueError('invalid clients')
    n=int((end-start).total_seconds()/300)
    if n>105120: raise ValueError('window too large')
    seen=set(); success={c:0 for c in clients}; failure={c:0 for c in clients}
    for r in rows:
        if not all(k in r for k in FIELDS): raise ValueError('missing columns')
        c=r['client_id']; t=parse_utc(r['timestamp_utc'])
        if c not in success or not start<=t<end: raise ValueError('unknown client/outside window')
        if (c,t) in seen: raise ValueError('duplicate interval')
        seen.add((c,t))
        if any(r[k] not in ('0','1') for k in FLAGS): raise ValueError('invalid flag')
        if all(r[k]=='1' for k in FLAGS): success[c]+=1
        else: failure[c]+=1
    per={}
    for c in clients:
        s,f=success[c],failure[c]; m=n-s-f
        per[c]=dict(expected=n,success=s,failure=f,missing=m,coverage_fraction=(s+f)/n,verified_hours=s/12,possible_upper_hours=(s+m)/12)
    total={k:sum(v[k] for v in per.values()) for k in ('expected','success','failure','missing','verified_hours','possible_upper_hours')}
    total['coverage_fraction']=(total['success']+total['failure'])/total['expected']
    return dict(per_client=per,total=total,note='Offline CSV assertions only; no proof of probe authenticity, physical Internet or ISP billing')
def main():
    p=argparse.ArgumentParser()
    p.add_argument('csv_path');p.add_argument('--start',required=True);p.add_argument('--end',required=True);p.add_argument('--clients',required=True)
    a=p.parse_args()
    with open(a.csv_path,newline='',encoding='utf-8') as f:
        result=audit(csv.DictReader(f),parse_utc(a.start),parse_utc(a.end),a.clients.split(','))
    print(json.dumps(result,indent=2,sort_keys=True))
if __name__=='__main__': main()
