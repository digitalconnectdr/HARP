package org.harp.l2;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.net.SocketFactory;
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
        testPersistentRelay(random);

        System.out.println("PASS_STAGE2_SESSION_CREDENTIALS_1000");
        System.out.println("PASS_STAGE2_CONTROL_PROTOCOL");
        System.out.println("PASS_STAGE2_PERSISTENT_RELAY");
        System.out.println("PASS_STAGE2_HEV_CONFIG");
        System.out.println("PASS_STAGE2_PURE_JAVA_SELFTEST");
    }


    private static void testPersistentRelay(SecureRandom random) throws Exception {
        ExecutorService targetIo = Executors.newSingleThreadExecutor();
        try (ServerSocket target =
                     new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
             ServerSocket relayListener =
                     new ServerSocket(0, 10, InetAddress.getLoopbackAddress())) {

            Future<?> targetFuture = targetIo.submit(() -> {
                try {
                    for (int i = 0; i < 2; i++) {
                        try (Socket socket = target.accept()) {
                            byte[] request = socket.getInputStream().readNBytes(4);
                            if (!"PING".equals(
                                    new String(request, StandardCharsets.US_ASCII))) {
                                throw new AssertionError("persistent relay target payload");
                            }
                            socket.getOutputStream().write(
                                    "PONG".getBytes(StandardCharsets.US_ASCII));
                            socket.shutdownOutput();
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            Stage2SessionCredentials credentials =
                    Stage2SessionCredentials.create(random);

            try (Stage2RelayServer relay = new Stage2RelayServer(
                    relayListener,
                    credentials,
                    SocksPolicies.exactTargetForTest(
                            "localhost", target.getLocalPort()),
                    host -> new InetAddress[]{InetAddress.getLoopbackAddress()},
                    SocketFactory.getDefault(),
                    SocketPeerPolicies.loopbackOnlyForTest(),
                    4,
                    ignored -> {})) {
                relay.start();

                for (int i = 0; i < 2; i++) {
                    try (Socket client = new Socket(
                            InetAddress.getLoopbackAddress(), relay.port())) {
                        client.setSoTimeout(5_000);
                        MiniSocks5.clientConnect(
                                client,
                                credentials.username(),
                                credentials.password(),
                                "localhost",
                                target.getLocalPort());
                        client.getOutputStream().write(
                                "PING".getBytes(StandardCharsets.US_ASCII));
                        client.shutdownOutput();
                        String got = new String(
                                client.getInputStream().readNBytes(4),
                                StandardCharsets.US_ASCII);
                        if (!"PONG".equals(got)) {
                            throw new AssertionError(
                                    "persistent relay payload=" + got);
                        }
                    }
                }
            }

            targetFuture.get(5, TimeUnit.SECONDS);
        } finally {
            targetIo.shutdownNow();
        }
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
