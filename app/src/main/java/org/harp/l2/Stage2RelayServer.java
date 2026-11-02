package org.harp.l2;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javax.net.SocketFactory;

/**
 * Persistent authenticated TCP relay used by Stage-2A on phone B.
 *
 * The listener itself is created by the Aware publisher so its port can be
 * advertised in the Wi-Fi Aware data-path metadata.
 */
final class Stage2RelayServer implements AutoCloseable {
    private final ServerSocket listener;
    private final Stage2SessionCredentials credentials;
    private final SocksDestinationPolicy destinationPolicy;
    private final SocksAddressResolver resolver;
    private final SocketFactory outboundFactory;
    private final SocketPeerPolicy peerPolicy;
    private final ExecutorService io;
    private final Semaphore sessionSlots;
    private final Set<Socket> activeSockets =
            ConcurrentHashMap.newKeySet();
    // Track the sockets returned by the selected Android Network's factory.
    // Closing the accepted peer socket alone does not reliably close upstream TCP.
    private final Set<Socket> activeOutboundSockets = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean started = new AtomicBoolean();
    private final Consumer<String> logger;

    Stage2RelayServer(
            ServerSocket listener,
            Stage2SessionCredentials credentials,
            SocksDestinationPolicy destinationPolicy,
            SocksAddressResolver resolver,
            SocketFactory outboundFactory,
            SocketPeerPolicy peerPolicy,
            int maxConcurrentSessions,
            Consumer<String> logger) {
        if (listener == null || listener.isClosed() || !listener.isBound()) {
            throw new IllegalArgumentException("listener must be bound and open");
        }
        if (maxConcurrentSessions < 1 || maxConcurrentSessions > 128) {
            throw new IllegalArgumentException("invalid maxConcurrentSessions");
        }
        this.listener = listener;
        this.credentials = credentials;
        this.destinationPolicy = destinationPolicy;
        this.resolver = resolver;
        this.outboundFactory = new TrackingOutboundSocketFactory(outboundFactory);
        this.peerPolicy = peerPolicy;
        this.sessionSlots = new Semaphore(maxConcurrentSessions);
        this.io = Executors.newCachedThreadPool();
        this.logger = logger != null ? logger : ignored -> {};
    }

    void start() {
        if (closed.get()) throw new IllegalStateException("relay already closed");
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("relay already started");
        }
        io.execute(this::acceptLoop);
    }

    int port() {
        return listener.getLocalPort();
    }

    private void acceptLoop() {
        while (!closed.get()) {
            Socket client = null;
            try {
                client = listener.accept();
            } catch (IOException e) {
                closeQuietly(client);
                if (!closed.get()) {
                    logger.accept("Stage2 relay accept ERROR "
                            + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
                return;
            }

            try {
                peerPolicy.validate(client);
            } catch (IOException e) {
                logger.accept("Stage2 relay peer REJECTED "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
                closeQuietly(client);
                continue;
            }

            if (!sessionSlots.tryAcquire()) {
                logger.accept("Stage2 relay BUSY");
                closeQuietly(client);
                continue;
            }

            activeSockets.add(client);
            Socket accepted = client;
            try {
                io.execute(() -> serveClient(accepted));
            } catch (RuntimeException e) {
                activeSockets.remove(accepted);
                sessionSlots.release();
                closeQuietly(accepted);
                if (!closed.get()) {
                    logger.accept("Stage2 relay dispatch ERROR "
                            + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        }
    }

    private void serveClient(Socket client) {
        try (Socket accepted = client) {
            MiniSocks5.serveOne(
                    accepted,
                    io,
                    credentials.username(),
                    credentials.password(),
                    destinationPolicy,
                    resolver,
                    outboundFactory);
        } catch (Exception e) {
            if (!closed.get()) {
                logger.accept("Stage2 relay session ERROR "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        } finally {
            activeSockets.remove(client);
            sessionSlots.release();
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        closeQuietly(listener);
        for (Socket socket : activeSockets) closeQuietly(socket);
        for (Socket socket : activeOutboundSockets) closeQuietly(socket);
        activeSockets.clear();
        activeOutboundSockets.clear();
        io.shutdownNow();
        logger.accept("Stage2 relay stopped");
    }

    /**
     * Preserve the exact caller-selected Network socket factory (and binding)
     * while making all sockets it creates visible to close(). The zero-arg
     * createSocket path used by MiniSocks5 is tracked BEFORE connect().
     */
    private final class TrackingOutboundSocketFactory extends SocketFactory {
        private final SocketFactory delegate;

        TrackingOutboundSocketFactory(SocketFactory delegate) {
            if (delegate == null) throw new IllegalArgumentException("outbound factory required");
            this.delegate = delegate;
        }

        private Socket track(Socket socket) throws IOException {
            if (closed.get()) {
                closeQuietly(socket);
                throw new IOException("relay closed before outbound registration");
            }
            // Failed connection candidates are closed by MiniSocks5; discard
            // them before tracking another socket to avoid unbounded growth.
            activeOutboundSockets.removeIf(Socket::isClosed);
            activeOutboundSockets.add(socket);
            // A close may race with registration. Double-check fail-closed.
            if (closed.get()) {
                activeOutboundSockets.remove(socket);
                closeQuietly(socket);
                throw new IOException("relay closed during outbound registration");
            }
            return socket;
        }

        @Override
        public Socket createSocket() throws IOException {
            return track(delegate.createSocket());
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return track(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port,
                InetAddress localHost, int localPort) throws IOException {
            return track(delegate.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return track(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(InetAddress address, int port,
                InetAddress localAddress, int localPort) throws IOException {
            return track(delegate.createSocket(address, port, localAddress, localPort));
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }
}
