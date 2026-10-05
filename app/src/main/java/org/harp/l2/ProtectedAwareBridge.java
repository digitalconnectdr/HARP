package org.harp.l2;

import android.net.Network;
import android.net.VpnService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Protocol-blind local TCP bridge for Stage-2A.
 *
 * HEV connects to 127.0.0.1:11080. For every local TCP stream this bridge creates
 * an unconnected socket, protects it from the VPN, binds it to the Wi-Fi Aware
 * Network, then connects to B's SOCKS listener.
 */
final class ProtectedAwareBridge implements AutoCloseable {
    static final int DEFAULT_LOCAL_PORT = Stage2TunnelConfig.LOCAL_SOCKS_PORT;

    private final VpnService vpn;
    private final Network awareNetwork;
    private final InetSocketAddress relayAddress;
    private final ExecutorService io = Executors.newCachedThreadPool();
    private final AtomicBoolean closed = new AtomicBoolean();

    private ServerSocket localServer;

    ProtectedAwareBridge(
            VpnService vpn,
            Network awareNetwork,
            InetSocketAddress relayAddress) {
        this.vpn = vpn;
        this.awareNetwork = awareNetwork;
        this.relayAddress = requireAwareRelay(relayAddress);
    }

    synchronized void start() throws IOException {
        if (closed.get()) throw new IOException("bridge already closed");
        if (localServer != null) throw new IOException("bridge already started");

        ServerSocket server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), DEFAULT_LOCAL_PORT));
        localServer = server;

        HarpLog.i("Stage2 bridge listening=127.0.0.1:" + DEFAULT_LOCAL_PORT
                + " relay=" + relayAddress);
        io.execute(this::acceptLoop);
    }

    private void acceptLoop() {
        while (!closed.get()) {
            try {
                Socket downstream = localServer.accept();
                io.execute(() -> handle(downstream));
            } catch (IOException e) {
                if (!closed.get()) {
                    HarpLog.i("Stage2 bridge accept ERROR "
                            + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
                return;
            }
        }
    }

    private void handle(Socket downstream) {
        try (Socket local = downstream; Socket upstream = new Socket()) {
            local.setSoTimeout(0);

            if (!vpn.protect(upstream)) {
                throw new IOException("VpnService.protect() failed");
            }

            // Must happen before connect(): Android requires an unconnected socket.
            awareNetwork.bindSocket(upstream);
            upstream.connect(relayAddress, 10_000);
            upstream.setSoTimeout(0);

            HarpLog.i("Stage2 bridge stream connected relay=" + relayAddress);

            io.execute(() -> copy(local, upstream));
            copy(upstream, local);
        } catch (IOException e) {
            if (!closed.get()) {
                HarpLog.i("Stage2 bridge stream ERROR "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    }

    private static void copy(Socket from, Socket to) {
        try {
            InputStream in = from.getInputStream();
            OutputStream out = to.getOutputStream();
            byte[] buffer = new byte[32 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0) {
                out.write(buffer, 0, n);
                out.flush();
            }
            try { to.shutdownOutput(); } catch (IOException ignored) {}
        } catch (IOException ignored) {
            try { to.shutdownOutput(); } catch (IOException ignored2) {}
        }
    }

    @Override
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) return;
        try {
            if (localServer != null) localServer.close();
        } catch (IOException ignored) {
        }
        io.shutdownNow();
        HarpLog.i("Stage2 bridge stopped");
    }

    private static InetSocketAddress requireAwareRelay(InetSocketAddress address) {
        if (address == null) {
            throw new IllegalArgumentException("relay address is null");
        }
        InetAddress ip = address.getAddress();
        if (!(ip instanceof Inet6Address) || !ip.isLinkLocalAddress()) {
            throw new IllegalArgumentException("relay must be scoped IPv6 link-local");
        }
        if (address.getPort() <= 0 || address.getPort() > 65535) {
            throw new IllegalArgumentException("invalid relay port");
        }
        return address;
    }
}
