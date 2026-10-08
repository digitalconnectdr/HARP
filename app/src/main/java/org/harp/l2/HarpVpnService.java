package org.harp.l2;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.Network;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Stage-2A TCP-first VPN on phone A.
 *
 * This service is intentionally not sticky: a process restart loses the live
 * Wi-Fi Aware Network and per-session relay credentials, so a fresh relay
 * preflight is required instead of silently restoring stale state.
 */
public final class HarpVpnService extends VpnService {
    static final String ACTION_START = "org.harp.l2.action.START_VPN";
    static final String ACTION_STOP = "org.harp.l2.action.STOP_VPN";

    private static final String CHANNEL_ID = "harp-stage2-vpn";
    private static final int NOTIFICATION_ID = 2102;

    private ParcelFileDescriptor tun;
    private ProtectedAwareBridge bridge;
    private File configFile;
    private boolean running;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP.equals(action)) {
            shutdown();
            stopSelf();
            return START_NOT_STICKY;
        }

        startForegroundNow();

        synchronized (this) {
            if (running) {
                HarpLog.i("Stage2 VPN already running");
                return START_NOT_STICKY;
            }
        }

        try {
            startVpn();
        } catch (Exception e) {
            HarpLog.i("FAIL_STAGE2_VPN "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            shutdown();
            stopSelf();
        } catch (LinkageError e) {
            HarpLog.i("FAIL_STAGE2_VPN native linkage: " + e.getMessage());
            shutdown();
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private void startVpn() throws Exception {
        Stage2VpnSession session = HarpRuntime.currentVpnSession();
        if (session == null) {
            throw new IllegalStateException(
                    "no proven Stage2 relay session; run CLIENTE preflight first");
        }
        if (!HevTunnelAdapter.isPackaged()) {
            throw new IllegalStateException(
                    "HEV Android AAR is not packaged in this local build");
        }

        Network awareNetwork = session.awareNetwork();

        Builder builder = new Builder()
                .setSession("HARP Stage2A")
                .setBlocking(false)
                .setMtu(Stage2TunnelConfig.MTU)
                .addAddress(Stage2TunnelConfig.TUN_IPV4, 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(Stage2TunnelConfig.DNS_IPV4)
                .setUnderlyingNetworks(new Network[]{awareNetwork});

        // HARP's own Java/JNI sockets must stay outside the VPN. Normal apps
        // remain captured by the default route above.
        try {
            builder.addDisallowedApplication(getPackageName());
        } catch (PackageManager.NameNotFoundException e) {
            throw new IllegalStateException(
                    "could not exclude HARP package from its own VPN", e);
        }

        ParcelFileDescriptor established = builder.establish();
        if (established == null) {
            throw new IOException("VpnService.Builder.establish() returned null");
        }
        tun = established;

        Stage2SessionCredentials credentials = session.credentials();
        configFile = writeHevConfig(credentials);

        ProtectedAwareBridge newBridge = new ProtectedAwareBridge(
                this,
                awareNetwork,
                session.relayAddress());
        newBridge.start();
        bridge = newBridge;

        boolean started = HevTunnelAdapter.start(
                configFile.getAbsolutePath(),
                tun.getFd());
        if (!started) {
            throw new IOException("HEV TProxyStartService returned false");
        }

        synchronized (this) {
            running = true;
        }

        HarpLog.i("PASS_STAGE2_VPN_STARTED");
        HarpLog.i("Stage2 VPN underlying=" + awareNetwork
                + " relay=" + session.relayAddress());
    }

    private File writeHevConfig(
            Stage2SessionCredentials credentials) throws IOException {
        File file = new File(getCacheDir(), "harp-stage2-hev.yml");
        byte[] yaml = Stage2TunnelConfig.render(credentials)
                .getBytes(StandardCharsets.UTF_8);

        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(yaml);
            out.flush();
        }

        return file;
    }

    private void startForegroundNow() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "HARP Stage2 VPN",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("HARP Wi-Fi Aware relay VPN");
            nm.createNotificationChannel(channel);
        }

        Intent openApp = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("HARP Stage2 VPN")
                .setContentText("Internet relay over Wi-Fi Aware")
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @Override
    public void onRevoke() {
        HarpLog.i("Stage2 VPN permission revoked");
        shutdown();
        stopSelf();
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        shutdown();
        super.onDestroy();
    }

    private synchronized void shutdown() {
        boolean hadState =
                running || tun != null || bridge != null || configFile != null;

        try {
            if (HevTunnelAdapter.isPackaged()
                    && HevTunnelAdapter.isRunning()) {
                HevTunnelAdapter.stop();
            }
        } catch (Throwable e) {
            HarpLog.i("Stage2 HEV stop warning: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        if (bridge != null) {
            bridge.close();
            bridge = null;
        }

        if (tun != null) {
            try {
                tun.close();
            } catch (IOException ignored) {
            }
            tun = null;
        }

        if (configFile != null) {
            if (configFile.exists() && !configFile.delete()) {
                HarpLog.i("Stage2 config cleanup warning");
            }
            configFile = null;
        }

        running = false;

        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }

        if (hadState) {
            HarpLog.i("Stage2 VPN stopped");
        }
    }
}
