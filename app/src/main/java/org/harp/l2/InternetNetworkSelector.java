package org.harp.l2;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/** Selects an actual validated Internet egress for relay B. */
final class InternetNetworkSelector {
    private InternetNetworkSelector() {}

    static Network choose(ConnectivityManager cm) {
        Network active = cm.getActiveNetwork();
        if (isUsableInternet(cm, active)) return active;

        Network best = null;
        for (Network network : cm.getAllNetworks()) {
            if (!isUsableInternet(cm, network)) continue;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            // Prefer a direct non-VPN transport when possible. A VPN may still be
            // valid, but direct cellular/Wi-Fi is the cleanest Stage-1 proof.
            if (caps != null && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                return network;
            }
            if (best == null) best = network;
        }
        return best;
    }

    static boolean isUsableInternet(ConnectivityManager cm, Network network) {
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        if (caps == null) return false;
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE)) return false;
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    static String describe(ConnectivityManager cm, Network network) {
        if (network == null) return "null";
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return network + " caps=" + caps;
    }
}
