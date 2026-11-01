# HARP W16 — NAT64 WKP embedded-IPv4 policy and October 2026 IETF draft (2026-11-01)

**Evidence grade:** actual GitHub Java source + official RFC 6052, IANA registry and IETF Internet-Draft dated 2026-10-29; locally executed **JDK-only** regression on the exact old and patched Java source. **No Android APK build, DNS query, socket, translator, phone, RFsim, PC5, external network or Internet experiment.**

## 1. Newly falsified assumption

W15 deliberately preserved `64:ff9b::1` as an allowed IPv6 address because IANA marks the NAT64 Well-Known Prefix (WKP) `64:ff9b::/96` globally reachable. But under RFC 6052 the **last 32 bits encode an IPv4 destination**. `64:ff9b::1` represents `0.0.0.1`; `64:ff9b::a00:1` represents `10.0.0.1`.

The original HARP `SocksPolicies.isPublicInternetAddress()` accepted **ten** sampled WKP addresses embedding non-public IPv4 ranges, including RFC1918, loopback, CGNAT, link-local, documentation, benchmarking and broadcast. Its native IPv4 branch correctly rejected these embedded IPv4 destinations. This is a source-level **policy inconsistency**, not proof of a reachable private server or exploit.

**Why this became urgent:** On **29 October 2026**, IETF published `draft-ietf-v6ops-nat64-wkp-1918-09`, proposing to relax RFC 6052's existing restriction against translating WKP addresses embedding non-global IPv4. As of 1 November 2026, it is an **active Internet-Draft, not an approved RFC**; the IETF datatracker lists IESG state **AD Evaluation: Revised I-D Needed**. The draft explicitly warns against relying on a translator's implicit filter as a security boundary, and recommends explicit ACLs/firewall policy. Do not mistake a draft's proposed behavior for current normative RFC behavior or for any deployment HARP has measured.

Sources:
- RFC 6052 §2.1, §3.1: https://www.rfc-editor.org/rfc/rfc6052.html
- IANA IPv6 registry (WKP globally reachable, local-use prefix not): https://www.iana.org/assignments/iana-ipv6-special-registry
- IETF draft version 09 (published 2026-10-29): https://datatracker.ietf.org/doc/draft-ietf-v6ops-nat64-wkp-1918/09/
- Draft §5 security considerations: https://datatracker.ietf.org/doc/html/draft-ietf-v6ops-nat64-wkp-1918-09#section-5

## 2. Small code change: embedded IPv4 must also pass the existing policy

In `app/src/main/java/org/harp/l2/SocksPolicies.java`, the `Inet6Address` branch now recognizes the **exact** WKP `64:ff9b::/96`, extracts bytes 12–15 and runs them through the **same existing IPv4 policy**. It denies non-public embedded IPv4 and preserves public destinations (e.g. `64:ff9b::808:808` representing `8.8.8.8`). This avoids the simplistic but damaging alternative of blocking all NAT64 traffic.

This does **not** solve every translation alias: operator-specific NAT64 prefixes (NSP) can embed IPv4 at several RFC 6052 prefix lengths. Without an authorized operator's Pref64 information and gateway policy, do not assume they can be safely decoded. Other translation/tunneling mechanisms also need a separate threat review. This is a narrow, explicitly tested WKP fix, not a complete SSRF defense or Internet-routability oracle.

The W15 positive regression fixture was updated from `64:ff9b::1` (non-public embedded IPv4) to `64:ff9b::808:808` (public embedded IPv4), preserving the original W15 special-range negative checks.

## 3. Reproducible local test (actual source, no network)

Before modification, exact `SocksPolicies.java` Git blob:
`2595a3869e2e42f7b29e4bbf769ed219670469a8`

```text
PASS_HARP_W16_ACTUAL_SOURCE_BASELINE_NAT64_GAPS_REPRODUCED
BASELINE_WKP_NONPUBLIC_ALLOWED=10
NO_DNS_NETWORK_ANDROID_OR_NAT64_TRANSLATOR_TEST_PERFORMED
```

After modification, Git blob:
`1f758680d57bdcaffb5692594501bc710aab9d1b`

```text
PASS_HARP_W16_JDK_NAT64_WKP_EMBEDDED_IPV4_POLICY
WKP_NONPUBLIC_ALLOWED=0
NO_DNS_NETWORK_ANDROID_OR_NAT64_TRANSLATOR_TEST_PERFORMED
PASS_HARP_W15_JDK_PUBLIC_ADDRESS_POLICY_REGRESSION
```

The new test compiles with the actual `SocksDestinationPolicy.java` and `SocksPolicies.java`, uses numeric IPv6 literals only, verifies ten negative WKP cases, two positive WKP cases, native IPv6 controls, W15 controls and mixed-address `publicWeb().validateResolved()` rejection. Both the patched policy and new test have Git blobs matching the locally compiled versions.

To reproduce from the repository root using local OpenJDK (no CI):

```sh
mkdir -p /tmp/harp-w16-classes
javac -d /tmp/harp-w16-classes \
  app/src/main/java/org/harp/l2/SocksDestinationPolicy.java \
  app/src/main/java/org/harp/l2/SocksPolicies.java \
  tools/w15/SocksPublicAddressPolicySelfTest.java \
  tools/w16/SocksNat64WkpPolicySelfTest.java
java -cp /tmp/harp-w16-classes org.harp.l2.SocksNat64WkpPolicySelfTest
java -cp /tmp/harp-w16-classes org.harp.l2.SocksPublicAddressPolicySelfTest
```

`--expect-baseline-gaps` is exclusively for the **pre-W16** policy source and must fail against the fixed source.

## 4. Architecture, economics and next falsifiable gates

This finding does not create new Internet backhaul for A. It protects a narrow class of relay egress destinations. **Priority P0** remains a consenting venue and ISP-authorized guest Wi-Fi uplink, with A's cellular data OFF, real DNS/HTTPS/gateway egress, isolation, measured usable user-hours and dated operator costs. The previous 25/60 Mbps and RD$ traffic scenarios are synthetic; **no current ISP quote or new cost observation was obtained**.

**W16-G1:** local Android build with patched source (NOT DONE). **W16-G2:** controlled on-device negative destination test with an explicitly authorized NAT64 translator (NOT DONE). **W16-G3:** operator-specific Pref64/NSP detection and upstream ACL review, with no bypass of private destinations (NOT DONE). **W16-G4:** regression of W14 TCP/UDP mismatch, W13 quotas and W11 peer identity before any public relay (NOT DONE).

**Other routes:** voluntary B needs consent, ISP redistribution rights, authenticated pairing and quotas; unknown nonconsenting stock B is not an available relay. Community networks require permitted backhaul. 5G ProSe/SNPN still requires provisioned UEs and participating network, with no physical PC5 or RFsim PASS. eSIM provisions credentials and does not generate free radio connectivity.

**Operational constraints:** no GitHub Actions or CI, no phone installs, no external probing, no physical Internet or NAT64 result asserted.
