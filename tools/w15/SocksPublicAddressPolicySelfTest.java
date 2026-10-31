package org.harp.l2;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * HARP W15 JDK-only regression test of the ACTUAL SocksPolicies implementation.
 * No Android SDK, network sockets, DNS queries, or Internet access are used.
 * Compile with SocksPolicies.java and SocksDestinationPolicy.java from the repo.
 */
public final class SocksPublicAddressPolicySelfTest {
    private SocksPublicAddressPolicySelfTest() {}

    private static InetAddress literal(String address) throws Exception {
        // Test cases are IP literals only: no DNS resolution or external network calls.
        if (!address.contains(":") && !address.matches("[0-9.]+")) {
            throw new IllegalArgumentException("IP literal required");
        }
        return InetAddress.getByName(address);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        boolean baseline = args.length == 1 && "--expect-baseline-gaps".equals(args[0]);
        if (args.length > 0 && !baseline) throw new IllegalArgumentException("unknown option");

        List<String> shouldBeRejected = Arrays.asList(
                "127.0.0.1", "10.0.0.1", "100.64.0.1", "169.254.1.1",
                "192.0.2.1", "198.18.0.1", "203.0.113.1", "240.0.0.1",
                "::1", "fe80::1", "fc00::1", "2001:db8::1",
                // IANA IPv6 Special-Purpose registry: Globally Reachable = False.
                "64:ff9b:1::1", "100::1", "2001:2::1", "3fff::1", "5f00::1",
                "64:ff9b:1:ffff::1", "100::ffff:ffff:ffff:ffff",
                "2001:2:0:ffff::1", "3fff:fff::1", "5f00:ffff::1"
        );
        List<String> unexpectedAllows = new ArrayList<>();
        for (String ip : shouldBeRejected) {
            if (SocksPolicies.isPublicInternetAddress(literal(ip))) {
                unexpectedAllows.add(ip);
            }
        }
        for (String ip : Arrays.asList("1.1.1.1", "8.8.8.8", "2606:4700:4700::1111",
                "64:ff9b::1", "2001:3::1")) {
            check(SocksPolicies.isPublicInternetAddress(literal(ip)),
                    "unexpected global-address denial: " + ip);
        }
        SocksDestinationPolicy policy = SocksPolicies.publicWeb();
        policy.validateRequest("example.org", 443);
        boolean blockedPort = false;
        try { policy.validateRequest("example.org", 22); }
        catch (IOException expected) { blockedPort = true; }
        check(blockedPort, "non-web port not blocked");
        boolean blockedMixed = false;
        try {
            policy.validateResolved("example.org", 443, new InetAddress[]{
                    literal("1.1.1.1"), literal("127.0.0.1")});
        } catch (IOException expected) { blockedMixed = true; }
        check(blockedMixed, "mixed public/private resolution not blocked");

        if (baseline) {
            List<String> expected = Arrays.asList(
                    "64:ff9b:1::1", "100::1", "2001:2::1", "3fff::1", "5f00::1",
                    "64:ff9b:1:ffff::1", "100::ffff:ffff:ffff:ffff",
                    "2001:2:0:ffff::1", "3fff:fff::1", "5f00:ffff::1");
            check(unexpectedAllows.equals(expected),
                    "baseline changed: " + unexpectedAllows);
            System.out.println("PASS_HARP_W15_ACTUAL_SOURCE_BASELINE_GAPS_REPRODUCED");
            System.out.println("GAPS=" + String.join(",", unexpectedAllows));
            System.out.println("NOT_A_NETWORK_OR_ANDROID_TEST");
            return;
        }
        check(unexpectedAllows.isEmpty(), "non-global IPv6 addresses accepted: " + unexpectedAllows);
        System.out.println("PASS_HARP_W15_JDK_PUBLIC_ADDRESS_POLICY_REGRESSION");
        System.out.println("NO_NETWORK_OR_ANDROID_TEST_PERFORMED");
    }
}
