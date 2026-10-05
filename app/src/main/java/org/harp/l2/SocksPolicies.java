package org.harp.l2;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/** Reusable SOCKS policies for the lab Stage-1 proof and future Stage-2 web relay. */
final class SocksPolicies {
    private SocksPolicies() {}

    static SocksDestinationPolicy exactPublicTarget(String expectedHost, int expectedPort) {
        return new SocksDestinationPolicy() {
            @Override
            public void validateRequest(String host, int port) throws IOException {
                if (!expectedHost.equalsIgnoreCase(host) || port != expectedPort) {
                    throw new IOException("destination not allowed: " + host + ":" + port);
                }
            }

            @Override
            public void validateResolved(
                    String host, int port, InetAddress[] addresses) throws IOException {
                requirePublicAddresses(host, addresses);
            }
        };
    }

    /**
     * Stage-2A browser policy: public Internet web destinations only.
     * This is intentionally narrower than a general-purpose proxy.
     */
    static SocksDestinationPolicy publicWeb() {
        return new SocksDestinationPolicy() {
            @Override
            public void validateRequest(String host, int port) throws IOException {
                if (host == null || host.trim().isEmpty() || host.length() > 253) {
                    throw new IOException("invalid destination host");
                }
                if (port != 80 && port != 443) {
                    throw new IOException("destination port not allowed: " + port);
                }
            }

            @Override
            public void validateResolved(
                    String host, int port, InetAddress[] addresses) throws IOException {
                requirePublicAddresses(host, addresses);
            }
        };
    }

    /** Test-only policy allowing loopback so pure-Java relay tests need no Internet. */
    static SocksDestinationPolicy exactTargetForTest(String expectedHost, int expectedPort) {
        return new SocksDestinationPolicy() {
            @Override
            public void validateRequest(String host, int port) throws IOException {
                if (!expectedHost.equalsIgnoreCase(host) || port != expectedPort) {
                    throw new IOException("destination not allowed: " + host + ":" + port);
                }
            }

            @Override
            public void validateResolved(
                    String host, int port, InetAddress[] addresses) throws IOException {
                if (addresses == null || addresses.length == 0) {
                    throw new IOException("no addresses resolved for " + host);
                }
            }
        };
    }

    static boolean isPublicInternetAddress(InetAddress address) {
        if (address == null) return false;
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }

        byte[] raw = address.getAddress();
        if (address instanceof Inet4Address && raw.length == 4) {
            int a = raw[0] & 0xff;
            int b = raw[1] & 0xff;
            int c = raw[2] & 0xff;

            if (a == 0) return false;                              // 0.0.0.0/8
            if (a == 100 && b >= 64 && b <= 127) return false;   // 100.64.0.0/10 CGNAT
            if (a == 192 && b == 0 && c == 0) return false;      // 192.0.0.0/24 IETF
            if (a == 192 && b == 0 && c == 2) return false;      // TEST-NET-1
            if (a == 192 && b == 88 && c == 99) return false;    // deprecated 6to4 relay
            if (a == 198 && (b == 18 || b == 19)) return false;  // benchmark 198.18/15
            if (a == 198 && b == 51 && c == 100) return false;   // TEST-NET-2
            if (a == 203 && b == 0 && c == 113) return false;    // TEST-NET-3
            if (a >= 240) return false;                           // reserved/broadcast space
            return true;
        }

        if (address instanceof Inet6Address && raw.length == 16) {
            int first = raw[0] & 0xff;
            int second = raw[1] & 0xff;

            if ((first & 0xfe) == 0xfc) return false;             // fc00::/7 ULA
            if (first == 0x20 && second == 0x01
                    && (raw[2] & 0xff) == 0x0d
                    && (raw[3] & 0xff) == 0xb8) return false;     // 2001:db8::/32
            return true;
        }

        return false;
    }

    private static void requirePublicAddresses(
            String host, InetAddress[] addresses) throws IOException {
        if (addresses == null || addresses.length == 0) {
            throw new IOException("no addresses resolved for " + host);
        }
        for (InetAddress address : addresses) {
            if (!isPublicInternetAddress(address)) {
                throw new IOException(
                        "resolved destination not public: " + host + " -> "
                                + address.getHostAddress());
            }
        }
    }
}
