import unittest
import datetime as dt
from audit import audit,parse_utc
S=parse_utc('2026-11-01T00:00:00Z');E=S+dt.timedelta(minutes=15)
def row(c,t,**flags):
 d=dict(client_id=c,timestamp_utc=t,cellular_off='1',dns_ok='1',https1_ok='1',https2_ok='1',egress_ok='1');d.update(flags);return d
class AuditTests(unittest.TestCase):
 def test_multi_client(self):
  x=audit([row('A','2026-11-01T00:00:00Z'),row('B','2026-11-01T00:00:00Z',dns_ok='0')],S,E,['A','B'])['total'];self.assertEqual((x['expected'],x['success'],x['failure'],x['missing']),(6,1,1,4))
 def test_complete(self):
  r=[row('A',f'2026-11-01T00:{m:02d}:00Z') for m in (0,5,10)];self.assertEqual(audit(r,S,E,['A'])['total']['verified_hours'],.25)
 def test_missing(self):self.assertEqual(audit([],S,E,['A'])['total']['missing'],3)
 def test_duplicate(self):
  r=row('A','2026-11-01T00:00:00Z');self.assertRaises(ValueError,audit,[r,r],S,E,['A'])
 def test_invalid_flag(self):self.assertRaises(ValueError,audit,[row('A','2026-11-01T00:00:00Z',dns_ok='yes')],S,E,['A'])
 def test_unaligned(self):self.assertRaises(ValueError,audit,[row('A','2026-11-01T00:01:00Z')],S,E,['A'])
 def test_outside(self):self.assertRaises(ValueError,audit,[row('A','2026-11-01T00:15:00Z')],S,E,['A'])
 def test_unknown_client(self):self.assertRaises(ValueError,audit,[row('B','2026-11-01T00:00:00Z')],S,E,['A'])
 def test_duplicate_clients(self):self.assertRaises(ValueError,audit,[],S,E,['A','A'])
 def test_non_utc(self):self.assertRaises(ValueError,parse_utc,'2026-11-01T00:00:00+00:00')
 def test_unaligned_start(self):self.assertRaises(ValueError,audit,[],S+dt.timedelta(minutes=1),E+dt.timedelta(minutes=1),['A'])
 def test_unaligned_end(self):self.assertRaises(ValueError,audit,[],S,E+dt.timedelta(minutes=1),['A'])
 def test_noncanonical_timestamp(self):self.assertRaises(ValueError,parse_utc,'2026-11-01T00:00Z')
 def test_fractional_timestamp(self):self.assertRaises(ValueError,parse_utc,'2026-11-01T00:00:00.0Z')
 def test_whitespace_client(self):self.assertRaises(ValueError,audit,[],S,E,[' A'])
 def test_seven_days_two_clients_one_absent(self):
  end=S+dt.timedelta(days=7)
  x=audit([row('A','2026-11-01T00:00:00Z')],S,end,['A','B'])
  self.assertEqual(x['total']['expected'],4032)
  self.assertEqual(x['per_client']['B']['missing'],2016)
  self.assertEqual(x['total']['verified_hours'],1/12)
 def test_naive_window(self):self.assertRaises(ValueError,audit,[],S.replace(tzinfo=None),E,['A'])
 def test_non_utc_window(self):
  tz=dt.timezone(dt.timedelta(hours=-4))
  self.assertRaises(ValueError,audit,[],S.astimezone(tz),E.astimezone(tz),['A'])
 def test_empty_client(self):self.assertRaises(ValueError,audit,[],S,E,[''])
 def test_malformed_timestamp(self):self.assertRaises(ValueError,parse_utc,'2026-11-01T00:00:00Zjunk')
if __name__=='__main__':unittest.main()
