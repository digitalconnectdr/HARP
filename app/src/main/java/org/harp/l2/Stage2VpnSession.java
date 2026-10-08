package org.harp.l2;

import android.net.Network;

import java.net.Inet6Address;
import java.net.InetSocketAddress;

/** In-process handoff from the proven Aware relay to Stage-2A VpnService. */
final class Stage2VpnSession {
    private final Network awareNetwork;
    private final InetSocketAddress relayAddress;
    private final Stage2SessionCredentials credentials;

    Stage2VpnSession(
            Network awareNetwork,
            InetSocketAddress relayAddress,
            Stage2SessionCredentials credentials) {
        if (awareNetwork == null) {
            throw new IllegalArgumentException("awareNetwork is null");
        }
        if (relayAddress == null
                || !(relayAddress.getAddress() instanceof Inet6Address)
                || !relayAddress.getAddress().isLinkLocalAddress()
                || relayAddress.getPort() <= 0) {
            throw new IllegalArgumentException(
                    "relayAddress must be scoped IPv6 link-local");
        }
        if (credentials == null) {
            throw new IllegalArgumentException("credentials are null");
        }
        this.awareNetwork = awareNetwork;
        this.relayAddress = relayAddress;
        this.credentials = credentials;
    }

    Network awareNetwork() {
        return awareNetwork;
    }

    InetSocketAddress relayAddress() {
        return relayAddress;
    }

    Stage2SessionCredentials credentials() {
        return credentials;
    }
}
