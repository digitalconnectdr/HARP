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
        return new Stage2SessionCredentials(
                "h_" + encoder.encodeToString(userBytes),
                encoder.encodeToString(passBytes));
    }

    String username() {
        return username;
    }

    String password() {
        return password;
    }
}
