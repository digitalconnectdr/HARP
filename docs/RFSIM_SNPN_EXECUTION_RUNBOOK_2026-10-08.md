# HARP SNPN RFsim execution runbook

**Date:** 2026-10-08

This runbook is for the first executable SNPN/KDF experiment. It is deliberately split into:

1. **KDF/authentication gate** — does not require a full SMF/UPF data session.
2. **Full registration gate** — optional second layer once the rest of the 5GC is available.

No GitHub Actions are used.

## 1. Pinned sources

RAN:

```
openairinterface/openairinterface5g
f8f769592a7030be88ede4bb5ca66fa1ca6a80e0
```

AMF:

```
openairinterface/oai-cn5g-amf
5eedea557a3745b13ed9ec4bf29e6a28bd912574
```

HARP lab identity:

```
MCC  = 999
MNC  = 99
IMSI = 999990000000001
NID  = 10000000001
SNN  = 5G:mnc099.mcc999.3gppnetwork.org:10000000001
```

Negative AMF NID:

```
10000000002
```

## 2. Build gates first

RAN:

```bash
bash tools/oai_snpn/run_pre_rfsim_gates.sh /path/to/openairinterface5g
```

Required final marker:

```
PASS_HARP_OAI_SNPN_PRE_RFSIM_GATES
```

AMF:

```bash
bash tools/oai_amf_snpn/run_amf_build_gate.sh /path/to/oai-cn5g-amf
```

Required final marker:

```
PASS_HARP_AMF_SNPN_BUILD
```

Do not proceed to RFsim if either build gate fails.

## 3. Generate experiment fixtures

```bash
python3 tools/oai_snpn/prepare_rfsim_lab_configs.py \
  --ran /path/to/openairinterface5g \
  --amf /path/to/oai-cn5g-amf \
  --out /tmp/harp-snpn-rfsim
```

Expected:

```
PASS_HARP_RFSIM_SINGLE_VARIABLE_FIXTURES
PASS_HARP_RFSIM_FIXTURE_GENERATION
```

Outputs:

```
/tmp/harp-snpn-rfsim/
  gnb.harp-snpn.conf
  nrue.harp-snpn.conf
  amf.harp-snpn-positive.yaml
  amf.harp-snpn-negative.yaml
  harp_subscriber.sql
```

The two AMF YAML files are checked by the generator to differ only in the configured `snpn_nid`.

## 4. Network addresses

The generated gNB and AMF files intentionally preserve the IP topology of the pinned OAI sample configs.

Before execution, verify that these addresses match the actual lab/container topology:

- AMF N2 address;
- gNB NG-AMF address;
- gNB NG-U address;
- MySQL hostname/address.

Address changes are transport/deployment changes and must be identical in positive and negative runs.

## 5. Subscriber reset — mandatory before every run

The AMF simple scenario retrieves Ki/OPc/SQN from MySQL and increments SQN during authentication.

Therefore run the subscriber fixture **before the positive run and again before the negative run**.

Example:

```bash
mysql -h <mysql-host> -u test -ptest oai_db \
  < /tmp/harp-snpn-rfsim/harp_subscriber.sql
```

Verify the query printed at the end shows:

```
imsi = 999990000000001
key  = FEC86BA6EB707ED08905757B1BB44B8F
sqn  = 0
opc  = C42449363BBAD02B66D16BC975D77CC1
```

This reset is essential to keep the negative experiment single-variable.

## 6. Positive experiment

### 6.1 AMF

Use:

```
amf.harp-snpn-positive.yaml
```

The upstream AMF accepts YAML through:

```bash
<amf-binary> -c /tmp/harp-snpn-rfsim/amf.harp-snpn-positive.yaml -o \
  2>&1 | tee /tmp/harp-amf-positive.log
```

Expected identity evidence:

```
HARP_SNPN_AMF_SNN 5G:mnc099.mcc999.3gppnetwork.org:10000000001
```

### 6.2 gNB

From the RAN build directory:

```bash
sudo ./nr-softmodem \
  -O /tmp/harp-snpn-rfsim/gnb.harp-snpn.conf \
  --gNBs.[0].min_rxtxtime 6 \
  --rfsim \
  --log_config.global_log_options level,nocolor,time \
  2>&1 | tee /tmp/harp-gnb-positive.log
```

Expected SNPN broadcast evidence includes:

```
HARP_SNPN_CONFIG
HARP_SNPN_BROADCAST
```

### 6.3 nrUE

From the same RAN build directory when using loopback RFsim:

```bash
sudo ./nr-uesoftmodem \
  -r 106 \
  --numerology 1 \
  --band 78 \
  -C 3619200000 \
  --rfsim \
  --rfsimulator.[0].serveraddr 127.0.0.1 \
  -O /tmp/harp-snpn-rfsim/nrue.harp-snpn.conf \
  --log_config.global_log_options level,nocolor,time \
  2>&1 | tee /tmp/harp-ue-positive.log
```

Expected UE markers:

```
PASS_V5G_SNPN_SELECT mcc=999 mnc=99 nid=10000000001
HARP_SNPN_AUTH_RESPONSE_UE nid=10000000001
```

The current experiment must not contain:

```
HARP_SNPN_AUTH_FAILURE_UE
```

after the current SNPN selection.

### 6.4 KDF/authentication gate

Run:

```bash
python3 tools/oai_snpn/check_rfsim_snpn_logs.py \
  --mode positive \
  --ue-log /tmp/harp-ue-positive.log \
  --amf-log /tmp/harp-amf-positive.log
```

Required sequence:

```
UE_PLMN=999/99
AMF_PLMN=999/099
UE_NID=10000000001
UE_AUTH_RESPONSE_NID=10000000001
UE_AUTH_FAILURE_SEEN=0
AMF_NID=10000000001
AUTH_MARKER_SEEN=1
PASS_V5G_SNPN_AUTH
PASS_V5G_SNPN_KDF_LAB
```

The checker compares PLMN numerically, so UE MNC `99` and canonical SNN MNC
`099` are treated as the same two-digit MNC representation.

The AMF marker is emitted only after RES* has been accepted:

```
PASS_V5G_SNPN_AUTH_AMF nid=10000000001
```

This is the main KDF experiment gate.

### 6.5 Optional full-registration gate

Only when the remaining core functions needed for complete registration are available:

```bash
python3 tools/oai_snpn/check_rfsim_snpn_logs.py \
  --mode positive \
  --require-registration \
  --ue-log /tmp/harp-ue-positive.log \
  --amf-log /tmp/harp-amf-positive.log
```

Additional required marker:

```
PASS_V5G_SNPN_REGISTERED_AMF nid=10000000001
PASS_V5G_SNPN_REGISTERED
```

Do not confuse failure of this later registration gate with failure of the SNPN KDF experiment.

## 7. Negative experiment — NID mismatch

Stop the AMF, gNB and nrUE from the positive run.

Re-import:

```
harp_subscriber.sql
```

This resets SQN and RAND.

Use the **same**:

- gNB config;
- nrUE config;
- IMSI;
- Ki;
- OPc;
- SQN baseline;
- PLMN;
- RFsim parameters;
- network topology.

Change only the AMF YAML:

```
amf.harp-snpn-positive.yaml
        ->
amf.harp-snpn-negative.yaml
```

The AMF now formats:

```
5G:mnc099.mcc999.3gppnetwork.org:10000000002
```

while the nrUE still selected:

```
5G:mnc099.mcc999.3gppnetwork.org:10000000001
```

Start AMF/gNB/nrUE again and capture:

```
/tmp/harp-amf-negative.log
/tmp/harp-gnb-negative.log
/tmp/harp-ue-negative.log
```

Run:

```bash
python3 tools/oai_snpn/check_rfsim_snpn_logs.py \
  --mode negative \
  --ue-log /tmp/harp-ue-negative.log \
  --amf-log /tmp/harp-amf-negative.log \
  --expected-amf-nid 10000000002
```

Required:

```
UE_PLMN=999/99
AMF_PLMN=999/099
UE_NID=10000000001
UE_AUTH_RESPONSE_NID=10000000001
UE_AUTH_FAILURE_SEEN=0
AMF_NID=10000000002
AUTH_MARKER_SEEN=0
REGISTERED_MARKER_SEEN=0
REJECT_MARKER_SEEN=1
PASS_V5G_SNPN_KDF_NEGATIVE
```

Explicit AMF rejection evidence:

```
HARP_SNPN_AUTH_REJECT_AMF nid=10000000002
```

The checker fails if the negative run reaches either:

```
PASS_V5G_SNPN_AUTH_AMF
PASS_V5G_SNPN_REGISTERED_AMF
```

## 8. Experimental invariant

For the positive/negative pair:

```
positive:
UE  SNN = ...:10000000001
AMF SNN = ...:10000000001

negative:
UE  SNN = ...:10000000001
AMF SNN = ...:10000000002
```

Everything else must remain constant.

That makes the negative test evidence that changing the NID changes the authentication/KDF outcome rather than merely changing a log string.

## 9. Gate interpretation

```
PASS_HARP_OAI_SNPN_PRE_RFSIM_GATES
        |
PASS_HARP_AMF_SNPN_BUILD
        |
PASS_HARP_RFSIM_FIXTURE_GENERATION
        |
PASS_V5G_SNPN_SELECT
        |
PASS_V5G_SNPN_AUTH
        |
PASS_V5G_SNPN_KDF_LAB
        |
PASS_V5G_SNPN_KDF_NEGATIVE
        |
[optional]
PASS_V5G_SNPN_REGISTERED
```

No RFsim gate is considered passed until produced by real RAN/AMF execution logs.


## 10. Log-session correlation rules

The RFsim checker does not trust markers found anywhere in a log file.

It uses:

- the **last** `PASS_V5G_SNPN_SELECT` event in the UE log;
- the **last** `HARP_SNPN_AMF_SNN` event in the AMF log;
- only authentication/registration/rejection markers that occur **after**
  that AMF SNN event.

This prevents an old successful authentication from contaminating a later
negative run, or an old rejection from contaminating a later positive run.

The checker also requires:

```
UE MCC/MNC == AMF SNN MCC/MNC
```

before evaluating authentication success.

Therefore these are rejected:

```
correct NID + wrong PLMN
stale PASS before current SNN
stale REJECT before current SNN
negative run with later PASS
positive run without later AUTH PASS
```

Synthetic self-test markers:

```
PASS_RFSIM_SNPN_LOG_CHECKER_SELFTEST
PASS_RFSIM_LOG_SESSION_CORRELATION
PASS_RFSIM_LOG_PLMN_MISMATCH_REJECTED
```

These remain checker-logic tests only; they are not RFsim authentication PASS
evidence.


## 11. Causal-order requirements

Within the current AMF SNPN session:

Positive mode rejects the log if an authentication rejection appears after the
current SNN, even when an authentication PASS is also present.

When `--require-registration` is used, the registration marker must occur
after authentication success:

```
HARP_SNPN_AMF_SNN
        ->
PASS_V5G_SNPN_AUTH_AMF
        ->
PASS_V5G_SNPN_REGISTERED_AMF
```

The checker rejects:

```
AUTH PASS + AUTH REJECT in the same current session
REGISTERED before AUTH PASS
```

Synthetic self-test marker:

```
PASS_RFSIM_LOG_CAUSAL_ORDER_REJECTED
```

This marker validates checker logic only; it is not RFsim evidence.


## 12. Single-variable fixture gate

Generated fixtures are validated automatically with:

```bash
python3 tools/oai_snpn/check_rfsim_fixture_pair.py /tmp/harp-snpn-rfsim
```

The gate verifies that the positive and negative AMF YAML files differ only in
`snpn_nid`, and also cross-checks the UE, gNB and subscriber fixtures:

```
UE NID              10000000001
AMF positive NID    10000000001
AMF negative NID    10000000002
gNB PLMN             999/99
gNB NID              10000000001
IMSI                 999990000000001
UE Ki == SQL Ki
UE OPc == SQL OPc
fixed SQL RAND
```

Required marker:

```
PASS_HARP_RFSIM_SINGLE_VARIABLE_FIXTURES
```

Do not start the positive/negative pair unless this gate passes.


## 13. SQN byte order and reset invariant

Pinned AMF source reads MySQL `sqn` as a decimal integer and expands it into
six bytes in big-endian order:

```
sqn[0] = bits 47..40
sqn[1] = bits 39..32
sqn[2] = bits 31..24
sqn[3] = bits 23..16
sqn[4] = bits 15..8
sqn[5] = bits 7..0
```

Therefore:

```
SQL sqn = 0
        ->
00 00 00 00 00 00
        ->
nrUE sqn = "000000"
```

The AMF updates the database with:

```
sqn = sqn + 32
```

after authentication-vector generation.

So after one authentication attempt, the database state can become:

```
decimal 32
        ->
00 00 00 00 00 20
```

That state is no longer the baseline used by the paired experiment.

Reference gate:

```bash
python3 tools/oai_amf_snpn/sqn_reference.py
```

Expected:

```
PASS_HARP_AMF_SQN_ENDIANNESS
PASS_HARP_AMF_SQN_INCREMENT_MODEL
```

Consequently, re-importing `harp_subscriber.sql` before **every** positive or
negative run is mandatory. A second run without reset is not a valid
single-variable comparison.


## 14. Deterministic RAND and AMF restart invariant

Pinned AMF source initializes:

```
authentication::no_random_delta = 0
auth_para = {}
MAX_5GS_AUTH_VECTORS = 1
```

Therefore `auth_para.random` is false by default and the first authentication
vector generated by a fresh AMF process uses:

```
RAND = 00 01 02 03 04 05 06 07
       08 09 0A 0B 0C 0D 0E 0F
```

This exactly matches the RAND baseline written by
`harp_subscriber.sql`.

However, `generate_random()` increments the process-local
`no_random_delta`. A second vector generated by the **same AMF process**
would use:

```
01 02 03 04 05 06 07 08
09 0A 0B 0C 0D 0E 0F 10
```

even if MySQL was reset.

Reference gate:

```bash
python3 tools/oai_amf_snpn/rand_reference.py
```

Expected:

```
PASS_HARP_AMF_DETERMINISTIC_RAND_MODEL
PASS_HARP_AMF_RESTART_REQUIRED_FOR_IDENTICAL_RAND
```

Therefore the positive/negative pair requires both:

1. re-import `harp_subscriber.sql`;
2. stop and restart the AMF process.

Resetting only MySQL is insufficient for a strict single-variable experiment.

The required transition is:

```
positive run
        ->
stop AMF
        ->
reset subscriber SQL
        ->
start fresh AMF
        ->
negative run
```

This restores both persistent DB state and process-local RAND state.


## 15. Expected NID-mismatch authentication path

The pinned nrUE authentication path builds Authentication Response after
deriving RES*/KAUSF/KSEAF with its selected serving-network identity.

For the current HARP negative experiment, the expected sequence is:

```
PASS_V5G_SNPN_SELECT ... nid=10000000001
        ->
HARP_SNPN_AUTH_RESPONSE_UE nid=10000000001
        ->
AMF SNN ...:10000000002
        ->
AMF RES*/HRES* mismatch
        ->
HARP_SNPN_AUTH_REJECT_AMF nid=10000000002
        ->
PASS_V5G_SNPN_KDF_NEGATIVE
```

The UE marker proves that the UE reached Authentication Response with its own
selected NID rather than failing earlier in another authentication branch.

Any:

```
HARP_SNPN_AUTH_FAILURE_UE cause=...
```

after the current SNPN selection invalidates this specific single-variable
NID experiment and must be diagnosed separately.

The pinned nrUE implementation reviewed for this experiment does not perform a
visible AUTN/SQN synchronization-failure branch before
`generateAuthenticationResp()` in the normal Authentication Request path.
Nevertheless, SQN/RAND resets remain mandatory because they are part of the
cryptographic input/state and are required for a controlled comparison.
