package org.harp.l2;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

/**
 * W16 offline regression for RFC 6052 NAT64 WKP embedded-IPv4 policy.
 * Actual SocksPolicies source is compiled with this test; no DNS or sockets.
 * --expect-baseline-gaps is ONLY for the pre-W16 source.
 */
public final class SocksNat64WkpPolicySelfTest {
    private SocksNat64WkpPolicySelfTest() {}

    private static InetAddress literal(String ip) throws Exception {
        if (!ip.contains(":")) throw new IllegalArgumentException("IPv6 literal required");
        return InetAddress.getByName(ip); // Numeric IPv6 literal: no DNS query.
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        boolean baseline = args.length == 1 && "--expect-baseline-gaps".equals(args[0]);
        if (args.length > 0 && !baseline) throw new IllegalArgumentException("unknown option");

        List<String> forbidden = Arrays.asList(
                "64:ff9b::1",           // 0.0.0.1
                "64:ff9b::a00:1",       // 10.0.0.1 RFC1918
                "64:ff9b::ac10:1",      // 172.16.0.1 RFC1918
                "64:ff9b::c0a8:101",    // 192.168.1.1 RFC1918
                "64:ff9b::7f00:1",      // 127.0.0.1 loopback
                "64:ff9b::6440:1",      // 100.64.0.1 CGNAT
                "64:ff9b::a9fe:101",    // 169.254.1.1 link local
                "64:ff9b::c000:201",    // 192.0.2.1 documentation
                "64:ff9b::c612:1",      // 198.18.0.1 benchmarking
                "64:ff9b::ffff:ffff"    // 255.255.255.255 broadcast
        );
        int unexpectedAllowed = 0;
        for (String ip : forbidden) {
            if (SocksPolicies.isPublicInternetAddress(literal(ip))) {
                unexpectedAllowed++;
            }
        }
        require(unexpectedAllowed == (baseline ? forbidden.size() : 0),
                "unexpected WKP forbidden allow count: " + unexpectedAllowed);

        for (String ip : Arrays.asList(
                "64:ff9b::808:808",       // 8.8.8.8
                "64:ff9b::101:101",       // 1.1.1.1
                "2606:4700:4700::1111",  // native public IPv6
                "2001:3::1"              // W15 public control
        )) {
            require(SocksPolicies.isPublicInternetAddress(literal(ip)),
                    "public address denied: " + ip);
        }
        for (String ip : Arrays.asList("64:ff9b:1::1", "100::1", "2001:2::1")) {
            require(!SocksPolicies.isPublicInternetAddress(literal(ip)),
                    "W15 non-global range allowed: " + ip);
        }

        SocksDestinationPolicy web = SocksPolicies.publicWeb();
        web.validateRequest("example.org", 443);
        web.validateResolved("example.org", 443,
                new InetAddress[]{literal("64:ff9b::808:808")});
        boolean mixedBlocked = false;
        try {
            web.validateResolved("example.org", 443,
                    new InetAddress[]{literal("64:ff9b::808:808"), literal("64:ff9b::a00:1")});
        } catch (IOException expected) {
            mixedBlocked = true;
        }
        require(mixedBlocked == !baseline, "mixed WKP DNS response enforcement mismatch");

        if (baseline) {
            System.out.println("PASS_HARP_W16_ACTUAL_SOURCE_BASELINE_NAT64_GAPS_REPRODUCED");
            System.out.println("BASELINE_WKP_NONPUBLIC_ALLOWED=" + unexpectedAllowed);
        } else {
            System.out.println("PASS_HARP_W16_JDK_NAT64_WKP_EMBEDDED_IPV4_POLICY");
            System.out.println("WKP_NONPUBLIC_ALLOWED=" + unexpectedAllowed);
        }
        System.out.println("NO_DNS_NETWORK_ANDROID_OR_NAT64_TRANSLATOR_TEST_PERFORMED");
    }
}
