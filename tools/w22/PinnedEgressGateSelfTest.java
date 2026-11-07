/** Deterministic W22 model tests; no Android callbacks, sockets or Internet. */
public final class PinnedEgressGateSelfTest {
  static int checks;
  static void eq(Object expected, Object actual) { checks++; if (!expected.equals(actual)) throw new AssertionError("expected="+expected+" actual="+actual); }
  static PinnedEgressGate gate() { return new PinnedEgressGate(2,"wifi"); }
  static void active(PinnedEgressGate g) { g.capabilities(2,"wifi",true); g.blocked(2,"wifi",false); }
  public static void main(String[] args) {
    PinnedEgressGate g=gate(); g.capabilities(2,"wifi",true); eq(PinnedEgressGate.State.PENDING,g.state()); eq(0,g.starts()); // B01
    g.blocked(2,"wifi",false); eq(PinnedEgressGate.State.ACTIVE,g.state()); eq(1,g.starts()); // B02
    g=gate(); g.blocked(2,"wifi",false); eq(PinnedEgressGate.State.PENDING,g.state()); g.capabilities(2,"wifi",true); eq(PinnedEgressGate.State.ACTIVE,g.state()); // B03
    g=gate(); g.capabilities(2,"wifi",true); g.blocked(2,"wifi",true); eq(PinnedEgressGate.State.REVOKED,g.state()); eq(0,g.starts()); // B04
    g=gate(); active(g); g.blocked(2,"wifi",true); eq(PinnedEgressGate.State.REVOKED,g.state()); eq(1,g.closes()); // B05
    g=gate(); active(g); g.capabilities(2,"wifi",false); eq(PinnedEgressGate.State.REVOKED,g.state()); eq(1,g.closes()); // B06
    g=gate(); g.timeout(); eq(PinnedEgressGate.State.REVOKED,g.state()); eq(0,g.starts()); // B07
    g.blocked(2,"wifi",false); eq(PinnedEgressGate.State.REVOKED,g.state()); // B08
    g=gate(); g.blocked(1,"wifi",false); g.capabilities(2,"wifi",true); eq(PinnedEgressGate.State.PENDING,g.state()); // B09
    g=gate(); g.capabilities(2,"cell",true); g.blocked(2,"cell",false); eq(PinnedEgressGate.State.PENDING,g.state()); // B10
    g=gate(); g.capabilities(2,"wifi",true); g.revoke(); g.blocked(2,"wifi",false); eq(PinnedEgressGate.State.REVOKED,g.state()); eq(0,g.starts()); // B11
    g=gate(); eq(PinnedEgressGate.State.PENDING,g.state()); eq(0,g.starts()); // B12 simulated fresh object only
    g=gate(); active(g); g.lost(1,"wifi"); eq(PinnedEgressGate.State.ACTIVE,g.state()); g.lost(2,"wifi"); eq(PinnedEgressGate.State.REVOKED,g.state());
    g.revoke(); eq(1,g.closes());
    System.out.println("PASS_HARP_W22_JDK_ONLY_PINNED_EGRESS_MODEL checks="+checks);
  }
}
