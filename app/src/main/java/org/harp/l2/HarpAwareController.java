package org.harp.l2;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.LinkProperties;
import android.net.wifi.aware.AttachCallback;
import android.net.wifi.aware.DiscoverySessionCallback;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.PublishDiscoverySession;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.SubscribeDiscoverySession;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareNetworkInfo;
import android.net.wifi.aware.WifiAwareNetworkSpecifier;
import android.net.wifi.aware.WifiAwareSession;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

final class HarpAwareController {
    private static final String SERVICE = "harp-relay-v1";
    private static final String PSK = "harp-lab-2026";
    private static final int MSG_HELLO = 1;
    private static final int MSG_READY = 2;
    private static final int MSG_HELLO_RETRY = 3;
    private static final int MSG_READY_RETRY = 4;
    private static final int NDP_TIMEOUT_MS = 30000;

    private final Context appContext;
    private final WifiAwareManager aware;
    private final ConnectivityManager cm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newCachedThreadPool();
    private final SecureRandom random = new SecureRandom();

    private WifiAwareSession awareSession;
    private PublishDiscoverySession pub;
    private SubscribeDiscoverySession sub;
    private PeerHandle relayPeer;
    private PeerHandle clientPeer;
    private ConnectivityManager.NetworkCallback netCb;
    private ConnectivityManager.NetworkCallback upstreamCb;
    private volatile Network relayUpstreamNetwork;
    private volatile int relayAwareInterfaceIndex;
    private volatile String relayAwareInterfaceName;
    private volatile List<byte[]> relayAwareLocalIpv6 =
            Collections.emptyList();
    private ServerSocket server;
    private Stage2RelayServer stage2Relay;
    private volatile Network clientAwareNetwork;
    private volatile Stage2VpnSession vpnSession;
    private volatile boolean closed;
    private long discoverStarted;
    private long ndpStarted;
    private final AtomicBoolean helloSent = new AtomicBoolean();
    private final AtomicBoolean relayNdpStarted = new AtomicBoolean();
    private final AtomicBoolean clientNdpStarted = new AtomicBoolean();

    HarpAwareController(Context context) {
        appContext = context.getApplicationContext();
        aware = appContext.getSystemService(WifiAwareManager.class);
        cm = appContext.getSystemService(ConnectivityManager.class);
    }

    void startRelay() {
        if (!preflight()) return;
        HarpLog.i("B: attach...");
        aware.attach(new AttachCallback() {
            @Override public void onAttached(WifiAwareSession session) {
                if (closed) { session.close(); return; }
                awareSession = session;
                HarpLog.i("B: attach OK");
                session.publish(new PublishConfig.Builder().setServiceName(SERVICE).build(),
                        new DiscoverySessionCallback() {
                    @Override public void onPublishStarted(PublishDiscoverySession session) {
                        pub = session;
                        HarpLog.i("B: publish OK service=" + SERVICE);
                    }

                    @Override public void onMessageReceived(PeerHandle peer, byte[] msg) {
                        String text = new String(msg, StandardCharsets.UTF_8);
                        HarpLog.i("B: discovery RX=" + text);
                        if (Stage0Protocol.HELLO.equals(text)
                                && relayNdpStarted.compareAndSet(false, true)) {
                            relayPeer = peer;
                            startRelayDataPath(peer);
                        }
                    }

                    @Override public void onMessageSendSucceeded(int messageId) {
                        HarpLog.i("B: discovery TX OK id=" + messageId);
                    }

                    @Override public void onMessageSendFailed(int messageId) {
                        HarpLog.i("B: discovery TX FAILED id=" + messageId);
                        if (messageId == MSG_READY && relayPeer != null && pub != null && !closed) {
                            HarpLog.i("B: retry READY una vez");
                            pub.sendMessage(relayPeer, MSG_READY_RETRY,
                                    Stage0Protocol.READY.getBytes(StandardCharsets.UTF_8));
                        } else if (messageId == MSG_READY_RETRY) {
                            HarpLog.i("B: READY retry FAILED");
                        }
                    }

                    @Override public void onSessionConfigFailed() {
                        HarpLog.i("B: publish FAILED");
                    }
                }, main);
            }

            @Override public void onAttachFailed() {
                HarpLog.i("B: attach FAILED");
            }
        }, main);
    }

    void startClient() {
        if (!preflight()) return;
        discoverStarted = SystemClock.elapsedRealtime();
        HarpLog.i("A: attach...");
        aware.attach(new AttachCallback() {
            @Override public void onAttached(WifiAwareSession session) {
                if (closed) { session.close(); return; }
                awareSession = session;
                HarpLog.i("A: attach OK");
                session.subscribe(new SubscribeConfig.Builder().setServiceName(SERVICE).build(),
                        new DiscoverySessionCallback() {
                    @Override public void onSubscribeStarted(SubscribeDiscoverySession session) {
                        sub = session;
                        HarpLog.i("A: subscribe OK service=" + SERVICE);
                    }

                    @Override public void onServiceDiscovered(
                            PeerHandle peer, byte[] info, java.util.List<byte[]> filters) {
                        if (!helloSent.compareAndSet(false, true)) return;
                        clientPeer = peer;
                        long ms = SystemClock.elapsedRealtime() - discoverStarted;
                        HarpLog.i("A: relay descubierto discovery_ms=" + ms);
                        sub.sendMessage(peer, MSG_HELLO,
                                Stage0Protocol.HELLO.getBytes(StandardCharsets.UTF_8));
                    }

                    @Override public void onMessageReceived(PeerHandle peer, byte[] msg) {
                        String text = new String(msg, StandardCharsets.UTF_8);
                        HarpLog.i("A: discovery RX=" + text);
                        if (Stage0Protocol.READY.equals(text)
                                && clientNdpStarted.compareAndSet(false, true)) {
                            startClientDataPath(peer);
                        }
                    }

                    @Override public void onMessageSendSucceeded(int messageId) {
                        HarpLog.i("A: discovery TX OK id=" + messageId);
                    }

                    @Override public void onMessageSendFailed(int messageId) {
                        HarpLog.i("A: discovery TX FAILED id=" + messageId);
                        if (messageId == MSG_HELLO && clientPeer != null && sub != null && !closed) {
                            HarpLog.i("A: retry HELLO una vez");
                            sub.sendMessage(clientPeer, MSG_HELLO_RETRY,
                                    Stage0Protocol.HELLO.getBytes(StandardCharsets.UTF_8));
                        } else if (messageId == MSG_HELLO_RETRY) {
                            HarpLog.i("A: HELLO retry FAILED");
                        }
                    }

                    @Override public void onSessionConfigFailed() {
                        HarpLog.i("A: subscribe FAILED");
                    }
                }, main);
            }

            @Override public void onAttachFailed() {
                HarpLog.i("A: attach FAILED");
            }
        }, main);
    }

    private boolean preflight() {
        if (closed) return false;

        boolean feature = appContext.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE);
        HarpLog.i("Device manufacturer=" + android.os.Build.MANUFACTURER
                + " model=" + android.os.Build.MODEL
                + " sdk=" + android.os.Build.VERSION.SDK_INT
                + " awareFeature=" + feature);

        if (aware == null) {
            HarpLog.i("FAIL: WifiAwareManager ausente");
            return false;
        }

        boolean available = aware.isAvailable();
        HarpLog.i("Aware available=" + available);

        if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.net.wifi.aware.Characteristics ch = aware.getCharacteristics();
            if (ch != null) {
                HarpLog.i("Aware cipherSuites=0x"
                        + Integer.toHexString(ch.getSupportedCipherSuites()));
            } else {
                HarpLog.i("Aware characteristics=null");
            }
        }

        if (!feature) {
            HarpLog.i("FAIL: FEATURE_WIFI_AWARE no declarado por el dispositivo");
            return false;
        }
        if (!available) {
            HarpLog.i("FAIL: Wi-Fi Aware no disponible");
            return false;
        }
        return true;
    }

    private void secure(WifiAwareNetworkSpecifier.Builder b) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            AwareSecurityApi33.apply(aware, b, PSK);
        } else {
            b.setPskPassphrase(PSK);
            HarpLog.i("Aware security=legacy PSK");
        }
    }

    private void startRelayDataPath(PeerHandle peer) {
        if (pub == null || closed) return;
        io.execute(() -> {
            try {
                server = new ServerSocket(0);
                server.setSoTimeout(NDP_TIMEOUT_MS);
                int port = server.getLocalPort();
                HarpLog.i("B: TCP server port=" + port);

                WifiAwareNetworkSpecifier.Builder sb =
                        new WifiAwareNetworkSpecifier.Builder(pub, peer);
                secure(sb);
                sb.setPort(port);

                NetworkRequest req = new NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
                        .setNetworkSpecifier(sb.build())
                        .build();

                netCb = new ConnectivityManager.NetworkCallback() {
                    @Override public void onAvailable(Network n) {
                        HarpLog.i("B: NDP available " + n);
                    }
                    @Override public void onLinkPropertiesChanged(
                            Network n, LinkProperties lp) {
                        recordRelayAwareInterface(lp);
                    }
                    @Override public void onUnavailable() {
                        HarpLog.i("B: NDP_TIMEOUT");
                        closeRelayTransport();
                    }
                    @Override public void onLost(Network n) {
                        HarpLog.i("B: NDP lost; stopping relay transport");
                        closeRelayTransport();
                    }
                };
                cm.requestNetwork(req, netCb, main, NDP_TIMEOUT_MS);
                pub.sendMessage(peer, MSG_READY,
                        Stage0Protocol.READY.getBytes(StandardCharsets.UTF_8));
                serveStage0Then1();
            } catch (Exception e) {
                HarpLog.i("B: NDP/server ERROR " + e.getClass().getSimpleName()
                        + ": " + e.getMessage());
            }
        });
    }

    private void serveStage0Then1() {
        try (Socket s = server.accept();
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)) {
            if (!isAwarePeer(s)) {
                HarpLog.i("B: FAIL_STAGE0 non-link-local peer=" + s.getRemoteSocketAddress());
                closeRelayTransport();
                return;
            }
            s.setSoTimeout(10000);
            String ping = in.readLine();
            HarpLog.i("B: Stage0 RX=" + ping);
            if (!Stage0Protocol.isValidPing(ping)) {
                HarpLog.i("B: FAIL_STAGE0 framing");
                closeRelayTransport();
                return;
            }
            out.println(Stage0Protocol.pongFor(ping));
            HarpLog.i("B: Stage0 PONG enviado");
        } catch (Exception e) {
            HarpLog.i("B: FAIL_STAGE0 " + e.getClass().getSimpleName() + ": " + e.getMessage());
            closeRelayTransport();
            return;
        }

        final Network internet;
        try (Socket s = server.accept()) {
            if (!isAwarePeer(s)) {
                HarpLog.i("B: FAIL_STAGE1 non-link-local peer=" + s.getRemoteSocketAddress());
                closeRelayTransport();
                return;
            }
            internet = InternetNetworkSelector.choose(cm);
            if (internet == null) {
                HarpLog.i("B: FAIL_STAGE1 sin Internet VALIDATED separado de Aware");
                closeRelayTransport();
                return;
            }
            HarpLog.i("B: upstream=" + InternetNetworkSelector.describe(cm, internet));
            MiniSocks5.serveOne(
                    s,
                    io,
                    Stage1InternetProbe.USER,
                    Stage1InternetProbe.PASS,
                    SocksPolicies.exactPublicTarget(
                            Stage1InternetProbe.TARGET_HOST,
                            Stage1InternetProbe.TARGET_PORT),
                    host -> {
                        java.net.InetAddress[] resolved = internet.getAllByName(host);
                        HarpLog.i("B: DNS via upstream host=" + host
                                + " count=" + resolved.length);
                        return resolved;
                    },
                    internet.getSocketFactory());
            HarpLog.i("B: Stage1 proxy finalizado");
        } catch (Exception e) {
            HarpLog.i("B: FAIL_STAGE1 proxy " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
            closeRelayTransport();
            return;
        }

        startStage2Relay(internet);
    }

    private void startStage2Relay(Network internet) {
        Stage2SessionCredentials credentials =
                Stage2SessionCredentials.create(random);

        try (Socket control = server.accept();
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(
                             control.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(
                             control.getOutputStream(), StandardCharsets.UTF_8), true)) {
            if (!isAwarePeer(control)) {
                HarpLog.i("B: FAIL_STAGE2_CONTROL non-link-local peer="
                        + control.getRemoteSocketAddress());
                closeRelayTransport();
                return;
            }

            control.setSoTimeout(10000);
            out.println(Stage2ControlProtocol.sessionLine(credentials));

            String ack = in.readLine();
            if (!Stage2ControlProtocol.OK.equals(ack)) {
                HarpLog.i("B: FAIL_STAGE2_CONTROL ack=" + ack);
                closeRelayTransport();
                return;
            }
            HarpLog.i("B: Stage2 control OK");
        } catch (Exception e) {
            HarpLog.i("B: FAIL_STAGE2_CONTROL "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            closeRelayTransport();
            return;
        }

        try {
            // Stage2 relay is persistent: remove the Stage0/1 accept timeout.
            server.setSoTimeout(0);

            if (!InternetNetworkSelector.isUsableInternet(cm, internet)) {
                HarpLog.i("B: FAIL_STAGE2_RELAY upstream no longer VALIDATED before start");
                closeRelayTransport();
                return;
            }

            registerRelayUpstreamWatch(internet);

            Stage2RelayServer relay = new Stage2RelayServer(
                    server,
                    credentials,
                    SocksPolicies.publicWeb(),
                    host -> {
                        java.net.InetAddress[] resolved = internet.getAllByName(host);
                        HarpLog.i("B: Stage2 DNS host=" + host
                                + " count=" + resolved.length);
                        return resolved;
                    },
                    internet.getSocketFactory(),
                    SocketPeerPolicies.awareLinkLocalOnly(),
                    8,
                    HarpLog::i);
            stage2Relay = relay;
            relay.start();
            HarpLog.i("PASS_STAGE2_RELAY_READY port=" + relay.port());
        } catch (Exception e) {
            HarpLog.i("B: FAIL_STAGE2_RELAY "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            closeRelayTransport();
        }
    }

    private void startClientDataPath(PeerHandle peer) {
        if (sub == null || closed) return;
        try {
            WifiAwareNetworkSpecifier.Builder sb =
                    new WifiAwareNetworkSpecifier.Builder(sub, peer);
            secure(sb);

            NetworkRequest req = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
                    .setNetworkSpecifier(sb.build())
                    .build();

            AtomicBoolean started = new AtomicBoolean();
            ndpStarted = SystemClock.elapsedRealtime();
            netCb = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network n) {
                    HarpLog.i("A: NDP available ndp_ms="
                            + (SystemClock.elapsedRealtime() - ndpStarted));
                    // Android guarantees onCapabilitiesChanged() immediately after
                    // onAvailable(); use that ordered callback instead of a
                    // synchronous getNetworkCapabilities() lookup here.
                }

                @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities caps) {
                    maybeRun(n, caps, started);
                }

                @Override public void onUnavailable() {
                    clientAwareNetwork = null;
                    vpnSession = null;
                    stopStage2VpnAfterTransportLoss();
                    HarpLog.i("A: NDP_TIMEOUT; Stage2 VPN stopped");
                }

                @Override public void onLost(Network n) {
                    if (n.equals(clientAwareNetwork)) {
                        clientAwareNetwork = null;
                    }
                    vpnSession = null;
                    stopStage2VpnAfterTransportLoss();
                    HarpLog.i("A: NDP lost; Stage2 VPN stopped and session invalidated");
                }
            };
            cm.requestNetwork(req, netCb, main, NDP_TIMEOUT_MS);
        } catch (Exception e) {
            HarpLog.i("A: NDP ERROR " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void maybeRun(Network network, NetworkCapabilities caps, AtomicBoolean started) {
        if (caps == null || started.get()) return;
        Object infoObject = caps.getTransportInfo();
        if (!(infoObject instanceof WifiAwareNetworkInfo)) return;
        WifiAwareNetworkInfo info = (WifiAwareNetworkInfo) infoObject;
        if (info.getPeerIpv6Addr() == null || info.getPort() <= 0) return;
        if (!started.compareAndSet(false, true)) return;

        InetSocketAddress dst = new InetSocketAddress(info.getPeerIpv6Addr(), info.getPort());
        clientAwareNetwork = network;
        HarpLog.i("A: peer=" + dst + " advertisedPort=" + info.getPort());
        io.execute(() -> runTests(network, dst));
    }

    private void runTests(Network network, InetSocketAddress dst) {
        long t0 = SystemClock.elapsedRealtime();
        try (Socket s = network.getSocketFactory().createSocket()) {
            s.connect(dst, 8000);
            s.setSoTimeout(10000);
            PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            String ping = Stage0Protocol.newPing(random);
            out.println(ping);
            String pong = in.readLine();
            long rtt = SystemClock.elapsedRealtime() - t0;
            HarpLog.i("A: stage0_rtt_ms=" + rtt + " RX=" + pong);
            if (!Stage0Protocol.isExpectedPong(ping, pong)) {
                HarpLog.i("FAIL_STAGE0 respuesta incorrecta");
                return;
            }
            HarpLog.i("PASS_STAGE0");
        } catch (Exception e) {
            HarpLog.i("FAIL_STAGE0 " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return;
        }

        long t1 = SystemClock.elapsedRealtime();
        try (Socket s = network.getSocketFactory().createSocket()) {
            s.connect(dst, 8000);
            String status = Stage1InternetProbe.run(s);
            HarpLog.i("A: stage1_elapsed_ms="
                    + (SystemClock.elapsedRealtime() - t1) + " status=" + status);
            HarpLog.i("PASS_STAGE1");
        } catch (Exception e) {
            HarpLog.i("FAIL_STAGE1 " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return;
        }

        final Stage2SessionCredentials credentials;
        try (Socket control = network.getSocketFactory().createSocket();
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(
                             control.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(
                             control.getOutputStream(), StandardCharsets.UTF_8), true)) {
            control.connect(dst, 8000);
            control.setSoTimeout(10000);

            String sessionLine = in.readLine();
            credentials = Stage2ControlProtocol.parseSessionLine(sessionLine);
            out.println(Stage2ControlProtocol.OK);
            HarpLog.i("A: Stage2 credentials recibidas userLen="
                    + credentials.username().length()
                    + " passLen=" + credentials.password().length());
        } catch (Exception e) {
            HarpLog.i("FAIL_STAGE2_CONTROL "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return;
        }

        long t2 = SystemClock.elapsedRealtime();
        try (Socket s = network.getSocketFactory().createSocket()) {
            s.connect(dst, 8000);
            String status = Stage1InternetProbe.run(
                    s,
                    credentials.username(),
                    credentials.password());
            HarpLog.i("A: stage2_relay_elapsed_ms="
                    + (SystemClock.elapsedRealtime() - t2)
                    + " status=" + status);

            if (closed || !network.equals(clientAwareNetwork)) {
                HarpLog.i("A: Stage2 proof completed after NDP invalidation; "
                        + "VPN handoff discarded");
                return;
            }

            vpnSession = new Stage2VpnSession(
                    network,
                    dst,
                    credentials);
            HarpLog.i("PASS_STAGE2_RELAY");
            HarpLog.i("A: Stage2 VPN session READY");
        } catch (Exception e) {
            HarpLog.i("FAIL_STAGE2_RELAY "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    Stage2VpnSession stage2VpnSession() {
        return vpnSession;
    }

    private void stopStage2VpnAfterTransportLoss() {
        try {
            Intent stop = new Intent(appContext, HarpVpnService.class)
                    .setAction(HarpVpnService.ACTION_STOP);
            // stopService() guarantees onDestroy()/shutdown even if the service
            // does not receive a new start command. ACTION_STOP is retained for
            // explicit callers and future service-command handling.
            appContext.stopService(stop);
        } catch (RuntimeException e) {
            HarpLog.i("Stage2 VPN stop warning after NDP loss: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }



    private void recordRelayAwareInterface(LinkProperties lp) {
        if (lp == null) return;
        String name = lp.getInterfaceName();
        if (name == null || name.isEmpty()) return;

        List<byte[]> localIpv6 = new ArrayList<>();
        for (android.net.LinkAddress linkAddress : lp.getLinkAddresses()) {
            java.net.InetAddress address = linkAddress.getAddress();
            if (address instanceof java.net.Inet6Address
                    && address.isLinkLocalAddress()) {
                localIpv6.add(address.getAddress().clone());
            }
        }
        relayAwareLocalIpv6 = Collections.unmodifiableList(localIpv6);

        try {
            java.net.NetworkInterface nif = java.net.NetworkInterface.getByName(name);
            if (nif == null) {
                HarpLog.i("B: Aware interface name=" + name
                        + " index=unresolved localLinkLocal=" + localIpv6.size());
                return;
            }
            relayAwareInterfaceName = name;
            relayAwareInterfaceIndex = nif.getIndex();
            HarpLog.i("B: Aware interface name=" + name
                    + " index=" + relayAwareInterfaceIndex
                    + " localLinkLocal=" + localIpv6.size());
        } catch (Exception e) {
            HarpLog.i("B: Aware interface resolve warning "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private synchronized void registerRelayUpstreamWatch(Network internet) {
        unregisterRelayUpstreamWatch();
        relayUpstreamNetwork = internet;

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        ConnectivityManager.NetworkCallback callback =
                new ConnectivityManager.NetworkCallback() {
            @Override public void onCapabilitiesChanged(
                    Network network, NetworkCapabilities caps) {
                if (!network.equals(relayUpstreamNetwork)) return;
                if (caps == null
                        || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE)) {
                    HarpLog.i("B: Stage2 upstream lost VALIDATED/INTERNET; "
                            + "stopping relay fail-closed network=" + network);
                    closeRelayTransport();
                }
            }

            @Override public void onLost(Network network) {
                if (!network.equals(relayUpstreamNetwork)) return;
                HarpLog.i("B: Stage2 upstream lost; stopping relay fail-closed network="
                        + network);
                closeRelayTransport();
            }
        };

        upstreamCb = callback;
        cm.registerNetworkCallback(request, callback, main);
        HarpLog.i("B: Stage2 upstream watch registered "
                + InternetNetworkSelector.describe(cm, internet));
    }

    private synchronized void unregisterRelayUpstreamWatch() {
        ConnectivityManager.NetworkCallback callback = upstreamCb;
        upstreamCb = null;
        relayUpstreamNetwork = null;
        if (callback == null) return;
        try {
            cm.unregisterNetworkCallback(callback);
        } catch (Exception ignored) {
        }
    }

    private synchronized void closeRelayTransport() {
        unregisterRelayUpstreamWatch();

        ConnectivityManager.NetworkCallback callback = netCb;
        netCb = null;
        if (callback != null) {
            try {
                cm.unregisterNetworkCallback(callback);
            } catch (Exception ignored) {
            }
        }

        relayAwareInterfaceIndex = 0;
        relayAwareInterfaceName = null;
        relayAwareLocalIpv6 = Collections.emptyList();
        try {
            if (stage2Relay != null) {
                stage2Relay.close();
                stage2Relay = null;
            } else if (server != null) {
                server.close();
            }
            server = null;
        } catch (Exception e) {
            HarpLog.i("B: relay transport close warning "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private boolean isAwarePeer(Socket socket) {
        java.net.InetAddress remote = socket.getInetAddress();
        if (!(remote instanceof java.net.Inet6Address) || !remote.isLinkLocalAddress()) {
            return false;
        }

        java.net.InetAddress local = socket.getLocalAddress();
        List<byte[]> expectedLocalAddresses = relayAwareLocalIpv6;
        if (!expectedLocalAddresses.isEmpty()) {
            if (!(local instanceof java.net.Inet6Address)
                    || !containsAddressBytes(expectedLocalAddresses, local.getAddress())) {
                HarpLog.i("B: peer local-address mismatch awareIf="
                        + relayAwareInterfaceName
                        + " local=" + local
                        + " expectedCount=" + expectedLocalAddresses.size());
                return false;
            }
        }

        int expected = relayAwareInterfaceIndex;
        if (expected <= 0) {
            HarpLog.i("B: peer interface-index check unavailable remote=" + remote
                    + " local=" + local
                    + " awareIf=" + relayAwareInterfaceName);
            return true;
        }

        int remoteScope = ((java.net.Inet6Address) remote).getScopeId();
        int localScope = local instanceof java.net.Inet6Address
                ? ((java.net.Inet6Address) local).getScopeId()
                : 0;

        if ((remoteScope != 0 || localScope != 0)
                && remoteScope != expected
                && localScope != expected) {
            HarpLog.i("B: peer scope mismatch expectedIf=" + relayAwareInterfaceName
                    + " expectedIndex=" + expected
                    + " remoteScope=" + remoteScope
                    + " localScope=" + localScope);
            return false;
        }

        HarpLog.i("B: peer Aware binding accepted expectedIf="
                + relayAwareInterfaceName
                + " expectedIndex=" + expected
                + " localAddressMatched=" + !expectedLocalAddresses.isEmpty()
                + " remoteScope=" + remoteScope
                + " localScope=" + localScope);
        return true;
    }

    private static boolean containsAddressBytes(List<byte[]> expected, byte[] actual) {
        for (byte[] candidate : expected) {
            if (java.util.Arrays.equals(candidate, actual)) return true;
        }
        return false;
    }

    void close() {
        if (closed) return;
        closed = true;
        clientAwareNetwork = null;
        vpnSession = null;
        relayAwareInterfaceIndex = 0;
        relayAwareInterfaceName = null;
        relayAwareLocalIpv6 = Collections.emptyList();
        try { if (netCb != null) cm.unregisterNetworkCallback(netCb); } catch (Exception ignored) {}
        unregisterRelayUpstreamWatch();
        try {
            if (stage2Relay != null) {
                stage2Relay.close();
            } else if (server != null) {
                server.close();
            }
        } catch (Exception ignored) {}
        try { if (pub != null) pub.close(); } catch (Exception ignored) {}
        try { if (sub != null) sub.close(); } catch (Exception ignored) {}
        try { if (awareSession != null) awareSession.close(); } catch (Exception ignored) {}
        io.shutdownNow();
    }
}
