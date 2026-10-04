package org.harp.l2;

import java.security.SecureRandom;

/** Pure-Java framing/validation for the Stage-0 proof. */
final class Stage0Protocol {
    static final String HELLO = "HELLO";
    static final String READY = "READY";
    private static final String PING_PREFIX = "PING-";
    private static final String PONG_PREFIX = "PONG:";
    private static final int NONCE_BYTES = 16;
    private static final int NONCE_HEX = NONCE_BYTES * 2;

    private Stage0Protocol() {}

    static String newPing(SecureRandom random) {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        return PING_PREFIX + hex(nonce);
    }

    static boolean isValidPing(String line) {
        if (line == null || line.length() != PING_PREFIX.length() + NONCE_HEX) return false;
        if (!line.startsWith(PING_PREFIX)) return false;
        for (int i = PING_PREFIX.length(); i < line.length(); i++) {
            char c = line.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) return false;
        }
        return true;
    }

    static String pongFor(String ping) {
        if (!isValidPing(ping)) throw new IllegalArgumentException("invalid Stage-0 PING");
        return PONG_PREFIX + ping;
    }

    static boolean isExpectedPong(String ping, String pong) {
        return isValidPing(ping) && (PONG_PREFIX + ping).equals(pong);
    }

    private static String hex(byte[] b) {
        char[] out = new char[b.length * 2];
        final char[] digits = "0123456789abcdef".toCharArray();
        for (int i = 0; i < b.length; i++) {
            int v = b[i] & 0xff;
            out[i * 2] = digits[v >>> 4];
            out[i * 2 + 1] = digits[v & 0x0f];
        }
        return new String(out);
    }
}
