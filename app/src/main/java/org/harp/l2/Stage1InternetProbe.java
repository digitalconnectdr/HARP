package org.harp.l2;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** HTTPS probe over the Stage-1 SOCKS5 relay. */
final class Stage1InternetProbe {
    static final String TARGET_HOST = "example.com";
    static final int TARGET_PORT = 443;
    static final String USER = "harp";
    static final String PASS = "harp-stage1-lab"; // LAB ONLY

    private Stage1InternetProbe() {}

    static String run(Socket awareSocket) throws Exception {
        return run(awareSocket, USER, PASS);
    }

    static String run(
            Socket awareSocket,
            String username,
            String password) throws Exception {
        awareSocket.setSoTimeout(15_000);
        MiniSocks5.clientConnect(
                awareSocket,
                username,
                password,
                TARGET_HOST,
                TARGET_PORT);

        SSLSocketFactory f = (SSLSocketFactory) SSLSocketFactory.getDefault();
        try (SSLSocket tls = (SSLSocket) f.createSocket(
                awareSocket, TARGET_HOST, TARGET_PORT, true)) {
            SSLParameters params = tls.getSSLParameters();
            params.setEndpointIdentificationAlgorithm("HTTPS");
            params.setServerNames(java.util.Collections.singletonList(new SNIHostName(TARGET_HOST)));
            tls.setSSLParameters(params);
            tls.setSoTimeout(15_000);
            tls.startHandshake();

            Certificate[] chain = tls.getSession().getPeerCertificates();
            if (chain.length == 0) throw new IllegalStateException("empty TLS certificate chain");

            PrintWriter w = new PrintWriter(
                    new OutputStreamWriter(tls.getOutputStream(), StandardCharsets.US_ASCII), true);
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(tls.getInputStream(), StandardCharsets.US_ASCII));
            w.print("HEAD / HTTP/1.1\r\nHost: " + TARGET_HOST
                    + "\r\nConnection: close\r\nUser-Agent: HARP-Stage1/0.1\r\n\r\n");
            w.flush();
            String status = r.readLine();
            if (status == null || !status.startsWith("HTTP/")) {
                throw new IllegalStateException("invalid HTTPS response: " + status);
            }
            return status;
        }
    }
}
