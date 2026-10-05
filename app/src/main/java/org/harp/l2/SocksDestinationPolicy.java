package org.harp.l2;

import java.io.IOException;
import java.net.InetAddress;

/** Authorization policy applied to every SOCKS CONNECT request and its resolved addresses. */
interface SocksDestinationPolicy {
    void validateRequest(String host, int port) throws IOException;
    void validateResolved(String host, int port, InetAddress[] addresses) throws IOException;
}
