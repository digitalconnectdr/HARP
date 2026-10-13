# HARP SNPN RFsim execution runbook

**Date:** 2026-10-08

This runbook is for the first executable SNPN/KDF experiment. It is deliberately split into:

1. **KDF/authentication gate** — does not require a full SMF/UPF data session.
2. **Full registration gate** — optional second layer once the rest of the 5GC is available.

No GitHub Actions are used.

> **Execution-order correction (2026-10-12):** generate the RFsim fixtures from
> both clean pinned worktrees **before** running either build gate. The build
> gates apply the HARP blueprints and therefore modify those worktrees. The
> preferred entry point is now `tools/oai_snpn/run_rfsim_auth_lab.sh`, which
> enforces this order automatically.

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

Required final markers:

```
PASS_HARP_OAI_RFSIM_BINARIES
PASS_HARP_OAI_SNPN_PRE_RFSIM_GATES
```

The RAN gate now also verifies these exact runtime artifacts:

```
<build-dir>/nr-softmodem
<build-dir>/nr-uesoftmodem
<build-dir>/librfsimulator.so
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

## 5.1 Runtime artifact preflight

Before starting AMF/gNB/nrUE, run:

```bash
bash tools/oai_snpn/check_auth_only_runtime_artifacts.sh \
  /path/to/oai-build \
  /path/to/amf-binary \
  /tmp/harp-snpn-rfsim
```

Required marker:

```
PASS_HARP_AUTH_ONLY_RUNTIME_ARTIFACTS
```

This verifies:

```
nr-softmodem
nr-uesoftmodem
librfsimulator.so
AMF executable
all generated fixtures
single-variable fixture invariant
positive auth-only AMF config
negative auth-only AMF config
```

It does not prove that MySQL, N2 networking or RFsim sockets are reachable yet;
those are runtime checks during the actual experiment.

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


## 16. nrUE ngKSI process-state invariant

Pinned nrUE source stores the NAS Key Set Identifier on the first
Authentication Request:

```
if (!nas->ksi)
    store ngKSI
```

If the same nrUE process later receives an Authentication Request with the same
ngKSI, the current implementation follows:

```
same ngKSI already stored
        ->
generateAuthenticationFailure(... ngKSI_already_in_use)
```

instead of constructing Authentication Response.

Therefore a strict positive/negative pair must restart the nrUE process as well
as the AMF.

Required reset sequence:

```
positive run
        ->
stop nrUE
stop AMF
[stop/restart gNB for clean RRC/log isolation]
        ->
reset subscriber SQL
        ->
start fresh AMF
start fresh gNB
start fresh nrUE
        ->
negative run
```

The cryptographic reasons are now separated:

- SQL reset restores Ki/OPc/SQN/RAND baseline;
- AMF restart restores `no_random_delta=0`;
- nrUE restart clears stored ngKSI/security/NAS process state;
- gNB restart provides a clean RRC/RFsim/log session.

A negative run that emits:

```
HARP_SNPN_AUTH_FAILURE_UE
```

is invalid for the NID experiment and must be rerun from fresh processes.


## 17. Minimal service stack for the first auth/KDF gate

For the first HARP SNPN experiment, do **not** start the full 5GC unless a later
registration/PDU-session gate requires it.

Pinned AMF behavior was audited around the Registration Request and
authentication-vector path.

With:

```
enable_simple_scenario: yes
```

the AMF executes:

```
authentication_vectors_generator_in_udm()
        ->
authentication_vectors_generator_in_ausf()
        ->
derive_kamf()
```

locally inside the AMF, using subscriber data from MySQL.

The pinned AMF also initializes:

```
enable_nf_registration = false
```

so NRF registration is not required for this path.

The HARP marker:

```
PASS_V5G_SNPN_AUTH_AMF
```

is emitted immediately after RES*/HRES* authentication acceptance and before
Security Mode Control. No SMF/UPF/PCF interaction is required to reach that
marker.

Therefore the minimum runtime stack for the first positive/negative KDF proof is:

```
MySQL subscriber DB
        +
HARP-patched AMF
        +
HARP-patched gNB (RFsim)
        +
HARP-patched nrUE (RFsim)
```

External services that are **not required** to reach the auth/KDF gate:

```
NRF
external AUSF
external UDM
UDR
NSSF
PCF
SMF
UPF
```

SMF/UPF and the broader core become relevant only for later registration /
PDU-session / user-plane gates.

This separation is intentional: failure of a later core-network service must
not be allowed to obscure the SNPN KDF experiment.


## 18. Auth-only lab readiness preflight

Before starting AMF/gNB/nrUE, run:

```bash
bash tools/oai_snpn/check_rfsim_auth_lab_ready.sh \
  /tmp/harp-snpn-rfsim \
  /path/to/amf \
  /path/to/nr-softmodem \
  /path/to/nr-uesoftmodem
```

The preflight verifies:

- all five generated fixture files exist;
- AMF/gNB/nrUE binaries exist and are executable;
- the RFsim single-variable fixture checker passes;
- MySQL host/user/password/database are present in the generated AMF YAML;
- MySQL hostname resolves when `getent` is available;
- the `mysql` client is installed;
- a real `SELECT 1` succeeds against the configured database;
- the configured AMF N2 interface exists when the `ip` command is available.

Stock pinned AMF values inherited by the generated fixture are:

```
database host     mysql
database user     test
database password test
database name     oai_db
AMF N2 interface  eth0
AMF N2 port       38412
```

For a host-native or custom-container topology, overrides are available:

```
HARP_MYSQL_HOST
HARP_MYSQL_USER
HARP_MYSQL_PASSWORD
HARP_MYSQL_DATABASE
HARP_AMF_N2_IFACE
```

Required final marker:

```
PASS_HARP_RFSIM_AUTH_LAB_PREFLIGHT
```

Supporting markers may include:

```
PASS_HARP_RFSIM_SINGLE_VARIABLE_FIXTURES
PASS_HARP_MYSQL_HOST_RESOLUTION
PASS_HARP_MYSQL_CONNECTIVITY
PASS_HARP_AMF_N2_INTERFACE
```

Do not start the paired authentication experiment until the final preflight
marker is produced in the target lab environment.


## 19. Topology must match the actual fixture files

The auth-lab preflight reads deployment values from the files that AMF/gNB will
actually consume. Environment variables are **not** allowed to override MySQL,
N2 interface or N2/NG-U IP values only inside the checker.

The pinned/generated gNB baseline currently carries:

```
AMF N2 peer    192.168.71.132
gNB N2 local   192.168.71.140
gNB NG-U local 192.168.71.140
```

The pinned/generated AMF baseline carries:

```
N2 interface   eth0
MySQL host     mysql
MySQL user     test
MySQL password test
database       oai_db
```

If the runtime topology differs, change the actual generated fixtures:

- update database/N2 deployment fields in **both**
  `amf.harp-snpn-positive.yaml` and
  `amf.harp-snpn-negative.yaml` identically;
- update the gNB N2/NG-U addresses in `gnb.harp-snpn.conf`;
- do not change IMSI/Ki/OPc/SQN/PLMN/NID experimental fields;
- rerun:

```bash
python3 tools/oai_snpn/check_rfsim_fixture_pair.py /tmp/harp-snpn-rfsim
```

before the readiness preflight.

Then choose one topology mode:

Host-native, same network namespace:

```bash
export HARP_TOPOLOGY_MODE=host
```

The preflight requires the AMF and gNB N2 addresses from the fixture to be
present on the host.

Container/network-namespace deployment:

```bash
export HARP_TOPOLOGY_MODE=container
export HARP_TOPOLOGY_CONFIRMED=1
```

Set the confirmation only after verifying that the addresses encoded in the
fixture are actually routed/reachable between the AMF and gNB namespaces.

The final marker:

```
PASS_HARP_RFSIM_AUTH_LAB_PREFLIGHT
```

is not emitted until one topology mode has passed.


## 17. Minimal auth/KDF laboratory stack

The first SNPN/KDF proof does **not** require the full 5GC.

With:

```
support_features_options:
    enable_simple_scenario: yes
```

the pinned AMF:

- generates UDM authentication vectors internally;
- generates AUSF-side vectors internally;
- derives KAMF locally;
- disables NF registration;
- disables NSSF;
- disables PCF;
- disables advanced-feature paths that require external UDM/NRF interactions;
- reads Ki/OPc/SQN directly from MySQL.

Pinned source evidence:

```
auth_vectors_generator()
    if simple_scenario:
        authentication_vectors_generator_in_udm()
        authentication_vectors_generator_in_ausf()
        derive_kamf()
```

and the local UDM path calls:

```
connect_to_mysql()
get_mysql_auth_info()
generate_5g_he_av_in_udm(... nc->serving_network ...)
mysql_push_rand_sqn()
mysql_increment_sqn()
```

Therefore the **authentication-only** experiment requires:

```
nrUE
gNB
AMF
MySQL
```

It does not require these external services for the auth/KDF gate:

```
AUSF
UDM
UDR
NRF
PCF
NSSF
SMF
UPF
```

SMF/UPF become relevant when progressing beyond authentication toward PDU
session/data-plane validation. Full registration may also require additional
core interactions depending on the exact procedure reached.

Configuration gate:

```bash
python3 tools/oai_amf_snpn/check_auth_only_config.py   /tmp/harp-snpn-rfsim/amf.harp-snpn-positive.yaml
```

Expected:

```
PASS_HARP_AMF_AUTH_ONLY_CONFIG
```

The fixture generator now runs this check for both positive and negative AMF
YAML files automatically.

Synthetic self-test:

```bash
bash tools/oai_amf_snpn/run_auth_only_config_selftest.sh
```

Expected:

```
PASS_AMF_AUTH_ONLY_CONFIG_SELFTEST
```

This separation is intentional:

```
Gate 1: SNPN selection + authentication/KDF
        -> minimal stack

Gate 2: complete registration
        -> add only what that procedure requires

Gate 3: PDU session + Internet data plane
        -> SMF/UPF and remaining data-plane dependencies
```

This minimizes infrastructure cost for the first proof.


## 12. Local single-case runner

A local orchestration helper is available for the actual RFsim authentication
experiment:

```bash
bash tools/oai_snpn/run_rfsim_auth_case.sh \
  positive \
  /path/to/oai-build \
  /path/to/amf-binary \
  /tmp/harp-snpn-rfsim
```

Then reset and run the negative case with the same RAN build, subscriber
fixture and topology:

```bash
bash tools/oai_snpn/run_rfsim_auth_case.sh \
  negative \
  /path/to/oai-build \
  /path/to/amf-binary \
  /tmp/harp-snpn-rfsim
```

The runner:

- invokes the runtime-artifact gate;
- invokes the live MySQL/N2/topology preflight;
- resets the subscriber fixture before each case;
- starts AMF, gNB and nrUE locally;
- writes separate AMF/gNB/nrUE logs;
- stops background processes after the UE runtime window;
- invokes the existing positive or negative RFsim log checker.

Default process window:

```
HARP_RFSIM_TIMEOUT=45
```

Optional log directory:

```
HARP_LOG_DIR=/tmp
```

For a command-plan inspection without starting MySQL/RFsim processes:

```bash
HARP_DRY_RUN=1 bash tools/oai_snpn/run_rfsim_auth_case.sh \
  positive /path/to/oai-build /path/to/amf-binary /tmp/harp-snpn-rfsim
```

Dry-run output is not experimental evidence. The only real case marker is:

```
PASS_HARP_RFSIM_AUTH_CASE mode=positive
PASS_HARP_RFSIM_AUTH_CASE mode=negative
```

and it is emitted only after the existing log checker accepts the captured
runtime logs.

No GitHub Actions are involved.


## 21. Early-failure handling and evidence preservation

The single-case runner now treats RFsim execution as an evidence-producing
experiment rather than a fixed sleep followed by log inspection.

After AMF and gNB start, the runner waits a short startup grace:

```bash
HARP_STARTUP_GRACE=2
```

If AMF, gNB or nrUE exits before the mode-specific authentication evidence is
present, the run fails immediately and prints the tail of the corresponding
log. This avoids waiting the entire RFsim window for a process that already
died.

The main runtime window begins after nrUE start:

```bash
HARP_RFSIM_TIMEOUT=45
```

The runner polls for the minimum evidence needed before invoking the strict log
checker.

Positive readiness:

```
PASS_V5G_SNPN_SELECT
HARP_SNPN_AUTH_RESPONSE_UE
HARP_SNPN_AMF_SNN
PASS_V5G_SNPN_AUTH_AMF
```

Negative readiness:

```
PASS_V5G_SNPN_SELECT
HARP_SNPN_AUTH_RESPONSE_UE
HARP_SNPN_AMF_SNN
HARP_SNPN_AUTH_REJECT_AMF
```

These polling markers do **not** replace the existing log checker. They only
decide when enough runtime material exists to stop the processes and run the
full causal/session validation.

Every real case receives a unique local evidence directory:

```
/tmp/harp-rfsim-positive-<run-id>/
/tmp/harp-rfsim-negative-<run-id>/
```

Each directory contains:

```
harp-amf-<mode>.log
harp-gnb-<mode>.log
harp-ue-<mode>.log
evidence-manifest.txt
checksums.sha256
configs/
```

The evidence manifest records mode, timestamps, result, failure reason,
runtime paths, topology mode and the HARP commit when available. SHA-256
checksums cover the runtime binaries, logs and the original experiment
fixtures.

Sensitive fixture handling is deliberate:

- the subscriber SQL is **not copied** into the evidence directory;
- its SHA-256 is recorded instead;
- the copied AMF YAML has the database password redacted;
- gNB/nrUE configs may be copied because they are needed for RFsim reproduction.

The runner always prints:

```
HARP_RFSIM_EVIDENCE_DIR=<path>
```

for a real run, including failed runs, so diagnostics are not lost.

Synthetic dry-run coverage is provided by:

```bash
bash tools/oai_snpn/run_rfsim_auth_case_selftest.sh
```

Expected:

```
PASS_HARP_RFSIM_AUTH_CASE_RUNNER_SELFTEST
```

This is orchestration coverage only and is not RFsim authentication evidence.


## 22. Paired positive/negative RFsim runner

To reduce operator error in the single-variable experiment, the preferred
entry point is now:

```bash
bash tools/oai_snpn/run_rfsim_auth_pair.sh \
  /path/to/oai-build \
  /path/to/amf-binary \
  /tmp/harp-snpn-rfsim
```

The pair runner first executes the existing fixture-pair invariant checker and
then launches two completely separate single-case runs:

```
positive
  -> fresh AMF/gNB/nrUE processes
  -> subscriber reset
  -> strict positive checker
  -> evidence directory

negative
  -> fresh AMF/gNB/nrUE processes
  -> subscriber reset
  -> strict negative checker
  -> evidence directory
```

Because each case uses the single-case runner, process state, SQN and
deterministic-RAND state are reset as required by the experiment.

The pair runner refuses to emit its final marker unless both case manifests say:

```
RESULT=PASS
```

It then creates:

```
/tmp/harp-rfsim-pair-<pair-id>/
  pair-manifest.txt
  fixture-checksums.sha256
```

and emits:

```
PASS_HARP_RFSIM_AUTH_PAIR pair_id=<pair-id>
```

A dry-run pair marker is separate:

```
PASS_HARP_RFSIM_AUTH_PAIR_DRY_RUN
```

and is not experimental evidence.

The pair-runner structural self-test is:

```bash
bash tools/oai_snpn/run_rfsim_auth_pair_selftest.sh
```

Expected:

```
PASS_HARP_RFSIM_AUTH_PAIR_RUNNER_SELFTEST
```

No GitHub Actions are required for either runner.


## 23. End-to-end local RFsim auth lab runner

The complete host-local sequence is now orchestrated by:

```bash
bash tools/oai_snpn/run_rfsim_auth_lab.sh \
  /path/to/clean-openairinterface5g \
  /path/to/clean-oai-cn5g-amf
```

Both checkouts must be disposable clean worktrees at the pinned commits.

The runner deliberately executes these stages in this exact order:

```
1. build-environment preflight
2. generate RFsim fixtures from clean pinned sources
3. apply HARP RAN blueprints + build/test RAN + RFsim artifacts
4. apply HARP AMF blueprints + build AMF
5. execute the positive/negative paired RFsim auth experiment
```

The order of stages 2-4 is important. The fixture generator verifies that both
upstream worktrees are clean and pinned. The RAN and AMF build gates then apply
HARP patches, which intentionally make those disposable worktrees dirty.
Generating fixtures after the build gates would therefore be rejected.

Optional paths:

```bash
export HARP_RAN_BUILD_DIR=/path/to/ran-build
export HARP_AMF_BUILD_DIR=/path/to/amf-build
export HARP_LOG_DIR=/path/to/evidence
export HARP_PAIR_ID=my-run-id
```

A command-plan inspection is available without compiling or starting RFsim:

```bash
HARP_DRY_RUN=1 bash tools/oai_snpn/run_rfsim_auth_lab.sh \
  /path/to/ran-checkout \
  /path/to/amf-checkout
```

Dry-run marker:

```
PASS_HARP_RFSIM_AUTH_LAB_DRY_RUN
```

A real successful end-to-end run emits:

```
PASS_HARP_RFSIM_AUTH_PAIR pair_id=<pair-id>
PASS_HARP_RFSIM_AUTH_LAB pair_id=<pair-id>
```

The structural ordering self-test is:

```bash
bash tools/oai_snpn/run_rfsim_auth_lab_selftest.sh
```

Expected:

```
PASS_HARP_RFSIM_AUTH_LAB_RUNNER_SELFTEST
```

The build-environment preflight also honors `HARP_ASN1C_EXEC` when a pinned
asn1c binary is installed outside `PATH`.

No GitHub Actions are used by this path.
