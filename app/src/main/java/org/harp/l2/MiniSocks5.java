package org.harp.l2;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.net.SocketFactory;

/**
 * Minimal SOCKS5 CONNECT implementation for the controlled Stage-1 proof only.
 * It intentionally requires username/password and restricts the destination.
 */
final class MiniSocks5 {
    private static final int SOCKS_VERSION = 5;
    private static final int AUTH_USERPASS = 2;
    private static final int CMD_CONNECT = 1;

    private MiniSocks5() {}

    static void serveOne(
            Socket client,
            ExecutorService io,
            String username,
            String password,
            String allowedHost,
            int allowedPort,
            SocketFactory outboundFactory) throws Exception {
        client.setSoTimeout(15_000);
        DataInputStream in = new DataInputStream(client.getInputStream());
        DataOutputStream out = new DataOutputStream(client.getOutputStream());

        int ver = u8(in);
        if (ver != SOCKS_VERSION) throw new IOException("SOCKS version != 5");
        int nMethods = u8(in);
        boolean supportsUserPass = false;
        for (int i = 0; i < nMethods; i++) {
            if (u8(in) == AUTH_USERPASS) supportsUserPass = true;
        }
        if (!supportsUserPass) {
            out.write(new byte[]{0x05, (byte) 0xff});
            out.flush();
            throw new IOException("client does not offer username/password auth");
        }
        out.write(new byte[]{0x05, 0x02});
        out.flush();

        // RFC 1929 username/password sub-negotiation.
        if (u8(in) != 1) throw new IOException("auth version != 1");
        String gotUser = readUtf8(in, u8(in));
        String gotPass = readUtf8(in, u8(in));
        boolean authOk = constantTimeEquals(username, gotUser) && constantTimeEquals(password, gotPass);
        out.write(new byte[]{0x01, (byte) (authOk ? 0x00 : 0x01)});
        out.flush();
        if (!authOk) throw new IOException("SOCKS auth failed");

        if (u8(in) != SOCKS_VERSION) throw new IOException("request version != 5");
        int cmd = u8(in);
        u8(in); // RSV
        int atyp = u8(in);
        String host = readAddress(in, atyp);
        int port = in.readUnsignedShort();

        if (cmd != CMD_CONNECT) {
            sendReply(out, 0x07); // command not supported
            throw new IOException("only CONNECT supported");
        }
        if (!allowedHost.equalsIgnoreCase(host) || port != allowedPort) {
            sendReply(out, 0x02); // connection not allowed by ruleset
            throw new IOException("destination not allowed: " + host + ":" + port);
        }

        Socket upstream = outboundFactory.createSocket();
        try {
            upstream.connect(new InetSocketAddress(host, port), 10_000);
            upstream.setSoTimeout(30_000);
            sendReply(out, 0x00);
            client.setSoTimeout(0);

            Future<?> a = io.submit(() -> pipe(client, upstream));
            Future<?> b = io.submit(() -> pipe(upstream, client));
            a.get();
            b.get();
        } finally {
            try { upstream.close(); } catch (Exception ignored) {}
        }
    }

    static void clientConnect(
            Socket socket,
            String username,
            String password,
            String host,
            int port) throws Exception {
        DataInputStream in = new DataInputStream(socket.getInputStream());
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());

        out.write(new byte[]{0x05, 0x01, 0x02});
        out.flush();
        require(u8(in) == 5 && u8(in) == 2, "server rejected auth method");

        byte[] u = username.getBytes(StandardCharsets.UTF_8);
        byte[] p = password.getBytes(StandardCharsets.UTF_8);
        if (u.length == 0 || u.length > 255 || p.length == 0 || p.length > 255) {
            throw new IOException("invalid SOCKS credential length");
        }
        out.writeByte(1);
        out.writeByte(u.length);
        out.write(u);
        out.writeByte(p.length);
        out.write(p);
        out.flush();
        require(u8(in) == 1 && u8(in) == 0, "SOCKS authentication failed");

        byte[] h = host.getBytes(StandardCharsets.US_ASCII);
        if (h.length == 0 || h.length > 255) throw new IOException("host too long");
        out.writeByte(5);
        out.writeByte(1); // CONNECT
        out.writeByte(0);
        out.writeByte(3); // DOMAIN
        out.writeByte(h.length);
        out.write(h);
        out.writeShort(port);
        out.flush();

        require(u8(in) == 5, "invalid reply version");
        int rep = u8(in);
        u8(in); // RSV
        int atyp = u8(in);
        skipAddress(in, atyp);
        in.readUnsignedShort();
        require(rep == 0, "SOCKS CONNECT failed rep=" + rep);
    }

    private static void sendReply(DataOutputStream out, int rep) throws IOException {
        // BND.ADDR/BND.PORT are not needed by this proof; return 0.0.0.0:0.
        out.write(new byte[]{0x05, (byte) rep, 0x00, 0x01, 0, 0, 0, 0, 0, 0});
        out.flush();
    }

    private static String readAddress(DataInputStream in, int atyp) throws IOException {
        switch (atyp) {
            case 1: {
                byte[] v4 = new byte[4];
                in.readFully(v4);
                return InetAddress.getByAddress(v4).getHostAddress();
            }
            case 3:
                return readUtf8(in, u8(in));
            case 4: {
                byte[] v6 = new byte[16];
                in.readFully(v6);
                return InetAddress.getByAddress(v6).getHostAddress();
            }
            default:
                throw new IOException("unsupported ATYP=" + atyp);
        }
    }

    private static void skipAddress(DataInputStream in, int atyp) throws IOException {
        switch (atyp) {
            case 1: skipFully(in, 4); break;
            case 3: skipFully(in, u8(in)); break;
            case 4: skipFully(in, 16); break;
            default: throw new IOException("unsupported reply ATYP=" + atyp);
        }
    }

    private static void pipe(Socket from, Socket to) {
        try {
            InputStream in = from.getInputStream();
            OutputStream out = to.getOutputStream();
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
                out.flush();
            }
            try { to.shutdownOutput(); } catch (Exception ignored) {}
        } catch (Exception ignored) {
            try { to.shutdownOutput(); } catch (Exception ignored2) {}
        }
    }

    private static int u8(DataInputStream in) throws IOException {
        return in.readUnsignedByte();
    }

    private static String readUtf8(DataInputStream in, int length) throws IOException {
        byte[] b = new byte[length];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    private static void skipFully(DataInputStream in, int n) throws IOException {
        int left = n;
        while (left > 0) {
            int skipped = in.skipBytes(left);
            if (skipped <= 0) {
                if (in.read() < 0) throw new EOFException();
                skipped = 1;
            }
            left -= skipped;
        }
    }

    private static boolean constantTimeEquals(String expected, String got) {
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = got.getBytes(StandardCharsets.UTF_8);
        int diff = a.length ^ b.length;
        int max = Math.max(a.length, b.length);
        for (int i = 0; i < max; i++) {
            int av = i < a.length ? a[i] & 0xff : 0;
            int bv = i < b.length ? b[i] & 0xff : 0;
            diff |= av ^ bv;
        }
        return diff == 0;
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) throw new IOException(message);
    }
}
