package org.harp.l2;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

public final class Stage2PureJavaSelfTest {
    public static void main(String[] args) throws Exception {
        SecureRandom random = new SecureRandom();
        Set<String> users = new HashSet<>();
        Set<String> passwords = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            Stage2SessionCredentials credentials =
                    Stage2SessionCredentials.create(random);

            if (!users.add(credentials.username())) {
                throw new AssertionError("duplicate Stage2 username");
            }
            if (!passwords.add(credentials.password())) {
                throw new AssertionError("duplicate Stage2 password");
            }

            String controlLine = Stage2ControlProtocol.sessionLine(credentials);
            Stage2SessionCredentials parsed =
                    Stage2ControlProtocol.parseSessionLine(controlLine);
            require(parsed.username().equals(credentials.username()), "control username");
            require(parsed.password().equals(credentials.password()), "control password");

            String yaml = Stage2TunnelConfig.render(credentials);
            require(yaml.contains("mtu: 1500"), "MTU");
            require(yaml.contains("ipv4: 198.18.0.1"), "TUN IPv4");
            require(yaml.contains("address: 127.0.0.1"), "local SOCKS");
            require(yaml.contains("port: 11080"), "local SOCKS port");
            require(yaml.contains("udp: 'tcp'"), "UDP-over-TCP mode");
            require(yaml.contains("address: 198.18.0.2"), "mapdns");
            require(yaml.contains("network: 100.64.0.0"), "mapdns network");
            require(yaml.contains("netmask: 255.192.0.0"), "mapdns mask");
            require(yaml.contains("username: '" + credentials.username() + "'"), "username");
            require(yaml.contains("password: '" + credentials.password() + "'"), "password");
        }

        expectControlRejected("HARP2 SESSION bad space extra");
        expectControlRejected("HARP2 SESSION x y");
        expectControlRejected("HARP2 BAD anything");

        System.out.println("PASS_STAGE2_SESSION_CREDENTIALS_1000");
        System.out.println("PASS_STAGE2_CONTROL_PROTOCOL");
        System.out.println("PASS_STAGE2_HEV_CONFIG");
        System.out.println("PASS_STAGE2_PURE_JAVA_SELFTEST");
    }

    private static void expectControlRejected(String line) throws Exception {
        try {
            Stage2ControlProtocol.parseSessionLine(line);
            throw new AssertionError("control frame should be rejected");
        } catch (IOException expected) {
            // expected
        }
    }

    private static void require(boolean condition, String what) {
        if (!condition) throw new AssertionError("missing/invalid " + what);
    }
}
