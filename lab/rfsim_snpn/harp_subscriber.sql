-- HARP SNPN RFsim subscriber fixture
-- Keeps AKA material identical between positive and negative NID experiments.
-- OAI RAN DB schema anchor:
-- openairinterface/openairinterface5g
-- f8f769592a7030be88ede4bb5ca66fa1ca6a80e0

DELETE FROM users
WHERE imsi = '999990000000001'
  AND mmeidentity_idmmeidentity = 1;

INSERT INTO users (
  imsi,
  msisdn,
  imei,
  imei_sv,
  ms_ps_status,
  rau_tau_timer,
  ue_ambr_ul,
  ue_ambr_dl,
  access_restriction,
  mme_cap,
  mmeidentity_idmmeidentity,
  `key`,
  `RFSP-Index`,
  urrp_mme,
  sqn,
  rand,
  OPc
) VALUES (
  '999990000000001',
  '1',
  '55000000000000',
  NULL,
  'PURGED',
  50,
  40000000,
  100000000,
  47,
  0,
  1,
  UNHEX('fec86ba6eb707ed08905757b1bb44b8f'),
  0,
  0,
  0,
  UNHEX('000102030405060708090A0B0C0D0E0F'),
  UNHEX('C42449363BBAD02B66D16BC975D77CC1')
);

SELECT
  imsi,
  HEX(`key`) AS key_hex,
  sqn,
  HEX(rand) AS rand_hex,
  HEX(OPc) AS opc_hex
FROM users
WHERE imsi = '999990000000001';
