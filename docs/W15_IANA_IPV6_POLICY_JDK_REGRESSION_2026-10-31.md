# HARP W15 — IANA IPv6 destination policy hardening with actual-source JDK regression (2026-10-31)

**Evidence grade:** official IANA IPv6 special-purpose registry + exact GitHub Java source, compiled and tested **locally with OpenJDK 21**. This is a source-level fix and offline unit test. **Not** an Android build, a SOCKS network test, an installed APK, a physical Internet demonstration, an RFsim/PC5 test, or an operator/backhaul agreement.

## 1. New falsified assumption: Java non-local != globally reachable

The W14 review established that B only accepts SOCKS5 CONNECT and that W13 quotas are not integrated. A new inspection of `app/src/main/java/org/harp/l2/SocksPolicies.java` identified a separate, testable issue: the public-web destination filter rejects RFC1918/private and selected reserved IPv6 ranges, but its IPv6 branch accepts **five additional IANA ranges explicitly marked `Globally Reachable = False`**.

Before modification, `SocksPolicies.java` Git blob `35a85fc66279f0f802fe6501322c41f0d47afe1a` returned `true` for both tested addresses in each of these ranges:

| IPv6 range | IANA purpose | Baseline source behavior | W15 behavior |
|---|---|---|---|
| `64:ff9b:1::/48` | local-use IPv4/IPv6 translation | allowed | denied |
| `100::/64` | discard-only | allowed | denied |
| `2001:2::/48` | benchmarking | allowed | denied |
| `3fff::/20` | documentation | allowed | denied |
| `5f00::/16` | SRv6 SID block | allowed | denied |

**Why it matters:** the label `publicWeb()` should not authorize destinations which IANA does not designate globally reachable. In unusual routing environments, non-global address acceptance may also create unintended egress to local/special networks. This is a **policy gap**, not evidence of an exploitable SSRF incident, an actual reachable host, or an unauthorized connection.

Authoritative registry: https://www.iana.org/assignments/iana-ipv6-special-registry/ ; IANA registry includes an explicit `Globally Reachable` field. See also https://www.rfc-editor.org/rfc/rfc6890.html .

## 2. Small source change, conservative scope

The W15 change adds explicit byte-prefix checks for those five ranges in the existing `Inet6Address` branch. Existing IPv4 rules and the public-web port restriction (80/443) are unchanged. Valid test examples `2606:4700:4700::1111`, `2001:3::1`, and `64:ff9b::1` remain accepted by the policy.

**This is not a complete machine-synchronized IANA address classifier.** The registry can evolve and some special ranges have nuanced global-reachability semantics. A future release gate should compare policy rules against a pinned IANA snapshot, review exceptions, and avoid interpreting the generic Java `isSiteLocalAddress()` or `isLinkLocalAddress()` predicates as a complete Internet-routability oracle.

## 3. Executed regression: actual GitHub Java, not synthetic stubs

Source verification before the fix:

```
git hash-object app/src/main/java/org/harp/l2/SocksPolicies.java
35a85fc66279f0f802fe6501322c41f0d47afe1a
PASS_HARP_W15_ACTUAL_SOURCE_BASELINE_GAPS_REPRODUCED
```

Ten literal IPv6 addresses (two per prefix) reproduced the gap with the original exact source. After the fix:

```
git hash-object app/src/main/java/org/harp/l2/SocksPolicies.java
2595a3869e2e42f7b29e4bbf769ed219670469a8
PASS_HARP_W15_JDK_PUBLIC_ADDRESS_POLICY_REGRESSION
NO_NETWORK_OR_ANDROID_TEST_PERFORMED
```

The test also checks previously blocked IPv4/IPv6 cases, positive global address examples, mixed public/private resolution rejection, and rejection of a non-web port. Both old and new source versions were compiled using `javac 21` with the **real** `SocksDestinationPolicy.java` dependency. No DNS query, socket or external connection was made.

To reproduce **locally**, from the repository root:

```sh
mkdir -p /tmp/harp-w15-classes
javac -d /tmp/harp-w15-classes \
  app/src/main/java/org/harp/l2/SocksDestinationPolicy.java \
  app/src/main/java/org/harp/l2/SocksPolicies.java \
  tools/w15/SocksPublicAddressPolicySelfTest.java
java -cp /tmp/harp-w15-classes org.harp.l2.SocksPublicAddressPolicySelfTest
```

The optional `--expect-baseline-gaps` argument is for reproducing the **pre-fix source** only; it is intentionally expected to fail against the corrected source.

## 4. Release implications and next falsifiable gates

- **W15-JDK: PASS.** The exact updated source and published test are independently compilable on OpenJDK 21; no Android SDK used.
- **W15-Android: PENDING.** A local offline Android APK build and device test remain necessary before any Android runtime claim.
- **W14 UDP contract: STILL BLOCKED.** HEV UDP-over-TCP is not served by CONNECT-only B. W15 does not implement UDP.
- **W11/W12/W13 public relay: NO-GO.** Identity pairing, consent, carrier-data quotas, lifecycle cancellation and actual B integration remain unresolved.
- **P0 Internet for A: STILL PENDING.** Authorized guest Wi-Fi with A cellular data OFF, actual DNS + HTTPS and gateway egress. No venue/ISP permission or physical PASS has been demonstrated.
- **Unknown B without HARP:** no stock-phone automatic forwarding mechanism found; B must knowingly provide an authorized service.
- **Community Wi-Fi:** viable only with permitted backhaul and verified site availability. W12 traffic-volume scenarios are assumptions, not current quotes.
- **5G ProSe/SNPN:** requires compatible, provisioned UEs and participating RAN/core; no PC5 or RFsim authentication PASS. **eSIM:** credential provisioning, not independent free connectivity.

**Next focused experiment:** run the W15 regression on the actual Android build toolchain locally (no CI), then obtain a consenting guest Wi-Fi pilot and capture measured A Internet egress. In parallel, perform the W14 local SOCKS5 CONNECT-vs-UDP handshake test and a W13 quota integration design review.

**Compliance:** no GitHub Actions, no CI capacity, no installation on phones, no third-party network access, no physical lab claims.
