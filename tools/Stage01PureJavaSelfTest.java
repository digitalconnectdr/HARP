package org.harp.l2;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class Stage01PureJavaSelfTest {
    public static void main(String[] args) throws Exception {
        testStage0();
        testSocksRelay();
        testSocksBadAuth();
        testSocksWrongDestination();
        testPublicDestinationPolicy();
        System.out.println("PASS_STAGE01_PURE_JAVA_SELFTEST");
    }

    private static void testStage0() {
        SecureRandom random = new SecureRandom();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            String ping = Stage0Protocol.newPing(random);
            if (!Stage0Protocol.isValidPing(ping)) throw new AssertionError("invalid ping");
            if (!seen.add(ping)) throw new AssertionError("duplicate nonce");
            String pong = Stage0Protocol.pongFor(ping);
            if (!Stage0Protocol.isExpectedPong(ping, pong)) throw new AssertionError("bad pong");
        }
        if (Stage0Protocol.isValidPing("PING-xyz")) {
            throw new AssertionError("accepted invalid frame");
        }
        System.out.println("PASS_STAGE0_10000_NONCES");
    }

    private static void testSocksRelay() throws Exception {
        ExecutorService io = Executors.newCachedThreadPool();
        try (ServerSocket target = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
             ServerSocket proxy = new ServerSocket(0, 10, InetAddress.getLoopbackAddress())) {
            Future<?> targetFuture = io.submit(() -> {
                try (Socket socket = target.accept()) {
                    byte[] request = socket.getInputStream().readNBytes(4);
                    if (!"PING".equals(new String(request, StandardCharsets.US_ASCII))) {
                        throw new AssertionError("target payload");
                    }
                    socket.getOutputStream().write("PONG".getBytes(StandardCharsets.US_ASCII));
                    socket.shutdownOutput();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            Future<?> proxyFuture = io.submit(() -> {
                try (Socket client = proxy.accept()) {
                    MiniSocks5.serveOne(
                            client,
                            io,
                            "harp",
                            "secret",
                            SocksPolicies.exactTargetForTest(
                                    "localhost", target.getLocalPort()),
                            host -> new InetAddress[]{InetAddress.getLoopbackAddress()},
                            SocketFactory.getDefault());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            try (Socket client = new Socket(
                    InetAddress.getLoopbackAddress(), proxy.getLocalPort())) {
                client.setSoTimeout(5_000);
                MiniSocks5.clientConnect(
                        client, "harp", "secret", "localhost", target.getLocalPort());
                client.getOutputStream().write("PING".getBytes(StandardCharsets.US_ASCII));
                client.shutdownOutput();
                String got = new String(
                        client.getInputStream().readNBytes(4), StandardCharsets.US_ASCII);
                if (!"PONG".equals(got)) throw new AssertionError("relay payload=" + got);
            }

            targetFuture.get(5, TimeUnit.SECONDS);
            proxyFuture.get(5, TimeUnit.SECONDS);
            System.out.println("PASS_SOCKS_RELAY");
        } finally {
            io.shutdownNow();
        }
    }

    private static void testSocksBadAuth() throws Exception {
        ExecutorService io = Executors.newCachedThreadPool();
        try (ServerSocket proxy = new ServerSocket(0, 10, InetAddress.getLoopbackAddress())) {
            Future<?> proxyFuture = io.submit(() -> {
                try (Socket client = proxy.accept()) {
                    try {
                        MiniSocks5.serveOne(
                                client,
                                io,
                                "harp",
                                "correct",
                                SocksPolicies.exactTargetForTest("localhost", 443),
                                host -> new InetAddress[]{InetAddress.getLoopbackAddress()},
                                SocketFactory.getDefault());
                        throw new AssertionError("bad auth accepted");
                    } catch (IOException expected) {
                        if (!expected.getMessage().contains("auth")) {
                            throw new RuntimeException(expected);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            try (Socket client = new Socket(
                    InetAddress.getLoopbackAddress(), proxy.getLocalPort())) {
                try {
                    MiniSocks5.clientConnect(client, "harp", "wrong", "localhost", 443);
                    throw new AssertionError("client auth should fail");
                } catch (IOException expected) {
                    // expected
                }
            }

            proxyFuture.get(5, TimeUnit.SECONDS);
            System.out.println("PASS_SOCKS_BAD_AUTH_REJECTED");
        } finally {
            io.shutdownNow();
        }
    }

    private static void testSocksWrongDestination() throws Exception {
        ExecutorService io = Executors.newCachedThreadPool();
        try (ServerSocket proxy = new ServerSocket(0, 10, InetAddress.getLoopbackAddress())) {
            Future<?> proxyFuture = io.submit(() -> {
                try (Socket client = proxy.accept()) {
                    try {
                        MiniSocks5.serveOne(
                                client,
                                io,
                                "harp",
                                "secret",
                                SocksPolicies.exactTargetForTest("allowed.invalid", 443),
                                host -> new InetAddress[]{InetAddress.getLoopbackAddress()},
                                SocketFactory.getDefault());
                        throw new AssertionError("wrong destination accepted");
                    } catch (IOException expected) {
                        if (!expected.getMessage().contains("destination")) {
                            throw new RuntimeException(expected);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            try (Socket client = new Socket(
                    InetAddress.getLoopbackAddress(), proxy.getLocalPort())) {
                try {
                    MiniSocks5.clientConnect(
                            client, "harp", "secret", "other.invalid", 443);
                    throw new AssertionError("destination should fail");
                } catch (IOException expected) {
                    // expected
                }
            }

            proxyFuture.get(5, TimeUnit.SECONDS);
            System.out.println("PASS_SOCKS_DESTINATION_POLICY");
        } finally {
            io.shutdownNow();
        }
    }

    private static void testPublicDestinationPolicy() throws Exception {
        SocksDestinationPolicy web = SocksPolicies.publicWeb();

        web.validateRequest("example.com", 443);
        web.validateRequest("example.com", 80);
        expectRejected(() -> web.validateRequest("example.com", 22));

        assertPublic("1.1.1.1", true);
        assertPublic("8.8.8.8", true);
        assertPublic("2606:4700:4700::1111", true);

        assertPublic("0.0.0.1", false);
        assertPublic("10.0.0.1", false);
        assertPublic("127.0.0.1", false);
        assertPublic("169.254.1.1", false);
        assertPublic("172.16.0.1", false);
        assertPublic("192.168.1.1", false);
        assertPublic("100.64.0.1", false);
        assertPublic("198.18.0.1", false);
        assertPublic("224.0.0.1", false);
        assertPublic("fc00::1", false);
        assertPublic("fe80::1", false);
        assertPublic("::1", false);
        assertPublic("2001:db8::1", false);

        expectRejected(() -> web.validateResolved(
                "example.com", 443,
                new InetAddress[]{InetAddress.getByName("127.0.0.1")}));

        web.validateResolved(
                "example.com", 443,
                new InetAddress[]{InetAddress.getByName("1.1.1.1")});

        System.out.println("PASS_PUBLIC_DESTINATION_POLICY");
    }

    private static void assertPublic(String literal, boolean expected) throws Exception {
        boolean actual =
                SocksPolicies.isPublicInternetAddress(InetAddress.getByName(literal));
        if (actual != expected) {
            throw new AssertionError(
                    "address policy " + literal + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void expectRejected(CheckedRunnable action) throws Exception {
        try {
            action.run();
            throw new AssertionError("operation should have been rejected");
        } catch (IOException expected) {
            // expected
        }
    }

    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
