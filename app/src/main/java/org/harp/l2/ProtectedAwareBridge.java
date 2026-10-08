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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
    private final Set<Socket> activeSockets = ConcurrentHashMap.newKeySet();

    private ServerSocket localServer;

    ProtectedAwareBridge(
            VpnService vpn,
            Network awareNetwork,
            InetSocketAddress relayAddress) {
        if (vpn == null) throw new IllegalArgumentException("vpn is null");
        if (awareNetwork == null) {
            throw new IllegalArgumentException("awareNetwork is null");
        }
        this.vpn = vpn;
        this.awareNetwork = awareNetwork;
        this.relayAddress = requireAwareRelay(relayAddress);
    }

    synchronized void start() throws IOException {
        if (closed.get()) throw new IOException("bridge already closed");
        if (localServer != null) throw new IOException("bridge already started");

        ServerSocket server = new ServerSocket();
        server.setReuseAddress(true);
        InetAddress loopbackV4 =
                InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        server.bind(new InetSocketAddress(loopbackV4, DEFAULT_LOCAL_PORT));
        localServer = server;

        HarpLog.i("Stage2 bridge listening=127.0.0.1:" + DEFAULT_LOCAL_PORT
                + " relay=" + relayAddress);
        io.execute(this::acceptLoop);
    }

    private void acceptLoop() {
        while (!closed.get()) {
            try {
                Socket downstream = localServer.accept();
                if (closed.get()) {
                    closeQuietly(downstream);
                    return;
                }
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
        Socket upstream = new Socket();
        activeSockets.add(downstream);
        activeSockets.add(upstream);

        try (Socket local = downstream; Socket remote = upstream) {
            local.setSoTimeout(0);

            // Protect first so the tunnel transport cannot be captured by its
            // own VpnService route.
            if (!vpn.protect(remote)) {
                throw new IOException("VpnService.protect() failed");
            }

            // Android requires the socket to still be unconnected here.
            awareNetwork.bindSocket(remote);
            remote.connect(relayAddress, 10_000);
            remote.setSoTimeout(0);

            HarpLog.i("Stage2 bridge stream connected relay=" + relayAddress);

            Future<?> localToRelay = io.submit(() -> copy(local, remote));
            Future<?> relayToLocal = io.submit(() -> copy(remote, local));
            await(localToRelay);
            await(relayToLocal);
        } catch (IOException e) {
            if (!closed.get()) {
                HarpLog.i("Stage2 bridge stream ERROR "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        } finally {
            activeSockets.remove(downstream);
            activeSockets.remove(upstream);
            closeQuietly(downstream);
            closeQuietly(upstream);
        }
    }

    private static void await(Future<?> future) throws IOException {
        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("bridge interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            throw new IOException("bridge copy failed", cause);
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

        if (localServer != null) {
            try { localServer.close(); } catch (IOException ignored) {}
        }

        for (Socket socket : activeSockets) closeQuietly(socket);
        activeSockets.clear();

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

    private static void closeQuietly(Socket socket) {
        if (socket == null) return;
        try { socket.close(); } catch (IOException ignored) {}
    }
}
