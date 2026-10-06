package org.harp.l2;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.Socket;

/** Peer admission policies for production Aware links and local tests. */
final class SocketPeerPolicies {
    private SocketPeerPolicies() {}

    static SocketPeerPolicy awareLinkLocalOnly() {
        return socket -> {
            InetAddress remote = socket.getInetAddress();
            if (!(remote instanceof Inet6Address) || !remote.isLinkLocalAddress()) {
                throw new IOException(
                        "relay peer is not IPv6 link-local: "
                                + socket.getRemoteSocketAddress());
            }
        };
    }

    static SocketPeerPolicy loopbackOnlyForTest() {
        return socket -> {
            InetAddress remote = socket.getInetAddress();
            if (remote == null || !remote.isLoopbackAddress()) {
                throw new IOException("test relay peer is not loopback");
            }
        };
    }
}
