import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic JDK-only checks; no Android, RF, phones, network or ISP billing. */
public final class RelayQuotaGateSelfTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static RelayQuotaGate.Snapshot wifi() {
        return new RelayQuotaGate.Snapshot(RelayQuotaGate.Transport.WIFI,
                true, true, true, true, true, false);
    }

    private static RelayQuotaGate.Consent consent(boolean metered, boolean roaming,
                                                  boolean vpn, long max) {
        return new RelayQuotaGate.Consent(true, true, metered, roaming, vpn, max,
                10_000_000_000L);
    }

    private static boolean rejects(RelayQuotaGate.Consent c, String id,
                                   RelayQuotaGate.Snapshot s) {
        try {
            RelayQuotaGate.open(c, id, s, 100L);
            return false;
        } catch (IllegalStateException expected) {
            return true;
        }
    }

    public static void main(String[] args) throws Exception {
        check(rejects(new RelayQuotaGate.Consent(false, true, false, false, false,
                100, 1_000_000L), "wifi1", wifi()), "consent required");
        check(rejects(new RelayQuotaGate.Consent(true, false, false, false, false,
                100, 1_000_000L), "wifi1", wifi()), "ISP rights required");
        check(rejects(consent(false, false, false, 100), "wifi1",
                new RelayQuotaGate.Snapshot(RelayQuotaGate.Transport.WIFI,
                        true, true, false, true, true, false)), "metered Wi-Fi denied");
        check(rejects(consent(false, false, false, 100), "cell1",
                new RelayQuotaGate.Snapshot(RelayQuotaGate.Transport.CELLULAR,
                        true, true, false, false, true, false)), "cellular denied");
        check(rejects(consent(true, false, false, 100), "cell1",
                new RelayQuotaGate.Snapshot(RelayQuotaGate.Transport.CELLULAR,
                        true, true, false, false, true, false)), "roaming denied");
        check(rejects(consent(false, false, false, 100), "vpn1",
                new RelayQuotaGate.Snapshot(RelayQuotaGate.Transport.VPN,
                        true, true, true, true, false, false)), "VPN denied");
        check(rejects(consent(true, true, true, 100), "vpn1",
                new RelayQuotaGate.Snapshot(RelayQuotaGate.Transport.VPN,
                        true, true, false, false, false, false)), "opaque VPN denied");
        check(rejects(consent(true, true, true, 100), "aware1",
                new RelayQuotaGate.Snapshot(RelayQuotaGate.Transport.AWARE,
                        true, true, true, true, true, true)), "Aware not upstream");
        RelayQuotaGate g = RelayQuotaGate.open(consent(false, false, false, 100), "wifi1", wifi(), 100L);
        check(g.reserve(60, 101L), "first reservation");
        check(!g.reserve(41, 102L), "over-quota must fail");
        check(g.reserve(40, 103L), "exact remaining bytes");
        check(!g.reserve(1, 104L) && g.isRevoked(), "quota exhausted");
        check(g.usedPayloadBytes() == 100, "exact counted payload");

        RelayQuotaGate h = RelayQuotaGate.open(consent(false, false, false, 100), "wifi1", wifi(), 100L);
        check(!h.reconcile("wifi2", wifi(), 200L), "network switch revokes");
        check(!h.reconcile("wifi1", wifi(), 300L), "no automatic re-enable");
        RelayQuotaGate e = RelayQuotaGate.open(consent(false, false, false, 100), "wifi1", wifi(), 100L);
        check(!e.reserve(1, 10_000_000_100L), "expiry revokes");
        RelayQuotaGate d = RelayQuotaGate.open(consent(false, false, false, 100), "wifi1", wifi(), 100L);
        check(!d.reconcile("wifi1", new RelayQuotaGate.Snapshot(
                RelayQuotaGate.Transport.WIFI, true, true, false, true, true, false), 101L),
                "unmetered to metered revokes");
        RelayQuotaGate r = RelayQuotaGate.open(consent(true, true, false, 100), "cell1",
                new RelayQuotaGate.Snapshot(RelayQuotaGate.Transport.CELLULAR,
                        true, true, false, false, true, false), 100L);
        check(r.reserve(10, 101L), "explicitly consented metered roaming allowed");
        RelayQuotaGate v = RelayQuotaGate.open(consent(true, true, true, 100), "vpn1",
                new RelayQuotaGate.Snapshot(RelayQuotaGate.Transport.VPN,
                        true, true, false, false, false, true), 100L);
        check(v.reserve(1, 101L), "verified VPN underlay plus explicit consent");

        final int threads = 8, attempts = 1000, chunk = 16, max = 777 * chunk;
        RelayQuotaGate concurrent = RelayQuotaGate.open(consent(false, false, false, max),
                "wifi1", wifi(), 100L);
        CountDownLatch start = new CountDownLatch(1);
        Thread[] workers = new Thread[threads];
        AtomicInteger allowed = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                try { start.await(); } catch (InterruptedException x) { throw new AssertionError(x); }
                for (int j = 0; j < attempts; j++) {
                    if (concurrent.reserve(chunk, 101L)) allowed.incrementAndGet();
                }
            });
            workers[i].start();
        }
        start.countDown();
        for (Thread worker : workers) worker.join();
        check(allowed.get() == 777 && concurrent.usedPayloadBytes() == max,
                "concurrent reservations must never exceed quota");
        concurrent.revoke();
        check(!concurrent.reserve(1, 102L), "revoked cannot reopen");
        System.out.println("PASS_HARP_W13_JDK_ONLY_QUOTA_GATE_SELFTEST");
        System.out.println("NO_ANDROID_BUILD_NO_LIVE_RELAY_NO_BILLING_MEASUREMENT");
    }
}
