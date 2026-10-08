package org.harp.l2;

import java.io.IOException;
import java.net.ServerSocket;
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
        this.outboundFactory = outboundFactory;
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
        activeSockets.clear();
        io.shutdownNow();
        logger.accept("Stage2 relay stopped");
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }
}
