package org.harp.l2;

import java.io.IOException;
import java.net.InetAddress;

/** Resolves a SOCKS destination through the caller-selected network. */
interface SocksAddressResolver {
    InetAddress[] resolve(String host) throws IOException;
}
