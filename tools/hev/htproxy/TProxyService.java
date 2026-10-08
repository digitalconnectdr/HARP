package hev.htproxy;

/** Test double for the official HEV 2.18.0 Android JNI binding. */
public final class TProxyService {
    private static boolean running;

    private TProxyService() {}

    public static boolean TProxyStartService(String configPath, int fd) {
        running = configPath != null && !configPath.isEmpty() && fd >= 0;
        return running;
    }

    public static boolean TProxyStopService() {
        boolean wasRunning = running;
        running = false;
        return wasRunning;
    }

    public static boolean TProxyIsRunning() {
        return running;
    }

    public static long[] TProxyGetStats() {
        return new long[]{11L, 22L, 33L, 44L};
    }
}
