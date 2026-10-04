package org.harp.l2;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

public final class HarpLog {
    public interface Listener { void onLine(String line); }

    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private static final StringBuilder buffer = new StringBuilder();
    private static final Object lock = new Object();
    private static final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    private HarpLog() {}

    public static void addListener(Listener l) { listeners.addIfAbsent(l); }
    public static void removeListener(Listener l) { listeners.remove(l); }

    public static String snapshot() {
        synchronized (lock) { return buffer.toString(); }
    }

    public static void i(String msg) {
        String line = fmt.format(new Date()) + "  " + msg;
        android.util.Log.i("HARP", msg);
        synchronized (lock) {
            buffer.append(line).append('\n');
            if (buffer.length() > 200000) buffer.delete(0, buffer.length() - 150000);
        }
        for (Listener l : listeners) l.onLine(line);
    }
}
