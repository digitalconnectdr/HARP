/** W22 JDK-only reference. NOT integrated into Android or production relay. */
public final class PinnedEgressGate {
  public enum State { PENDING, ACTIVE, REVOKED }
  private final long epoch;
  private final String network;
  private State state = State.PENDING;
  private boolean capsSeen, eligible, blockedSeen, blocked = true;
  private int starts, closes;
  public PinnedEgressGate(long epoch, String network) {
    if (epoch <= 0 || network == null || network.isEmpty()) throw new IllegalArgumentException();
    this.epoch = epoch; this.network = network;
  }
  private boolean matches(long e, String n) { return e == epoch && network.equals(n); }
  public synchronized void capabilities(long e, String n, boolean allowed) {
    if (!matches(e,n) || state == State.REVOKED) return;
    capsSeen = true; eligible = allowed;
    if (!allowed) revoke(); else activate();
  }
  public synchronized void blocked(long e, String n, boolean isBlocked) {
    if (!matches(e,n) || state == State.REVOKED) return;
    blockedSeen = true; blocked = isBlocked;
    if (blocked) revoke(); else activate();
  }
  public synchronized void lost(long e, String n) { if (matches(e,n)) revoke(); }
  public synchronized void timeout() { revoke(); }
  public synchronized void revoke() { if (state == State.REVOKED) return; if (state == State.ACTIVE) closes++; state = State.REVOKED; }
  private void activate() { if (state == State.PENDING && capsSeen && eligible && blockedSeen && !blocked) { state = State.ACTIVE; starts++; } }
  public synchronized State state() { return state; }
  public synchronized int starts() { return starts; }
  public synchronized int closes() { return closes; }
}
