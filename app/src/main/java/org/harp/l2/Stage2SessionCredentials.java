package org.harp.l2;

import java.security.SecureRandom;
import java.util.Base64;

/** Short-lived SOCKS credentials for one Stage-2 relay session. */
final class Stage2SessionCredentials {
    private final String username;
    private final String password;

    private Stage2SessionCredentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    static Stage2SessionCredentials create(SecureRandom random) {
        byte[] userBytes = new byte[9];
        byte[] passBytes = new byte[24];
        random.nextBytes(userBytes);
        random.nextBytes(passBytes);

        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return fromTokens(
                "h_" + encoder.encodeToString(userBytes),
                encoder.encodeToString(passBytes));
    }

    static Stage2SessionCredentials fromTokens(String username, String password) {
        requireSafeToken(username, 8, 64, "username");
        requireSafeToken(password, 24, 128, "password");
        return new Stage2SessionCredentials(username, password);
    }

    String username() {
        return username;
    }

    String password() {
        return password;
    }

    private static void requireSafeToken(
            String value, int minLength, int maxLength, String field) {
        if (value == null || value.length() < minLength || value.length() > maxLength) {
            throw new IllegalArgumentException("invalid " + field + " length");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok =
                    (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_'
                    || c == '-';
            if (!ok) throw new IllegalArgumentException("unsafe " + field);
        }
    }
}
