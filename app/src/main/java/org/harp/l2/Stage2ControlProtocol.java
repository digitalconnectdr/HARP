package org.harp.l2;

import java.io.IOException;

/** Small line protocol carried only inside the encrypted Wi-Fi Aware data path. */
final class Stage2ControlProtocol {
    static final String OK = "HARP2 OK";
    private static final String PREFIX = "HARP2 SESSION ";
    private static final int MAX_LINE_LENGTH = 256;

    private Stage2ControlProtocol() {}

    static String sessionLine(Stage2SessionCredentials credentials) {
        return PREFIX + credentials.username() + " " + credentials.password();
    }

    static Stage2SessionCredentials parseSessionLine(String line) throws IOException {
        if (line == null || line.length() > MAX_LINE_LENGTH || !line.startsWith(PREFIX)) {
            throw new IOException("invalid Stage2 control frame");
        }

        String body = line.substring(PREFIX.length());
        int separator = body.indexOf(' ');
        if (separator <= 0 || separator != body.lastIndexOf(' ')) {
            throw new IOException("invalid Stage2 session frame");
        }

        String username = body.substring(0, separator);
        String password = body.substring(separator + 1);
        try {
            return Stage2SessionCredentials.fromTokens(username, password);
        } catch (IllegalArgumentException e) {
            throw new IOException("invalid Stage2 credentials", e);
        }
    }
}
