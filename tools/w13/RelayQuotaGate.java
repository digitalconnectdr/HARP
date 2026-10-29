/**
 * HARP W13: offline reference for relay upstream consent + payload quota.
 * JDK-only; NOT wired into Android; not a carrier billing meter.
 * Every allowed direction must reserve bytes before a relay write.
 */
public final class RelayQuotaGate {
    public enum Transport { WIFI, ETHERNET, CELLULAR, VPN, AWARE, OTHER }

    public static final class Consent {
        public final boolean relayApproved, redistributionAuthorized;
        public final boolean allowMetered, allowRoaming, allowVpn;
        public final long maxPayloadBytes, durationNanos;

        public Consent(boolean relayApproved, boolean redistributionAuthorized,
                       boolean allowMetered, boolean allowRoaming, boolean allowVpn,
                       long maxPayloadBytes, long durationNanos) {
            if (maxPayloadBytes <= 0 || durationNanos <= 0 || durationNanos > Long.MAX_VALUE / 2) {
                throw new IllegalArgumentException("positive finite quota and duration required");
            }
            this.relayApproved = relayApproved;
            this.redistributionAuthorized = redistributionAuthorized;
            this.allowMetered = allowMetered;
            this.allowRoaming = allowRoaming;
            this.allowVpn = allowVpn;
            this.maxPayloadBytes = maxPayloadBytes;
            this.durationNanos = durationNanos;
        }
    }

    public static final class Snapshot {
        public final Transport transport;
        public final boolean internet, validated, notMetered, notRoaming;
        public final boolean notVpn, underlayVerified;

        public Snapshot(Transport transport, boolean internet, boolean validated,
                        boolean notMetered, boolean notRoaming, boolean notVpn,
                        boolean underlayVerified) {
            this.transport = transport;
            this.internet = internet;
            this.validated = validated;
            this.notMetered = notMetered;
            this.notRoaming = notRoaming;
            this.notVpn = notVpn;
            this.underlayVerified = underlayVerified;
        }
    }

    private final Consent consent;
    private final String networkId;
    private final long startNanos;
    private long usedBytes;
    private boolean revoked;

    private RelayQuotaGate(Consent consent, String networkId, long startNanos) {
        this.consent = consent;
        this.networkId = networkId;
        this.startNanos = startNanos;
    }

    /** No gate is issued until explicit user and ISP/venue authorization pass. */
    public static RelayQuotaGate open(Consent consent, String networkId,
                                      Snapshot snapshot, long nowNanos) {
        if (consent == null || networkId == null || networkId.trim().isEmpty()
                || !allowed(consent, snapshot)) {
            throw new IllegalStateException("relay upstream not authorized");
        }
        return new RelayQuotaGate(consent, networkId, nowNanos);
    }

    private static boolean allowed(Consent c, Snapshot s) {
        if (c == null || !c.relayApproved || !c.redistributionAuthorized || s == null
                || s.transport == null || !s.internet || !s.validated) return false;
        if (s.transport == Transport.AWARE || s.transport == Transport.OTHER) return false;
        boolean vpn = s.transport == Transport.VPN || !s.notVpn;
        if (vpn && (!c.allowVpn || !s.underlayVerified)) return false;
        if (!s.notMetered && !c.allowMetered) return false;
        // Missing NOT_ROAMING on CELLULAR is conservatively treated as roaming/unknown.
        if (s.transport == Transport.CELLULAR && !s.notRoaming && !c.allowRoaming) return false;
        return true;
    }

    private boolean expired(long nowNanos) {
        // Signed difference is safe for monotonic durations under 2^62 ns.
        return nowNanos - startNanos < 0
                || nowNanos - startNanos >= consent.durationNanos;
    }

    /** Permanently revoke on upstream loss, capability change, or network switch. */
    public synchronized boolean reconcile(String currentNetworkId, Snapshot snapshot,
                                           long nowNanos) {
        if (revoked || expired(nowNanos) || !networkId.equals(currentNetworkId)
                || !allowed(consent, snapshot)) revoked = true;
        return !revoked;
    }

    /** Atomic, non-refundable application-payload reservation in either direction. */
    public synchronized boolean reserve(int bytes, long nowNanos) {
        if (revoked || expired(nowNanos)) {
            revoked = true;
            return false;
        }
        if (bytes <= 0 || bytes > consent.maxPayloadBytes - usedBytes) return false;
        usedBytes += bytes;
        if (usedBytes == consent.maxPayloadBytes) revoked = true;
        return true;
    }

    public synchronized void revoke() { revoked = true; }
    public synchronized long usedPayloadBytes() { return usedBytes; }
    public synchronized boolean isRevoked() { return revoked; }
}
