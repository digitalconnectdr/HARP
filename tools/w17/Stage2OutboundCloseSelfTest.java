package org.harp.l2;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.SocketFactory;

/**
 * JDK-only loopback integration regression for the actual HARP SOCKS5 relay.
 * No Android SDK, radio, mobile data, Internet, third-party IP or GitHub CI.
 */
public final class Stage2OutboundCloseSelfTest {
    private static final String USER = "h_w17test";
    private static final String PASS = "ABCDEFGHIJKLMNOPQRSTUVWX1234";

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        boolean baseline = args.length == 1 && "--expect-baseline-gap".equals(args[0]);
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        AtomicReference<Socket> outbound = new AtomicReference<>();
        SocketFactory tracked = new SocketFactory() {
            @Override public Socket createSocket() throws IOException {
                Socket s = new Socket();
                outbound.set(s);
                return s;
            }
            @Override public Socket createSocket(String h, int p) throws IOException {
                throw new AssertionError("unexpected createSocket(host,port)");
            }
            @Override public Socket createSocket(String h, int p, InetAddress lh, int lp)
                    throws IOException { throw new AssertionError("unexpected createSocket(host,port,local)"); }
            @Override public Socket createSocket(InetAddress h, int p) throws IOException {
                throw new AssertionError("unexpected createSocket(address,port)");
            }
            @Override public Socket createSocket(InetAddress h, int p, InetAddress lh, int lp)
                    throws IOException { throw new AssertionError("unexpected createSocket(address,port,local)"); }
        };
        try (ServerSocket upstreamListener = new ServerSocket(0, 5, loopback);
             ServerSocket relayListener = new ServerSocket(0, 5, loopback)) {
            upstreamListener.setSoTimeout(3_000);
            CompletableFuture<Socket> acceptedUpstream = CompletableFuture.supplyAsync(() -> {
                try { return upstreamListener.accept(); }
                catch (IOException e) { throw new RuntimeException(e); }
            });
            SocksDestinationPolicy policy = new SocksDestinationPolicy() {
                @Override public void validateRequest(String host, int port) throws IOException {
                    if (!"w17.test".equals(host) || port != upstreamListener.getLocalPort())
                        throw new IOException("test-only destination rejected");
                }
                @Override public void validateResolved(String host, int port, InetAddress[] addresses)
                        throws IOException {
                    if (addresses.length != 1 || !loopback.equals(addresses[0]))
                        throw new IOException("test-only resolved destination rejected");
                }
            };
            Stage2RelayServer relay = new Stage2RelayServer(
                    relayListener,
                    Stage2SessionCredentials.fromTokens(USER, PASS),
                    policy,
                    host -> new InetAddress[]{loopback},
                    tracked,
                    peer -> { if (!peer.getInetAddress().isLoopbackAddress())
                        throw new IOException("only local peer"); },
                    1,
                    msg -> {});
            relay.start();
            try (Socket client = new Socket()) {
                client.connect(new InetSocketAddress(loopback, relay.port()), 2_000);
                client.setSoTimeout(2_000);
                MiniSocks5.clientConnect(client, USER, PASS, "w17.test", upstreamListener.getLocalPort());
                try (Socket serverSide = acceptedUpstream.get(3, TimeUnit.SECONDS)) {
                    serverSide.setSoTimeout(2_000);
                    Socket outgoing = outbound.get();
                    check(outgoing != null && outgoing.isConnected() && !outgoing.isClosed(),
                            "SOCKS5 CONNECT did not establish outbound TCP");
                    client.getOutputStream().write("A".getBytes(StandardCharsets.US_ASCII));
                    check(serverSide.getInputStream().read() == 'A', "A-to-B byte forwarding failed");
                    serverSide.getOutputStream().write("B".getBytes(StandardCharsets.US_ASCII));
                    check(client.getInputStream().read() == 'B', "B-to-A byte forwarding failed");
                    relay.close();
                    boolean closed = outgoing.isClosed();
                    if (baseline) {
                        check(!closed, "expected baseline lifecycle gap was not reproduced");
                        System.out.println("PASS_HARP_W17_BASELINE_OUTBOUND_REMAINS_OPEN_AFTER_STOP");
                    } else {
                        check(closed, "outbound socket remains open after relay close");
                        System.out.println("PASS_HARP_W17_JDK_LOOPBACK_RELAY_OUTBOUND_CLOSED");
                    }
                    relay.close(); // idempotent
                }
            } finally {
                relay.close();
            }
        }
        System.out.println("NO_ANDROID_DEVICE_CARRIER_INTERNET_OR_REMOTE_NETWORK_TEST");
    }
}
