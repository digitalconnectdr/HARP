package org.harp.l2;

/** Generates the HEV tun2socks YAML used by Stage-2A on phone A. */
final class Stage2TunnelConfig {
    static final String TUN_IPV4 = "198.18.0.1";
    static final String DNS_IPV4 = "198.18.0.2";
    static final int LOCAL_SOCKS_PORT = 11080;
    static final int MTU = 1500;

    private Stage2TunnelConfig() {}

    static String render(Stage2SessionCredentials credentials) {
        String username = safeCredential(credentials.username());
        String password = safeCredential(credentials.password());

        return ""
                + "tunnel:\n"
                + "  name: tun0\n"
                + "  mtu: " + MTU + "\n"
                + "  ipv4: " + TUN_IPV4 + "\n"
                + "  icmp: 'off'\n"
                + "\n"
                + "socks5:\n"
                + "  address: 127.0.0.1\n"
                + "  port: " + LOCAL_SOCKS_PORT + "\n"
                + "  udp: 'tcp'\n"
                + "  username: '" + username + "'\n"
                + "  password: '" + password + "'\n"
                + "\n"
                + "mapdns:\n"
                + "  address: " + DNS_IPV4 + "\n"
                + "  port: 53\n"
                + "  network: 100.64.0.0\n"
                + "  netmask: 255.192.0.0\n"
                + "  cache-size: 10000\n"
                + "\n"
                + "misc:\n"
                + "  connect-timeout: 10000\n"
                + "  tcp-read-write-timeout: 300000\n"
                + "  log-level: warn\n";
    }

    private static String safeCredential(String value) {
        if (value == null || value.length() < 8 || value.length() > 128) {
            throw new IllegalArgumentException("invalid session credential length");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok =
                    (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_'
                    || c == '-';
            if (!ok) {
                throw new IllegalArgumentException("unsafe session credential");
            }
        }
        return value;
    }
}
